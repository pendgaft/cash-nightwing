package sim;

import java.util.*;
import java.io.*;

import topo.AS;
import topo.BGPPath;
import util.Stats;
import decoy.DecoyAS;

/**
 * This class calculates the CDF graph of weighted traffic for both the
 * active and purged networks. But in this version, the traffic can only go from
 * the wardens, but cannot go through.
 * 
 * @author Bowen
 */

public class TrafficStat1 {
	/** Stores the active (routing) portion of the topology */
	private HashMap<Integer, DecoyAS> activeMap;
	/** Stores the pruned portion of the topology */
	private HashMap<Integer, DecoyAS> purgedMap;

	/** stores the traffic on each AS in the map */
	private HashMap<Integer, Double> countTraffic;

	/**
	 * store the paths of one purged node from different providers to the same
	 * destiny in a list
	 */
	private HashMap<Integer, List<BGPPath>> pathSets;
	private String outputFileName = "stats1";

	public TrafficStat1(HashMap<Integer, DecoyAS> activeMap,
			HashMap<Integer, DecoyAS> purgedMap) {

		this.activeMap = activeMap;
		this.purgedMap = purgedMap;
		countTraffic = new HashMap<Integer, Double>();
		pathSets = new HashMap<Integer, List<BGPPath>>();
	}

	/**
	 * add an unit to the given AS, if the AS is in the HashMap, increase its
	 * value by one, if it isn't in the HashMap, put it in it.
	 * 
	 * @param tHop
	 *            the ASN of the AS
	 */
	public void addTrafficUnit(int tHop) {
		if (countTraffic.containsKey(tHop))
			countTraffic.put(tHop, countTraffic.get(tHop) + 1.0);
		else
			countTraffic.put(tHop, 1.0);
	}
	
	public void addWeightedTraffic(int tHop, double amount) {
		if (countTraffic.containsKey(tHop))
			countTraffic.put(tHop, countTraffic.get(tHop) + amount);
		else
			countTraffic.put(tHop, amount);
	}

	/**
	 * add the traffic unit to all the nodes on the given path
	 * 
	 * @param path
	 *            the path to add traffic unit
	 */
	public void addTrafficOnThePath(BGPPath path, double amount) {
		for (int tHop : path.getPath()) {
			System.out.print(" to " + tHop);// !!
			addWeightedTraffic(tHop, amount);
		}
		System.out.println();// !!
	}

	/**
	 * add the path to the path container which stores all the paths of a purged
	 * AS going to a key AS via different providers
	 * 
	 * @param key
	 *            a destiny AS
	 * @param path
	 *            a path to the key AS
	 */
	public void addToPathSets(int key, BGPPath path) {
		if (pathSets.containsKey(key)) {
			List<BGPPath> tList = pathSets.get(key);
			tList.add(path);
			pathSets.put(key, tList);
		} else {
			List<BGPPath> tList = new ArrayList<BGPPath>();
			tList.add(path);
			pathSets.put(key, tList);
		}
	}

	/**
	 * Convert a set of ASes into a list of ASes
	 * 
	 * @param providers
	 *            ASes in a set
	 * @return the same ASes only in a list
	 */
	public List<Integer> getProvidersList(Set<AS> providers) {
		List<Integer> pList = new ArrayList<Integer>();
		for (AS tAS : providers) {
			pList.add(tAS.getASN());
		}
		return pList;
	}

	/**
	 * the traffic of all the paths starting from the given AS in activeMap to
	 * all ASes in the activeMap
	 * 
	 * @param tAS
	 *            the AS to start from
	 */
	public void activeToActive(AS tAS) {

		for (int t2ASN : this.activeMap.keySet()) {
			BGPPath tpath = tAS.getPath(t2ASN);
			// System.out.println("active!! " + t2ASN + " path " + tpath);
			if (tpath == null)
				continue;
			
			/* if the path exists, do the statistic */
			double amount = tAS.getIPCount();
			addTrafficOnThePath(tpath, amount);
		}
	}

	/**
	 * count the traffic of all the paths starting from the given AS in
	 * activeMap to all ASes in the purgedMap To get to the purged ASes, get all
	 * the providers of that ASes, and getPathToPurged will pick a best path.
	 * 
	 * @param tAS
	 *            the AS to start from
	 */
	public void activeToPurged(AS tAS) {

		for (DecoyAS purgedAS : this.purgedMap.values()) {
			List<Integer> hookASNs = getProvidersList(purgedAS.getProviders());
			BGPPath tpath = tAS.getPathToPurged(hookASNs);
			if (tpath == null)
				continue;

			double amount = tAS.getIPCount();
			addTrafficOnThePath(tpath, amount);
			/* add the traffic unit to the last node */
			addWeightedTraffic(purgedAS.getASN(), amount);
			System.out.println(" to " + purgedAS.getASN());// !!
		}
	}

	/**
	 * count the traffic of paths starting from activeMap ending at either
	 * activeMap or purgedMap
	 **/
	public void statActiveMap() {

		System.out.println("stat cctiveMap");// !!

		for (int t1ASN : this.activeMap.keySet()) {
			DecoyAS tAS = this.activeMap.get(t1ASN);
			System.out.println("from " + t1ASN);
			/* traffic from tAS which is in the activeMap to activeMap */
			activeToActive(tAS);

			/* traffic from tAS which is in the activeMap to purgedMap */
			System.out.println("to purged:");// !!
			activeToPurged(tAS);
			System.out.println();// !!
		}
	}

	/**
	 * count the traffic of all the paths starting from every AS in purgedMap to
	 * all ASes in the activeMap.
	 * 
	 * Get the providers of the purged AS, then get all the paths from the
	 * providers to other ASes in activeMap, and temporarily store in the
	 * container, then get the best path from the pathSet, and do the statistic.
	 */
	public void purgedToActive() {

		for (int t1ASN : this.purgedMap.keySet()) {
			/* empty the temporary container */
			pathSets.clear();

			System.out.println("from " + t1ASN + " to activeMap"); // !!
			DecoyAS tAS = this.purgedMap.get(t1ASN);

			/* get the path through the providers of nodes in purgedMap */
			Set<AS> providers = tAS.getProviders();
			for (AS pAS : providers) {
				for (int t2ASN : this.activeMap.keySet()) {
					/* get path from the provider to the activeMap */
					BGPPath tpath = pAS.getPath(t2ASN);
					if (tpath == null)
						continue;

					/*
					 * insert the src which is the provider in the front of the
					 * path, be careful about java here! need to do with a copy
					 * or, the old value will be changed!!!
					 */
					BGPPath cpath = tpath.deepCopy();
					cpath.prependASToPath(pAS.getASN());

					/* put the path into container. */
					addToPathSets(t2ASN, cpath);
				}
			}

			/* get best path from the pathSets and do the traffic stat */
			for (int tASN : pathSets.keySet()) {
				/* get the best path from the pathSets by calling pathSelection */
				BGPPath tpath = this.purgedMap.get(t1ASN).pathSelection(
						pathSets.get(tASN));
				/* stat the traffic on the path */
				double amount = tAS.getIPCount();
				addTrafficOnThePath(tpath, amount);
			}
		}
	}

	/**
	 * count the traffic of all the paths starting from every AS in purgedMap to
	 * all ASes in the activeMap.
	 * 
	 * Get the providers of the purged AS, then get the paths to the providers
	 * of other purged ASes, then get the best path of all of those by calling
	 * pathSelection(), and do the statistic.
	 */
	public void purgedToPurged() {
		/* path from purged AS t1ASN to purged AS t2ASN */
		for (int t1ASN : this.purgedMap.keySet()) {
			System.out.println("from " + t1ASN + " to purged");
			/* empty the temporary container */
			pathSets.clear();

			AS t1AS = this.purgedMap.get(t1ASN);
			/* get the providers of the AS in purgedMap */
			List<Integer> p1List = getProvidersList(t1AS.getProviders());
			/* get paths from the provider to the other ASes in purgedMap */
			for (int p1 : p1List) {
				AS pAS = this.activeMap.get(p1);

				for (int t2ASN : this.purgedMap.keySet()) {
					if (t1ASN == t2ASN)
						continue;

					AS t2AS = this.purgedMap.get(t2ASN);
					List<Integer> p2List = getProvidersList(t2AS.getProviders());

					BGPPath tpath = pAS.getPathToPurged(p2List);
					if (tpath == null)
						continue;
					BGPPath cpath = tpath.deepCopy();
					cpath.prependASToPath(pAS.getASN());

					/* put the path into container. */
					addToPathSets(t2ASN, cpath);
				}
			}
			/* select the best path and do the traffic stat */
			for (int t2ASN : pathSets.keySet()) {
				BGPPath tpath = t1AS.pathSelection(pathSets.get(t2ASN));
				if (tpath == null)
					continue;

				double amount = t1AS.getIPCount();
				addTrafficOnThePath(tpath, amount);
				addWeightedTraffic(t2ASN, amount);
				System.out.println(" to " + t2ASN);// !!
			}
		}
	}

	/**
	 * count the traffic of paths starting from purgedMap ending at either
	 * activeMap or purgedMap
	 **/
	public void statPurgedMap() {
		System.out.println("stat purgedMap");// !!

		purgedToActive();
		purgedToPurged();

	}

	public void runStat() throws IOException {

		statActiveMap();
		statPurgedMap();

		List<Double> statResult = new ArrayList<Double>(countTraffic.values());
		System.out.println("resultList: " + statResult);
		Stats.printCDF(statResult, outputFileName);

		System.out
				.println("The result of the CDF statistic is written into file: "
						+ outputFileName + ".");
	}
}

// git push
