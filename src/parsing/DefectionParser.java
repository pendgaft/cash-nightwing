package parsing;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;

import scijava.stats.CDF;

public class DefectionParser {

	public static void main(String[] args) throws IOException {
		String fileBase = args[0];
		File baseFolder = new File(fileBase + MaxParser.INPUT_SUFFIX);
		File children[] = baseFolder.listFiles();

		for (File child : children) {
			if (!child.isDirectory()) {
				continue;
			}
			String defectionSuffix = child.getName();

			/*
			 * skip defection runs for parsing
			 */
			if (!defectionSuffix.contains("DEFECTION")) {
				System.out.println("skipping non defection run: " + defectionSuffix);
				continue;
			}

			String nonDefectionSuffix = defectionSuffix.replace("DEFECTION", "");

			File outDir = new File(fileBase + MaxParser.OUTPUT_SUFFIX + defectionSuffix);
			if (!outDir.exists()) {
				outDir.mkdirs();
			}

			System.out.println("Working on: " + defectionSuffix);
			String defectionTransitFile = fileBase + MaxParser.INPUT_SUFFIX + defectionSuffix
					+ "/transit.log";
			String nonDefectionTransitFile = fileBase + MaxParser.INPUT_SUFFIX + nonDefectionSuffix
					+ "/transit.log";
			String baseDeployerFile = fileBase + MaxParser.OUTPUT_SUFFIX + nonDefectionSuffix
					+ "/deployers.log";

			HashMap<Integer, Set<Integer>> deployerLists = DefectionParser.buildIndexedDeployment(MaxParser
					.parseDeployerLog(baseDeployerFile));
			HashMap<Integer, HashMap<Integer, Double>> baseLossesMap = DefectionParser
					.parseBaseLosses(nonDefectionTransitFile);
			DefectionParser.parseDefectionRealMoney(defectionTransitFile, fileBase + MaxParser.OUTPUT_SUFFIX
					+ defectionSuffix + "/deployerCosts", baseLossesMap, deployerLists);
		}

	}

	/**
	 * Builds a map indexed by deployement size containing asn -> revenue
	 * mappings for that round
	 * 
	 * @param logFile
	 * @return
	 * @throws IOException
	 */
	public static HashMap<Integer, HashMap<Integer, Double>> parseBaseLosses(String logFile) throws IOException {

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
					if (roundFlag == 2 && Boolean.parseBoolean(dataMatch.group(3)) == true) {
						int asn = Integer.parseInt(dataMatch.group(1));
						roundValues.put(asn, Double.parseDouble(dataMatch.group(2)));
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

		return roundResults;
	}

	public static void parseDefectionRealMoney(String logFile, String outFile,
			HashMap<Integer, HashMap<Integer, Double>> baseLosses, HashMap<Integer, Set<Integer>> deployerSets)
			throws IOException {

		HashMap<Integer, HashMap<Integer, Double>> roundResults = new HashMap<Integer, HashMap<Integer, Double>>();

		HashMap<Integer, Double> roundValues = null;
		Set<Integer> currentSizeDeployers = null;
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
				sampleSize = Integer.parseInt(controlMatcher.group(1));
				if(!roundResults.containsKey(sampleSize)){
					roundResults.put(sampleSize, new HashMap<Integer, Double>());
				}
				currentSizeDeployers = deployerSets.get(sampleSize);
				continue;
			}

			if (roundFlag == 2) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					int asn = Integer.parseInt(dataMatch.group(1));
					if (currentSizeDeployers.contains(asn) && !Boolean.parseBoolean(dataMatch.group(3))) {
						double delta = baseLosses.get(sampleSize).get(asn) - Double.parseDouble(dataMatch.group(2));
						double value = MaxParser.convertTrafficToDollars(delta);
						roundResults.get(sampleSize).put(asn, value);
					}
				}
			}
		}
		inBuff.close();

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

	private static HashMap<Integer, Set<Integer>> buildIndexedDeployment(List<Set<Integer>> baseDeployList) {
		HashMap<Integer, Set<Integer>> retMapping = new HashMap<Integer, Set<Integer>>();

		for (Set<Integer> tDep : baseDeployList) {
			retMapping.put(tDep.size(), tDep);
		}

		return retMapping;
	}

}
