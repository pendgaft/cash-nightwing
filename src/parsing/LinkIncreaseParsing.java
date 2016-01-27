package parsing;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkIncreaseParsing {

	public static final String OUTPUT_SUFFIX = "parsedLogs/";
	public static final String INPUT_SUFFIX = "rawLogs/";
	public static final String AS_FILE_PREFIX = "cash-nightwing/countryMappings/";
	public static final String LINK_FILE_NAME = "link.log";

	public static void main(String[] args) throws IOException {
		String fileBase = args[0];
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

			System.out.println("Working on: " + suffix);
			File outDir = new File(fileBase + OUTPUT_SUFFIX + suffix);
			if (!outDir.exists()) {
				outDir.mkdirs();
			}
			File linkFile = new File(child, LinkIncreaseParsing.LINK_FILE_NAME);

			String wardenFileSuffix = null;
			if (suffix.substring(0, 2).equalsIgnoreCase("CN") || suffix.substring(0, 2).equalsIgnoreCase("IR")) {
				wardenFileSuffix = "-as.txt";
			} else {
				wardenFileSuffix = "-asInt.txt";
			}

			File allFile = new File(outDir, "mean-link-inc-all.csv");
			File onlyWardenFile = new File(outDir, "mean-link-inc-warden.csv");
			File allFileCDF = new File(outDir, "mean-link-inc-all-cdf.csv");
			File onlyWardenCDF = new File(outDir, "mean-link-inc-warden-cdf.csv");
			String wardenFile = AS_FILE_PREFIX + suffix.substring(0, 2) + wardenFileSuffix;
			Set<String> wardenSet = LinkIncreaseParsing.loadWardenASes(wardenFile);
			LinkIncreaseParsing.runParse(linkFile, allFileCDF, allFile, null);
			LinkIncreaseParsing.runParse(linkFile, onlyWardenCDF, onlyWardenFile, wardenSet);
		}
	}

	private static void runParse(File linkFile, File cdfFile, File meanFile, Set<String> screenSet) throws IOException {
		BufferedReader inBuffer = new BufferedReader(new FileReader(linkFile));

		HashMap<Integer, List<Double>> increaseMap = new HashMap<Integer, List<Double>>();
		HashMap<String, Double> beforeMap = new HashMap<String, Double>();
		HashMap<String, Double> afterMap = new HashMap<String, Double>();
		HashMap<String, Double> curMap = null;
		int curDep = 0;
		int curRound = 0;
		while (inBuffer.ready()) {
			String readStr = inBuffer.readLine().trim();
			Matcher cmdMatch = MaxParser.SAMPLE_PATTERN.matcher(readStr);
			if (cmdMatch.find()) {
				if (curDep != Integer.parseInt(cmdMatch.group(1))) {
					if (curDep != 0) {
						List<Double> increases = LinkIncreaseParsing.compareLoads(beforeMap, afterMap);
						increaseMap.put(curDep, increases);
					}
					curDep = Integer.parseInt(cmdMatch.group(1));
					beforeMap.clear();
					afterMap.clear();
				}
				curRound = Integer.parseInt(cmdMatch.group(2));
				if (curRound == 0) {
					curMap = beforeMap;
				} else if (curRound == 2) {
					curMap = afterMap;
				} else {
					curMap = null;
				}
			} else if (curRound == 0 || curRound == 2) {

				/*
				 * Split line and parse if it's a 3 col csv format line
				 */
				String[] splits = readStr.split(",");
				if (splits.length == 3) {
					/*
					 * If we were given a screen set skip if neither end is in
					 * it
					 */
					if (screenSet != null) {
						if ((!screenSet.contains(splits[0])) && (!screenSet.contains(splits[1]))) {
							continue;
						}
					}

					curMap.put(splits[0] + "," + splits[1], Double.parseDouble(splits[2]));
				}
			}
		}
		List<Double> increases = LinkIncreaseParsing.compareLoads(beforeMap, afterMap);
		increaseMap.put(curDep, increases);
		inBuffer.close();
		
		/*
		 * At this point increaseMap should be filled correctly, time to do some outputs
		 */
		
	}

	private static List<Double> compareLoads(HashMap<String, Double> before, HashMap<String, Double> after) {
		List<Double> retList = new ArrayList<Double>(before.size());

		for (String tLink : before.keySet()) {
			if (after.containsKey(tLink)) {
				double delta = (after.get(tLink) - before.get(tLink)) / Math.abs(before.get(tLink));
				if (delta >= 0.0) {
					retList.add(delta);
				}
			}
		}

		return retList;
	}

	private static Set<String> loadWardenASes(String filePath) throws IOException {
		HashSet<String> retSet = new HashSet<String>();
		BufferedReader inBuff = new BufferedReader(new FileReader(filePath));
		while (inBuff.ready()) {
			String readStr = inBuff.readLine().trim();
			if (readStr.length() > 0) {
				retSet.add(readStr);
			}
		}
		inBuff.close();
		return retSet;
	}

}
