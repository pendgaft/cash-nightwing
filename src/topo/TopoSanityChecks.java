package topo;

import java.util.*;
import java.io.*;

import decoy.DecoyAS;

public class TopoSanityChecks {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {

		//HashMap<Integer, DecoyAS> thisYear = ASTopoParser.parseFile("as-rel.txt", "china-as.txt");
		HashMap<Integer, DecoyAS> thisYear = ASTopoParser.parseFile("as-rel.txt", "iran-as.txt");
		HashMap<Integer, DecoyAS> thisYearPurge = ASTopoParser.doNetworkPrune(thisYear);
		//HashMap<Integer, DecoyAS> lastYear = ASTopoParser.parseFile("as-rel-2010.txt", "china-as.txt");
		HashMap<Integer, DecoyAS> lastYear = ASTopoParser.parseFile("as-rel-2010.txt", "iran-as.txt");
		HashMap<Integer, DecoyAS> lastYearPurge = ASTopoParser.doNetworkPrune(lastYear);


		figureOut(thisYear, lastYear);
		figureOut(lastYear, thisYear);


		
		
	}

	private static void figureOut(HashMap<Integer, DecoyAS> thisYear, HashMap<Integer, DecoyAS> lastYear){
		Set<Integer> currentASN = new HashSet<Integer>();
		currentASN.addAll(thisYear.keySet());
		currentASN.removeAll(lastYear.keySet());
		System.out.println("There are: " + currentASN.size() + " new transit ASNs");
		
		int peerToNotSum = 0;
		int newRel = 0;
		long relDelta = 0;
		for (int tASN : thisYear.keySet()) {
			DecoyAS lhsAS = thisYear.get(tASN);
			DecoyAS rhsAS = lastYear.get(tASN);

			if (rhsAS == null) {
				continue;
			}

			Set<AS> intSet = new HashSet<AS>();
			intSet.addAll(lhsAS.getPeers());
			intSet.removeAll(rhsAS.getPeers());
			for (AS tAS : intSet) {
				if (rhsAS.getProviders().contains(tAS) || rhsAS.getCustomers().contains(tAS)) {
					peerToNotSum++;
					relDelta++;
				} else {
					newRel++;
				}
			}

			intSet.clear();
			intSet.addAll(lhsAS.getCustomers());
			intSet.removeAll(rhsAS.getCustomers());
			for (AS tAS : intSet) {
				if (rhsAS.getProviders().contains(tAS) || rhsAS.getPeers().contains(tAS)) {
					relDelta++;
				} else {
					newRel++;
				}
			}

			intSet.clear();
			intSet.addAll(lhsAS.getProviders());
			intSet.removeAll(rhsAS.getProviders());
			for (AS tAS : intSet) {
				if (rhsAS.getCustomers().contains(tAS) || rhsAS.getPeers().contains(tAS)) {
					relDelta++;
				} else {
					newRel++;
				}
			}
		}

		System.out.println("new rel " + newRel + " relDelta " + relDelta + " peer to Not " + peerToNotSum);
	}
}
