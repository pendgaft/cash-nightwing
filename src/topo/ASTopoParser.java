package topo;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.*;
import java.io.*;

import decoy.DecoyAS;
import sim.Constants;

/**
 * Class made up of static methods used to build the topology used by the
 * nightwing simulator.
 * 
 * @author pendgaft
 * 
 */
public class ASTopoParser {

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
	public static TIntObjectMap<DecoyAS> doNetworkBuild(String wardenFile, AS.AvoidMode avoidMode,
			AS.ReversePoisonMode poisonMode) throws IOException {

		TIntObjectMap<DecoyAS> asMap = ASTopoParser.parseFile(Constants.AS_REL_FILE, wardenFile,
				Constants.SUPER_AS_FILE, avoidMode, poisonMode);
		System.out.println("Raw topo size is: " + asMap.size());
		ASTopoParser.parseIPScoreFile(asMap, Constants.IP_COUNT_FILE);

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
	public static TIntObjectMap<DecoyAS> doNetworkPrune(TIntObjectMap<DecoyAS> workingMap) {
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
	public static TIntObjectMap<DecoyAS> parseFile(String asRelFile, String wardenFile, String superASFile,
			AS.AvoidMode avoidMode, AS.ReversePoisonMode poisonMode) throws IOException {

		TIntObjectMap<DecoyAS> retMap = new TIntObjectHashMap<DecoyAS>();

		String pollString;
		StringTokenizer pollToks;
		int lhsASN, rhsASN, rel;

		System.out.println(asRelFile);

		BufferedReader fBuff = new BufferedReader(new FileReader(asRelFile));
		while (fBuff.ready()) {
			pollString = fBuff.readLine().trim();
			if (Constants.DEBUG) {
				System.out.println(pollString);
			}

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
		 * A little bit of short circuit logic bolted on so outside
		 * classes/projects can use this and not need to re-do code
		 */
		if (wardenFile == null && superASFile == null) {
			return retMap;
		}

		/*
		 * read the warden AS file, toggle all warden ASes
		 */
		fBuff = new BufferedReader(new FileReader(wardenFile));
		Set<AS> wardenSet = new HashSet<AS>();
		int lostWardens = 0;
		while (fBuff.ready()) {
			pollString = fBuff.readLine().trim();
			if (pollString.length() > 0) {
				int asn = Integer.parseInt(pollString);

				/*
				 * Sanity check that the warden AS actually exists in the topo
				 * and flag it, the warden AS not existing is kinda bad
				 */
				if (retMap.get(asn) != null) {
					retMap.get(asn).toggleWardenAS(avoidMode, poisonMode);
					wardenSet.add(retMap.get(asn));
				} else {
					lostWardens++;
				}
			}
		}
		fBuff.close();

		System.out.println(lostWardens + " listed warden ASes failed to exist in the topology.");

		/*
		 * Give all nodes a copy of the warden set
		 */
		for (DecoyAS tAS : retMap.valueCollection()) {
			tAS.setWardenSet(wardenSet);
		}

		/*
		 * read the super AS file, toggle all super ASes
		 */
		fBuff = new BufferedReader(new FileReader(superASFile));
		while (fBuff.ready()) {
			pollString = fBuff.readLine().trim();
			if (pollString.length() == 0) {
				continue;
			}

			/*
			 * Ignore comments
			 */
			if (pollString.charAt(0) == '#') {
				continue;
			}
			int asn = Integer.parseInt(pollString);
			System.out.println("superAS: " + asn);
			retMap.get(asn).toggleSupperAS();
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
	public static void parseIPScoreFile(TIntObjectMap<DecoyAS> asMap, String ipCountFile) throws IOException {
		BufferedReader fBuff = new BufferedReader(new FileReader(ipCountFile));
		int unMatchedAS = 0;
		long lostIPs = 0;
		while (fBuff.ready()) {
			String pollString = fBuff.readLine().trim();
			if (pollString.length() == 0 || pollString.charAt(0) == '#') {
				continue;
			}
			StringTokenizer tokenizerTokens = new StringTokenizer(pollString, " ");
			int tAS = Integer.parseInt(tokenizerTokens.nextToken());
			int score = Integer.parseInt(tokenizerTokens.nextToken());

			/*
			 * Sanity check that the AS actually is in the topo and ad the IPs,
			 * in theory this should never happen
			 */
			if (asMap.containsKey(tAS)) {
				asMap.get(tAS).setIPCount(score);
			} else {
				unMatchedAS++;
				lostIPs += score;
			}
		}
		fBuff.close();

		System.out.println(unMatchedAS + " times AS records were not found in IP seeding.");
		System.out.println(lostIPs + " IP addresses lost as a result.");
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
	private static TIntObjectMap<DecoyAS> pruneNoCustomerAS(TIntObjectMap<DecoyAS> asMap) {
		TIntObjectMap<DecoyAS> purgeMap = null;

		TIntObjectMap<DecoyAS> testAgressiveMap = new TIntObjectHashMap<DecoyAS>();
		TIntObjectMap<DecoyAS> testNonAgressiveMap = new TIntObjectHashMap<DecoyAS>();

		/*
		 * Find the ASes w/o customers
		 */
		for (DecoyAS tAS : asMap.valueCollection()) {
			/*
			 * Leave the super ASes in the topology as well for an efficency
			 * gain
			 */
			if (tAS.isSuperAS()) {
				continue;
			}

			/*
			 * leave the all warden ASes connected to our topo
			 */
			if (tAS.isWardenAS()) {
				continue;
			}

			/*
			 * Add to the correct (agressive prune vs non-agressive prune) maps
			 */
			if (tAS.getNonPrunedCustomerCount() == 0) {
				testAgressiveMap.put(tAS.getASN(), tAS);

				if (!tAS.connectedToWarden()) {
					testNonAgressiveMap.put(tAS.getASN(), tAS);
				}
			}
		}

		/*
		 * Choose which we want, agressive or non-agressive, based on resulting
		 * size of active topo
		 */
		if (asMap.size() - testNonAgressiveMap.size() <= Constants.MAX_LIVE_TOPO_SIZE) {
			System.out.println("Selected NON-AGRESSIVE prune.");
			purgeMap = testNonAgressiveMap;
		} else if (asMap.size() - testAgressiveMap.size() <= Constants.MAX_LIVE_TOPO_SIZE) {
			System.out.println("Selected AGRESSIVE prune.");
			purgeMap = testAgressiveMap;
		} else {
			throw new RuntimeException("No topology small enough! (" + (asMap.size() - testNonAgressiveMap.size())
					+ " agresssive prune size)");
		}

		/*
		 * Remove these guys from the asn map and remove them from their peer's
		 * data structure
		 */
		for (AS tAS : purgeMap.valueCollection()) {
			asMap.remove(tAS.getASN());
			tAS.purgeRelations();
		}

		return purgeMap;
	}
}
