package parsing;

import java.io.*;
import java.util.*;

import topo.ASRanker;

public class TorParsing {

	private HashMap<Integer, Integer> asToBW;
	private HashMap<Integer, Double> losses;
	private HashMap<Integer, Double> startingRev;

	private double sumTotalBW;
	
	private File outputDir;

	public static void main(String[] args) throws IOException {
		File startingDir = new File("nightwingData/rawLogs");
		File outDir = new File("nightwingData/parsedLogs");

		File[] runs = startingDir.listFiles();
		for (File tRun : runs) {
			if (TorParsing.isTorRelatedDir(tRun)) {
				System.out.println("on " + tRun.toString());
				TorParsing parser = new TorParsing(tRun, new File(outDir, tRun.getName()));
				parser.lossByValue();
			}
		}
	}

	public TorParsing(File baseFile, File outDir) throws IOException {
		File transitFile = new File(baseFile, "transit.log");
		this.outputDir = outDir;

		String bwFile = this.getCorrectBWFile(baseFile);
		this.loadASBW(bwFile);

		this.sumTotalBW = 0;
		for (int tASN : this.asToBW.keySet()) {
			this.sumTotalBW += this.asToBW.get(tASN);
		}

		this.startingRev = MaxParser.parseRealMoneyStarting(transitFile.getAbsolutePath());
		HashMap<Integer, HashMap<Integer, Double>> allLosses = MaxParser.parseRealMoney(transitFile.getAbsolutePath(),
				null);

		/*
		 * Ghetto hack to get the single map out of the list
		 */
		if (allLosses.size() != 1) {
			throw new RuntimeException("why more than 1");
		}
		for (HashMap<Integer, Double> tMap : allLosses.values()) {
			this.losses = tMap;
		}

		Set<Integer> toPurge = new HashSet<Integer>();
		for (int tASN : this.losses.keySet()) {
			if (this.losses.get(tASN) > 0.0) {
				toPurge.add(tASN);
			}
		}
		for (int tASN : toPurge) {
			this.losses.remove(tASN);
		}

	}

	private void loadASBW(String fileName) throws IOException {
		this.asToBW = new HashMap<Integer, Integer>();

		BufferedReader inBuff = new BufferedReader(new FileReader(fileName));
		String readStr = null;
		while ((readStr = inBuff.readLine()) != null) {
			String[] tokens = readStr.split(",");
			if (tokens.length == 2) {
				this.asToBW.put(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
			}
		}
		inBuff.close();
	}

	public void lossByValue() throws IOException {
		List<ASRanker> lossList = new ArrayList<ASRanker>();
		int lostBW = 0;
		for (int tASN : this.losses.keySet()) {
			lossList.add(new ASRanker(tASN, -1.0 * this.losses.get(tASN)));
			lostBW += this.asToBW.get(tASN);
		}
		Collections.sort(lossList);

		File outFile = new File(this.outputDir, "lossBySize.csv");
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));
		for (ASRanker tRanker : lossList) {
			int asn = tRanker.getASN();
			outBuff.write("" + (-1.0 * this.losses.get(asn)) + "," + lostBW + "," + ((double) lostBW / this.sumTotalBW)
					+ "\n");
			lostBW -= this.asToBW.get(asn);
		}
		outBuff.close();
	}

	private String getCorrectBWFile(File fullPath) {
		String baseFile = fullPath.getName();
		if (baseFile.contains("All") || baseFile.contains("full")) {
			return "cash-nightwing/tor-asn/asBW-all.csv";
		} else if (baseFile.contains("Exit") || baseFile.contains("exit")) {
			return "cash-nightwing/tor-asn/asBW-exits.csv";
		} else if (baseFile.contains("guard")) {
			return "cash-nightwing/tor-asn/asBW-guard.csv";
		}

		throw new RuntimeException("Wtf this isn't a tor file");
	}

	public static boolean isTorRelatedDir(File fullPath) {
		String dirPath = fullPath.getName();
		return dirPath.contains("All") || dirPath.contains("full") || dirPath.contains("Exit")
				|| dirPath.contains("exit") || dirPath.contains("guard");
	}

}
