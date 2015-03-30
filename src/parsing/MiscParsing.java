package parsing;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;

import sim.Constants;
import topo.*;
import decoy.DecoyAS;
import econ.EconomicEngine;
import scijava.stats.CDF;

public class MiscParsing {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {

	}

	private static void parseTransitRatios(String inFile, String outFile) throws IOException {
		List<Double> results = new ArrayList<Double>();
		BufferedReader fBuff = new BufferedReader(new FileReader(inFile));
		String readLine = null;
		while ((readLine = fBuff.readLine()) != null) {
			String[] fragments = readLine.split(",");
			double totalTransit = Double.parseDouble(fragments[0]);
			double countryTransit = Double.parseDouble(fragments[1]);

			if (totalTransit > 0.0 && countryTransit >= 0.0) {
				results.add(countryTransit / totalTransit);
			}
		}
		fBuff.close();

		CDF.printCDF(results, outFile);
	}

	private static void buildFixedASSimList(int minCCSize, int numberOfASes, int numberOfRounds, String wardenFile) throws IOException {
		HashMap<Integer, DecoyAS> asMap = ASTopoParser.parseFile(Constants.AS_REL_FILE, wardenFile, Constants.SUPER_AS_FILE);
		for (DecoyAS tAS : asMap.values()) {
			EconomicEngine.buildIndividualCustomerCone(tAS);
		}

		List<Integer> possSet = new ArrayList<Integer>();
		for (DecoyAS tAS : asMap.values()) {
			if (tAS.getCustomerConeSize() >= minCCSize && !tAS.isWardenAS() && !tAS.isSuperAS()) {
				possSet.add(tAS.getASN());
			}
		}

		if (possSet.size() < numberOfASes) {
			System.out.println("too few ASes (" + possSet.size() + ")");
			return;
		} else {
			System.out.println("poss  ASes: " + possSet.size());
		}

		BufferedWriter outBuff = new BufferedWriter(new FileWriter("drConfig.txt"));
		for (int counter = 0; counter < numberOfRounds; counter++) {
			Collections.shuffle(possSet);
			for (int numberCounter = 0; numberCounter < numberOfASes; numberCounter++) {
				outBuff.write("" + possSet.get(numberCounter) + "\n");
			}
			outBuff.write(" \n");
		}
		outBuff.close();
	}

	private static HashMap<Integer, Integer> buildCCSize(String wardenFile) {
		HashMap<Integer, Integer> retMap = new HashMap<Integer, Integer>();

		try {
			HashMap<Integer, DecoyAS> asMap = ASTopoParser.parseFile(Constants.AS_REL_FILE, wardenFile, Constants.SUPER_AS_FILE);
			for (int tAS : asMap.keySet()) {
				EconomicEngine.buildIndividualCustomerCone(asMap.get(tAS));
			}
			for (DecoyAS tAS : asMap.values()) {
				retMap.put(tAS.getASN(), tAS.getCustomerConeSize());
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		return retMap;
	}

	private static void addCCSizeToEcon(String in, String out, String wardenFile) throws IOException {
		HashMap<Integer, Integer> ccSizes = MiscParsing.buildCCSize(wardenFile);

		BufferedReader inFile = new BufferedReader(new FileReader(in));
		BufferedWriter outFile = new BufferedWriter(new FileWriter(out));
		while (inFile.ready()) {
			String pollStr = inFile.readLine().trim();
			String[] frags = pollStr.split(",");
			int asn = Integer.parseInt(frags[0]);
			int ccSize;
			if (ccSizes.containsKey(asn)) {
				ccSize = ccSizes.get(asn);
			} else {
				ccSize = 0;
			}
			outFile.write(pollStr + "," + ccSize + "\n");
		}
		inFile.close();
		outFile.close();
	}

	private static void buildDeployerList(String logPath, String outPath) throws IOException {

		List<Set<Integer>> roundSets = new LinkedList<Set<Integer>>();
		HashSet<Integer> deploySet = new HashSet<Integer>();
		BufferedReader inBuffer = new BufferedReader(new FileReader(logPath));
		String line = null;

		while ((line = inBuffer.readLine()) != null) {

			Matcher controlMatcher = MaxParser.ROUND_PATTERN.matcher(line);
			boolean controlFlag = false;
			if (controlMatcher.find()) {
				controlFlag = true;
			} else {
				controlMatcher = MaxParser.SAMPLE_PATTERN.matcher(line);
				if (controlMatcher.find()) {
					controlFlag = true;
				}
			}

			if (controlFlag) {
				int roundFlag = Integer.parseInt(controlMatcher.group(2));

				/*
				 * Store the deltas for this round and clear everything out for
				 * the next round
				 */
				if (roundFlag == 0) {
					if (deploySet.size() > 0) {
						roundSets.add(deploySet);
						deploySet = new HashSet<Integer>();
					}
				}

				continue;
			}

			Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(line);
			if (dataMatch.find()) {
				if (Boolean.parseBoolean(dataMatch.group(4))) {
					deploySet.add(Integer.parseInt(dataMatch.group(1)));
				}
			}
		}
		inBuffer.close();
		if (deploySet.size() > 0) {
			roundSets.add(deploySet);
			deploySet = new HashSet<Integer>();
		}

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outPath));
		for (Set<Integer> tSet : roundSets) {
			for (int tASN : tSet) {
				outBuff.write("" + tASN + "\n");
			}
			outBuff.write("\n");
		}
		outBuff.close();
	}

	private static HashMap<Integer, HashMap<Integer, Double>> computeProfitDeltas(String inFile, boolean isDR,
			int column, boolean normalized) throws IOException {
		HashMap<Integer, Double> firstRoundValue = new HashMap<Integer, Double>();
		HashMap<Integer, HashMap<Integer, Double>> results = new HashMap<Integer, HashMap<Integer, Double>>();
		HashMap<Integer, Double> roundDeltas = new HashMap<Integer, Double>();
		BufferedReader inBuff = new BufferedReader(new FileReader(inFile));
		int iteration = 0;
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

				/*
				 * Store the deltas for this round and clear everything out for
				 * the next round
				 */
				if (roundFlag == 0) {
					if (roundDeltas.size() != 0) {
						results.put(iteration, roundDeltas);
						iteration++;
						roundDeltas = new HashMap<Integer, Double>();
						firstRoundValue.clear();
					}
				}

				continue;
			}

			if (roundFlag != 0) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (Boolean.parseBoolean(dataMatch.group(4)) == isDR) {
						if (roundFlag == 1) {
							firstRoundValue.put(Integer.parseInt(dataMatch.group(1)), Double.parseDouble(dataMatch
									.group(column)));
						} else {
							double delta = -1.0
									* ((Double.parseDouble(dataMatch.group(column)) - firstRoundValue.get(Integer
											.parseInt(dataMatch.group(1)))) / firstRoundValue.get(Integer
											.parseInt(dataMatch.group(1))));
							roundDeltas.put(Integer.parseInt(dataMatch.group(1)), delta);
						}
					}
				}
			}
		}
		results.put(iteration, roundDeltas);
		inBuff.close();

		return results;
	}

	public static void directAStoASComparison(String fileA, String fileB, String outFileBase) throws IOException {
		HashMap<Integer, HashMap<Integer, Double>> roundADeltas = MiscParsing.computeProfitDeltas(fileA, true, 3, true);
		HashMap<Integer, HashMap<Integer, Double>> roundBDeltas = MiscParsing.computeProfitDeltas(fileB, true, 3, true);

		HashMap<Integer, List<Double>> origLosses = new HashMap<Integer, List<Double>>();
		origLosses.put(0, new ArrayList<Double>());
		origLosses.put(1, new ArrayList<Double>());
		origLosses.get(0).addAll(roundADeltas.get(0).values());
		origLosses.get(1).addAll(roundBDeltas.get(0).values());
		MaxParser.printCDF(origLosses, outFileBase + "-origLossCDF.csv");

		List<Collection<Double>> changes = new ArrayList<Collection<Double>>();
		for (int roundKey : roundADeltas.keySet()) {
			List<Double> roundDeltas = new ArrayList<Double>();
			HashMap<Integer, Double> a = roundADeltas.get(roundKey);
			HashMap<Integer, Double> b = roundBDeltas.get(roundKey);
			if (a == null || b == null) {
				System.err.println("the two sets of rounds don't match up!");
				return;
			}
			for (int tASN : a.keySet()) {
				Double aValue = a.get(tASN);
				Double bValue = b.get(tASN);
				if (aValue == null || bValue == null) {
					System.err.println("the two AS sets don't match up!");
					return;
				}
				roundDeltas.add((bValue - aValue) / aValue * 100.0);
			}

			changes.add(roundDeltas);
		}

		CDF.printCDFs(changes, outFileBase + "-lossDeltas");
	}

	public static void ringOneSize(String wardenFile) throws IOException {
		HashMap<Integer, DecoyAS> theTopo = ASTopoParser.doNetworkBuild(wardenFile);
		Set<Integer> wardenSet = new HashSet<Integer>();

		for (DecoyAS tAS : theTopo.values()) {
			if (tAS.isWardenAS()) {
				wardenSet.add(tAS.getASN());
			}
		}

		int r1Count = 0;
		int r1TransitCount = 0;
		int r1ProviderCount = 0;
		for (DecoyAS tAS : theTopo.values()) {
			if (wardenSet.contains(tAS.getASN())) {
				continue;
			}

			for (int tWardenASN : wardenSet) {
				DecoyAS tWarden = theTopo.get(tWardenASN);
				if (tWarden.getNeighbors().contains(tAS.getASN())) {
					r1Count++;
					if (tAS.getCustomers().size() > 0) {
						r1TransitCount++;
					}
					if (tAS.getCustomers().contains(tWarden)) {
						r1ProviderCount++;
					}
					break;
				}
			}
		}

		System.out.println("r1 size: " + r1Count + " warden size: " + wardenSet.size() + " tranist Size: "
				+ r1TransitCount + " provider count: " + r1ProviderCount);
	}

	public static void computeIPandIPCC(String wardenFile) throws IOException {
		HashMap<Integer, DecoyAS> theTopo = ASTopoParser.doNetworkBuild(wardenFile);

		long wardenIP = 0;
		long ccIP = 0;

		Set<Integer> ccASSet = new HashSet<Integer>();
		for (DecoyAS tAS : theTopo.values()) {
			if (tAS.isWardenAS()) {
				wardenIP += tAS.getIPCount();
				ccASSet.add(tAS.getASN());
				Set<Integer> toVisit = new HashSet<Integer>();
				for (AS custAS : tAS.getCustomers()) {
					toVisit.add(custAS.getASN());
				}
				while (!toVisit.isEmpty()) {
					HashSet<Integer> nextToVisit = new HashSet<Integer>();
					for (int tCustASN : toVisit) {
						ccASSet.add(tCustASN);
						DecoyAS custASObj = theTopo.get(tCustASN);
						for (AS tCustCustAS : custASObj.getCustomers()) {
							if (!ccASSet.contains(tCustCustAS.getASN())) {
								nextToVisit.add(tCustCustAS.getASN());
							}
						}
					}
					toVisit = nextToVisit;
				}
			}
		}

		for (int tASN : ccASSet) {
			ccIP += theTopo.get(tASN).getIPCount();
		}

		System.out.println("Results for: " + wardenFile + " Internal warden IP: " + (wardenIP / 256) + " CC IP size: "
				+ (ccIP / 256));
	}

	/*
	 * Used for parsing a detector set
	 */
	public static void generateDefectorConfig(String logFile, String outFile, int size) throws IOException {
		List<Integer> masterASList = new ArrayList<Integer>(size);

		BufferedReader oldLogBuff = new BufferedReader(new FileReader(logFile));
		boolean inRegion = false;
		while (oldLogBuff.ready()) {
			String pollStr = oldLogBuff.readLine().trim();

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
				int sampleSize = Integer.parseInt(controlMatcher.group(1));

				/*
				 * We're ready to actually extract deltas
				 */
				if (sampleSize == size) {
					if (roundFlag == 2) {
						break;
					} else if (roundFlag == 1) {
						inRegion = true;
					}
				}

				continue;
			}

			if (inRegion) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (Boolean.parseBoolean(dataMatch.group(4))) {
						masterASList.add(Integer.parseInt(dataMatch.group(1)));
					}
				}
			}

		}
		oldLogBuff.close();

		/*
		 * Write each N choose N - 1 combo to the out file
		 */
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));
		for (int counter = 0; counter < masterASList.size(); counter++) {
			for (int posCounter = 0; posCounter < masterASList.size(); posCounter++) {
				if (posCounter == counter) {
					continue;
				}
				outBuff.write(Integer.toString(masterASList.get(posCounter)) + "\n");
			}
			outBuff.write("\n");
		}
		outBuff.close();
	}

	/*
	 * Outputs the CDFs of profit losses for the same set of ASes each deploy,
	 * no matter what the actual set of deployers is, the set of ASes outputted
	 * is the first set of deployers
	 */
	public void profitSameASCDF(String inFile, String outFile) throws IOException {
		HashSet<Integer> targetASSet = null;
		HashMap<Integer, Double> firstRoundValue = new HashMap<Integer, Double>();
		List<Collection<Double>> results = new ArrayList<Collection<Double>>();
		HashMap<Integer, Double> deltas = new HashMap<Integer, Double>();
		BufferedReader inBuff = new BufferedReader(new FileReader(inFile));
		int sampleSize = 0;

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
				int oldSize = sampleSize;
				sampleSize = Integer.parseInt(controlMatcher.group(1));

				/*
				 * We're ready to actually extract deltas
				 */
				if (roundFlag == 0) {
					firstRoundValue.clear();
					if (oldSize != sampleSize && oldSize != 0) {
						if (targetASSet == null) {
							targetASSet = new HashSet<Integer>();
							targetASSet.addAll(deltas.keySet());
						}
						List<Double> resultList = new ArrayList<Double>();
						for (int tASN : targetASSet) {
							resultList.add(deltas.get(tASN));
						}
						results.add(resultList);
						deltas.clear();
					}

				}

				continue;
			}

			if (roundFlag != 0) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (Boolean.parseBoolean(dataMatch.group(4))) {
						if (roundFlag == 1) {
							firstRoundValue.put(Integer.parseInt(dataMatch.group(1)), Double.parseDouble(dataMatch
									.group(3)));
						} else {
							double delta = Double.parseDouble(dataMatch.group(3))
									- firstRoundValue.get(Integer.parseInt(dataMatch.group(1)));
							if (delta != 0.0) {

								deltas.put(Integer.parseInt(dataMatch.group(1)), 100.0 * delta
										/ Math.abs(firstRoundValue.get(Integer.parseInt(dataMatch.group(1)))));

							}
						}
					}
				}
			}
		}
		inBuff.close();

		// FIXME we're missing the last data point because we don't see a
		// control seq

		CDF.printCDFs(results, outFile);
	}

	public static void parseDefectorRun(String baseLogFile, String defectorRunLogFile, String defectorListFile,
			String outFile) throws IOException {

		List<Integer> defectorList = new ArrayList<Integer>();
		HashMap<Integer, Double> trafficNotDefecting = new HashMap<Integer, Double>();
		HashMap<Integer, Double> deltas = new HashMap<Integer, Double>();
		HashMap<Integer, Double> change = new HashMap<Integer, Double>();
		HashMap<Integer, Double> normalizationValues = new HashMap<Integer, Double>();

		BufferedReader inBuff = new BufferedReader(new FileReader(defectorListFile));
		while (inBuff.ready()) {
			String pollStr = inBuff.readLine().trim();
			if (pollStr.length() > 0) {
				defectorList.add(Integer.parseInt(pollStr));
			}
		}
		inBuff.close();

		int targetSize = defectorList.size();
		int roundFlag = 0;
		int sampleSize = 0;
		boolean inRegion = false;
		inBuff = new BufferedReader(new FileReader(baseLogFile));
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
				sampleSize = Integer.parseInt(controlMatcher.group(1));

				/*
				 * We're ready to actually extract deltas
				 */
				if (roundFlag == 0) {
					if (sampleSize == targetSize) {
						inRegion = true;
					} else if (inRegion) {
						break;
					}
				}

				continue;
			}

			if (inRegion) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					int asnThisLine = Integer.parseInt(dataMatch.group(1));
					if (roundFlag == 1) {
						normalizationValues.put(asnThisLine, Double.parseDouble(dataMatch.group(3)));
					} else if (roundFlag == 2) {
						if (defectorList.contains(asnThisLine)) {
							trafficNotDefecting.put(asnThisLine, Double.parseDouble(dataMatch.group(3)));
						}
					}
				}
			}

		}
		inBuff.close();

		int slot = -1;
		inBuff = new BufferedReader(new FileReader(defectorRunLogFile));
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
				sampleSize = Integer.parseInt(controlMatcher.group(1));

				/*
				 * We're ready to actually extract deltas
				 */
				if (roundFlag == 0) {
					slot++;
				}

				continue;
			}

			if (roundFlag == 2) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					int asnThisLine = Integer.parseInt(dataMatch.group(1));
					if (defectorList.get(slot) == asnThisLine) {
						deltas.put(asnThisLine, (Double.parseDouble(dataMatch.group(3)) - trafficNotDefecting
								.get(asnThisLine))
								/ normalizationValues.get(asnThisLine) * 100.0);
						System.out.println("" + asnThisLine + " " + dataMatch.group(3) + " "
								+ trafficNotDefecting.get(asnThisLine) + " " + normalizationValues.get(asnThisLine));
						double origLoss = (normalizationValues.get(asnThisLine) - trafficNotDefecting.get(asnThisLine))
								/ normalizationValues.get(asnThisLine) * 100.0;
						// change.put(asnThisLine, (deltas.get(asnThisLine) -
						// origLoss) / origLoss * 100.0);
						change.put(asnThisLine, deltas.get(asnThisLine));
					}
				}
			}

		}
		inBuff.close();

		List<Double> vals = new ArrayList<Double>(deltas.size());
		vals.addAll(deltas.values());
		CDF.printCDF(vals, outFile + "-lossCDF");
		vals.clear();
		vals.addAll(change.values());
		CDF.printCDF(vals, outFile + "-changeCDF");
	}

	public static void parseRealMoney(String logFile, String outFile) throws IOException {

		HashMap<Integer, HashMap<Integer, Double>> roundResults = new HashMap<Integer, HashMap<Integer, Double>>();

		HashMap<Integer, Double> roundValues = null;
		HashMap<Integer, Double> firstRoundValues = new HashMap<Integer, Double>();
		BufferedReader inBuff = new BufferedReader(new FileReader(logFile));
		int roundFlag = 0;
		int sampleSize = 0;
		BufferedWriter asBuff = new BufferedWriter(new FileWriter("/scratch/minerva2/public/nightwingData/asn.txt"));
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
							firstRoundValues.put(Integer.parseInt(dataMatch.group(1)), Double.parseDouble(dataMatch
									.group(3)));
						} else {
							int asn = Integer.parseInt(dataMatch.group(1));
							asBuff.write("" + asn + "\n");
							double delta = Double.parseDouble(dataMatch.group(3)) - firstRoundValues.get(asn);
							// TODO need to actually get the as object pulled in
							// here at some point
							double value = MiscParsing.convertTrafficToDollars(delta, null);
							roundValues.put(asn, value);
						}
					}
				}
			}
		}
		inBuff.close();
		asBuff.close();
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
		CDF.printCDFs(cdfLists, outFile + "-CDF");

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
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile + "-totalCost"));
		for (int counter = 0; counter < perRoundCost.size(); counter++) {
			outBuff.write("" + roundSizes.get(counter) + "," + perRoundCost.get(counter) + "\n");
		}
		outBuff.close();
	}

	public static void parseDefectionRealMoney(String baseFile, String logFile, String defectorConfig, String outFile)
			throws IOException {

		HashMap<Integer, Boolean> defectionList = new HashMap<Integer, Boolean>();
		BufferedReader confBuff = new BufferedReader(new FileReader(defectorConfig));
		while (confBuff.ready()) {
			String pollStr = confBuff.readLine().trim();
			if (pollStr.length() > 0) {
				defectionList.put(Integer.parseInt(pollStr), false);
			}
		}
		confBuff.close();

		HashMap<Integer, Double> roundValues = new HashMap<Integer, Double>();
		HashMap<Integer, Double> lowWaterMark = new HashMap<Integer, Double>();
		BufferedReader inBuff = new BufferedReader(new FileReader(baseFile));
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
				if (roundFlag == 0) {
					//FIXME crude work around since there is an off by one error lurking in the shadows of simulations (not here)
					if (sampleSize == defectionList.size() || sampleSize == defectionList.size() + 1) {
						break;
					} else {
						lowWaterMark.clear();
					}
				}
				sampleSize = newSampleSize;
				continue;
			}

			if (roundFlag != 0) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (Boolean.parseBoolean(dataMatch.group(4)) == true) {
						if (roundFlag == 2) {
							lowWaterMark.put(Integer.parseInt(dataMatch.group(1)), Double.parseDouble(dataMatch
									.group(3)));
						}
					}
				}
			}
		}
		inBuff.close();
		System.out.println("lwm: " + lowWaterMark.size());

		inBuff = new BufferedReader(new FileReader(logFile));
		HashSet<Integer> onSet = new HashSet<Integer>();
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
				if (roundFlag == 0) {
					System.out.println("OS " + onSet.size());
					onSet.clear();
				}
				continue;
			}

			if (roundFlag == 2) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					int asn = Integer.parseInt(dataMatch.group(1));
					if (defectionList.containsKey(asn)) {
						if (!Boolean.parseBoolean(dataMatch.group(4))) {
							if (defectionList.get(asn)) {
								System.out.println("already defected once: " + asn);
							}
							if (lowWaterMark.containsKey(asn)) {
								roundValues.put(asn, MiscParsing.convertTrafficToDollars(lowWaterMark.get(asn)
										- Double.parseDouble(dataMatch.group(3)), null));
								defectionList.put(asn, true);
							}
						} else {
							onSet.add(asn);
						}
					}
				}
			}
		}
		inBuff.close();

		int trueCount = 0;
		for (int tASN : defectionList.keySet()) {
			if (defectionList.get(tASN)) {
				trueCount++;
			}
		}
		System.out.println("T/F " + trueCount + "/" + (defectionList.size() - trueCount));

		List<Double> resultsList = new ArrayList<Double>(roundValues.size());
		resultsList.addAll(roundValues.values());
		CDF.printCDF(resultsList, outFile);
	}

	private static double convertTrafficToDollars(double amount, DecoyAS as) {
		// TODO implement
		return amount * 0.000001287;
	}

}
