package topo;

import java.util.*;
import java.util.regex.*;
import java.io.*;

import decoy.DecoyAS;

/**
 * Class made up of static methods used to build the topology used by the
 * nightwing simulator.
 * 
 * @author pendgaft
 * 
 */
public class ASTopoParser {

	private static final String AS_REL_FILE = "as-rel.txt";
	private static final String AS_IP_FILE = "ip-count.csv";

	public static void main(String args[]) throws IOException {
		/*
		 * This is no more, moved to BGPMaster at this point
		 */
	}

	/**
	 * Static method that builds AS objects along with the number of IP
	 * addresses they have. This function does NO PRUNING of the topology.
	 * 
	 * @param wardenFile
	 *            - a file that contains a list of ASNs that comprise the warden
	 * @return - an unpruned mapping between ASN and AS objects
	 * @throws IOException
	 *             - if there is an issue reading any config file
	 */
	public static HashMap<Integer, DecoyAS> doNetworkBuild(String wardenFile) throws IOException {

		HashMap<Integer, DecoyAS> asMap = ASTopoParser.parseFile(ASTopoParser.AS_REL_FILE, wardenFile);
		System.out.println("Raw topo size is: " + asMap.size());
		ASTopoParser.parseIPScoreFile(asMap);

		return asMap;
	}

	/**
	 * Simple static call to do the network prune. This servers as a single
	 * entry point to prune, allowing changes to the pruning strategy.
	 * 
	 * @param workingMap
	 *            - the unpruned AS map, this will be altered as a side effect
	 *            of this call
	 * @return - a mapping between ASN and AS object of PRUNED ASes
	 */
	public static HashMap<Integer, DecoyAS> doNetworkPrune(HashMap<Integer, DecoyAS> workingMap) {
		return ASTopoParser.pruneNoCustomerAS(workingMap);
	}

	/**
	 * Static method that parses the CAIDA as relationship files and the file
	 * that contains ASNs that make up the warden.
	 * 
	 * @param asRelFile
	 *            - CAIDA style AS relationship file
	 * @param wardenFile
	 *            - file with a list of ASNs that comprise the warden
	 * @return - an unpruned mapping between ASN and AS objects
	 * @throws IOException
	 *             - if there is an issue reading either config file
	 */
	private static HashMap<Integer, DecoyAS> parseFile(String asRelFile, String wardenFile) throws IOException {

		HashMap<Integer, DecoyAS> retMap = new HashMap<Integer, DecoyAS>();

		String pollString;
		StringTokenizer pollToks;
		int lhsASN, rhsASN, rel;

		BufferedReader fBuff = new BufferedReader(new FileReader(asRelFile));
		while (fBuff.ready()) {
			pollString = fBuff.readLine().trim();

			/*
			 * ignore blanks
			 */
			if (pollString.length() == 0) {
				continue;
			}

			/*
			 * Ignore comments
			 */
			if (pollString.charAt(0) == '#') {
				continue;
			}

			/*
			 * Parse line
			 */
			pollToks = new StringTokenizer(pollString, "|");
			lhsASN = Integer.parseInt(pollToks.nextToken());
			rhsASN = Integer.parseInt(pollToks.nextToken());
			rel = Integer.parseInt(pollToks.nextToken());

			/*
			 * Create either AS object if we've never encountered it before
			 */
			if (!retMap.containsKey(lhsASN)) {
				retMap.put(lhsASN, new DecoyAS(lhsASN));
			}
			if (!retMap.containsKey(rhsASN)) {
				retMap.put(rhsASN, new DecoyAS(rhsASN));
			}

			retMap.get(lhsASN).addRelation(retMap.get(rhsASN), rel);
		}
		fBuff.close();

		/*
		 * read the warden AS file, toggle all warden ASes
		 */
		fBuff = new BufferedReader(new FileReader(wardenFile));
		while (fBuff.ready()) {
			pollString = fBuff.readLine().trim();
			if (pollString.length() > 0) {
				int asn = Integer.parseInt(pollString);
				retMap.get(asn).toggleWardenAS();
			}
		}
		fBuff.close();

		return retMap;
	}

	/**
	 * Static method to parse the IP "score" (count) file (CSV), and add that
	 * attribute to the AS object
	 * 
	 * @param asMap
	 *            - the built as map (unpruned)
	 * @throws IOException
	 *             - if there is an issue reading from the IP count file
	 */
	private static void parseIPScoreFile(HashMap<Integer, DecoyAS> asMap) throws IOException {
		BufferedReader fBuff = new BufferedReader(new FileReader(ASTopoParser.AS_IP_FILE));
		Pattern csvPattern = Pattern.compile("(\\d+),(\\d+)");
		while (fBuff.ready()) {
			String pollString = fBuff.readLine();
			Matcher tMatch = csvPattern.matcher(pollString);
			tMatch.find();
			int tAS = Integer.parseInt(tMatch.group(1));
			int score = Integer.parseInt(tMatch.group(2));
			asMap.get(tAS).setIPCount(score);
		}
		fBuff.close();
	}

	public static void validateRelexive(HashMap<Integer, AS> asMap) {
		for (AS tAS : asMap.values()) {
			for (AS tCust : tAS.getCustomers()) {
				if (!tCust.getProviders().contains(tAS)) {
					System.out.println("fail - cust");
				}
			}
			for (AS tProv : tAS.getProviders()) {
				if (!tProv.getCustomers().contains(tAS)) {
					System.out.println("fail - prov");
				}
			}
			for (AS tPeer : tAS.getPeers()) {
				if (!tPeer.getPeers().contains(tAS)) {
					System.out.println("fail - peer");
				}
			}
		}
	}

	/**
	 * Static method that prunes out all ASes that have no customer ASes. In
	 * otherwords their customer cone is only themselves. This will alter the
	 * supplied AS mapping, reducing it in size and altering the AS objects
	 * 
	 * @param asMap
	 *            - the unpruned AS map, will be altered as a side effect
	 * @return - a mapping of ASN to AS object containing the PRUNED AS objects
	 */
	private static HashMap<Integer, DecoyAS> pruneNoCustomerAS(HashMap<Integer, DecoyAS> asMap) {
		HashMap<Integer, DecoyAS> purgeMap = new HashMap<Integer, DecoyAS>();

		/*
		 * Find the ASes w/o customers
		 */
		for (DecoyAS tAS : asMap.values()) {
			/*
			 * leave the all warden ASes connected to our topo
			 */
			if (tAS.isWardenAS() || tAS.connectedToWarden()) {
				continue;
			}

			if (tAS.getCustomerCount() == 0) {
				purgeMap.put(tAS.getASN(), tAS);
			}
		}

		/*
		 * Remove these guys from the asn map and remove them from their peer's
		 * data structure
		 */
		for (AS tAS : purgeMap.values()) {
			asMap.remove(tAS.getASN());
			tAS.purgeRelations();
		}

		return purgeMap;
	}
}
