package parsing;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class MaxParser {

	private HashMap<Integer, Double> asToIP;

	private static final String IP_FILE = "/scratch/minerva/schuch/cash-nightwing/ip-count.csv";
	private static final String FILE_BASE = "/scratch/minerva2/public/nightwingData/";
	private static final String OUTPUT_SUFFIX = "parsingResults_Run";
	private static final String INPUT_SUFFIX = "rawData/";

	public static final Pattern ROUND_PATTERN = Pattern.compile("\\*\\*\\*(\\d+),(\\d+)");
	public static final Pattern SAMPLE_PATTERN = Pattern.compile("###(\\d+),(\\d+)");
	public static final Pattern WARDEN_PATTERN = Pattern.compile("(\\d+),([^,]+),([^,]+)");
	public static final Pattern TRANSIT_PATTERN = Pattern.compile("(\\d+),([^,]+),([^,]+),(.+)");

	private static double[] PERCENTILES = { 0.10, 0.25, 0.50, 0.75, 0.90 };

	public static void main(String[] args) throws IOException {
		MaxParser self = new MaxParser(MaxParser.IP_FILE);
		for (String suffix : args) {
			System.out.println("Working on: " + suffix);
			String wardenFile = MaxParser.FILE_BASE + MaxParser.INPUT_SUFFIX + "warden-" + suffix.toLowerCase()
					+ ".log";
			String transitFile = MaxParser.FILE_BASE + MaxParser.INPUT_SUFFIX + "transit-" + suffix.toLowerCase()
					+ ".log";

			self.fullReachabilty(wardenFile, MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix
					+ "/wardenCleanBefore.csv", 1, 2);
			self.fullReachabilty(wardenFile, MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix
					+ "/wardenCleanAfter.csv", 2, 2);
			// self.computeFullWardenReachabilityDeltas(wardenFile,
			// MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix
			// + "/wardenCleanDelta.csv");
			// self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
			// MaxParser.OUTPUT_SUFFIX + suffix
			// + "/drProfitDelta.csv", true, 2);
			// self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
			// MaxParser.OUTPUT_SUFFIX + suffix
			// + "/nonDRProfitDelta.csv", false, 2);
			// self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
			// MaxParser.OUTPUT_SUFFIX + suffix
			// + "/drTransitProfitDelta.csv", true, 3);
			// self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
			// MaxParser.OUTPUT_SUFFIX + suffix
			// + "/nonDRTransitProfitDelta.csv", false, 3);
		}
	}

	public MaxParser(String ipFile) throws IOException {
		this.asToIP = new HashMap<Integer, Double>();
		BufferedReader fBuff = new BufferedReader(new FileReader(ipFile));
		while (fBuff.ready()) {
			String pollString = fBuff.readLine().trim();
			if (pollString.length() == 0 || pollString.charAt(0) == '#') {
				continue;
			}
			StringTokenizer tokenizerTokens = new StringTokenizer(pollString, ",");
			int tAS = Integer.parseInt(tokenizerTokens.nextToken());
			double score = Double.parseDouble(tokenizerTokens.nextToken());
			this.asToIP.put(tAS, score);
		}
		fBuff.close();
	}

	private void fullProfitDeltas(String inFile, String outFile, boolean isDR, int column) throws IOException {
		HashMap<Double, HashMap<Integer, List<Double>>> results = new HashMap<Double, HashMap<Integer, List<Double>>>();
		for (int counter = 0; counter < MaxParser.PERCENTILES.length; counter++) {
			results.put(MaxParser.PERCENTILES[counter],
					this.computeProfitDeltas(inFile, isDR, column, MaxParser.PERCENTILES[counter], true));
		}

		/*
		 * Slightly ghetto hack to get the sample sizes
		 */
		List<Integer> sampleSizes = new ArrayList<Integer>();
		for (int tKey : results.get(MaxParser.PERCENTILES[0]).keySet()) {
			sampleSizes.add(tKey);
		}
		Collections.sort(sampleSizes);
		HashMap<Integer, HashMap<Double, Double>> invertedMap = this.invertParsingIndicies(results, sampleSizes);

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));
		this.writeTransitHeader(outBuff);
		for (int tSize : sampleSizes) {
			this.writePercentiles(tSize, invertedMap.get(tSize), outBuff);
			outBuff.write("\n");
		}
		outBuff.close();
	}

	private HashMap<Integer, List<Double>> computeProfitDeltas(String inFile, boolean isDR, int column,
			double percentile, boolean normalized) throws IOException {
		HashMap<Integer, Double> firstRoundValue = new HashMap<Integer, Double>();
		HashMap<Integer, List<Double>> results = new HashMap<Integer, List<Double>>();
		List<Double> sampleDeltas = new ArrayList<Double>();
		BufferedReader inBuff = new BufferedReader(new FileReader(inFile));

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
				int sampleSize = Integer.parseInt(controlMatcher.group(1));

				/*
				 * We're ready to actually extract deltas
				 */
				if (roundFlag == 0) {
					if (sampleDeltas.size() > 0) {
						double thisRoundValue = this.extractValue(sampleDeltas, percentile);
						/*
						 * Check to make sure this isn't the first result at
						 * this size
						 */
						if (!results.containsKey(sampleSize)) {
							results.put(sampleSize, new ArrayList<Double>());
						}
						results.get(sampleSize).add(thisRoundValue);
					}
					firstRoundValue.clear();
					sampleDeltas.clear();
				}

				continue;
			}

			/*
			 * This block of code handles any string that is NOT a control//
			 * self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
			 * MaxParser.OUTPUT_SUFFIX + suffix // + "/drProfitDelta.csv", true,
			 * 2); // self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
			 * MaxParser.OUTPUT_SUFFIX + suffix // + "/nonDRProfitDelta.csv",
			 * false, 2); // self.fullProfitDeltas(transitFile,
			 * MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix // +
			 * "/drTransitProfitDelta.csv", true, 3); //
			 * self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
			 * MaxParser.OUTPUT_SUFFIX + suffix // +
			 * "/nonDRTransitProfitDelta.csv", false, 3); string
			 */
			if (roundFlag != 0) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (Boolean.parseBoolean(dataMatch.group(4)) == isDR) {
						if (roundFlag == 1) {
							firstRoundValue.put(Integer.parseInt(dataMatch.group(1)),
									Double.parseDouble(dataMatch.group(column)));
						} else {
							double delta = Double.parseDouble(dataMatch.group(column))
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
					}// self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
						// MaxParser.OUTPUT_SUFFIX + suffix
						// + "/drProfitDelta.csv", true, 2);
					// self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
					// MaxParser.OUTPUT_SUFFIX + suffix
					// + "/nonDRProfitDelta.csv", false, 2);
					// self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
					// MaxParser.OUTPUT_SUFFIX + suffix
					// + "/drTransitProfitDelta.csv", true, 3);
					// self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE +
					// MaxParser.OUTPUT_SUFFIX + suffix
					// + "/nonDRTransitProfitDelta.csv", false, 3);
				}
			}
		}
		inBuff.close();

		return results;
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
	private void fullReachabilty(String infile, String outFile, int round, int column) throws IOException {

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
		
		this.printCDF(values, outFile + "-CDF");
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
		while (inBuff.ready()) {
			boolean controlFlag = false;
			int roundValue = -1;
			int sampleSize = -1;
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
				sampleSize = Integer.parseInt(sampleMatch.group(1));
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
				continue;
			}

			/*
			 * If we're in a log region we care about, parse it
			 */
			if (inTargetRound) {
				Matcher dataMatch = MaxParser.WARDEN_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					Double ips = this.asToIP.get(Integer.parseInt(dataMatch.group(1)));
					double cleanness = Double.parseDouble(dataMatch.group(column));
					/*
					 * there can be some "strangeness" from the graph not being
					 * connected, deal with that here...
					 */
					if (cleanness > 0.01 && ips != null) {
						currentIPCount += ips;
						currentIPCleanness += cleanness * ips;
					}
				}
			}
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

	private void printCDF(HashMap<Integer, List<Double>> values, String outfile) throws IOException {
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
			for (int listCounter = 0; listCounter < values.size(); listCounter++) {
				if (listCounter != 0) {
					outBuff.write(",");
				}
				List<Double> tList = values.get(listCounter);
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
}