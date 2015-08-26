package parsing;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.lang.Double;

import decoy.DecoyAS;
import scijava.stats.CDF;
import sim.Constants;

public class MaxParser {

	private HashMap<Integer, Double> asToIP;

	public static final String IP_FILE = "cash-nightwing/realTopo/whole-internet-20150101-ip.txt";
	public static final String OUTPUT_SUFFIX = "parsedLogs/";
	public static final String INPUT_SUFFIX = "rawLogs/";

	public static final Pattern ROUND_PATTERN = Pattern.compile("\\*\\*\\*(\\d+),(\\d+)");
	public static final Pattern SAMPLE_PATTERN = Pattern.compile("###(\\d+),(\\d+)");
	public static final Pattern WARDEN_PATTERN = Pattern.compile("(\\d+),([^,]+),([^,]+),([^,]+)");
	public static final Pattern TRANSIT_PATTERN = Pattern.compile("(\\d+),([^,]+),([^,]+),([a-zA-Z]+),?([E\\d\\.\\-]+)");

	private static double[] PERCENTILES = { 0.10, 0.25, 0.50, 0.75, 0.90 };

	private static final String DEPLOYER_LOG_ROUND_TERM = "###";

	private static final int IP_REACHABILITY_COL = 2;
	private static final int NC_REACHABILITY_COL = 4;

	public static void main(String[] args) throws IOException {
		String fileBase = args[0];
		MaxParser self = new MaxParser(IP_FILE);

		File baseFolder = new File(fileBase + INPUT_SUFFIX);
		File children[] = baseFolder.listFiles();

		for (File child : children) {
			if (!child.isDirectory()) {
				continue;
			}
			String suffix = child.getName();

			/*
			 * skip defection runs for parsing
			 */
			if (suffix.contains("DEFECTION")) {
				System.out.println("skipping defection run: " + suffix);
				continue;
			}

			File outDir = new File(fileBase + OUTPUT_SUFFIX + suffix);
			if (!outDir.exists()) {
				outDir.mkdirs();
			} else{
				System.out.println("Skiping " + suffix + " since it's already done.");
				continue;
			}

			System.out.println("Working on: " + suffix);
			String wardenFile = fileBase + MaxParser.INPUT_SUFFIX + suffix + "/warden.log";
			String transitFile = fileBase + MaxParser.INPUT_SUFFIX + suffix + "/transit.log";
			String pathFile = fileBase + MaxParser.INPUT_SUFFIX + suffix + "/path.log";

			self.writeDeployerLog(transitFile, fileBase + OUTPUT_SUFFIX + suffix + "/deployers.log");
			self.fullReachabilty(wardenFile, fileBase + OUTPUT_SUFFIX + suffix + "/wardenCleanBefore.csv", 1,
					MaxParser.IP_REACHABILITY_COL);
			self.fullReachabilty(wardenFile, fileBase + OUTPUT_SUFFIX + suffix + "/wardenCleanAfter.csv", 2,
					MaxParser.IP_REACHABILITY_COL);
			self.fullReachabilty(wardenFile, fileBase + OUTPUT_SUFFIX + suffix + "/nonCoopCleanBefore.csv", 1,
					MaxParser.NC_REACHABILITY_COL);
			self.fullReachabilty(wardenFile, fileBase + OUTPUT_SUFFIX + suffix + "/nonCoopCleanAfter.csv", 2,
					MaxParser.NC_REACHABILITY_COL);

			self.fullRevenueDetails(transitFile,
					fileBase + MaxParser.OUTPUT_SUFFIX + suffix + "/drTransitRevDelta.csv", fileBase
							+ MaxParser.OUTPUT_SUFFIX + suffix + "/drRevCDF.csv", true, false, 3);
			//self.fullRevenueDetails(transitFile, fileBase + MaxParser.OUTPUT_SUFFIX + suffix
			//		+ "/nonDRTransitRevDelta.csv", fileBase + MaxParser.OUTPUT_SUFFIX + suffix + "/nonDRRevCDF.csv",
			//		false, false, 3);
			//self.fullRevenueDetails(transitFile, fileBase + MaxParser.OUTPUT_SUFFIX + suffix
			//		+ "/resistorTransitRevDelta.csv", fileBase + MaxParser.OUTPUT_SUFFIX + suffix
			//		+ "/resistorRevCDF.csv", false, true, 3);

			self.parseRealMoney(transitFile, fileBase + MaxParser.OUTPUT_SUFFIX + suffix + "/deployerCosts");
			self.parseResistorRealMoney(transitFile, fileBase + MaxParser.OUTPUT_SUFFIX + suffix + "/resistorCosts");
			self.parsePathLength(wardenFile, pathFile, fileBase + MaxParser.OUTPUT_SUFFIX + suffix
					+ "/pathLenDeltas.csv", !pathFile.contains("NoRev"));
		}
	}

	public MaxParser(String ipFile) throws IOException {
		// FIXME please make this handle ASes that don't show up in the IP count
		// file
		this.asToIP = new HashMap<Integer, Double>();
		BufferedReader fBuff = new BufferedReader(new FileReader(ipFile));
		while (fBuff.ready()) {
			String pollString = fBuff.readLine().trim();
			if (pollString.length() == 0 || pollString.charAt(0) == '#') {
				continue;
			}
			StringTokenizer tokenizerTokens = new StringTokenizer(pollString, " ");
			int tAS = Integer.parseInt(tokenizerTokens.nextToken());
			double score = Double.parseDouble(tokenizerTokens.nextToken());
			this.asToIP.put(tAS, score);
		}
		fBuff.close();
	}

	private void fullRevenueDetails(String inFile, String outFile, String cdfFile, boolean isDR, boolean isResistor,
			int column) throws IOException {
		if (isDR && isResistor) {
			throw new RuntimeException("can't be both dr and resistor");
		}

		HashMap<Integer, HashMap<Double, Double>> results = this.computeProfitDeltas(inFile, isDR, isResistor, column,
				true, cdfFile);

		/*
		 * Slightly ghetto hack to get the sample sizes
		 */
		List<Integer> sampleSizes = new ArrayList<Integer>();
		for (int tKey : results.keySet()) {
			sampleSizes.add(tKey);
		}
		Collections.sort(sampleSizes);

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));
		this.writeTransitHeader(outBuff);
		for (int tSize : sampleSizes) {
			this.writePercentiles(tSize, results.get(tSize), outBuff);
			outBuff.write("\n");
		}
		outBuff.close();
	}

	private static HashSet<String> getWardens(String wardenFile) throws IOException {
		HashSet<String> wardenList = new HashSet<String>();

		BufferedReader inBuff = new BufferedReader(new FileReader(wardenFile));
		while (inBuff.ready()) {
			String pollStr = inBuff.readLine().trim();
			if (pollStr.equals("&&&")) {
				continue;
			}
			Matcher controlMatcher = MaxParser.ROUND_PATTERN.matcher(pollStr);
			boolean controlFlag = false;
			if (controlMatcher.find()) {
				controlFlag = true;
			} else {
				controlMatcher = MaxParser.SAMPLE_PATTERN.matcher(pollStr);
				if (controlMatcher.find()) {
					controlFlag = true;
				}
			}

			if (controlFlag && wardenList.size() > 0) {
				break;
			}

			if (!controlFlag) {
				String[] splits = pollStr.split(",");
				wardenList.add(splits[0]);
			}
		}
		inBuff.close();

		return wardenList;
	}

	private HashMap<Integer, HashMap<Double, Double>> computeProfitDeltas(String inFile, boolean isDR,
			boolean isResistor, int column, boolean normalized, String profitCDFFile) throws IOException {
		HashMap<Integer, Double> firstRoundValue = new HashMap<Integer, Double>();
		HashMap<Integer, HashMap<Double, Double>> results = new HashMap<Integer, HashMap<Double, Double>>();
		List<Double> sampleDeltas = new ArrayList<Double>();
		List<Collection<Double>> fullDeltas = new ArrayList<Collection<Double>>();
		BufferedReader inBuff = new BufferedReader(new FileReader(inFile));
		int sampleSize = 0;
		int oldSize = 0;

		int roundFlag = 0;
		while (inBuff.ready()) {
			String pollStr = inBuff.readLine().trim();

			Matcher controlMatcher = MaxParser.ROUND_PATTERN.matcher(pollStr);
			boolean controlFlag = false;
			if (controlMatcher.find()) {
				controlFlag = true;
			} else {
				controlMatcher = MaxParser.SAMPLE_PATTERN.matcher(pollStr);
				if (controlMatcher.find()) {
					controlFlag = true;
				}
			}

			if (controlFlag) {
				roundFlag = Integer.parseInt(controlMatcher.group(2));
				oldSize = sampleSize;
				sampleSize = Integer.parseInt(controlMatcher.group(1));

				/*
				 * We're ready to actually extract deltas
				 */
				if (roundFlag == 0) {
					firstRoundValue.clear();
					if (oldSize != sampleSize && oldSize != 0) {
						HashMap<Double, Double> extractMap = new HashMap<Double, Double>();
						List<Double> listClone = new ArrayList<Double>(sampleDeltas.size());
						for (double tVal : sampleDeltas) {
							listClone.add(-1.0 * tVal);
						}
						fullDeltas.add(listClone);
						for (double tPercent : MaxParser.PERCENTILES) {
							extractMap.put(tPercent, this.extractValue(sampleDeltas, tPercent));
						}
						results.put(oldSize, extractMap);
						sampleDeltas.clear();
					}

				}

				continue;
			}

			if (roundFlag != 0) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (Boolean.parseBoolean(dataMatch.group(3)) == isDR
							&& Boolean.parseBoolean(dataMatch.group(4)) == isResistor) {
						if (roundFlag == 1) {
							firstRoundValue.put(Integer.parseInt(dataMatch.group(1)),
									Double.parseDouble(dataMatch.group(2)));
						} else {
							double delta = Double.parseDouble(dataMatch.group(2))
									- firstRoundValue.get(Integer.parseInt(dataMatch.group(1)));
							if (delta != 0.0) {
								if (normalized) {
									sampleDeltas.add(100.0 * delta
											/ Math.abs(firstRoundValue.get(Integer.parseInt(dataMatch.group(1)))));
								} else {
									sampleDeltas.add(delta);
								}
							}
						}
					}
				}
			}
		}
		inBuff.close();

		HashMap<Double, Double> extractMap = new HashMap<Double, Double>();
		List<Double> listClone = new ArrayList<Double>(sampleDeltas.size());
		for (double tVal : sampleDeltas) {
			listClone.add(-1.0 * tVal);
		}
		fullDeltas.add(listClone);
		for (double tPercent : MaxParser.PERCENTILES) {
		    if(sampleDeltas.size() > 0 ){
			extractMap.put(tPercent, this.extractValue(sampleDeltas, tPercent));
		    }else{
			extractMap.put(tPercent, 0.0);
		    }
		}
		results.put(oldSize, extractMap);
		sampleDeltas.clear();

		CDF.printCDFs(fullDeltas, profitCDFFile);

		return results;
	}

	private void writeDeployerLog(String transitFile, String outFile) throws IOException {
		BufferedReader inBuff = new BufferedReader(new FileReader(transitFile));
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));

		boolean inRegion = false;
		while (inBuff.ready()) {
			String pollLine = inBuff.readLine().trim();

			Matcher roundLineMatcher = MaxParser.ROUND_PATTERN.matcher(pollLine);
			if (roundLineMatcher.matches()) {
				int roundFlag = Integer.parseInt(roundLineMatcher.group(2));
				inRegion = (roundFlag == 1);
				if (inRegion) {
					outBuff.write(MaxParser.DEPLOYER_LOG_ROUND_TERM + "\n");
				}
			} else if (inRegion) {
				Matcher dataLineMatcher = MaxParser.TRANSIT_PATTERN.matcher(pollLine);
				if (dataLineMatcher.matches()) {
					if (Boolean.parseBoolean(dataLineMatcher.group(3))) {
						outBuff.write(dataLineMatcher.group(1) + "\n");
					}
				}
			}
		}

		inBuff.close();
		outBuff.close();
	}

	public static List<Set<Integer>> parseDeployerLog(String deployerLog) throws IOException {
		List<Set<Integer>> retList = new LinkedList<Set<Integer>>();
		BufferedReader inBuff = new BufferedReader(new FileReader(deployerLog));
		while (inBuff.ready()) {
			String pollString = inBuff.readLine().trim();

			if (pollString.length() > 0) {
				if (pollString.equals(MaxParser.DEPLOYER_LOG_ROUND_TERM)) {
					retList.add(new HashSet<Integer>());
				} else {
					retList.get(retList.size() - 1).add(Integer.parseInt(pollString));
				}
			}
		}
		inBuff.close();

		return retList;
	}

	private void computeFullWardenReachabilityDeltas(String inFile, String outFile) throws IOException {
		HashMap<Double, HashMap<Integer, List<Double>>> valuesMap = new HashMap<Double, HashMap<Integer, List<Double>>>();
		for (int counter = 0; counter < MaxParser.PERCENTILES.length; counter++) {
			valuesMap.put(MaxParser.PERCENTILES[counter],
					this.computeWardenReacabilityDeltaForPercentile(inFile, MaxParser.PERCENTILES[counter], 2, true));
		}

		// IO magic
		ArrayList<Integer> sampleSizes = new ArrayList<Integer>();
		for (int tSize : valuesMap.get(MaxParser.PERCENTILES[0]).keySet()) {
			sampleSizes.add(tSize);
		}
		Collections.sort(sampleSizes);
		HashMap<Integer, HashMap<Double, Double>> invertedMap = this.invertParsingIndicies(valuesMap, sampleSizes);

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));
		this.writeWardenHeader(outBuff, 2);
		for (int tSize : sampleSizes) {
			this.writePercentiles(tSize, invertedMap.get(tSize), outBuff);
			outBuff.write("\n");
		}
		outBuff.close();
	}

	/**
	 * Parses delta in warden reachability. It outputs a map that has the Nth
	 * percentile value for change in clean paths after warden for each sample,
	 * the map is indexed by sample size.
	 * 
	 * @param inFile
	 * @param percentile
	 * @param column
	 *            2 for IP based stats, 3 for AS based stats
	 * @return
	 * @throws IOException
	 */
	private HashMap<Integer, List<Double>> computeWardenReacabilityDeltaForPercentile(String inFile, double percentile,
			int column, boolean normalized) throws IOException {
		BufferedReader inBuff = new BufferedReader(new FileReader(inFile));
		HashMap<Integer, List<Double>> deltaValues = new HashMap<Integer, List<Double>>();
		HashMap<Integer, Double> firstRoundValue = new HashMap<Integer, Double>();
		List<Double> deltasForThisSample = new ArrayList<Double>();

		int roundFlag = 0;
		while (inBuff.ready()) {
			boolean controlFlag = false;
			int roundValue = -1;
			int sampleSize = -1;
			String pollStr = inBuff.readLine().trim();

			/*
			 * figure out if we're in a control sequence
			 */
			Matcher sampleMatch = MaxParser.SAMPLE_PATTERN.matcher(pollStr);
			if (sampleMatch.find()) {
				controlFlag = true;
			} else {
				sampleMatch = MaxParser.ROUND_PATTERN.matcher(pollStr);
				if (sampleMatch.find()) {
					controlFlag = true;
				}
			}

			if (controlFlag) {
				sampleSize = Integer.parseInt(sampleMatch.group(1));
				roundValue = Integer.parseInt(sampleMatch.group(2));

				roundFlag = roundValue;
				/*
				 * If we're at the top of any sample other than the first, parse
				 * the results of the last sample
				 */
				if (roundValue == (0) && deltasForThisSample.size() > 0) {
					if (!deltaValues.containsKey(sampleSize)) {
						deltaValues.put(sampleSize, new ArrayList<Double>());
					}
					deltaValues.get(sampleSize).add(this.extractValue(deltasForThisSample, percentile));

					firstRoundValue.clear();
					deltasForThisSample.clear();
				}
				continue;
			}

			if (roundFlag != 0) {
				Matcher dataMatch = MaxParser.WARDEN_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (roundFlag == 1) {
						firstRoundValue.put(Integer.parseInt(dataMatch.group(1)),
								Double.parseDouble(dataMatch.group(column)));
					} else {
						if (!normalized) {
							deltasForThisSample.add(Double.parseDouble(dataMatch.group(column))
									- firstRoundValue.get(Integer.parseInt(dataMatch.group(1))));
						} else {
							deltasForThisSample.add((Double.parseDouble(dataMatch.group(column)) - firstRoundValue
									.get(Integer.parseInt(dataMatch.group(1))))
									/ (1.0 - firstRoundValue.get(Integer.parseInt(dataMatch.group(1)))) * 100.0);
						}
					}
				}
			}
		}
		inBuff.close();

		return deltaValues;
	}

	/**
	 * Master function for building warden reachability stats.
	 * 
	 * @param infile
	 * @param outFile
	 * @param round
	 *            should be 1 if you want the reachability before reaction, 2 if
	 *            you want reachability after reaction
	 * @param column
	 *            should be 2 if you want IP based information, 3 if you want AS
	 *            based information
	 * @throws IOException
	 */
	public void fullReachabilty(String infile, String outFile, int round, int column) throws IOException {

		/*
		 * Actually do the computations
		 */
		HashMap<Integer, List<Double>> values = this.parseWardenReachability(infile, round, column);

		/*
		 * Invert the mapping
		 */
		List<Integer> sampleSizes = new LinkedList<Integer>();
		for (int tSize : values.keySet()) {
			sampleSizes.add(tSize);
		}
		Collections.sort(sampleSizes);

		HashMap<Integer, HashMap<Double, Double>> invertedMap = new HashMap<Integer, HashMap<Double, Double>>();
		for (int tSize : values.keySet()) {
			invertedMap.put(tSize, new HashMap<Double, Double>());
			for (double tPerc : MaxParser.PERCENTILES) {
				invertedMap.get(tSize).put(tPerc, this.extractValue(values.get(tSize), tPerc));
			}
		}

		/*
		 * IO magic gogo
		 */
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));
		this.writeWardenHeader(outBuff, column);
		for (int tSize : sampleSizes) {
			this.writePercentiles(tSize, invertedMap.get(tSize), outBuff);
			outBuff.write("\n");
		}
		outBuff.close();

		MaxParser.printCDF(values, outFile + "-CDF");
	}

	/**
	 * Parses warden reachability information. It outputs a map that has the Nth
	 * percentile value for clean paths for each sample, the map is indexed by
	 * sample size.
	 * 
	 * @param inFile
	 * @param round
	 *            should be 1 for before warden reaction, 2 for after warden
	 *            reaction
	 * @param column
	 *            should be 2 for IP based stats, 3 for AS based stats
	 * @return
	 * @throws IOException
	 */
	private HashMap<Integer, List<Double>> parseWardenReachability(String inFile, int round, int column)
			throws IOException {
		BufferedReader inBuff = new BufferedReader(new FileReader(inFile));
		HashMap<Integer, List<Double>> values = new HashMap<Integer, List<Double>>();
		double currentIPCleanness = 0.0;
		double currentIPCount = 0.0;

		boolean inTargetRound = false;
		int sampleSize = -1;
		while (inBuff.ready()) {
			boolean controlFlag = false;
			int roundValue = -1;

			String pollStr = inBuff.readLine().trim();

			/*
			 * Hunt if we're on a "control" line
			 */
			Matcher sampleMatch = MaxParser.SAMPLE_PATTERN.matcher(pollStr);
			if (sampleMatch.find()) {
				controlFlag = true;
			} else {
				sampleMatch = MaxParser.ROUND_PATTERN.matcher(pollStr);
				if (sampleMatch.find()) {
					controlFlag = true;
				}
			}

			/*
			 * Handle the control line case (start logging, or stop logging and
			 * dump stats, or nothing...)
			 */
			if (controlFlag) {
				int newSampleSize = Integer.parseInt(sampleMatch.group(1));
				roundValue = Integer.parseInt(sampleMatch.group(2));

				/*
				 * If it's the round we're told to record for, turn on value
				 * parsing and reset a list
				 */
				if (roundValue == round) {
					inTargetRound = true;
					currentIPCleanness = 0.0;
					currentIPCount = 0.0;
				}
				/*
				 * If it's the round directly after our round, then we should
				 * actually parse the list, extracting the Nth percentile and
				 * record it
				 */
				if (roundValue == ((round + 1) % 3) && inTargetRound) {
					// stop logging
					inTargetRound = false;
					/*
					 * Dump in the list if it doesn't exist in the map
					 */
					if (!values.containsKey(sampleSize)) {
						values.put(sampleSize, new ArrayList<Double>());
					}
					values.get(sampleSize).add(currentIPCleanness / currentIPCount);
				}
				/*
				 * No matter what we should skip the attempt to parse the line
				 * as a data line
				 */
				sampleSize = newSampleSize;
				continue;
			}

			/*
			 * If we're in a log region we care about, parse it
			 */
			if (inTargetRound) {
				Matcher dataMatch = MaxParser.WARDEN_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					Double ips = this.asToIP.get(Integer.parseInt(dataMatch.group(1)));
					if (ips != null) {
						double cleanness = Double.parseDouble(dataMatch.group(column));
						
						/*
						 * Don't let NaN taint our measurements
						 */
						if(!Double.isNaN(cleanness)){
						    currentIPCount += ips;
						    currentIPCleanness += cleanness * ips;
						}
					}
				}
			}
		}

		/*
		 * If we're looking at the last round, then we won't see a control flag,
		 * so do it manually
		 */
		if (round == 2) {
			// stop logging
			inTargetRound = false;
			/*
			 * Dump in the list if it doesn't exist in the map
			 */
			if (!values.containsKey(sampleSize)) {
				values.put(sampleSize, new ArrayList<Double>());
			}
			values.get(sampleSize).add(currentIPCleanness / currentIPCount);
		}

		inBuff.close();

		return values;
	}

	private void writeTransitHeader(BufferedWriter outBuffer) throws IOException {
		StringBuilder header = new StringBuilder();

		for (int innerCounter = 0; innerCounter < MaxParser.PERCENTILES.length; innerCounter++) {
			if (innerCounter != 0) {
				header.append(",");
			}
			header.append("sample size,");
			header.append(Double.toString(MaxParser.PERCENTILES[innerCounter]));
			header.append(" percentage profit delta");
		}
		header.append("\n");

		outBuffer.write(header.toString());
	}

	private void writeWardenHeader(BufferedWriter outBuffer, int column) throws IOException {
		StringBuilder header = new StringBuilder();

		for (int innerCounter = 0; innerCounter < MaxParser.PERCENTILES.length; innerCounter++) {
			if (innerCounter != 0) {
				header.append(",");
			}
			header.append("sample size,");
			header.append(Double.toString(MaxParser.PERCENTILES[innerCounter]));
			if (column == 2) {
				header.append(" by IP count");
			} else if (column == 3) {
				header.append(" by AS count");
			}
		}
		header.append("\n");
		outBuffer.write(header.toString());
	}

	/**
	 * Helper function to write out to a file what is assumed to be a mapping
	 * between certain percentiles and their represenetative values from another
	 * list. Basically all this does is write on a single line the sample size,
	 * a comma, and then the percentile values in ascending percentile order.
	 * Does some assurances that interator order doesn't somehow screw us.
	 * 
	 * @param size
	 * @param values
	 * @param outBuffer
	 * @throws IOException
	 */
	private void writePercentiles(int size, HashMap<Double, Double> values, BufferedWriter outBuffer)
			throws IOException {
		StringBuilder outStr = new StringBuilder();
		for (int counter = 0; counter < MaxParser.PERCENTILES.length; counter++) {
			if (counter != 0) {
				outStr.append(",");
			}
			outStr.append(Integer.toString(size));
			outStr.append(",");
			outStr.append(Double.toString(values.get(MaxParser.PERCENTILES[counter])));
		}
		outBuffer.write(outStr.toString());
	}

	/**
	 * Takes a mapping of percentiles to a second mapping of DR sizes to Nth
	 * percentile values for each sample, and converts it into something we can
	 * output. Specificially a mapping from DR sizes to a second mapping between
	 * percentiles and the median value of the Nth percentile seen across all
	 * corrisponding samples of.
	 * 
	 * Long as the short, if you are keeping with the paradim of the above
	 * parsing, call this function before doing IO.
	 * 
	 * @param original
	 * @param sampleSizes
	 * @return
	 */
	private HashMap<Integer, HashMap<Double, Double>> invertParsingIndicies(
			HashMap<Double, HashMap<Integer, List<Double>>> original, List<Integer> sampleSizes) {
		/*
		 * Build data struct to return result, seed in intial maps
		 */
		HashMap<Integer, HashMap<Double, Double>> result = new HashMap<Integer, HashMap<Double, Double>>();
		for (int counter = 0; counter < sampleSizes.size(); counter++) {
			result.put(sampleSizes.get(counter), new HashMap<Double, Double>());
		}

		/*
		 * Iterate through the original, extracting medians and filling them
		 * into index
		 */
		for (int counter = 0; counter < MaxParser.PERCENTILES.length; counter++) {
			double tPerctile = MaxParser.PERCENTILES[counter];
			HashMap<Integer, List<Double>> tempMap = original.get(tPerctile);
			for (Integer tSize : tempMap.keySet()) {
				List<Double> tList = tempMap.get(tSize);
				double value = this.extractValue(tList, 0.5);
				result.get(tSize).put(tPerctile, value);
			}
		}

		return result;
	}

	/**
	 * Simple function to extract the Nth percentile value of a list. Has the
	 * side effect of sorting the list.
	 * 
	 * @param valueList
	 *            - a list of doubles
	 * @param percentile
	 *            - the percentile you want to extract
	 * @return
	 */
	private double extractValue(List<Double> valueList, double percentile) {
		Collections.sort(valueList);
		int pos = (int) Math.floor(percentile * valueList.size());
		return valueList.get(pos);
	}

	public static void printCDF(HashMap<Integer, List<Double>> values, String outfile) throws IOException {
		int maxSize = 0;
		List<Integer> keyList = new LinkedList<Integer>();
		keyList.addAll(values.keySet());
		Collections.sort(keyList);

		for (List<Double> tList : values.values()) {
			Collections.sort(tList);
			maxSize = Math.max(maxSize, tList.size());
		}

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outfile));
		String header = "";
		for (int key : keyList) {
			header += "," + key + ",";
		}
		header += "\n";
		outBuff.write(header);

		for (int counter = 0; counter < maxSize; counter++) {
			for (int listCounter = 0; listCounter < keyList.size(); listCounter++) {
				if (listCounter != 0) {
					outBuff.write(",");
				}
				List<Double> tList = values.get(keyList.get(listCounter));
				if (tList.size() > counter) {
					outBuff.write(tList.get(counter) + "," + Double.toString((double) counter / (double) tList.size()));
				} else {
					outBuff.write(",");
				}
			}
			outBuff.write("\n");
		}
		outBuff.close();
	}

	public void parseRealMoney(String logFile, String outFile) throws IOException {

		HashMap<Integer, HashMap<Integer, Double>> roundResults = new HashMap<Integer, HashMap<Integer, Double>>();

		HashMap<Integer, Double> roundValues = null;
		HashMap<Integer, Double> firstRoundValues = new HashMap<Integer, Double>();
		BufferedReader inBuff = new BufferedReader(new FileReader(logFile));
		int roundFlag = 0;
		int sampleSize = 0;
		while (inBuff.ready()) {
			String pollStr = inBuff.readLine().trim();

			Matcher controlMatcher = MaxParser.ROUND_PATTERN.matcher(pollStr);
			boolean controlFlag = false;
			if (controlMatcher.find()) {
				controlFlag = true;
			} else {
				controlMatcher = MaxParser.SAMPLE_PATTERN.matcher(pollStr);
				if (controlMatcher.find()) {
					controlFlag = true;
				}
			}

			if (controlFlag) {
				roundFlag = Integer.parseInt(controlMatcher.group(2));
				int newSampleSize = Integer.parseInt(controlMatcher.group(1));

				/*
				 * We're ready to actually extract deltas
				 */
				if (roundFlag == 0 && sampleSize != 0) {
					firstRoundValues.clear();
					roundResults.put(sampleSize, roundValues);
				}

				roundValues = new HashMap<Integer, Double>();
				sampleSize = newSampleSize;
				continue;
			}

			if (roundFlag != 0) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (Boolean.parseBoolean(dataMatch.group(3)) == true) {
						if (roundFlag == 1) {
							firstRoundValues.put(Integer.parseInt(dataMatch.group(1)),
									Double.parseDouble(dataMatch.group(2)));
						} else {
							int asn = Integer.parseInt(dataMatch.group(1));
							double delta = Double.parseDouble(dataMatch.group(2)) - firstRoundValues.get(asn);
							double value = MaxParser.convertTrafficToDollars(delta);
							roundValues.put(asn, value);
						}
					}
				}
			}
		}
		inBuff.close();
		/*
		 * Dont' forget to do the last round
		 */
		firstRoundValues.clear();
		roundResults.put(sampleSize, roundValues);

		List<Integer> roundSizes = new ArrayList<Integer>(roundResults.size());
		roundSizes.addAll(roundResults.keySet());
		Collections.sort(roundSizes);

		/*
		 * Output CDF of the costs for each round on a per AS basis
		 */
		List<Collection<Double>> cdfLists = new ArrayList<Collection<Double>>(roundSizes.size());
		for (int tSize : roundSizes) {
			cdfLists.add(roundResults.get(tSize).values());
		}
		CDF.printCDFs(cdfLists, outFile + "-CDF.csv");

		/*
		 * Output the total cost of each round
		 */
		List<Double> perRoundCost = new ArrayList<Double>(roundResults.size());
		for (int size : roundSizes) {
			double sum = 0;
			for (Double tVal : roundResults.get(size).values()) {
				sum += tVal;
			}
			perRoundCost.add(sum);
		}
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile + "-totalCost.csv"));
		for (int counter = 0; counter < perRoundCost.size(); counter++) {
			outBuff.write("" + roundSizes.get(counter) + "," + perRoundCost.get(counter) + "\n");
		}
		outBuff.close();
	}

	public void parseResistorRealMoney(String logFile, String outFile) throws IOException {

		HashMap<Integer, HashMap<Integer, Double>> roundResults = new HashMap<Integer, HashMap<Integer, Double>>();

		HashMap<Integer, Double> roundValues = null;
		HashMap<Integer, Double> firstRoundValues = new HashMap<Integer, Double>();
		BufferedReader inBuff = new BufferedReader(new FileReader(logFile));
		int roundFlag = 0;
		int sampleSize = 0;
		while (inBuff.ready()) {
			String pollStr = inBuff.readLine().trim();

			Matcher controlMatcher = MaxParser.ROUND_PATTERN.matcher(pollStr);
			boolean controlFlag = false;
			if (controlMatcher.find()) {
				controlFlag = true;
			} else {
				controlMatcher = MaxParser.SAMPLE_PATTERN.matcher(pollStr);
				if (controlMatcher.find()) {
					controlFlag = true;
				}
			}

			if (controlFlag) {
				roundFlag = Integer.parseInt(controlMatcher.group(2));
				int newSampleSize = Integer.parseInt(controlMatcher.group(1));

				/*
				 * We're ready to actually extract deltas
				 */
				if (roundFlag == 0 && sampleSize != 0) {
					firstRoundValues.clear();
					roundResults.put(sampleSize, roundValues);
				}

				roundValues = new HashMap<Integer, Double>();
				sampleSize = newSampleSize;
				continue;
			}

			if (roundFlag != 0) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (Boolean.parseBoolean(dataMatch.group(4)) == true) {
						if (roundFlag == 1) {
							firstRoundValues.put(Integer.parseInt(dataMatch.group(1)),
									Double.parseDouble(dataMatch.group(5)));
						} else {
							int asn = Integer.parseInt(dataMatch.group(1));
							double delta = Double.parseDouble(dataMatch.group(5)) - firstRoundValues.get(asn);
							double value = MaxParser.convertTrafficToDollars(delta);
							if (value > 0.0) {
								roundValues.put(asn, 0.0);
							} else {
								roundValues.put(asn, value);
							}
						}
					}
				}
			}
		}
		inBuff.close();
		/*
		 * Dont' forget to do the last round
		 */
		firstRoundValues.clear();
		roundResults.put(sampleSize, roundValues);

		List<Integer> roundSizes = new ArrayList<Integer>(roundResults.size());
		roundSizes.addAll(roundResults.keySet());
		Collections.sort(roundSizes);

		/*
		 * Output CDF of the costs for each round on a per AS basis
		 */
		List<Collection<Double>> cdfLists = new ArrayList<Collection<Double>>(roundSizes.size());
		for (int tSize : roundSizes) {
			cdfLists.add(roundResults.get(tSize).values());
		}
		CDF.printCDFs(cdfLists, outFile + "-CDF.csv");

		/*
		 * Output the total cost of each round
		 */
		List<Double> perRoundCost = new ArrayList<Double>(roundResults.size());
		for (int size : roundSizes) {
			double sum = 0;
			for (Double tVal : roundResults.get(size).values()) {
				sum += tVal;
			}
			perRoundCost.add(sum);
		}
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile + "-totalCost.csv"));
		for (int counter = 0; counter < perRoundCost.size(); counter++) {
			outBuff.write("" + roundSizes.get(counter) + "," + perRoundCost.get(counter) + "\n");
		}
		outBuff.close();
	}

	public static double convertTrafficToDollars(double amount) {
		/*
		 * Currently units of milUSD / sim units Source:
		 * https://www.telegeography
		 * .com/research-services/ip-transit-forecast-service/index.html
		 */
		return amount * 0.000000008154451706;
	}

	public void parsePathLength(String wardenFile, String pathLogFile, String outFile, boolean revWarn)
			throws IOException {

		boolean inParseRegion = false;
		boolean inFirstRun = true;

		HashSet<String> wardenSet = MaxParser.getWardens(wardenFile);

		HashMap<String, Integer> pathHashes = new HashMap<String, Integer>();
		HashMap<String, Integer> lengthMap = new HashMap<String, Integer>();

		BufferedReader pathBuffer = new BufferedReader(new FileReader(pathLogFile));
		BufferedWriter fromResistBuffer = new BufferedWriter(new FileWriter(outFile + "-fromResist.csv"));
		BufferedWriter toResistBuffer = new BufferedWriter(new FileWriter(outFile + "-toResist.csv"));
		fromResistBuffer.write("size,mean,median,count\n");

		List<Integer> fromResistDeltas = new LinkedList<Integer>();
		List<Integer> toResistDeltas = new LinkedList<Integer>();

		int sampleSize = 0;
		while (pathBuffer.ready()) {
			String pollStr = pathBuffer.readLine().trim();

			Matcher controlMatcher = MaxParser.ROUND_PATTERN.matcher(pollStr);
			boolean controlFlag = false;
			if (controlMatcher.find()) {
				controlFlag = true;
			} else {
				controlMatcher = MaxParser.SAMPLE_PATTERN.matcher(pollStr);
				if (controlMatcher.find()) {
					controlFlag = true;
				}
			}

			if (controlFlag) {
				int roundFlag = Integer.parseInt(controlMatcher.group(2));
				int newSampleSize = Integer.parseInt(controlMatcher.group(1));

				if ((roundFlag == 0 && pathHashes.size() == 0) || roundFlag == 2) {
					inParseRegion = true;
				} else {
					if (inParseRegion && inFirstRun) {
						inFirstRun = false;
					} else if (inParseRegion) {
						double fromMean = MaxParser.getMean(fromResistDeltas);
						double toMean = MaxParser.getMean(toResistDeltas);
						double fromMedian = MaxParser.getMedian(fromResistDeltas);
						double toMedian = MaxParser.getMedian(toResistDeltas);

						fromResistBuffer.write(sampleSize + "," + fromMean + "," + fromMedian + ","
								+ fromResistDeltas.size() + "\n");
						fromResistDeltas.clear();
						toResistBuffer.write(sampleSize + "," + toMean + "," + toMedian + "," + toResistDeltas.size()
								+ "\n");
						toResistDeltas.clear();
					}
					inParseRegion = false;
				}

				sampleSize = newSampleSize;
				continue;
			} else if (inParseRegion && pollStr.length() > 0) {
				String[] splits = pollStr.split(",");
				if (splits.length != 2) {
					continue;
				}

				String pathKey = splits[0];
				String pathStr = splits[1].trim();

				String[] srcDestPair = pathKey.split(":");
				boolean fromWarden = wardenSet.contains(srcDestPair[0]);
				boolean toWarden = wardenSet.contains(srcDestPair[1]);

				if (revWarn) {
					String triggerStr = " " + srcDestPair[1] + " ";
					String modPathStr = " " + pathStr;
					int pos = modPathStr.indexOf(triggerStr);
					if (pos != -1) {
						String nonLyingPathStr = modPathStr.substring(0, pos + triggerStr.length());
						pathStr = nonLyingPathStr.trim();
					}
				}

				if (inFirstRun) {
					pathHashes.put(pathKey, pathStr.hashCode());
					lengthMap.put(pathKey, pathStr.split(" ").length);
				} else if (pathHashes.get(pathKey) != pathStr.hashCode()) {
					int tempDelta = pathStr.split(" ").length - lengthMap.get(pathKey);
					if (fromWarden) {
						fromResistDeltas.add(tempDelta);
					}
					if (toWarden) {
						toResistDeltas.add(tempDelta);
					}
					if (!(fromWarden || toWarden)) {
						System.out.println("fuuuu " + pathKey);
					}
				}
			}

		}

		pathBuffer.close();
		fromResistBuffer.close();
		toResistBuffer.close();
	}

	private static double getMean(List<Integer> valList) {
		double delta = 0.0;
		for (int tVal : valList) {
			delta += (double) tVal;
		}

		return delta / (double) valList.size();
	}

	private static double getMedian(List<Integer> valList) {
		double median = 0;
		Collections.sort(valList);
		if (valList.size() % 2 == 0) {
			median = ((double) valList.get(valList.size() / 2) - (double) valList.get(valList.size() / 2 - 1)) / 2.0;
		} else {
			median = valList.get(valList.size() / 2);
		}

		return median;
	}
}
