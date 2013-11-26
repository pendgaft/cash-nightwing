package parsing;

import java.util.*;
import java.util.regex.Matcher;
import java.io.*;

import decoy.DecoyAS;

import topo.ASTopoParser;

public class DepthParser {

	private String baseLogFile;
	private HashMap<Integer, Integer> depthMap;

	public DepthParser(String logFile, String wardenFile) throws IOException {
		this.baseLogFile = logFile;
		this.buildDepthList(wardenFile);
	}

	private void buildDepthList(String wardenFile) throws IOException {
		this.depthMap = new HashMap<Integer, Integer>();

		HashMap<Integer, DecoyAS> asMap = ASTopoParser.doNetworkBuild(wardenFile);
		Set<Integer> wardenSet = new HashSet<Integer>();
		for (Integer tASN : asMap.keySet()) {
			if (asMap.get(tASN).isWardenAS()) {
				wardenSet.add(tASN);
			}
			this.depthMap.put(tASN, Integer.MAX_VALUE);
		}

		for (int tWardenASN : wardenSet) {
			this.depthFinder(tWardenASN, asMap, this.depthMap);
		}
	}

	private void depthFinder(int start, HashMap<Integer, DecoyAS> asMap, HashMap<Integer, Integer> depthMap) {
		HashSet<Integer> visited = new HashSet<Integer>();
		HashSet<Integer> connected = new HashSet<Integer>();
		HashSet<Integer> staging = new HashSet<Integer>();

		connected.add(start);
		int currentHop = 0;

		while (!connected.isEmpty()) {
			for (int tVisit : connected) {
				depthMap.put(tVisit, Math.min(currentHop, depthMap.get(tVisit)));
				for (int tasn : asMap.get(tVisit).getNeighbors()) {
					if (visited.contains(tasn) || connected.contains(tasn)) {
						continue;
					}
					staging.add(tasn);
				}
			}

			visited.addAll(connected);
			connected.clear();
			connected.addAll(staging);
			staging.clear();

			currentHop++;
		}
	}

	public void parseFile(int size) throws IOException {
		HashMap<Integer, List<Double>> profitDeltas = this.computeProfitDeltas(this.baseLogFile, true, 3, true, size);
		List<Double> full = new LinkedList<Double>();
		List<Double> first = new LinkedList<Double>();
		List<Double> second = new LinkedList<Double>();
		List<Double> third = new LinkedList<Double>();
		List<Double> more = new LinkedList<Double>();

		for (int tASN : profitDeltas.keySet()) {
			List<Double> subList = null;
			int depth = this.depthMap.get(tASN);
			if (depth == 1) {
				subList = first;
			} else if (depth == 2) {
				subList = second;
			} else if (depth == 3) {
				subList = third;
			} else if (depth > 3) {
				subList = more;
			}

			full.addAll(profitDeltas.get(tASN));
			if (subList != null) {
				subList.addAll(profitDeltas.get(tASN));
			}
		}

		List<List<Double>> results = new LinkedList<List<Double>>();
		results.add(full);
		results.add(first);
		results.add(second);
		results.add(third);
		results.add(more);
		this.printCDF(results, size);
	}

	private void printCDF(List<List<Double>> values, int size) throws IOException {
		int maxSize = 0;
		for (List<Double> tList : values) {
			Collections.sort(tList);
			maxSize = Math.max(maxSize, tList.size());
		}

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(this.baseLogFile + "-byDepth" + size));
		for (int counter = 0; counter < maxSize; counter++) {
			for (int listCounter = 0; listCounter < values.size(); listCounter++) {
				if (listCounter != 0) {
					outBuff.write(",");
				}
				List<Double> tList = values.get(listCounter);
				if (tList.size() > counter) {
					outBuff.write(Double.toString((double) counter / (double) tList.size()) + "," + tList.get(counter));
				} else {
					outBuff.write(",");
				}
			}
			outBuff.write("\n");
		}
		outBuff.close();
	}

	private HashMap<Integer, List<Double>> computeProfitDeltas(String inFile, boolean isDR, int column,
			boolean normalized, int size) throws IOException {
		HashMap<Integer, Double> firstRoundValue = new HashMap<Integer, Double>();
		HashMap<Integer, List<Double>> results = new HashMap<Integer, List<Double>>();
		BufferedReader inBuff = new BufferedReader(new FileReader(inFile));

		boolean correctSize = false;
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

				correctSize = (sampleSize == size);

				/*
				 * We're ready to actually extract deltas
				 */
				if (roundFlag == 0) {
					firstRoundValue.clear();
				}

				continue;
			}

			/*
			 * This block of code handles any string that is NOT a control
			 * string
			 */
			if (roundFlag != 0 && correctSize) {
				Matcher dataMatch = MaxParser.TRANSIT_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (Boolean.parseBoolean(dataMatch.group(4)) == isDR) {
						if (roundFlag == 1) {
							firstRoundValue.put(Integer.parseInt(dataMatch.group(1)),
									Double.parseDouble(dataMatch.group(column)));
						} else {
							if (!results.containsKey(Integer.parseInt(dataMatch.group(1)))) {
								results.put(Integer.parseInt(dataMatch.group(1)), new LinkedList<Double>());
							}

							double delta = Double.parseDouble(dataMatch.group(column))
									- firstRoundValue.get(Integer.parseInt(dataMatch.group(1)));
							if (delta != 0.0) {
								if (normalized) {
									results.get(Integer.parseInt(dataMatch.group(1)))
											.add(100.0
													* delta
													/ Math.abs(firstRoundValue.get(Integer.parseInt(dataMatch.group(1)))));
								} else {
									results.get(Integer.parseInt(dataMatch.group(1))).add(delta);
								}
							}
						}
					}
				}
			}
		}
		inBuff.close();

		return results;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		DepthParser self = new DepthParser(args[0], args[1]);
		System.out.println("done with setup");
		self.parseFile(500);
		System.out.println("done with one");
		self.parseFile(1500);
		System.out.println("done with two");
		self.parseFile(2500);
		System.out.println("done with three");
	}

}
