package parsing;

import java.util.*;
import java.io.*;
import java.util.regex.*;

public class ValidRouteParse {

	private static final String ROUTE_DELIM = " ";
	private static final String SIM_DELIM = ",";

	private static final Pattern PATH_PATTERN = Pattern.compile("ASPATH: (.+)");

	public static final Pattern ROUND_PATTERN = Pattern.compile("[\\*#]{3}(\\d+),(\\d+)");

	public static void main(String[] args) throws Exception {

		fetch();
//		validCheck("realTopo/int-as.txt",
//				"/export/scratch2/schuch/nightwingData/pathStuff/rawLogs/CN-asOrderedCoveragePathLenNoRev/path.log",
//				"CN-pl-valid.csv");
	}

	private static void validCheck(String intFile, String pathFile, String outFile) throws Exception {
		double preTotal = 0.0;
		double postTotal = 0.0;

		double preSeen = 0.0;
		double postSeen = 0.0;

		HashSet<String> tested = new HashSet<String>();
		ObjectInputStream objIn = new ObjectInputStream(new FileInputStream("mergedTree"));
		HashMap baseTree = (HashMap) objIn.readObject();
		objIn.close();

		HashSet<String> intAS = new HashSet<String>();
		BufferedReader inBuff = new BufferedReader(new FileReader(intFile));
		while (inBuff.ready()) {
			String line = inBuff.readLine().trim();
			if (line.length() > 0) {
				intAS.add(line);
			}
		}
		inBuff.close();

		inBuff = new BufferedReader(new FileReader(pathFile));
		boolean currentlyPre = true;
		while (inBuff.ready()) {
			String line = inBuff.readLine().trim();
			if (line.length() == 0) {
				continue;
			}

			Matcher roundMatch = ValidRouteParse.ROUND_PATTERN.matcher(line);
			if (roundMatch.find()) {
				currentlyPre = Integer.parseInt(roundMatch.group(2)) == 0;
				continue;
			}

			/*
			 * If we have not already tested do so
			 */
			String[] splits = line.split(",");
			String[] toFrom = splits[0].split(":");
			String pathStr = splits[1].trim();
			if (!tested.contains(pathStr)) {

				boolean interesting = intAS.contains(toFrom[0]) && intAS.contains(toFrom[1]);
				if(!interesting){
					continue;
				}

				if (currentlyPre) {
					preTotal += 1.0;
				} else {
					postTotal += 1.0;
				}


				String[] hops = pathStr.split(" ");

				if (isValid(baseTree, hops)) {
					if (currentlyPre) {
						preSeen += 1.0;
					} else {
						postSeen += 1.0;
					}

				}

				tested.add(pathStr);
			}
		}
		inBuff.close();

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));
		outBuff.write("pre,post\n");
		outBuff.write("" + (preSeen / preTotal) + "," + (postSeen / postTotal) + "\n");
		outBuff.close();
	}

	private static boolean isValid(HashMap theTree, String[] path) {
		for (int i = 0; i < path.length; i++) {
			if (theTree.containsKey(path[i])) {
				theTree = (HashMap) theTree.get(path[i]);
			} else {
				return false;
			}
		}

		return true;
	}

	private static void fetch() throws Exception {
		HashMap baseMap = new HashMap();
		long startTime = System.currentTimeMillis();

		for (int month = 4; month < 8; month++) {
			String monthStr = String.format("%02d", month);
			for (int day = 1; day < 32; day++) {
				String dayStr = String.format("%02d", day);
				if (month == 2 && day > 28) {
					break;
				}
				if ((month == 9 || month == 4 || month == 6 || month == 11) && day > 30) {
					break;
				}
				for (int hourCounter = 0; hourCounter < 24; hourCounter++) {
					for (int minuteCounter = 0; minuteCounter < 60; minuteCounter += 5) {
						String hourStr = String.format("%04d", (hourCounter * 100 + minuteCounter));
						ValidRouteParse.handleFile(baseMap, "2015." + monthStr,
								"updates.2015" + monthStr + dayStr + "." + hourStr + ".gz");
					}
				}
			}
		}
		System.out.println("size is " + baseMap.size());
		System.out.println("took " + (System.currentTimeMillis() - startTime));

		startTime = System.currentTimeMillis();
		ObjectOutputStream obOut = new ObjectOutputStream(new FileOutputStream("theTree"));
		obOut.writeObject(baseMap);
		obOut.close();
		System.out.println("dump took " + (System.currentTimeMillis() - startTime));
	}

	private static void doFullMerge() throws Exception {
		HashMap baseMap = new HashMap();
		mergeMap(baseMap, "theTree-2014.1");
		mergeMap(baseMap, "theTree-2014.2");
		mergeMap(baseMap, "theTree-2015.1");
		mergeMap(baseMap, "theTree-2015.2");
		mergeMap(baseMap, "theTree-2015.3");
		mergeMap(baseMap, "theTree-2015.4");
		mergeMap(baseMap, "theTree-201601");

		ObjectOutputStream obOut = new ObjectOutputStream(new FileOutputStream("mergedTree"));
		obOut.writeObject(baseMap);
		obOut.close();
	}

	private static void mergeMap(HashMap currentTree, String treeSerialFile)
			throws IOException, ClassNotFoundException {
		System.out.println("On " + treeSerialFile);
		ObjectInputStream objIn = new ObjectInputStream(new FileInputStream(treeSerialFile));
		HashMap readInMap = (HashMap) objIn.readObject();
		objIn.close();
		ValidRouteParse.merge(currentTree, readInMap);
	}

	private static void merge(HashMap currentTree, HashMap treeToMoveIn) {
		for (String tKey : (Set<String>) treeToMoveIn.keySet()) {
			if (currentTree.containsKey(tKey)) {
				ValidRouteParse.merge((HashMap) currentTree.get(tKey), (HashMap) treeToMoveIn.get(tKey));
			} else {
				currentTree.put(tKey, treeToMoveIn.get(tKey));
			}
		}
	}

	private static void handleFile(HashMap currentTree, String stubURL, String fileURL)
			throws IOException, InterruptedException {
		System.out.println("on " + fileURL);
		String baseURL = "http://data.ris.ripe.net/rrc06/";

		/*
		 * Fetch the URL
		 */
		String fetchCmd = "wget " + baseURL + stubURL + "/" + fileURL;
		Process p = Runtime.getRuntime().exec(fetchCmd);
		p.waitFor();

		/*
		 * Unzip the file and delete the zip
		 */
		String unzipCmd = "gunzip " + fileURL;
		p = Runtime.getRuntime().exec(unzipCmd);
		p.waitFor();
		File tmpFile = new File(fileURL);
		tmpFile.delete();

		/*
		 * Invoke BGP dump, hunting for paths and then feeding them into our
		 * current tree
		 */
		String bgpdumpCmd = "bgpdump " + fileURL.substring(0, fileURL.length() - 3);
		p = Runtime.getRuntime().exec(bgpdumpCmd);
		BufferedReader outBuffer = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line = null;
		while ((line = outBuffer.readLine()) != null) {

			/*
			 * Hunt for AS path
			 */
			Matcher tmpMatcher = ValidRouteParse.PATH_PATTERN.matcher(line);
			if (tmpMatcher.find()) {
				ValidRouteParse.addToTree(currentTree, tmpMatcher.group(1));
			}

		}
		outBuffer.close();
		tmpFile = new File(fileURL.substring(0, fileURL.length() - 3));
		tmpFile.delete();
	}

	private static void addToTree(HashMap currentTree, String pathString) {
		String[] hops = pathString.split(ROUTE_DELIM);
		for (int firstCounter = 0; firstCounter < hops.length; firstCounter++) {
			HashMap tempMap = currentTree;
			for (int innerCounter = firstCounter; innerCounter < hops.length; innerCounter++) {
				String tmpStr = hops[innerCounter];
				tmpStr = tmpStr.trim();
				if (!tempMap.containsKey(tmpStr)) {
					tempMap.put(tmpStr, new HashMap());
				}
				tempMap = (HashMap) tempMap.get(tmpStr);
			}
		}
	}

	private static boolean routeSeen(HashMap currentTree, String simPathString) {
		String[] hops = simPathString.split(SIM_DELIM);
		for (String tmpStr : hops) {
			tmpStr = tmpStr.trim();
			if (!currentTree.containsKey(tmpStr)) {
				return false;
			}
			currentTree = (HashMap) currentTree.get(tmpStr);
		}
		return true;
	}

}
