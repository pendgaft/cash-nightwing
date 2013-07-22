package sim;

import java.util.*;
import java.io.*;

import topo.AS;
import topo.BGPPath;
import util.Stats;
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
	/** the number of super ASes */
	private int superASNum;

	/** Stores the active (routing) portion of the topology */
	private HashMap<Integer, DecoyAS> activeMap;
	/** Stores the pruned portion of the topology */
	private HashMap<Integer, DecoyAS> purgedMap;

	/** stores the traffic on each AS in the map */
	private HashMap<Integer, Double> countTraffic;

	/** stores the traffic of each AS sent from/to the warden ASes */
	private HashMap<Integer, Double> trafficFromWarden;
	private HashMap<Integer, Double> trafficToWarden;

	/**
	 * store the paths of one purged node from different providers to the same
	 * destiny in a list
	 */
	private HashMap<Integer, List<BGPPath>> pathSets;

	/** stores the CDF statistic result of peer-to-peer traffic network */
	private String ptpNetworkFile = "ptpNetwork";
	/**
	 * stores the CDF statistic result of the traffic from super AS to regular
	 * ASes
	 */
	private String stpNetworkFile = "stpNetwork";
	/** stores the CDF statistic result of total traffic flowing through each AS */
	private String wholeNetworkFile = "wholeNetwork";
	/** stores the CDF statistic result of the traffic sent from warden */
	private String fromWardenFile = "fromWarden";
	/** stores the CDF statistic result of the traffic sent to warden */
	private String toWardenFile = "toWarden";

	/** stores normal ASes and super ASes */
	// ?? deep copy???
	private List<DecoyAS> normalASList;
	private List<DecoyAS> superASList;
	
	public TrafficStat1(HashMap<Integer, DecoyAS> activeMap,
			HashMap<Integer, DecoyAS> purgedMap, String trafficSplitFile) {

		this.totalP2PTraffic = 0;
		this.superASNum = 0;
		this.activeMap = activeMap;
		this.purgedMap = purgedMap;
		this.trafficSplitFile = trafficSplitFile;
		countTraffic = new HashMap<Integer, Double>();
		trafficFromWarden = new HashMap<Integer, Double>();
		trafficToWarden = new HashMap<Integer, Double>();
		pathSets = new HashMap<Integer, List<BGPPath>>();
		normalASList = new ArrayList<DecoyAS>();
		superASList = new ArrayList<DecoyAS>();
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
	 * the lowest-level operation to stat the traffic sent from/to a warden AS.
	 * If src is a warden, add from-wardon traffic of tHop, if tHop is a wardon,
	 * add to-wardon traffic of src.
	 * 
	 * @param tHop
	 * @param amount
	 * @param src
	 */
	public void addWardenTraffic(int tHop, double amount, int src) {

		/* initialize the temporal src and dest ASes */
		DecoyAS srcAS = null, destAS = null;
		if (this.activeMap.containsKey(src)) {
			srcAS = this.activeMap.get(src);
		} else if (this.purgedMap.containsKey(src)) {
			srcAS = this.purgedMap.get(src);
		} else {
			// not possible actually..
		}

		if (this.activeMap.containsKey(tHop)) {
			destAS = this.activeMap.get(tHop);
		} else if (this.purgedMap.containsKey(tHop)) {
			destAS = this.purgedMap.get(tHop);
		} else {
			// not possible actually..
		}

		/* for each AS on the path, add its from-warden traffic if src is warden */
		if (srcAS.isWardenAS()) {
			if (trafficFromWarden.containsKey(tHop))
				trafficFromWarden.put(tHop, trafficFromWarden.get(tHop)
						+ amount);
			else
				trafficFromWarden.put(tHop, amount);
		}
		/* for the src AS, add its to-warden traffic if dest is warden */
		/*if (destAS.isWardenAS()) {
			if (trafficToWarden.containsKey(src))
				trafficToWarden.put(src, trafficToWarden.get(srcAS) + amount);
			else
				trafficToWarden.put(src, amount);
		}??*/
		if (destAS.isWardenAS()) {
			if (trafficToWarden.containsKey(src))
				trafficToWarden.put(src, trafficToWarden.get(src) + amount);
			else
				trafficToWarden.put(src, amount);
		}
	}

	/**
	 * add traffic from the warden, a traffic locally stored version
	 * which means that the traffic is stored by the AS, rather than
	 * a global variable.
	 * @param tHop
	 * @param amount
	 * @param src
	 */
	public void addTrafficFromWarden(int tHop, double amount, int src) {

		/* initialize the temporal src and dest ASes */
		DecoyAS srcAS = null, curAS = null;
		if (this.activeMap.containsKey(src)) {
			srcAS = this.activeMap.get(src);
		} else if (this.purgedMap.containsKey(src)) {
			srcAS = this.purgedMap.get(src);
		} else {
			// not possible actually..
		}

		if (this.activeMap.containsKey(tHop)) {
			curAS = this.activeMap.get(tHop);
		} else if (this.purgedMap.containsKey(tHop)) {
			curAS = this.purgedMap.get(tHop);
		} else {
			// not possible actually..
		}

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
	public void addTrafficOnThePath(BGPPath path, double amount, int src) {
		for (int tHop : path.getPath()) {
			//System.out.print(" to " + tHop);// !!
			
			/* initialize the temporal src and dest ASes */
			DecoyAS tAS = null;
			if (this.activeMap.containsKey(tHop)) {
				tAS = this.activeMap.get(tHop);
			} else if (this.purgedMap.containsKey(tHop)) {
				tAS = this.purgedMap.get(tHop);
			} else {
				// not possible actually..
			}

			/* globally stored */
			addWeightedTraffic(tHop, amount);
			addWardenTraffic(tHop, amount, src);
			/* locally stored */
			this.activeMap.get(tHop).addOnTotalTraffic(amount);
			addTrafficFromWarden(tHop, amount, src);
		}
		//System.out.println();// !!
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
	 * also stat the from/to-warden traffic at the same time
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
			DecoyAS destAS = this.activeMap.get(t2ASN);
			double amount = tAS.getIPCount() * destAS.getIPCount();
			addTrafficOnThePath(tpath, amount, tAS.getASN());
		}
	}

	/**
	 * count the traffic of all the paths starting from the given AS in
	 * activeMap to all ASes in the purgedMap To get to the purged ASes, get all
	 * the providers of that ASes, and getPathToPurged will pick a best path.
	 * 
	 * also stat the from/to-warden traffic at the same time
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

			double amount = tAS.getIPCount() * purgedAS.getIPCount();
			addTrafficOnThePath(tpath, amount, tAS.getASN());
			
			/* add the traffic unit to the last AS on the purged network */
			/* globally stored */
			addWeightedTraffic(purgedAS.getASN(), amount);
			addWardenTraffic(purgedAS.getASN(), amount, tAS.getASN());
			/* locally stored */
			purgedAS.addOnTotalTraffic(amount);
			addTrafficFromWarden(purgedAS.getASN(), amount, tAS.getASN());
			//System.out.println(" to " + purgedAS.getASN());// !!
		}
	}

	/**
	 * count the traffic of paths starting from activeMap ending at either
	 * activeMap or purgedMap
	 * 
	 * also stat the from/to-warden traffic at the same time
	 **/
	public void statActiveMap() {

		//System.out.println("stat activeMap");// !!

		for (int t1ASN : this.activeMap.keySet()) {
			DecoyAS tAS = this.activeMap.get(t1ASN);
			//System.out.println("from " + t1ASN);
			/* traffic from tAS which is in the activeMap to activeMap */
			activeToActive(tAS);

			/* traffic from tAS which is in the activeMap to purgedMap */
			//System.out.println("to purged:");// !!
			activeToPurged(tAS);
			//System.out.println();// !!
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

			//System.out.println("from " + t1ASN + " to activeMap"); // !!
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
				double amount = tAS.getIPCount() * this.activeMap.get(tASN).getIPCount();
				addTrafficOnThePath(tpath, amount, t1ASN);
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
			//System.out.println("from " + t1ASN + " to purged");
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

				//double amount = t1AS.getIPCount();
				double amount = t1AS.getIPCount() * this.purgedMap.get(t2ASN).getIPCount();
				addTrafficOnThePath(tpath, amount, t1ASN);
				/* globally stored */
				addWeightedTraffic(t2ASN, amount);
				addWardenTraffic(t2ASN, amount, t1ASN);
				/* locally stored */
				this.purgedMap.get(t2ASN).addOnTotalTraffic(amount);
				addTrafficFromWarden(t2ASN, amount, t1ASN);
				System.out.println(" to " + t2ASN);// !!
			}
		}
	}

	/**
	 * count the traffic of paths starting from purgedMap ending at either
	 * activeMap or purgedMap
	 **/
	public void statPurgedMap() {
		//System.out.println("stat purgedMap");// !!

		purgedToActive();
		purgedToPurged();

	}

	/**
	 * calculate the total amount of traffic flowing on the peer to peer
	 * network.
	 * @throws IOException 
	 */
	public void statTotalP2PTraffic() throws IOException {
		/*for (double amount : countTraffic.values()) {
			this.totalTraffic += amount;
		}*/
		int ASN, ipNum, cnt = 0;
		int ips[] = new int[this.activeMap.size() + this.purgedMap.size()];
		String pollString;
		
		BufferedReader fBuff = new BufferedReader(new FileReader(Constants.IP_COUNT_FILE));
		while (fBuff.ready()) {
			pollString = fBuff.readLine().trim();
			//System.out.println(pollString);

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
		/*for (int i = 0; i < cnt; ++i)
			System.out.print(ips[i] + ", ");
		System.out.println();*/
		for (int i = 0; i < cnt; ++i)
			for (int j = i+1; j < cnt; ++j)
				this.totalP2PTraffic += ips[i] * ips[j];
		this.totalP2PTraffic *= 2;
	}

	/**
	 * @return the total amount of traffic flowing on the peer to peer network.
	 */
	public double getTotalP2PTraffic() {
		return this.totalP2PTraffic;
	}

	/**
	 * return the ratio of a given regular AS to the total amount of traffic
	 * flowing on the peer-to-peer network.
	 * 
	 * @return
	 */
	public double getASTrafficRatio(int AS) {
		//return this.countTraffic.get(AS) / this.getTotalTraffic();
		return 0;
	}

	/**
	 * calculate the traffic on peer-to-peer network, and draw the CDF graph for
	 * each AS
	 * 
	 * @throws IOException
	 */
	public void statTrafficOnPToPNetwork() throws IOException {

		statActiveMap();
		statPurgedMap();

		System.out.println("\nShow Results: Traffic on peer to peer network");
		//List<Double> statResult = new ArrayList<Double>(countTraffic.values());
		for (DecoyAS tAS : this.activeMap.values())
			System.out.println(tAS.getASN() + ", " + tAS.getTotalTraffic() + ", " + tAS.getTrafficFromWarden());
		for (DecoyAS tAS : this.purgedMap.values())
			System.out.println(tAS.getASN() + ", " + tAS.getTotalTraffic() + ", " + tAS.getTrafficFromWarden());
		
		//System.out.println("resultList: " + statResult);
		//Stats.printCDF(statResult, ptpNetworkFile);
		//System.out.println("The result of the peer-to-peer 
		//traffic network CDF statistic"	+ " is written into file: " + ptpNetworkFile + ".\n");
	}

	/**
	 * group all the ASes into normal ASes and super ASes.
	 * create a list of normal ASes, and set the ipCount
	 * percentage for each of them and calculate the traffic
	 * sent from each super AS to that normal AS and store
	 * the value locally. Also create a list of super ASes.
	 * 
	 * @param totalTrafficFromSuperASes
	 */
	private void groupASesAndCalcTrafficForNormalAS(double totalTrafficFromSuperASes) {
		
		double totalIPCounts = 0;
		for (DecoyAS tAS : this.activeMap.values()) {
			if (!tAS.isSuperAS()) {
				this.normalASList.add(tAS);
				totalIPCounts += tAS.getIPCount();
			} else {
				this.superASList.add(tAS);
				++this.superASNum;
			}
		}
		for (DecoyAS tAS : this.purgedMap.values()) {
			if (!tAS.isSuperAS()) {
				this.normalASList.add(tAS);
				totalIPCounts += tAS.getIPCount();
			} else {
				this.superASList.add(tAS);
				++this.superASNum;
			}
		}
		
		/* calculate the percentage */
		double trafficFromOneSuperAS = totalTrafficFromSuperASes / this.superASNum;
		for (DecoyAS tAS : this.normalASList) {
			tAS.setIPPercentage(tAS.getIPCount() / totalIPCounts);
			tAS.setTrafficFromEachSuperAS(tAS.getIPCount() / totalIPCounts * trafficFromOneSuperAS);
		}
	}
	
	/**
	 * read the ratio of the traffic on peer to peer network and 
	 * the traffic flowing from the super ASes from the traffic split file, 
	 * based on that calculate the total amount of traffic flowing 
	 * from all super ASes. then assume that the total amount of traffic 
	 * is split equally on each super AS, hence, divided by the number of 
	 * super ASes, and return the total amount of traffic flowing from
	 * each super AS. 
	 * @return
	 * @throws IOException 
	 */
	public double readRatiosAndCalcSuperASesTraffic() throws IOException {
		
		String pollString;
		int ASN, cnt = 0;
		double tRatio;
		double ratio[] = new double[2];
	
		/*
		 * read the traffic split file, and calculate the 
		 * traffic flowing from the superAS to normal ASes
		 */
		BufferedReader fBuff = new BufferedReader(new FileReader(this.trafficSplitFile));
		while (fBuff.ready()) {
			if (cnt == 2) {
				System.out.println("WRONG TRAFFIC SPLIT FILE!!");
				return -1;
			}
				
			pollString = fBuff.readLine().trim();
			//System.out.println(pollString);

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
			ratio[ASN-1] = tRatio;
			++cnt;
		}

		if (ratio[0] + ratio[1] != 1.0) {
			System.out.println("WRONG TRAFFIC SPLIT FILE!!");
			return -1;
		}
		
		/* calculate based on the ratio, and then split equally */
		double totalTrafficFromSuperASes = getTotalP2PTraffic() * ratio[1] / ratio[0];
		 
		return totalTrafficFromSuperASes;
	}
	
	/**
	 * very similar to purgedToActive() function, but only
	 * start from super ASes in the purged network,
	 * and add the traffic from each super AS of the destiny AS
	 * to all the normal ASes on the path
	 */
	public void superASFromPurgedToActive() {
		
		for (int t1ASN : this.purgedMap.keySet()) {
			/* only select super ASes in the purged network */
			if (!this.purgedMap.get(t1ASN).isSuperAS())
				continue;
			/* empty the temporary container */
			pathSets.clear();

			//System.out.println("from " + t1ASN + " to activeMap"); // !!
			DecoyAS tAS = this.purgedMap.get(t1ASN);

			/* get the path through the providers of nodes in purgedMap */
			Set<AS> providers = tAS.getProviders();
			for (AS pAS : providers) {
				for (int t2ASN : this.activeMap.keySet()) {
					
					/* only count traffic from super ASes to normal ASes */
					if (this.activeMap.get(t2ASN).isSuperAS())
						continue;
					
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
				//double amount = tAS.getIPCount() * this.activeMap.get(tASN).getIPCount();
				double amount = this.activeMap.get(tASN).getTrafficFromEachSuperAS();
				addTrafficOnThePath(tpath, amount, t1ASN);
			}
		}

	}
	
	public void superASFromPurgedToPurged () {
		
		/* path from purged AS t1ASN to purged AS t2ASN */
		for (int t1ASN : this.purgedMap.keySet()) {
			
			/* only select super ASes in the purged network */
			if (!this.purgedMap.get(t1ASN).isSuperAS())
				continue;
			
			//System.out.println("from " + t1ASN + " to purged");
			/* empty the temporary container */
			pathSets.clear();

			AS t1AS = this.purgedMap.get(t1ASN);
			/* get the providers of the AS in purgedMap */
			List<Integer> p1List = getProvidersList(t1AS.getProviders());
			/* get paths from the provider to the other ASes in purgedMap */
			for (int p1 : p1List) {
				AS pAS = this.activeMap.get(p1);

				for (int t2ASN : this.purgedMap.keySet()) {
					
					/* only stat traffic from super ASes to normal ASes */
					if (this.purgedMap.get(t2ASN).isSuperAS())
						continue;
					
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

				/* the traffic of the destiny AS from each super AS */
				//double amount = t1AS.getIPCount() * this.purgedMap.get(t2ASN).getIPCount();
				double amount = this.purgedMap.get(t2ASN).getTrafficFromEachSuperAS();
				addTrafficOnThePath(tpath, amount, t1ASN);
				/* globally stored */
				addWeightedTraffic(t2ASN, amount);
				addWardenTraffic(t2ASN, amount, t1ASN);
				/* locally stored */
				this.purgedMap.get(t2ASN).addOnTotalTraffic(amount);
				addTrafficFromWarden(t2ASN, amount, t1ASN);
				//System.out.println(" to " + t2ASN);// !!
			}
		}
	}

	public void superASFromActiveToActive(DecoyAS tAS) {

		for (int t2ASN : this.activeMap.keySet()) {
			DecoyAS destAS = this.activeMap.get(t2ASN);
			if (destAS.isSuperAS())
				continue;

			BGPPath tpath = tAS.getPath(t2ASN);
			// System.out.println("active!! " + t2ASN + " path " + tpath);
			if (tpath == null)
				continue;

			/* if the path exists, do the statistic */
			//double amount = tAS.getIPCount() * destAS.getIPCount();
			double amount = destAS.getTrafficFromEachSuperAS();
			addTrafficOnThePath(tpath, amount, tAS.getASN());
		}
		
	}
	
	public void superASFromActiveToPurged(DecoyAS tAS) {

		for (DecoyAS purgedAS : this.purgedMap.values()) {
			if (purgedAS.isSuperAS())
				continue;
			
			List<Integer> hookASNs = getProvidersList(purgedAS.getProviders());
			BGPPath tpath = tAS.getPathToPurged(hookASNs);
			if (tpath == null)
				continue;

			//double amount = tAS.getIPCount() * purgedAS.getIPCount();
			double amount = purgedAS.getTrafficFromEachSuperAS();
			addTrafficOnThePath(tpath, amount, tAS.getASN());
			
			/* add the traffic unit to the last AS on the purged network */
			/* globally stored */
			addWeightedTraffic(purgedAS.getASN(), amount);
			addWardenTraffic(purgedAS.getASN(), amount, tAS.getASN());
			/* locally stored */
			purgedAS.addOnTotalTraffic(amount);
			addTrafficFromWarden(purgedAS.getASN(), amount, tAS.getASN());
			//System.out.println(" to " + purgedAS.getASN());// !!
		}
		
	}
	
	/**
	 * add the traffic on each normal AS and the normal
	 * ASes on the path which the super ASes send traffic to.
	 * four cases, since the super AS might be either on the active
	 * network or the purged network, and the strategy of doing 
	 * the traffic flow is almost the same to the peer to peer
	 * traffic flow, the only difference is that it is the traffic
	 * from super ASes to normal ASes. 
	 */
	public void trafficFromSuperASToNormalAS() {
		
		superASFromPurgedToActive();
		superASFromPurgedToPurged();

		for (int tASN : this.activeMap.keySet()) {
			DecoyAS tAS = this.activeMap.get(tASN);
			if (!tAS.isSuperAS())
				continue;
			//System.out.println("from " + tASN);
			/* traffic from tAS which is in the activeMap to activeMap */
			superASFromActiveToActive(tAS);

			/* traffic from tAS which is in the activeMap to purgedMap */
			//System.out.println("to purged:");// !!
			superASFromActiveToPurged(tAS);
			//System.out.println();// !!
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
	public void statTrafficFromSuperAS() throws IOException {

		statTotalP2PTraffic();
		System.out.println("The total traffic on peer-to-peer network: "
				+ getTotalP2PTraffic());

		/* read the file and calculate the traffic flowing to each 
		 * normal AS from each super AS */
		double totalTrafficFromSuperASes = readRatiosAndCalcSuperASesTraffic();
		groupASesAndCalcTrafficForNormalAS(totalTrafficFromSuperASes);
		
		// print out the traffic from each super AS to each normal AS
		/*System.out.println("totalTrafficFromSuperASes: " + totalTrafficFromSuperASes);
		for (DecoyAS tAS : this.activeMap.values()) {
			if (!tAS.isSuperAS())
				System.out.println(tAS.getASN() + ": " + tAS.getTrafficFromEachSuperAS());
		}
		for (DecoyAS tAS : this.purgedMap.values()) {
			if (!tAS.isSuperAS())
				System.out.println(tAS.getASN() + ": " + tAS.getTrafficFromEachSuperAS());
		}*/
		
		trafficFromSuperASToNormalAS();		
	}

	
	public void runStat() throws IOException {

		statTrafficOnPToPNetwork();
		statTrafficFromSuperAS();

		testResults();
	}

	/**
	 * print out the result
	 */
	private void testResults() {
		// test results
		System.out.println("\nShow Results: the whole network");
		System.out.println("AS, total traffic, from warden traffic");
		for (DecoyAS tAS : this.activeMap.values()) {
			System.out.println(tAS.getASN() + ", " + tAS.getTotalTraffic() + ", " + tAS.getTrafficFromWarden());
		}
		for (DecoyAS tAS : this.purgedMap.values()) {
			System.out.println(tAS.getASN() + ", " + tAS.getTotalTraffic() + ", " + tAS.getTrafficFromWarden());
		}
	}
}

