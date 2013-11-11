package parsing;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class MaxParser {

	private static final String FILE_BASE = "/scratch/minerva2/public/nightwingData/";
	private static final String OUTPUT_SUFFIX = "parsingResults_Run";
	private static final String INPUT_SUFFIX = "rawData/";

	private static final Pattern ROUND_PATTERN = Pattern.compile("\\*\\*\\*(\\d+),(\\d+)");
	private static final Pattern SAMPLE_PATTERN = Pattern.compile("###(\\d+),(\\d+)");
	private static final Pattern WARDEN_PATTERN = Pattern.compile("(\\d+),([^,]+),([^,]+)");
	private static final Pattern TRANSIT_PATTERN = Pattern.compile("(\\d+),([^,]+),([^,]+),(.+)");

	private static double[] PERCENTILES = { 0.10, 0.25, 0.50, 0.75, 0.90 };

	public static void main(String[] args) throws IOException {
		MaxParser self = new MaxParser();
		for (String suffix : args) {
			System.out.println("Working on: " + suffix);
			String wardenFile = MaxParser.FILE_BASE + MaxParser.INPUT_SUFFIX + "warden-" + suffix.toLowerCase()
					+ ".log";
			String transitFile = MaxParser.FILE_BASE + MaxParser.INPUT_SUFFIX + "transit-" + suffix.toLowerCase()
					+ ".log";

			//FIXME fix the warden clean-ness parsing
//			self.computePercentiles(wardenFile, MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix
//					+ "/wardenCleanBefore.csv", 1);
//			self.computePercentiles(wardenFile, MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix
//					+ "/wardenCleanAfter.csv", 2);
//			self.computeWardenDeltas(wardenFile, MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix
//					+ "/wardenCleanDelta.csv");
			self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix
					+ "/drProfitDelta.csv", true, 2);
			self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix
					+ "/nonDRProfitDelta.csv", false, 2);
			self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix
					+ "/drTranistProfitDelta.csv", true, 3);
			self.fullProfitDeltas(transitFile, MaxParser.FILE_BASE + MaxParser.OUTPUT_SUFFIX + suffix
					+ "/nonDRTranistProfitDelta.csv", false, 3);
		}
	}

	public MaxParser() {

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
	
	private HashMap<Integer, HashMap<Double, Double>> invertParsingIndicies(HashMap<Double, HashMap<Integer, List<Double>>> original, List<Integer> sampleSizes){
		/*
		 * Build data struct to return result, seed in intial maps
		 */
		HashMap<Integer, HashMap<Double, Double>> result = new HashMap<Integer, HashMap<Double, Double>>();
		for(int counter = 0; counter < sampleSizes.size(); counter++){
			result.put(sampleSizes.get(counter), new HashMap<Double, Double>());
		}
		
		/*
		 * Iterate through the original, extracting medians and filling them into index
		 */
		for(int counter = 0; counter < MaxParser.PERCENTILES.length; counter++){
			double tPerctile = MaxParser.PERCENTILES[counter];
			HashMap<Integer, List<Double>> tempMap = original.get(tPerctile);
			for(Integer tSize: tempMap.keySet()){
				List<Double> tList = tempMap.get(tSize);
				double value = this.extractValue(tList, 0.5);
				result.get(tSize).put(tPerctile, value);
			}
		}
		
		return result;
	}

	// FIXME adust to total,transit income format
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
			 * This block of code handles any string that is NOT a control
			 * string
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
					}
				}
			}
		}
		inBuff.close();

		return results;
	}

	private void computeWardenDeltas(String inFile, String outFile) throws IOException {
		BufferedReader inBuff = new BufferedReader(new FileReader(inFile));
		HashMap<Integer, List<Double>> deltaMedians = new HashMap<Integer, List<Double>>();
		HashMap<Integer, Double> firstRoundValue = new HashMap<Integer, Double>();
		List<Double> deltasForThisSample = new ArrayList<Double>();

		int roundFlag = 0;
		while (inBuff.ready()) {
			boolean controlFlag = false;
			int roundValue = -1;
			int sampleSize = -1;
			String pollStr = inBuff.readLine().trim();

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
				if (roundValue == 2) {
					deltasForThisSample.clear();
				}
				if (roundValue == (0) && deltasForThisSample.size() > 0) {
					Collections.sort(deltasForThisSample);
					if (!deltaMedians.containsKey(sampleSize)) {
						deltaMedians.put(sampleSize, new ArrayList<Double>());
					}
					if (deltasForThisSample.size() % 2 == 1) {
						int pos = (int) Math.floor((double) deltasForThisSample.size() / 2.0);
						deltaMedians.get(sampleSize).add(deltasForThisSample.get(pos));
					} else {
						int pos = (int) Math.floor((double) deltasForThisSample.size() / 2.0);
						deltaMedians.get(sampleSize).add(
								(deltasForThisSample.get(pos) + deltasForThisSample.get(pos - 1)) / 2.0);
					}
					firstRoundValue.clear();
				}
				continue;
			}

			if (roundFlag != 0) {
				Matcher dataMatch = MaxParser.WARDEN_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					if (roundFlag == 1) {
						firstRoundValue.put(Integer.parseInt(dataMatch.group(1)),
								Double.parseDouble(dataMatch.group(2)));
					} else {
						deltasForThisSample.add(Double.parseDouble(dataMatch.group(2))
								- firstRoundValue.get(Integer.parseInt(dataMatch.group(1))));
					}
				}
			}
		}
		inBuff.close();

		ArrayList<Integer> sampleSizes = new ArrayList<Integer>();
		for (int tSize : deltaMedians.keySet()) {
			sampleSizes.add(tSize);
		}
		Collections.sort(sampleSizes);

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));
		this.writeWardenHeader(outBuff);
		for (int tSize : sampleSizes) {
			List<Double> valueList = deltaMedians.get(tSize);
			Collections.sort(valueList);
			//this.writePercentiles(tSize, valueList, outBuff);
			outBuff.write("\n");
		}
		outBuff.close();
	}

	private void computePercentiles(String inFile, String outFile, int round) throws IOException {
		BufferedReader inBuff = new BufferedReader(new FileReader(inFile));
		HashMap<Integer, List<Double>> roundMediansFirstColumn = new HashMap<Integer, List<Double>>();
		HashMap<Integer, List<Double>> roundMediansSecondColumn = new HashMap<Integer, List<Double>>();
		ArrayList<Double> currentFirstColumnValues = new ArrayList<Double>();
		ArrayList<Double> currentSecondColumnValues = new ArrayList<Double>();

		boolean inTargetRound = false;
		while (inBuff.ready()) {
			boolean controlFlag = false;
			int roundValue = -1;
			int sampleSize = -1;
			String pollStr = inBuff.readLine().trim();

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

				if (roundValue == round) {
					inTargetRound = true;
					currentFirstColumnValues.clear();
					currentSecondColumnValues.clear();
				}
				if (roundValue == ((round + 1) % 3) && inTargetRound) {
					inTargetRound = false;
					Collections.sort(currentFirstColumnValues);
					Collections.sort(currentSecondColumnValues);
					if (!roundMediansFirstColumn.containsKey(sampleSize)) {
						roundMediansFirstColumn.put(sampleSize, new ArrayList<Double>());
						roundMediansSecondColumn.put(sampleSize, new ArrayList<Double>());
					}
					if (currentFirstColumnValues.size() % 2 == 1) {
						int pos = (int) Math.floor((double) currentFirstColumnValues.size() / 2.0);
						roundMediansFirstColumn.get(sampleSize).add(currentFirstColumnValues.get(pos));
						roundMediansSecondColumn.get(sampleSize).add(currentSecondColumnValues.get(pos));
					} else {
						int pos = (int) Math.floor((double) currentFirstColumnValues.size() / 2.0);
						roundMediansFirstColumn.get(sampleSize).add(
								(currentFirstColumnValues.get(pos) + currentFirstColumnValues.get(pos - 1)) / 2.0);
						roundMediansSecondColumn.get(sampleSize).add(
								(currentSecondColumnValues.get(pos) + currentSecondColumnValues.get(pos - 1)) / 2.0);
					}
				}
				continue;
			}

			if (inTargetRound) {
				Matcher dataMatch = MaxParser.WARDEN_PATTERN.matcher(pollStr);
				if (dataMatch.find()) {
					currentFirstColumnValues.add(Double.parseDouble(dataMatch.group(2)));
					currentSecondColumnValues.add(Double.parseDouble(dataMatch.group(3)));
				}
			}
		}
		inBuff.close();

		ArrayList<Integer> sampleSizes = new ArrayList<Integer>();
		for (int tSize : roundMediansFirstColumn.keySet()) {
			sampleSizes.add(tSize);
		}
		Collections.sort(sampleSizes);

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));
		this.writeWardenHeader(outBuff);
		for (int tSize : sampleSizes) {
			List<Double> valueList = roundMediansFirstColumn.get(tSize);
			List<Double> secondValueList = roundMediansSecondColumn.get(tSize);
			//this.writePercentiles(tSize, valueList, outBuff);
			outBuff.write(",");
			//this.writePercentiles(tSize, secondValueList, outBuff);
			outBuff.write("\n");
		}
		outBuff.close();
	}

	private void writeTransitHeader(BufferedWriter outBuffer) throws IOException {
		StringBuilder header = new StringBuilder();
		for (int counter = 0; counter < 3; counter++) {
			for (int innerCounter = 0; innerCounter < MaxParser.PERCENTILES.length; innerCounter++) {
				if (innerCounter != 0) {
					header.append(",");
				}
				header.append("sample size,");
				header.append(Double.toString(MaxParser.PERCENTILES[innerCounter]));
				if (counter == 0) {
					header.append(" aboslute profit delta");
				} else if (counter == 1) {
					header.append(" percentage profit delta");
				} else {
					header.append(" percentage with delta");
				}
			}

			if (counter != 2) {
				header.append(",");
			} else {
				header.append("\n");
			}
		}
		outBuffer.write(header.toString());
	}

	private void writeWardenHeader(BufferedWriter outBuffer) throws IOException {
		StringBuilder header = new StringBuilder();
		for (int counter = 0; counter < 2; counter++) {
			for (int innerCounter = 0; innerCounter < MaxParser.PERCENTILES.length; innerCounter++) {
				if (innerCounter != 0) {
					header.append(",");
				}
				header.append("sample size,");
				header.append(Double.toString(MaxParser.PERCENTILES[innerCounter]));
				if (counter == 0) {
					header.append(" by IP count");
				} else {
					header.append(" by AS count");
				}
			}
			if (counter == 0) {
				header.append(",");
			} else {
				header.append("\n");
			}
		}
		outBuffer.write(header.toString());
	}

	private void writePercentiles(int size, HashMap<Double, Double> values, BufferedWriter outBuffer) throws IOException {
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

	private double extractValue(List<Double> valueList, double percentile) {
		Collections.sort(valueList);
		int pos = (int) Math.floor(percentile * valueList.size());
		return valueList.get(pos);
	}
}
