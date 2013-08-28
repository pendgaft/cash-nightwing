package sim;

import java.util.*;
import java.io.*;

import topo.AS;
import topo.BGPPath;
import decoy.DecoyAS;

/**
 * This class calculates the CDF graph of weighted traffic for both the active
 * and purged networks. But in this version, the traffic can only go from the
 * wardens, but cannot go through.
 * 
 * @author Bowen
 */

public class ParallelTrafficStat {

	/*
	 * Values needed to drive traffic flows, namely ratios of p2p vs super as
	 * traffic and the total amount of p2p traffic
	 */
	private double totalP2PTraffic;
	private double p2pRatio;
	private double superASRatio;

	/** Stores the active (routing) portion of the topology */
	private HashMap<Integer, DecoyAS> activeMap;
	/** Stores the pruned portion of the topology */
	private HashMap<Integer, DecoyAS> purgedMap;
	private HashMap<Integer, DecoyAS> fullTopology;
	/**
	 * store the ASN whose ip count is not zero, used to split ASes among the
	 * threads
	 */
	private List<Integer> validASNList;

	private List<List<Integer>> workSplit;

	private static final boolean DEBUG = false;
	private static final boolean REPORT_TIMING = true;
	private static final boolean FROMSUPERAS = true;
	private static final boolean NOTFROMSUPERAS = false;

	/** stores normal ASes and super ASes */
	private List<DecoyAS> normalASList;
	private List<DecoyAS> superASList;

	/**
	 * constructor function
	 * 
	 * @param activeMap
	 * @param purgedMap
	 * @param trafficSplitFile
	 */
	public ParallelTrafficStat(HashMap<Integer, DecoyAS> activeMap, HashMap<Integer, DecoyAS> purgedMap,
			String trafficSplitFile) {

		this.totalP2PTraffic = 0;
		this.activeMap = activeMap;
		this.purgedMap = purgedMap;
		this.fullTopology = new HashMap<Integer, DecoyAS>();
		this.validASNList = new ArrayList<Integer>();

		for (int tASN : this.activeMap.keySet()) {
			this.fullTopology.put(tASN, this.activeMap.get(tASN));
			/* only need AS which has IP count */
			if (this.activeMap.get(tASN).getIPCount() != 0) {
				this.validASNList.add(tASN);
			}
		}
		for (int tASN : this.purgedMap.keySet()) {
			this.fullTopology.put(tASN, this.purgedMap.get(tASN));
			if (this.purgedMap.get(tASN).getIPCount() != 0) {
				this.validASNList.add(tASN);
			}
		}
		this.normalASList = new ArrayList<DecoyAS>();
		this.superASList = new ArrayList<DecoyAS>();
		this.statTotalP2PTraffic();

		try {
			this.loadTrafficRatios(trafficSplitFile);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		/*
		 * Divide up the ASes into working groups for threads
		 */
		this.workSplit = divideASToWorkSets();
	}

	/**
	 * the class used to run to statistic the traffic only on peer to peer
	 * network in parallel with atomic operation by semaphore of each AS
	 * 
	 * @author bobo
	 */
	private class ParallelRunningThread implements Runnable {

		private List<Integer> localASList;

		ParallelRunningThread(List<Integer> ASes) {
			this.localASList = ASes;
		}

		@Override
		public void run() {

			for (int tASN : this.localASList) {
				if (activeMap.containsKey(tASN)) {
					activeToActive(fullTopology.get(tASN));
					activeToPurged(fullTopology.get(tASN));
				} else if (purgedMap.containsKey(tASN)) {
					HashMap<Integer, Integer> toActiveMap = purgedToActive(fullTopology.get(tASN));
					purgedToPurged(fullTopology.get(tASN), toActiveMap);
				} else {
					System.out.println("Inlegel Decoy AS!!");
					System.exit(-1);
				}
			}
		}
	}

	private void loadTrafficRatios(String trafficSplitFile) throws IOException {
		BufferedReader fBuff = new BufferedReader(new FileReader(trafficSplitFile));
		while (fBuff.ready()) {

			String pollString = fBuff.readLine().trim();
			/* ignore blanks and comments */
			if (pollString.length() == 0 || pollString.charAt(0) == '#') {
				continue;
			}
			/* Parse line */
			StringTokenizer pollToks = new StringTokenizer(pollString, ",");
			int flag = Integer.parseInt(pollToks.nextToken());
			double value = Double.parseDouble(pollToks.nextToken());
			if (flag == 1) {
				this.p2pRatio = value;
			} else if (flag == 2) {
				this.superASRatio = value;
			} else {
				System.err.println("Bad flag in traffic split file.");
				System.exit(-2);
			}
		}
		fBuff.close();

		if (this.p2pRatio + this.superASRatio != 1.0) {
			System.err.println("Invalid set of ratios in traffic split file (does not add to 1.0).");
			System.exit(-2);
		}
	}

	public void runStat() {
		/*
		 * Clear out the values from last round
		 */
		for (DecoyAS tAS : this.fullTopology.values()) {
			tAS.resetTraffic();
		}

		long startTime, ptpNetwork, superAS;
		startTime = System.currentTimeMillis();

		/*
		 * Run the p2p traffic flow
		 */
		this.statTrafficOnPToPNetworkInParallel();
		ptpNetwork = System.currentTimeMillis();
		if (ParallelTrafficStat.REPORT_TIMING) {
			System.out.println("\nTraffic flowing on peer to peer network done, this took: " + (ptpNetwork - startTime)
					/ 60000 + " minutes. ");
		}

		/*
		 * Run the super AS traffic flow
		 */
		this.statTrafficFromSuperAS();
		superAS = System.currentTimeMillis();
		if (ParallelTrafficStat.REPORT_TIMING) {
			System.out.println("Traffic flowing from super ASes done, this took: " + (superAS - ptpNetwork) / 60000
					+ " minutes.");
		}

		if (ParallelTrafficStat.DEBUG) {
			testResults();
		}
	}

	/**
	 * split the ASes about equally to each thread and store them in an array of
	 * lists
	 * 
	 * @return
	 */
	private List<List<Integer>> divideASToWorkSets() {

		int amount = (int) (this.validASNList.size() / Constants.NTHREADS);
		int left = (int) (this.validASNList.size() % Constants.NTHREADS);
		List<List<Integer>> allLists = new ArrayList<List<Integer>>();

		/* distribute the ASes */
		int currentPos = 0;
		for (int i = 0; i < Constants.NTHREADS; ++i) {
			ArrayList<Integer> tempList = new ArrayList<Integer>();
			if (i == Constants.NTHREADS - 1) {
				tempList.addAll(this.validASNList.subList(currentPos, currentPos + amount + left));
			} else {
				tempList.addAll(this.validASNList.subList(currentPos, currentPos + amount));
				currentPos += amount;
			}
			allLists.add(tempList);
		}
		return allLists;
	}

	/**
	 * calculate the traffic on peer-to-peer network create two kinds of thread
	 * objects
	 * 
	 */
	private void statTrafficOnPToPNetworkInParallel() {

		/* make the threads run to calculate its own list of ASes */
		Thread[] workers = new Thread[Constants.NTHREADS];
		for (int i = 0; i < Constants.NTHREADS; ++i) {
			workers[i] = new Thread(new ParallelRunningThread(this.workSplit.get(i)));
			workers[i].start();
		}

		/* wait for threads finish */
		try {
			for (Thread thread : workers) {
				thread.join();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(-3);
		}
	}

	/**
	 * calculate the amount of traffic flowing from superAS to each AS based on
	 * the total traffic on the peer-to-peer network and the estimated ratio of
	 * total traffic on the peer-to-peer network and the total amount traffic
	 * flowing from all superASes.
	 * 
	 * the traffic of each super AS is equally split in this case.
	 * 
	 * finally draw the CDF graph of the amount of traffic flowing from one
	 * superAS to all regular ASes.
	 * 
	 * the amount of traffic to each regular AS is estimated based on the
	 * peer-to-peer network, which is the total amount traffic of a super AS
	 * times the ratio of the traffic flowing over the regular to the total
	 * amount of traffic on the peer-to-peer network
	 * 
	 * @throws IOException
	 */
	private void statTrafficFromSuperAS() {

		if (ParallelTrafficStat.DEBUG) {
			System.out.println("The total traffic on peer-to-peer network: " + getTotalP2PTraffic());
		}

		/*
		 * read the file and calculate the traffic flowing to each normal AS
		 * from each super AS
		 */
		double totalTrafficFromSuperASes = calcSuperASTrafficVolume();
		groupASesAndCalcTrafficForNormalAS(totalTrafficFromSuperASes);

		trafficFromSuperASToNormalAS();
	}

	/**
	 * for each current AS on the path, add its next hop as its neighbor with
	 * the traffic amount equals to srcAS's ip count times destAS's ip count
	 * 
	 * parallel version
	 * 
	 * @param tpath
	 * @param srcAS
	 * @param destAS
	 * @return
	 */
	private double addTrafficToLinks(BGPPath tpath, DecoyAS srcAS, DecoyAS destAS, boolean fromSuperAS) {

		double amountOfTraffic = 0;
		if (fromSuperAS) {
			amountOfTraffic = destAS.getTrafficFromEachSuperAS();
		} else {
			amountOfTraffic = srcAS.getIPCount() * destAS.getIPCount();
		}

		// no traffic between super ASes
		if (fromSuperAS && destAS.isSuperAS()) {
			return 0;
		}
		// add the first
		srcAS.updateTrafficOverOneNeighbor(tpath.getNextHop(), amountOfTraffic);

		List<Integer> pathList = tpath.getPath();
		if (pathList.size() != 1) {
			// add on the path
			for (int tASN = 0; tASN < pathList.size() - 1; ++tASN) {
				DecoyAS currentAS = this.fullTopology.get(pathList.get(tASN));
				DecoyAS nextAS = this.fullTopology.get(pathList.get(tASN + 1));
				currentAS.updateTrafficOverOneNeighbor(nextAS.getASN(), amountOfTraffic);
			}
		}

		return amountOfTraffic;
	}

	private double workAroundAddTrafficToLinks(BGPPath tpath, DecoyAS srcAS, DecoyAS destAS, int missingHop,
			boolean fromSuperAS) {

		double amountOfTraffic = 0;
		if (fromSuperAS) {
			amountOfTraffic = destAS.getTrafficFromEachSuperAS();
		} else {
			amountOfTraffic = srcAS.getIPCount() * destAS.getIPCount();
		}

		// no traffic between super ASes
		if (fromSuperAS && destAS.isSuperAS()) {
			return 0;
		}

		/*
		 * Because of how BGPPath are stored in the simulator, add traffic to
		 * the first and second hops manually
		 */
		srcAS.updateTrafficOverOneNeighbor(missingHop, amountOfTraffic);
		if(missingHop != tpath.getNextHop()){
			this.fullTopology.get(missingHop).updateTrafficOverOneNeighbor(tpath.getNextHop(), amountOfTraffic);
		}

		List<Integer> pathList = tpath.getPath();
		// add on the path
		for (int pos = 0; pos < pathList.size() - 1; pos++) {
			DecoyAS currentAS = this.fullTopology.get(pathList.get(pos));
			currentAS.updateTrafficOverOneNeighbor(pathList.get(pos + 1), amountOfTraffic);
		}

		return amountOfTraffic;
	}

	/**
	 * does the same thing as addTrafficOnTheLinkBasisPath(BGPPath tpath,
	 * DecoyAS srcAS, DecoyAS destAS), only difference is that the path doesn't
	 * contain the AS on the purged map, so need to add them manually.
	 * 
	 * parallel version
	 * 
	 * @param tpath
	 * @param srcAS
	 * @param destPurgedAS
	 */
	private void addTrafficOnTheLinkBasisPathAndPurgedInParallel(BGPPath tpath, DecoyAS srcAS, DecoyAS destPurgedAS,
			boolean fromSuperAS) {

		double amountOfTraffic = this.addTrafficToLinks(tpath, srcAS, destPurgedAS, fromSuperAS);
		// tpath doesn't contain the destiny AS on the purged map, so add the
		// traffic on it manually
		List<Integer> pathList = tpath.getPath();
		DecoyAS secondLastAS = this.fullTopology.get(pathList.get(pathList.size() - 1));
		secondLastAS.updateTrafficOverOneNeighbor(destPurgedAS.getASN(), amountOfTraffic);
	}

	/**
	 * serial version which works the same as parallel version, only used for
	 * traffic from super AS
	 * 
	 * @param tpath
	 * @param srcAS
	 * @param destPurgedAS
	 * @param fromSuperAS
	 */
	private void addTrafficOnTheLinkBasisPathAndPurgedInSerial(BGPPath tpath, DecoyAS srcAS, DecoyAS destPurgedAS,
			boolean fromSuperAS) {

		double amountOfTraffic = this.addTrafficToLinks(tpath, srcAS, destPurgedAS, fromSuperAS);
		// tpath doesn't contain the destiny AS on the purged map, so add the
		// traffic on it manually
		List<Integer> pathList = tpath.getPath();
		DecoyAS secondLastAS = this.fullTopology.get(pathList.get(pathList.size() - 1));
		secondLastAS.updateTrafficOverOneNeighbor(destPurgedAS.getASN(), amountOfTraffic);
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
	private static void addToPathSets(HashMap<Integer, List<BGPPath>> pathSets, int key, BGPPath path) {

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
	private List<Integer> getProvidersList(Set<AS> providers) {
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
	 * also stat traffic in link basis, accumulate the traffic for the neighbors
	 * of each AS. basically, if a path A B C D flowing from A to D, then A add
	 * B as its neighbor with traffic of A's ip amount times D's ip amount. same
	 * for B, C is added as its neighbor with traffic of A's ip amount times D's
	 * ip amount.
	 * 
	 * parallel version
	 * 
	 * @param srcActiveAS
	 *            the AS to start from
	 */
	private void activeToActive(DecoyAS srcActiveAS) {

		for (DecoyAS tdestActiveAS : this.activeMap.values()) {
			if (srcActiveAS.getASN() == tdestActiveAS.getASN()) {
				continue;
			}

			/*
			 * Fetch the actual path object, deal with the odd case in which it
			 * does not exist
			 */
			BGPPath tpath = srcActiveAS.getPath(tdestActiveAS.getASN());
			if (tpath == null)
				continue;

			/* if the path exists, do the statistic */
			this.addTrafficToLinks(tpath, srcActiveAS, tdestActiveAS, ParallelTrafficStat.NOTFROMSUPERAS);
		}
	}

	/**
	 * count the traffic of all the paths starting from the given AS in
	 * activeMap to all ASes in the purgedMap To get to the purged ASes, get all
	 * the providers of that ASes, and getPathToPurged will pick a best path.
	 * 
	 * also stat the from/to-warden traffic at the same time stat the link basis
	 * traffic flow at the same time
	 * 
	 * parallel version
	 * 
	 * @param srcActiveAS
	 *            the AS to start from
	 */
	private void activeToPurged(DecoyAS srcActiveAS) {

		for (DecoyAS tdestPurgedAS : this.purgedMap.values()) {
			List<Integer> hookASNs = getProvidersList(tdestPurgedAS.getProviders());
			BGPPath tpath = srcActiveAS.getPathToPurged(hookASNs);
			if (tpath == null)
				continue;

			if (tdestPurgedAS.getProviders().contains(srcActiveAS)) {
				double amountOfTraffic = srcActiveAS.getIPCount() * tdestPurgedAS.getIPCount();
				srcActiveAS.updateTrafficOverOneNeighbor(tdestPurgedAS.getASN(), amountOfTraffic);
				continue;
			}
			this.addTrafficOnTheLinkBasisPathAndPurgedInParallel(tpath, srcActiveAS, tdestPurgedAS,
					ParallelTrafficStat.NOTFROMSUPERAS);
		}
	}

	/**
	 * count the traffic of all the paths starting from every AS in purgedMap to
	 * all ASes in the activeMap.
	 * 
	 * Get the providers of the purged AS, then get all the paths from the
	 * providers to other ASes in activeMap, and temporarily store in the
	 * container, then get the best path from the pathSet, and do the statistic.
	 * 
	 * stat the link basis traffic flow at the same time
	 * 
	 */
	private HashMap<Integer, Integer> purgedToActive(DecoyAS srcPurgedAS) {
		HashMap<Integer, Integer> bestPathMapping = new HashMap<Integer, Integer>();
		HashMap<Integer, BGPPath> availMap = new HashMap<Integer, BGPPath>();

		/* get the path through the providers of nodes in purgedMap */
		Set<AS> providers = srcPurgedAS.getProviders();
		for (int tdestActiveASN : this.activeMap.keySet()) {
			availMap.clear();
			for (AS tProviderAS : providers) {
				BGPPath tpath = tProviderAS.getPath(tdestActiveASN);
				if (tpath == null)
					continue;
				availMap.put(tProviderAS.getASN(), tpath);
			}

			int bestNextHop = srcPurgedAS.purgedToActivePathSelection(availMap);
			if (bestNextHop == -1) {
				continue;
			}

			this.workAroundAddTrafficToLinks(availMap.get(bestNextHop), srcPurgedAS,
					this.fullTopology.get(tdestActiveASN), bestNextHop, false);
			bestPathMapping.put(tdestActiveASN, bestNextHop);
		}

		return bestPathMapping;
	}

	/**
	 * count the traffic of all the paths starting from every AS in purgedMap to
	 * all ASes in the activeMap.
	 * 
	 * Get the providers of the purged AS, then get the paths to the providers
	 * of other purged ASes, then get the best path of all of those by calling
	 * pathSelection(), and do the statistic.
	 * 
	 * stat the link basis traffic flow at the same time
	 * 
	 * parallel version
	 */
	private void purgedToPurged(DecoyAS srcPurgedAS, HashMap<Integer, Integer> toActiveMap) {

		HashMap<Integer, BGPPath> availMap = new HashMap<Integer, BGPPath>();

		for (int tdestPurgedASN : this.purgedMap.keySet()) {
			if (srcPurgedAS.getASN() == tdestPurgedASN)
				continue;

			availMap.clear();
			List<Integer> destProviderList = this.getProvidersList(this.fullTopology.get(tdestPurgedASN).getProviders());
			for (int tDestHook : destProviderList) {
				if (toActiveMap.containsKey(tDestHook)) {
					BGPPath tPath = this.fullTopology.get(toActiveMap.get(tDestHook)).getPath(tDestHook);
					availMap.put(tDestHook, tPath);
				}
			}

			int lastHop = srcPurgedAS.purgedToPurgedPathSelection(availMap, toActiveMap);
			if (lastHop == -1) {
				continue;
			}
			BGPPath bestPath = availMap.get(lastHop);

			double amountOfTraffic = this.workAroundAddTrafficToLinks(bestPath, srcPurgedAS, this.fullTopology.get(tdestPurgedASN),
					toActiveMap.get(lastHop), false);
			DecoyAS secondLastAS = this.fullTopology.get(bestPath.getPath().get(bestPath.getPathLength() - 1));
			secondLastAS.updateTrafficOverOneNeighbor(tdestPurgedASN, amountOfTraffic);
			
		}
	}

	/**
	 * calculate the total amount of traffic flowing on the peer to peer
	 * network.
	 * 
	 * @throws IOException
	 */
	private void statTotalP2PTraffic() {

		int ips[] = new int[this.activeMap.size() + this.purgedMap.size()];

		int counter = 0;
		for (DecoyAS tAS : this.fullTopology.values()) {
			ips[counter] = tAS.getIPCount();
			counter++;
		}

		for (int i = 0; i < ips.length; ++i)
			for (int j = i + 1; j < ips.length; ++j)
				this.totalP2PTraffic += ips[i] * ips[j];
		this.totalP2PTraffic *= 2;
	}

	/**
	 * @return the total amount of traffic flowing on the peer to peer network.
	 */
	private double getTotalP2PTraffic() {
		return this.totalP2PTraffic;
	}

	/**
	 * group all the ASes into normal ASes and super ASes. create a list of
	 * normal ASes, and set the ipCount percentage for each of them and
	 * calculate the traffic sent from each super AS to that normal AS and store
	 * the value locally. Also create a list of super ASes.
	 * 
	 * @param totalTrafficFromSuperASes
	 */
	private void groupASesAndCalcTrafficForNormalAS(double totalTrafficFromSuperASes) {
		int superASNum = 0;
		double totalIPCounts = 0;
		for (DecoyAS tAS : this.activeMap.values()) {
			if (!tAS.isSuperAS()) {
				this.normalASList.add(tAS);
				totalIPCounts += tAS.getIPCount();
			} else {
				this.superASList.add(tAS);
				superASNum++;
			}
		}
		for (DecoyAS tAS : this.purgedMap.values()) {
			if (!tAS.isSuperAS()) {
				this.normalASList.add(tAS);
				totalIPCounts += tAS.getIPCount();
			} else {
				this.superASList.add(tAS);
				superASNum++;
			}
		}

		/* calculate the percentage */
		double trafficFromOneSuperAS = totalTrafficFromSuperASes / superASNum;
		for (DecoyAS tAS : this.normalASList) {
			tAS.setIPPercentage(tAS.getIPCount() / totalIPCounts);
			tAS.setTrafficFromEachSuperAS(tAS.getIPCount() / totalIPCounts * trafficFromOneSuperAS);
		}
	}

	/**
	 * read the ratio of the traffic on peer to peer network and the traffic
	 * flowing from the super ASes from the traffic split file, based on that
	 * calculate the total amount of traffic flowing from all super ASes. then
	 * assume that the total amount of traffic is split equally on each super
	 * AS, hence, divided by the number of super ASes, and return the total
	 * amount of traffic flowing from each super AS.
	 * 
	 * @return
	 * @throws IOException
	 */
	private double calcSuperASTrafficVolume() {

		/* calculate based on the ratio, and then split equally */
		double totalTrafficFromSuperASes = getTotalP2PTraffic() * this.superASRatio / this.p2pRatio;

		return totalTrafficFromSuperASes;
	}

	/**
	 * very similar to purgedToActive() function, but only start from super ASes
	 * in the purged network, and add the traffic from each super AS of the
	 * destiny AS to all the normal ASes on the path
	 * 
	 * stat the link basis traffic at the same time
	 */
	private void statTrafficFromSuperASPurgedToActive() {

		HashMap<Integer, List<BGPPath>> pathSets = new HashMap<Integer, List<BGPPath>>();
		for (int tsrcASN : this.purgedMap.keySet()) {
			/* only select super ASes in the purged network */
			if (!this.purgedMap.get(tsrcASN).isSuperAS())
				continue;
			/* empty the temporary container */
			pathSets.clear();
			DecoyAS srcSuperAS = this.purgedMap.get(tsrcASN);

			/* get the path through the providers of nodes in purgedMap */
			Set<AS> providers = srcSuperAS.getProviders();
			for (AS tSuperASProvider : providers) {
				for (int tdestASN : this.activeMap.keySet()) {

					/* only count traffic from super ASes to normal ASes */
					if (this.activeMap.get(tdestASN).isSuperAS())
						continue;

					/* get path from the provider to the activeMap */
					BGPPath tpath = tSuperASProvider.getPath(tdestASN);
					if (tpath == null)
						continue;

					/*
					 * insert the src which is the provider in the front of the
					 * path, be careful about java here! need to do with a copy
					 * or, the old value will be changed!!!
					 */
					BGPPath cpath = tpath.deepCopy();
					cpath.prependASToPath(tSuperASProvider.getASN());

					/* put the path into container. */
					ParallelTrafficStat.addToPathSets(pathSets, tdestASN, cpath);
				}
			}

			/* get best path from the pathSets and do the traffic stat */
			for (int tdestASN : pathSets.keySet()) {
				/* get the best path from the pathSets by calling pathSelection */
				BGPPath tpath = this.purgedMap.get(tsrcASN).pathSelection(pathSets.get(tdestASN));
				/* stat the traffic on the path */
				this.addTrafficToLinks(tpath, srcSuperAS, this.fullTopology.get(tdestASN),
						ParallelTrafficStat.FROMSUPERAS);
			}
		}

	}

	/**
	 * very similar to purgedToPurged() function, but only start from super ASes
	 * in the purged network, and add the traffic from each super AS of the
	 * destiny AS to all the normal ASes on the path
	 * 
	 * stat the link basis traffic at the same time
	 */
	private void statFromTrafficSuperASPurgedToPurged() {

		/* path from purged AS t1ASN to purged AS t2ASN */
		HashMap<Integer, List<BGPPath>> pathSets = new HashMap<Integer, List<BGPPath>>();
		for (int srcASN : this.purgedMap.keySet()) {

			/* only select super ASes in the purged network */
			if (!this.purgedMap.get(srcASN).isSuperAS())
				continue;

			/* empty the temporary container */
			pathSets.clear();
			DecoyAS srcSuperAS = this.purgedMap.get(srcASN);
			/* get the providers of the AS in purgedMap */

			List<Integer> superASProviderList = getProvidersList(srcSuperAS.getProviders());
			/* get paths from the provider to the other ASes in purgedMap */
			for (int superASProviders : superASProviderList) {
				AS superASProviderAS = this.activeMap.get(superASProviders);

				for (int tdestASN : this.purgedMap.keySet()) {

					/* only stat traffic from super ASes to normal ASes */
					if (this.purgedMap.get(tdestASN).isSuperAS())
						continue;

					if (srcASN == tdestASN)
						continue;

					AS tdestAS = this.purgedMap.get(tdestASN);
					List<Integer> destProviderList = getProvidersList(tdestAS.getProviders());

					BGPPath tpath = superASProviderAS.getPathToPurged(destProviderList);
					if (tpath == null)
						continue;
					BGPPath cpath = tpath.deepCopy();
					cpath.prependASToPath(superASProviderAS.getASN());

					/* put the path into container. */
					ParallelTrafficStat.addToPathSets(pathSets, tdestASN, cpath);
				}
			}

			/* select the best path and do the traffic stat */
			for (int tdestASN : pathSets.keySet()) {
				BGPPath tpath = srcSuperAS.pathSelection(pathSets.get(tdestASN));
				if (tpath == null)
					continue;

				/* the traffic of the destiny AS from each super AS */
				this.addTrafficOnTheLinkBasisPathAndPurgedInSerial(tpath, srcSuperAS, this.fullTopology.get(tdestASN),
						ParallelTrafficStat.FROMSUPERAS);
			}
		}
	}

	/**
	 * very similar to activeToActive() function, but only start from super ASes
	 * in the active network, and add the traffic from each super AS of the
	 * destiny AS to all the normal ASes on the path
	 * 
	 * stat the link basis traffic at the same time
	 */
	private void statTrafficFromSuperASActiveToActive(DecoyAS srcSuperAS) {

		for (DecoyAS tDestActiveAS : this.activeMap.values()) {
			if (tDestActiveAS.isSuperAS())
				continue;

			BGPPath tpath = srcSuperAS.getPath(tDestActiveAS.getASN());
			if (tpath == null)
				continue;

			/* if the path exists, do the statistic */
			// this.addTrafficOnThePath(tpath, srcSuperAS, tDestActiveAS);
			this.addTrafficToLinks(tpath, srcSuperAS, tDestActiveAS, ParallelTrafficStat.FROMSUPERAS);
		}

	}

	/**
	 * very similar to activeToPurged() function, but only start from super ASes
	 * in the active network, and add the traffic from each super AS of the
	 * destiny AS to all the normal ASes on the path
	 * 
	 * stat the link basis traffic at the same time
	 */
	private void statTrafficFromSuperASActiveToPurged(DecoyAS srcSuperAS) {

		for (DecoyAS tDestPurgedAS : this.purgedMap.values()) {
			if (tDestPurgedAS.isSuperAS())
				continue;

			List<Integer> hookASNs = getProvidersList(tDestPurgedAS.getProviders());
			BGPPath tpath = srcSuperAS.getPathToPurged(hookASNs);
			if (tpath == null)
				continue;

			// deal with the case that super AS is adjacent to purged ASes
			if (tpath.getPathLength() == 0) {
				double amountOfTraffic = tDestPurgedAS.getTrafficFromEachSuperAS();
				srcSuperAS.updateTrafficOverOneNeighbor(tDestPurgedAS.getASN(), amountOfTraffic);
				continue;
			}
			this.addTrafficOnTheLinkBasisPathAndPurgedInSerial(tpath, srcSuperAS, tDestPurgedAS,
					ParallelTrafficStat.FROMSUPERAS);
		}

	}

	/**
	 * add the traffic on each normal AS and the normal ASes on the path which
	 * the super ASes send traffic to. four cases, since the super AS might be
	 * either on the active network or the purged network, and the strategy of
	 * doing the traffic flow is almost the same to the peer to peer traffic
	 * flow, the only difference is that it is the traffic from super ASes to
	 * normal ASes.
	 */
	private void trafficFromSuperASToNormalAS() {

		statTrafficFromSuperASPurgedToActive();
		statFromTrafficSuperASPurgedToPurged();

		for (DecoyAS tAS : this.activeMap.values()) {
			if (!tAS.isSuperAS())
				continue;

			/* traffic from tAS which is in the activeMap to activeMap */
			statTrafficFromSuperASActiveToActive(tAS);

			/* traffic from tAS which is in the activeMap to purgedMap */
			statTrafficFromSuperASActiveToPurged(tAS);
		}
	}

	/**
	 * print out the result
	 */
	private void testResults() {
		// test results
		System.out.println("\nShow Results: traffic flowing on the whole network");
		System.out.println("AS, total traffic, from warden traffic");

		System.out.println("\nShowResults: traffic flowing to neighbors");
		System.out.println("AS, neighbor AS, total traffic");
		for (DecoyAS tAS : this.fullTopology.values()) {
			for (int tASN : tAS.getNeighbors()) {
				System.out.println(tAS.getASN() + ", " + tASN + ", " + tAS.getTrafficOverLinkBetween(tASN));
			}
			System.out.println();

		}
	}
}
