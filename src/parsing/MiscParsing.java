package parsing;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;

import sim.Constants;
import topo.ASTopoParser;
import decoy.DecoyAS;
import econ.EconomicEngine;

public class MiscParsing {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		MiscParsing.directAStoASComparison(args[0], args[1], args[2]);
	}
	
	private static void buildFixedASSimList(int minCCSize, int numberOfASes, int numberOfRounds) throws IOException {
		HashMap<Integer, DecoyAS> asMap = ASTopoParser.parseFile("as-rel.txt", "china-as.txt", "superAS.txt");
		for(DecoyAS tAS: asMap.values()){
			EconomicEngine.buildIndividualCustomerCone(tAS);
		}
		
		List<Integer> possSet = new ArrayList<Integer>();
		for(DecoyAS tAS: asMap.values()){
			if(tAS.getCustomerConeSize() >= minCCSize && !tAS.isWardenAS() && !tAS.isSuperAS()){
				possSet.add(tAS.getASN());
			}
		}
		
		if(possSet.size() < numberOfASes){
			System.out.println("too few ASes (" + possSet.size() + ")");
			return;
		} else{
			System.out.println("poss  ASes: " + possSet.size());
		}
		
		BufferedWriter outBuff = new BufferedWriter(new FileWriter("drConfig.txt"));
		for(int counter = 0; counter < numberOfRounds; counter++){
			Collections.shuffle(possSet);
			for(int numberCounter = 0; numberCounter < numberOfASes; numberCounter++){
				outBuff.write("" + possSet.get(numberCounter) + "\n");
			}
			outBuff.write(" \n");
		}
		outBuff.close();
	}

	private static HashMap<Integer, Integer> buildCCSize() {
		HashMap<Integer, Integer> retMap = new HashMap<Integer, Integer>();

		try {
			HashMap<Integer, DecoyAS> asMap = ASTopoParser.parseFile("as-rel.txt", "china-as.txt", "superAS.txt");
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

	public static void addCCSizeToEcon(String in, String out) throws IOException {
		HashMap<Integer, Integer> ccSizes = MiscParsing.buildCCSize();

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
							firstRoundValue.put(Integer.parseInt(dataMatch.group(1)),
									Double.parseDouble(dataMatch.group(column)));
						} else {
							double delta = Double.parseDouble(dataMatch.group(column))
									- firstRoundValue.get(Integer.parseInt(dataMatch.group(1)));
							roundDeltas.put(Integer.parseInt(dataMatch.group(1)), delta);
						}
					}
				}
			}
		}
		inBuff.close();

		return results;
	}

	private static void directAStoASComparison(String fileA, String fileB, String outFile) throws IOException {
		HashMap<Integer, HashMap<Integer, Double>> roundADeltas = MiscParsing.computeProfitDeltas(fileA, true, 3, true);
		HashMap<Integer, HashMap<Integer, Double>> roundBDeltas = MiscParsing.computeProfitDeltas(fileB, true, 3, true);

		HashMap<Integer, HashMap<Integer, Double>> change = new HashMap<Integer, HashMap<Integer, Double>>();
		for (int roundKey : roundADeltas.keySet()) {
			HashMap<Integer, Double> roundChanges = new HashMap<Integer, Double>();
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
				roundChanges.put(tASN, (bValue - aValue) / aValue * -1.0);
			}

			change.put(roundKey, roundChanges);
		}
		
		HashMap<Integer, List<Double>> outMap = new HashMap<Integer, List<Double>>();
		for(int tRoundKey: change.keySet()){
			List<Double> roundDeltas = new ArrayList<Double>();
			roundDeltas.addAll(change.get(tRoundKey).values());
			outMap.put(tRoundKey, roundDeltas);
		}
		MaxParser.printCDF(outMap, outFile);
	}

}
