package parsing;

import java.io.*;
import java.util.*;

import topo.ASTopoParser;
import decoy.DecoyAS;
import econ.EconomicEngine;

public class MiscParsing {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException{
		MiscParsing.addCCSizeToEcon(args[0], args[1]);

	}

	private static HashMap<Integer, Integer> buildCCSize() {
		HashMap<Integer, Integer> retMap = new HashMap<Integer, Integer>();
		
		try {
			HashMap<Integer, DecoyAS> asMap = ASTopoParser.parseFile("as-rel.txt", "china-as.txt", "superAS.txt");
			for(int tAS: asMap.keySet()){
				EconomicEngine.buildIndividualCustomerCone(asMap.get(tAS));
			}
			for(DecoyAS tAS: asMap.values()){
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

}
