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

public class TrafficStat1 {

	/**
	 * the total amount of traffic on the peer-to-peer network over the total
	 * amount of traffic from super ASes to peer-to-peer network
	 */
	private String trafficSplitFile;

	/** the total amount of traffic flowing on the peer to peer network */
	private double totalP2PTraffic;

	/** Stores the active (routing) portion of the topology */
	private HashMap<Integer, DecoyAS> activeMap;
	/** Stores the pruned portion of the topology */
	private HashMap<Integer, DecoyAS> purgedMap;

	private HashMap<Integer, DecoyAS> fullTopology;

	private static final boolean DEBUG = false;
	private static final boolean FROMSUPERAS = true;
	private static final boolean NOTFROMSUPERAS = false;

	/** stores normal ASes and super ASes */
	private List<DecoyAS> normalASList;
	private List<DecoyAS> superASList;

	public TrafficStat1(HashMap<Integer, DecoyAS> activeMap, HashMap<Integer, DecoyAS> purgedMap,
			String trafficSplitFile) {

		this.totalP2PTraffic = 0;
		this.activeMap = activeMap;
		this.purgedMap = purgedMap;
		this.fullTopology = new HashMap<Integer, DecoyAS>();
		for (int tASN : this.activeMap.keySet()) {
			this.fullTopology.put(tASN, this.activeMap.get(tASN));
		}
		for (int tASN : this.purgedMap.keySet()) {
			this.fullTopology.put(tASN, this.purgedMap.get(tASN));
		}
		this.trafficSplitFile = trafficSplitFile;
		normalASList = new ArrayList<DecoyAS>();
		superASList = new ArrayList<DecoyAS>();

		try {
			this.statTotalP2PTraffic();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void runStat() throws IOException {
		/*
		 * Clear out the values from last round
		 */
		for (DecoyAS tAS : this.fullTopology.values()) {
			tAS.resetTraffic();
		}
		
		long startTime, ptpNetwork, superAS;
		startTime = System.currentTimeMillis();
		
		this.statTrafficOnPToPNetwork();
		ptpNetwork = System.currentTimeMillis();
		System.out.println("\nTraffic flowing on peer to peer network done, this took: " + (ptpNetwork-startTime)/60000 + " minutes. ");
		
		this.statTrafficFromSuperAS();
		superAS = System.currentTimeMillis();
		System.out.println("Traffic flowing from super ASes done, this took: " + (superAS - ptpNetwork)/60000 + " minutes.");

		if (Constants.DEBUG) {
			testResults();
		}

		/*
		 * for (DecoyAS tAS : this.fullTopology.values()) {
		 * System.out.println(tAS.getASN()); for (DecoyAS destAS :
		 * this.fullTopology.values()) { System.out.println(" to " +
		 * tAS.getPath(destAS.getASN())); } }
		 */
	}

	/**
	 * calculate the traffic on peer-to-peer network, and draw the CDF graph for
	 * each AS
	 * 
	 * @throws IOException
	 */
	private void statTrafficOnPToPNetwork() throws IOException {

		this.statActiveMap();
		this.statPurgedMap();

		if (TrafficStat1.DEBUG) {
			System.out.println("\nShow Results: Traffic on peer to peer network");
			for (DecoyAS tAS : this.activeMap.values())
				System.out.println(tAS.getASN() + ", " + tAS.getTotalTraffic() + ", " + tAS.getTrafficFromWarden());
			for (DecoyAS tAS : this.purgedMap.values())
				System.out.println(tAS.getASN() + ", " + tAS.getTotalTraffic() + ", " + tAS.getTrafficFromWarden());
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
	private void statTrafficFromSuperAS() throws IOException {

		if (TrafficStat1.DEBUG) {
			System.out.println("The total traffic on peer-to-peer network: " + getTotalP2PTraffic());
		}

		/*
		 * read the file and calculate the traffic flowing to each normal AS
		 * from each super AS
		 */
		double totalTrafficFromSuperASes = readRatiosAndCalcSuperASesTraffic();
		groupASesAndCalcTrafficForNormalAS(totalTrafficFromSuperASes);

		trafficFromSuperASToNormalAS();
	}

	/**
	 * for each current AS on the path, add its next hop as its neighbor with
	 * the traffic amount equals to srcAS's ip count times destAS's ip count
	 * 
	 * @param tpath
	 * @param srcAS
	 * @param destAS
	 * @return
	 */
	private double addTrafficOnTheLinkBasisPath(BGPPath tpath, DecoyAS srcAS, DecoyAS destAS, boolean fromSuperAS) {

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

	/**
	 * does the same thing as addTrafficOnTheLinkBasisPath(BGPPath tpath,
	 * DecoyAS srcAS, DecoyAS destAS), only difference is that the path doesn't
	 * contain the AS on the purged map, so need to add them manually.
	 * 
	 * @param tpath
	 * @param srcAS
	 * @param destPurgedAS
	 */
	private void addTrafficOnTheLinkBasisPathAndPurged(BGPPath tpath, DecoyAS srcAS, DecoyAS destPurgedAS,
			boolean fromSuperAS) {

		double amountOfTraffic = this.addTrafficOnTheLinkBasisPath(tpath, srcAS, destPurgedAS, fromSuperAS);
		// tpath doesn't contain the destiny AS on the purged map, so add the traffic on it manually 
		List<Integer> pathList = tpath.getPath();
		DecoyAS secondLastAS = this.fullTopology.get(pathList.get(pathList.size() - 1));
		secondLastAS.updateTrafficOverOneNeighbor(destPurgedAS.getASN(), amountOfTraffic);
	}

	/**
	 * add traffic from the warden, a traffic locally stored version which means
	 * that the traffic is stored by the AS, rather than a global variable.
	 * 
	 * @param tHop
	 * @param amount
	 * @param src
	 */
	private void addTrafficFromWarden(int tHop, double amount, int src) {

		/* initialize the temporal src and dest ASes */
		DecoyAS srcAS = this.fullTopology.get(src);
		DecoyAS curAS = this.fullTopology.get(tHop);
		/* for each AS on the path, add its from-warden traffic if src is warden */
		if (srcAS.isWardenAS()) {
			curAS.addOnTrafficFromWarden(amount);
		}
	}

	/**
	 * add the weighted traffic to all ASes on the given path and stat the
	 * warden traffic at the same time
	 * 
	 * @param path
	 *            the path to add traffic unit
	 */
	private double addTrafficOnThePath(BGPPath path, DecoyAS srcAS, DecoyAS destAS) {

		double amountOfTraffic = srcAS.getIPCount() * destAS.getIPCount();
		for (int tHop : path.getPath()) {
			DecoyAS tAS = this.fullTopology.get(tHop);
			tAS.addOnTotalTraffic(amountOfTraffic);
			this.addTrafficFromWarden(tHop, amountOfTraffic, srcAS.getASN());
		}

		return amountOfTraffic;
	}

	/**
	 * add the weighted traffic to all ASes on the given path and stat the
	 * warden traffic at the same time. since the ASes on the purged map are not
	 * in BGPPath path, so need to be added manually.
	 * 
	 * @param path
	 * @param srcAS
	 * @param destAS
	 */
	private void addTrafficOnThePathAndPurged(BGPPath path, DecoyAS srcAS, DecoyAS destAS) {

		double amountOfTraffic = this.addTrafficOnThePath(path, srcAS, destAS);
		destAS.addOnTotalTraffic(amountOfTraffic);
		this.addTrafficFromWarden(destAS.getASN(), amountOfTraffic, srcAS.getASN());
	}

	private double addSuperASTrafficOnThePath(BGPPath path, DecoyAS superAS, DecoyAS destAS) {

		double amountOfTraffic = destAS.getTrafficFromEachSuperAS();
		for (int tHop : path.getPath()) {
			DecoyAS tAS = this.fullTopology.get(tHop);
			tAS.addOnTotalTraffic(amountOfTraffic);
			this.addTrafficFromWarden(tHop, amountOfTraffic, superAS.getASN());
		}

		return amountOfTraffic;
	}

	private void addSuperASTrafficOnThePathAndPurged(BGPPath path, DecoyAS superAS, DecoyAS destAS) {

		double amountOfTraffic = this.addSuperASTrafficOnThePath(path, superAS, destAS);
		destAS.addOnTotalTraffic(amountOfTraffic);
		this.addTrafficFromWarden(destAS.getASN(), amountOfTraffic, superAS.getASN());
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
	 * count the traffic of paths starting from activeMap ending at either
	 * activeMap or purgedMap
	 * 
	 * also stat the from/to-warden traffic at the same time
	 * 
	 * stat the link basis traffic flow at the same time
	 **/
	private void statActiveMap() {

		for (int tSrcAS : this.activeMap.keySet()) {
			DecoyAS tAS = this.activeMap.get(tSrcAS);
			/* traffic from tAS which is in the activeMap to activeMap */
			this.activeToActive(tAS);

			/* traffic from tAS which is in the activeMap to purgedMap */
			this.activeToPurged(tAS);
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
	 * @param srcActiveAS
	 *            the AS to start from
	 */
	private void activeToActive(DecoyAS srcActiveAS) {

		for (DecoyAS tdestActiveAS : this.activeMap.values()) {
			/*
			 * Don't send traffic to yourself
			 */
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
			this.addTrafficOnThePath(tpath, srcActiveAS, tdestActiveAS);
			this.addTrafficOnTheLinkBasisPath(tpath, srcActiveAS, tdestActiveAS, TrafficStat1.NOTFROMSUPERAS);
		}
	}

	/**
	 * count the traffic of all the paths starting from the given AS in
	 * activeMap to all ASes in the purgedMap To get to the purged ASes, get all
	 * the providers of that ASes, and getPathToPurged will pick a best path.
	 * 
	 * also stat the from/to-warden traffic at the same time
	 * 
	 * stat the link basis traffic flow at the same time
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

			this.addTrafficOnThePathAndPurged(tpath, srcActiveAS, tdestPurgedAS);

			if (tdestPurgedAS.getProviders().contains(srcActiveAS)) {
				double amountOfTraffic = srcActiveAS.getIPCount() * tdestPurgedAS.getIPCount();
				srcActiveAS.updateTrafficOverOneNeighbor(tdestPurgedAS.getASN(), amountOfTraffic);
				continue;
			}
			this.addTrafficOnTheLinkBasisPathAndPurged(tpath, srcActiveAS, tdestPurgedAS, TrafficStat1.NOTFROMSUPERAS);
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
	private void purgedToActive() {

		HashMap<Integer, List<BGPPath>> pathSets = new HashMap<Integer, List<BGPPath>>();
		for (DecoyAS tsrcPurgedAS : this.purgedMap.values()) {
			/* empty the temporary container */
			pathSets.clear();

			/* get the path through the providers of nodes in purgedMap */
			Set<AS> providers = tsrcPurgedAS.getProviders();
			for (AS pAS : providers) {
				for (int tdestActiveASN : this.activeMap.keySet()) {
					/* get path from the provider to the activeMap */
					BGPPath tpath = pAS.getPath(tdestActiveASN);
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
					TrafficStat1.addToPathSets(pathSets, tdestActiveASN, cpath);
				}
			}

			/* get best path from the pathSets and do the traffic stat */
			for (int tdestActiveASN : pathSets.keySet()) {
				/* get the best path from the pathSets by calling pathSelection */
				BGPPath tpath = tsrcPurgedAS.pathSelection(pathSets.get(tdestActiveASN));
				/* stat the traffic on the path */
				this.addTrafficOnThePath(tpath, tsrcPurgedAS, this.fullTopology.get(tdestActiveASN));
				this.addTrafficOnTheLinkBasisPath(tpath, tsrcPurgedAS, this.fullTopology.get(tdestActiveASN),
						TrafficStat1.NOTFROMSUPERAS);
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
	 * 
	 * stat the link basis traffic flow at the same time
	 */
	private void purgedToPurged() {
		/* path from purged AS t1ASN to purged AS t2ASN */
		HashMap<Integer, List<BGPPath>> pathSets = new HashMap<Integer, List<BGPPath>>();
		//for (int t1ASN : this.purgedMap.keySet()) {
		for (DecoyAS tsrcPurgedAS : this.purgedMap.values()) {
			/* empty the temporary container */
			pathSets.clear();

			/* get the providers of the AS in purgedMap */
			List<Integer> p1List = getProvidersList(tsrcPurgedAS.getProviders());
			/* get paths from the provider to the other ASes in purgedMap */
			for (int providerASN : p1List) {
				AS providerAS = this.activeMap.get(providerASN);

				for (int tdestPurgedASN : this.purgedMap.keySet()) {
					/*
					 * Don't send traffic to ourself
					 */
					if (tsrcPurgedAS.getASN() == tdestPurgedASN)
						continue;

					AS destAS = this.purgedMap.get(tdestPurgedASN);
					List<Integer> destinationProividerList = getProvidersList(destAS.getProviders());

					BGPPath tpath = providerAS.getPathToPurged(destinationProividerList);
					if (tpath == null)
						continue;
					BGPPath cpath = tpath.deepCopy();
					cpath.prependASToPath(providerAS.getASN());

					/* put the path into container. */
					TrafficStat1.addToPathSets(pathSets, tdestPurgedASN, cpath);
				}
			}

			/* select the best path and do the traffic stat */
			for (int tdestPurgedASN : pathSets.keySet()) {
				BGPPath tpath = tsrcPurgedAS.pathSelection(pathSets.get(tdestPurgedASN));
				if (tpath == null)
					continue;

				this.addTrafficOnThePathAndPurged(tpath, tsrcPurgedAS, this.fullTopology.get(tdestPurgedASN));
				this.addTrafficOnTheLinkBasisPathAndPurged(tpath, tsrcPurgedAS, this.fullTopology.get(tdestPurgedASN),
						TrafficStat1.NOTFROMSUPERAS);
			}
		}
	}

	/**
	 * count the traffic of paths starting from purgedMap ending at either
	 * activeMap or purgedMap
	 * 
	 * stat the link basis traffic flow at the same time
	 **/
	private void statPurgedMap() {
		this.purgedToActive();
		this.purgedToPurged();
	}

	/**
	 * calculate the total amount of traffic flowing on the peer to peer
	 * network.
	 * 
	 * @throws IOException
	 */
	private void statTotalP2PTraffic() throws IOException {
		/*
		 * for (double amount : countTraffic.values()) { this.totalTraffic +=
		 * amount; }
		 */
		int ASN, ipNum, cnt = 0;
		int ips[] = new int[this.activeMap.size() + this.purgedMap.size()];
		String pollString;

		BufferedReader fBuff = new BufferedReader(new FileReader(Constants.IP_COUNT_FILE));
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
			StringTokenizer pollToks = new StringTokenizer(pollString, ",");
			ASN = Integer.parseInt(pollToks.nextToken());
			ipNum = Integer.parseInt(pollToks.nextToken());
			ips[cnt++] = ipNum;
		}

		for (int i = 0; i < cnt; ++i)
			for (int j = i + 1; j < cnt; ++j)
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
	private double readRatiosAndCalcSuperASesTraffic() throws IOException {

		String pollString;
		int ASN, cnt = 0;
		double tRatio;
		double ratio[] = new double[2];

		/*
		 * read the traffic split file, and calculate the traffic flowing from
		 * the superAS to normal ASes
		 */
		BufferedReader fBuff = new BufferedReader(new FileReader(this.trafficSplitFile));
		while (fBuff.ready()) {
			if (cnt == 2) {
				System.out.println("WRONG TRAFFIC SPLIT FILE!!");
				return -1;
			}

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
			StringTokenizer pollToks = new StringTokenizer(pollString, ",");
			ASN = Integer.parseInt(pollToks.nextToken());
			tRatio = Double.parseDouble(pollToks.nextToken());
			ratio[ASN - 1] = tRatio;
			++cnt;
		}
		fBuff.close();

		if (ratio[0] + ratio[1] != 1.0) {
			System.out.println("WRONG TRAFFIC SPLIT FILE!!");
			return -1;
		}

		/* calculate based on the ratio, and then split equally */
		double totalTrafficFromSuperASes = getTotalP2PTraffic() * ratio[1] / ratio[0];

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
					TrafficStat1.addToPathSets(pathSets, tdestASN, cpath);
				}
			}

			/* get best path from the pathSets and do the traffic stat */
			for (int tdestASN : pathSets.keySet()) {
				/* get the best path from the pathSets by calling pathSelection */
				BGPPath tpath = this.purgedMap.get(tsrcASN).pathSelection(pathSets.get(tdestASN));
				/* stat the traffic on the path */
				this.addSuperASTrafficOnThePath(tpath, srcSuperAS, this.fullTopology.get(tdestASN));
				this.addTrafficOnTheLinkBasisPath(tpath, srcSuperAS, this.fullTopology.get(tdestASN),
						TrafficStat1.FROMSUPERAS);
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
					TrafficStat1.addToPathSets(pathSets, tdestASN, cpath);
				}
			}

			/* select the best path and do the traffic stat */
			for (int tdestASN : pathSets.keySet()) {
				BGPPath tpath = srcSuperAS.pathSelection(pathSets.get(tdestASN));
				if (tpath == null)
					continue;

				/* the traffic of the destiny AS from each super AS */
				this.addSuperASTrafficOnThePathAndPurged(tpath, srcSuperAS, this.fullTopology.get(tdestASN));
				this.addTrafficOnTheLinkBasisPathAndPurged(tpath, srcSuperAS, this.fullTopology.get(tdestASN),
						TrafficStat1.FROMSUPERAS);
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
			//this.addTrafficOnThePath(tpath, srcSuperAS, tDestActiveAS);
			this.addSuperASTrafficOnThePath(tpath, srcSuperAS, tDestActiveAS);
			this.addTrafficOnTheLinkBasisPath(tpath, srcSuperAS, tDestActiveAS, TrafficStat1.FROMSUPERAS);
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
			
			this.addSuperASTrafficOnThePathAndPurged(tpath, srcSuperAS, tDestPurgedAS);
			
			// deal with the case that super AS is adjacent to purged ASes 
			if (tpath.getPathLength() == 0) {
				double amountOfTraffic = tDestPurgedAS.getTrafficFromEachSuperAS();
				srcSuperAS.updateTrafficOverOneNeighbor(tDestPurgedAS.getASN(), amountOfTraffic);
				continue;
			}
			this.addTrafficOnTheLinkBasisPathAndPurged(tpath, srcSuperAS, tDestPurgedAS, TrafficStat1.FROMSUPERAS);
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
		for (DecoyAS tAS : this.activeMap.values()) {
			System.out.println(tAS.getASN() + ", " + tAS.getTotalTraffic() + ", " + tAS.getTrafficFromWarden());
		}
		for (DecoyAS tAS : this.purgedMap.values()) {
			System.out.println(tAS.getASN() + ", " + tAS.getTotalTraffic() + ", " + tAS.getTrafficFromWarden());
		}

		System.out.println("\nShowResults: traffic flowing to neighbors");
		System.out.println("AS, neighbor AS, total traffic");
		for (DecoyAS tAS : this.fullTopology.values()) {
			for (int tASN : this.fullTopology.keySet()) {
				System.out.println(tAS.getASN() + ", " + tASN + ", " + tAS.getTrafficOverLinkBetween(tASN));
			}
			System.out.println();

		}
	}
}

