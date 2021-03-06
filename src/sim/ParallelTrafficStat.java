package sim;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;

import java.util.*;
import java.io.*;

import topo.AS;
import topo.BGPPath;
import topo.SerializationMaster;
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
	private TIntObjectMap<DecoyAS> activeMap;
	/** Stores the pruned portion of the topology */
	private TIntObjectMap<DecoyAS> purgedMap;
	private TIntObjectMap<DecoyAS> fullTopology;
	/**
	 * store the ASN whose ip count is not zero, used to split ASes among the
	 * threads
	 */
	private List<Integer> validASNList;

	private List<List<Integer>> activeASWorkgroups;
	private List<List<Integer>> prunedASWorkgroups;

	private boolean firstRound;
	private SerializationMaster serialControl;

	private HashMap<Integer, String> ccMap = null;

	private static final boolean DEBUG = false;
	private static final boolean REPORT_TIMING = false;

	/** stores normal ASes and super ASes */
	private List<DecoyAS> normalASList;
	private List<DecoyAS> superASList;

	private PerformanceLogger perfLog = null;

	/**
	 * constructor function
	 * 
	 * @param activeMap
	 * @param purgedMap
	 * @param trafficSplitFile
	 */
	public ParallelTrafficStat(TIntObjectMap<DecoyAS> activeMap, TIntObjectMap<DecoyAS> purgedMap,
			SerializationMaster serialControl, PerformanceLogger perfLogger) {

		this.totalP2PTraffic = 0;
		this.activeMap = activeMap;
		this.purgedMap = purgedMap;
		this.fullTopology = new TIntObjectHashMap<DecoyAS>();
		this.validASNList = new ArrayList<Integer>();
		this.firstRound = true;
		this.serialControl = serialControl;
		this.perfLog = perfLogger;

		for (int tASN : this.activeMap.keys()) {
			this.fullTopology.put(tASN, this.activeMap.get(tASN));
			/* only need AS which has IP count */
			if (this.activeMap.get(tASN).getBaseTrafficFactor() != 0) {
				this.validASNList.add(tASN);
			}
		}
		for (int tASN : this.purgedMap.keys()) {
			this.fullTopology.put(tASN, this.purgedMap.get(tASN));
			if (this.purgedMap.get(tASN).getBaseTrafficFactor() != 0) {
				this.validASNList.add(tASN);
			}
		}
		this.normalASList = new ArrayList<DecoyAS>();
		this.superASList = new ArrayList<DecoyAS>();
		this.statTotalP2PTraffic();

		try {
			this.loadTrafficRatios();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		/*
		 * Divide up the ASes into working groups for threads
		 */
		this.splitASWorkgroups();
	}

	/**
	 * the class used to run to statistic the traffic only on peer to peer
	 * network in parallel with atomic operation by semaphore of each AS
	 * 
	 * @author bobo
	 */
	private class ParallelRunningThread implements Runnable {

		private List<Integer> localASList;
		private boolean firstRun;

		ParallelRunningThread(List<Integer> ASes, boolean firstRun) {
			this.localASList = ASes;
			this.firstRun = firstRun;
		}

		@Override
		public void run() {

			for (int tASN : this.localASList) {
				if (activeMap.containsKey(tASN)) {
					activeToActive(fullTopology.get(tASN), this.firstRun);
					activeToPurged(fullTopology.get(tASN), this.firstRun);
				} else if (purgedMap.containsKey(tASN)) {
					HashMap<Integer, BGPPath> toActiveMap = purgedToActive(fullTopology.get(tASN), this.firstRun);
					purgedToPurged(fullTopology.get(tASN), toActiveMap, this.firstRun);
				} else {
					System.out.println("Inlegel Decoy AS!!");
					System.exit(-1);
				}
			}
		}
	}

	private void loadTrafficRatios() throws IOException {
		BufferedReader fBuff = new BufferedReader(new FileReader(Constants.TRAFFIC_SPLIT_FILE));
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
		this.perfLog.resetTimer();

		/*
		 * Clear out the values from last round
		 */
		for (DecoyAS tAS : this.fullTopology.valueCollection()) {
			tAS.resetTraffic();
		}

		long startTime, ptpNetwork, superAS;
		startTime = System.currentTimeMillis();

		/*
		 * If it is the first round and we already have a serial file please
		 * simply load that state, otherwise actually do the traffic flow
		 */
		if (this.firstRound && this.serialControl.hasValidTrafficSerialFile() && this.ccMap == null) {
			System.out.println("Valid serial file exists for traffic flows, skipping first round of traffic flow.");
			this.serialControl.loadTrafficSerialFile(this.fullTopology);
			System.out.println("Load of serial file complete.");
		} else {
			/*
			 * Run the p2p traffic flow
			 */
			this.statTrafficOnPToPNetworkInParallel(this.firstRound);
			ptpNetwork = System.currentTimeMillis();
			if (ParallelTrafficStat.REPORT_TIMING) {
				System.out.println("\nTraffic flowing on peer to peer network done, this took: "
						+ (ptpNetwork - startTime) / 60000 + " minutes. ");
			}

			/*
			 * Run the super AS traffic flow
			 */
			if (this.superASRatio != 0.0) {
				this.statTrafficFromSuperAS();
				superAS = System.currentTimeMillis();
				if (ParallelTrafficStat.REPORT_TIMING) {
					System.out.println("Traffic flowing from super ASes done, this took: "
							+ (superAS - ptpNetwork) / 60000 + " minutes.");
				}
			}
		}

		this.perfLog.logTime("traffic flow");

		if (this.firstRound) {
			this.firstRound = false;
			/*
			 * Build the serial file if we need to, as doing the same work over
			 * and over gets old...
			 */
			if (!this.serialControl.hasValidTrafficSerialFile()) {
				System.out.println("Saving opening traffic flow to serial file.");
				this.serialControl.buildTrafficSerialFile(this.fullTopology);
				System.out.println("Saving of serial file complete.");
			}
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
	private void splitASWorkgroups() {

		this.activeASWorkgroups = new ArrayList<List<Integer>>(Constants.NTHREADS);
		this.prunedASWorkgroups = new ArrayList<List<Integer>>(Constants.NTHREADS);

		for (int counter = 0; counter < Constants.NTHREADS; counter++) {
			this.activeASWorkgroups.add(new LinkedList<Integer>());
			this.prunedASWorkgroups.add(new LinkedList<Integer>());
		}

		/* distribute the ASes */
		int currentPos = 0;
		for (int tAS : this.activeMap.keys()) {
			this.activeASWorkgroups.get(currentPos).add(tAS);
			currentPos = (currentPos + 1) % Constants.NTHREADS;
		}

		currentPos = 0;
		for (int tAS : this.purgedMap.keys()) {
			this.prunedASWorkgroups.get(currentPos).add(tAS);
			currentPos = (currentPos + 1) % Constants.NTHREADS;
		}
	}

	/**
	 * calculate the traffic on peer-to-peer network create two kinds of thread
	 * objects
	 * 
	 */
	private void statTrafficOnPToPNetworkInParallel(boolean firstRun) {
		this.statTrafficOnPToPNetworkInParallel(firstRun, true);
		this.statTrafficOnPToPNetworkInParallel(firstRun, false);
	}

	private void statTrafficOnPToPNetworkInParallel(boolean firstRun, boolean activeMap) {

		List<List<Integer>> currentWorkSplit = null;
		if (activeMap) {
			currentWorkSplit = this.activeASWorkgroups;
		} else {
			currentWorkSplit = this.prunedASWorkgroups;
		}

		/* make the threads run to calculate its own list of ASes */
		Thread[] workers = new Thread[Constants.NTHREADS];
		for (int i = 0; i < Constants.NTHREADS; ++i) {
			workers[i] = new Thread(new ParallelRunningThread(currentWorkSplit.get(i), firstRun));
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
	 * @param thePath
	 * @param srcAS
	 * @param destAS
	 * @return
	 */
	private double addTrafficToPath(BGPPath thePath, DecoyAS srcAS, DecoyAS destAS, boolean fromSuperAS,
			boolean isVolatile, boolean srcIsClient) {

		double amountOfTraffic = 0;
		if (fromSuperAS) {
			amountOfTraffic = destAS.getTrafficFromEachSuperAS();
		} else {
			if (srcIsClient) {
				amountOfTraffic = srcAS.getUpTrafficFactor() * destAS.getBaseTrafficFactor();
			} else {
				amountOfTraffic = srcAS.getBaseTrafficFactor() * destAS.getDownTrafficFactor();
			}
		}

		/*
		 * Manually add traffic the source and the next hop on the path, as the
		 * source will not appear on the path
		 */
		srcAS.updateTrafficOverOneNeighbor(thePath.getNextHop(), amountOfTraffic, isVolatile);

		TIntList pathList = thePath.getPath();
		/*
		 * Add traffic for each of the remaining hops in the path
		 */
		// FIXME this is bad with linked lists..
		for (int tASN = 0; tASN < pathList.size() - 1; ++tASN) {
			DecoyAS currentAS = this.fullTopology.get(pathList.get(tASN));
			DecoyAS nextAS = this.fullTopology.get(pathList.get(tASN + 1));
			/*
			 * Placed here because we can have lying hole punched routes
			 */
			if (currentAS.getASN() == thePath.getDestinationASN()) {
				break;
			}
			currentAS.updateTrafficOverOneNeighbor(nextAS.getASN(), amountOfTraffic, isVolatile);
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
	 * @param thePath
	 * @param srcAS
	 * @param destAS
	 */
	private void addTrafficToPathAndLastHop(BGPPath thePath, DecoyAS srcAS, DecoyAS destAS, boolean fromSuperAS,
			boolean isVolatile, boolean srcIsClient) {

		double amountOfTraffic = this.addTrafficToPath(thePath, srcAS, destAS, fromSuperAS, isVolatile, srcIsClient);

		TIntList pathList = thePath.getPath();
		DecoyAS lastASInRealPath = this.fullTopology.get(pathList.get(pathList.size() - 1));
		lastASInRealPath.updateTrafficOverOneNeighbor(destAS.getASN(), amountOfTraffic, isVolatile);
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
	 * convert a set of integer into a set of corresponding DecoyAS, and return
	 * the set
	 * 
	 * @param tAS
	 * @return
	 */
	private Set<DecoyAS> fetchVolatileDestObjects(DecoyAS tAS, boolean activeMap) {
		Set<DecoyAS> decoyASList = new HashSet<DecoyAS>();
		TIntHashSet volDestASN = tAS.getVolatileDestinations();
		TIntIterator volDestASNIter = volDestASN.iterator();
		while (volDestASNIter.hasNext()) {
			int tASN = volDestASNIter.next();
			if ((activeMap && this.activeMap.containsKey(tASN)) || (!activeMap && this.purgedMap.containsKey(tASN))) {
				decoyASList.add(this.fullTopology.get(tASN));
			}
		}
		return decoyASList;
	}

	/**
	 * return the proper set of AS which depends on the number of the run, and
	 * the type of the map
	 * 
	 * @param tAS
	 * @param firstRun
	 * @param isActiveMap
	 * @return
	 */
	private Collection<DecoyAS> fetchASSetToDoTrafficFlowOn(DecoyAS tAS, boolean firstRun, boolean toActiveMap) {
		if (firstRun) {
			if (toActiveMap)
				return this.activeMap.valueCollection();
			else
				return this.purgedMap.valueCollection();
		} else {
			return fetchVolatileDestObjects(tAS, toActiveMap);
		}
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
	private void activeToActive(DecoyAS srcActiveAS, boolean firstRun) {

		Collection<DecoyAS> decoyASList = this.fetchASSetToDoTrafficFlowOn(srcActiveAS, firstRun, true);
		for (DecoyAS destAS : decoyASList) {
			if (srcActiveAS.getASN() == destAS.getASN()) {
				continue;
			}

			/*
			 * Fetch the actual path object, deal with the odd case in which it
			 * does not exist
			 */
			BGPPath usedPath = srcActiveAS.getPath(destAS.getASN());
			if (usedPath == null) {
				continue;
			}

			/*
			 * Figure out if the path is volatile or not
			 */
			boolean isVolatile;
			if (firstRun) {
				isVolatile = this.pathIsVolatile(usedPath, srcActiveAS.getASN(), destAS.getASN());
				if (isVolatile) {
					srcActiveAS.addVolatileDestionation(destAS.getASN());
				}
			} else {
				isVolatile = true;
			}

			/*
			 * Actually add the traffic to the AS objects
			 */
			this.addTrafficToPath(usedPath, srcActiveAS, destAS, false, isVolatile, true);
			this.addTrafficToPath(usedPath, srcActiveAS, destAS, false, isVolatile, false);
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
	private void activeToPurged(DecoyAS srcActiveAS, boolean firstRun) {

		Collection<DecoyAS> decoyASList = this.fetchASSetToDoTrafficFlowOn(srcActiveAS, firstRun, false);
		for (DecoyAS tdestPurgedAS : decoyASList) {
			List<Integer> hookASNs = getProvidersList(tdestPurgedAS.getProviders());
			BGPPath tpath = srcActiveAS.getPathToPurged(hookASNs);
			if (tpath == null) {
				continue;
			}

			boolean isVolatile;
			if (firstRun) {
				isVolatile = this.pathIsVolatile(tpath, srcActiveAS.getASN(), tdestPurgedAS.getASN());
				if (isVolatile) {
					srcActiveAS.addVolatileDestionation(tdestPurgedAS.getASN());
				}
			} else {
				isVolatile = true;
			}

			if (tdestPurgedAS.getProviders().contains(srcActiveAS)) {
				double a1 = srcActiveAS.getUpTrafficFactor() * tdestPurgedAS.getBaseTrafficFactor();
				double a2 = srcActiveAS.getBaseTrafficFactor() * tdestPurgedAS.getDownTrafficFactor();
				srcActiveAS.updateTrafficOverOneNeighbor(tdestPurgedAS.getASN(), a1 + a2, isVolatile);
				continue;
			}
			this.addTrafficToPathAndLastHop(tpath, srcActiveAS, tdestPurgedAS, false, isVolatile, true);
			this.addTrafficToPathAndLastHop(tpath, srcActiveAS, tdestPurgedAS, false, isVolatile, false);
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
	 */
	private HashMap<Integer, BGPPath> purgedToActive(DecoyAS srcPurgedAS, boolean firstRun) {
		HashMap<Integer, BGPPath> bestPathMapping = new HashMap<Integer, BGPPath>();
		List<BGPPath> pathList = new ArrayList<BGPPath>();

		/* get the path through the providers of nodes in purgedMap */
		Set<AS> providers = srcPurgedAS.getProviders();
		Collection<DecoyAS> decoyASList = this.fetchASSetToDoTrafficFlowOn(srcPurgedAS, firstRun, true);
		for (DecoyAS tdestActiveAS : decoyASList) {
			int tdestActiveASN = tdestActiveAS.getASN();
			pathList.clear();
			for (AS tProviderAS : providers) {
				BGPPath tpath = tProviderAS.getPath(tdestActiveASN);
				if (tpath == null) {
					continue;
				}
				BGPPath cpath = tpath.deepCopy();
				cpath.prependASToPath(tProviderAS.getASN());
				pathList.add(cpath);
			}

			BGPPath tpath = srcPurgedAS.pathSelection(pathList);
			if (tpath == null) {
				continue;
			}

			/*
			 * figure out if the path is volatile
			 */
			boolean isVolatile;
			if (firstRun) {
				isVolatile = this.pathIsVolatile(tpath, srcPurgedAS.getASN(), tdestActiveASN);
				if (isVolatile) {
					srcPurgedAS.addVolatileDestionation(tdestActiveASN);
				}
			} else {
				isVolatile = true;
			}

			this.addTrafficToPath(tpath, srcPurgedAS, this.fullTopology.get(tdestActiveASN), false, isVolatile, true);
			this.addTrafficToPath(tpath, srcPurgedAS, this.fullTopology.get(tdestActiveASN), false, isVolatile, false);
			bestPathMapping.put(tdestActiveASN, tpath);
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
	private void purgedToPurged(DecoyAS srcPurgedAS, HashMap<Integer, BGPPath> toActiveMap, boolean firstRun) {

		List<BGPPath> pathList = new ArrayList<BGPPath>();
		Collection<DecoyAS> decoyASList = this.fetchASSetToDoTrafficFlowOn(srcPurgedAS, firstRun, false);
		for (DecoyAS tdestPurgedAS : decoyASList) {
			int tdestPurgedASN = tdestPurgedAS.getASN();
			if (srcPurgedAS.getASN() == tdestPurgedASN) {
				continue;
			}

			pathList.clear();
			List<Integer> destProviderList = this
					.getProvidersList(this.fullTopology.get(tdestPurgedASN).getProviders());
			for (int tDestHook : destProviderList) {
				if (toActiveMap.containsKey(tDestHook)) {
					pathList.add(toActiveMap.get(tDestHook));
				}
			}

			BGPPath tpath = srcPurgedAS.pathSelection(pathList);
			if (tpath == null) {
				continue;
			}

			/*
			 * figure out if the path is volatile
			 */
			boolean isVolatile;
			if (firstRun) {
				isVolatile = this.pathIsVolatile(tpath, srcPurgedAS.getASN(), tdestPurgedASN);
				if (isVolatile) {
					srcPurgedAS.addVolatileDestionation(tdestPurgedASN);
				}
			} else {
				isVolatile = true;
			}

			this.addTrafficToPathAndLastHop(tpath, srcPurgedAS, this.fullTopology.get(tdestPurgedASN), false,
					isVolatile, true);
			this.addTrafficToPathAndLastHop(tpath, srcPurgedAS, this.fullTopology.get(tdestPurgedASN), false,
					isVolatile, false);
		}
	}

	/**
	 * calculate the total amount of traffic flowing on the peer to peer
	 * network.
	 * 
	 * @throws IOException
	 */
	//FIXME super incorrect right now, but not used
	private void statTotalP2PTraffic() {

		int ips[] = new int[this.activeMap.size() + this.purgedMap.size()];

		int counter = 0;
		for (DecoyAS tAS : this.fullTopology.valueCollection()) {
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
	/*
	 * TODO this is a hazard since super ASes are not used anymore, so dead
	 * code lying around, stop and think about what you're doing before you
	 * edit/use this!
	 */
	private void groupASesAndCalcTrafficForNormalAS(double totalTrafficFromSuperASes) {
		int superASNum = 0;
		double totalIPCounts = 0;
		for (DecoyAS tAS : this.activeMap.valueCollection()) {
			if (!tAS.isSuperAS()) {
				this.normalASList.add(tAS);
				totalIPCounts += tAS.getIPCount();
			} else {
				this.superASList.add(tAS);
				superASNum++;
			}
		}
		for (DecoyAS tAS : this.purgedMap.valueCollection()) {
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
		for (DecoyAS tAS : this.normalASList)

		{
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
	 * very similar to activeToActive() function, but only start from super ASes
	 * in the active network, and add the traffic from each super AS of the
	 * destiny AS to all the normal ASes on the path
	 * 
	 * stat the link basis traffic at the same time
	 */
	private void statTrafficFromSuperASActiveToActive(DecoyAS srcSuperAS, boolean firstRun) {

		Collection<DecoyAS> decoyASList = this.fetchASSetToDoTrafficFlowOn(srcSuperAS, firstRun, true);
		for (DecoyAS tDestActiveAS : decoyASList) {
			if (tDestActiveAS.isSuperAS())
				continue;

			BGPPath tpath = srcSuperAS.getPath(tDestActiveAS.getASN());
			if (tpath == null)
				continue;

			/*
			 * figure out if the path is volatile
			 */
			boolean isVolatile;
			if (firstRun) {
				isVolatile = this.pathIsVolatile(tpath, srcSuperAS.getASN(), tDestActiveAS.getASN());
				if (isVolatile) {
					srcSuperAS.addVolatileDestionation(tDestActiveAS.getASN());
				}
			} else {
				isVolatile = true;
			}

			/* if the path exists, do the statistic */
			this.addTrafficToPath(tpath, srcSuperAS, tDestActiveAS, true, isVolatile, true);
		}

	}

	/**
	 * very similar to activeToPurged() function, but only start from super ASes
	 * in the active network, and add the traffic from each super AS of the
	 * destiny AS to all the normal ASes on the path
	 * 
	 * stat the link basis traffic at the same time
	 */
	private void statTrafficFromSuperASActiveToPurged(DecoyAS srcSuperAS, boolean firstRun) {

		Collection<DecoyAS> decoyASList = this.fetchASSetToDoTrafficFlowOn(srcSuperAS, firstRun, false);
		for (DecoyAS tDestPurgedAS : decoyASList) {
			if (tDestPurgedAS.isSuperAS())
				continue;

			List<Integer> hookASNs = getProvidersList(tDestPurgedAS.getProviders());
			BGPPath tpath = srcSuperAS.getPathToPurged(hookASNs);
			if (tpath == null)
				continue;

			/*
			 * figure out if the path is volatile
			 */

			boolean isVolatile;
			if (firstRun) {
				isVolatile = this.pathIsVolatile(tpath, srcSuperAS.getASN(), tDestPurgedAS.getASN());
				if (isVolatile) {
					srcSuperAS.addVolatileDestionation(tDestPurgedAS.getASN());
				}
			} else {
				isVolatile = true;
			}

			// XXX does this handle this edge condition correctly?
			// deal with the case that super AS is adjacent to purged ASes
			if (tpath.getPathLength() == 0) {
				double amountOfTraffic = tDestPurgedAS.getTrafficFromEachSuperAS();
				boolean volatilePath = tDestPurgedAS.isWardenAS() || srcSuperAS.isWardenAS();
				srcSuperAS.updateTrafficOverOneNeighbor(tDestPurgedAS.getASN(), amountOfTraffic, volatilePath);
				continue;
			}
			this.addTrafficToPathAndLastHop(tpath, srcSuperAS, tDestPurgedAS, true, isVolatile, true);
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

		for (DecoyAS tAS : this.activeMap.valueCollection()) {
			if (!tAS.isSuperAS())
				continue;

			/* traffic from tAS which is in the activeMap to activeMap */
			statTrafficFromSuperASActiveToActive(tAS, this.firstRound);

			/* traffic from tAS which is in the activeMap to purgedMap */
			statTrafficFromSuperASActiveToPurged(tAS, this.firstRound);
		}
	}

	private boolean pathIsVolatile(BGPPath path, int src, int dest) {

		TIntIterator tIter = path.getPath().iterator();
		while (tIter.hasNext()) {
			int tAS = tIter.next();
			if (this.fullTopology.get(tAS).isWardenAS()) {
				return true;
			}
		}

		return this.fullTopology.get(src).isWardenAS() || this.fullTopology.get(dest).isWardenAS();
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
		for (DecoyAS tAS : this.fullTopology.valueCollection()) {
			for (int tASN : tAS.getNeighbors()) {
				System.out.println(tAS.getASN() + ", " + tASN + ", " + tAS.getTrafficOverLinkBetween(tASN) + ","
						+ tAS.getVolTraffic(tASN));
			}
			System.out.println();

		}
	}
}
