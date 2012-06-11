package sim;

import java.io.*;
import java.util.*;

import decoy.*;
import topo.AS;
import topo.BGPPath;
import util.Stats;

/**
 * This class is a stupidly poorly organized collections of methods used to
 * actually evaluate the effectivness of a decoy routing system in containing
 * the warden.
 * 
 * @author schuch
 * 
 */
public class FindSim {

	/**
	 * Stores the active (routing) portion of the topology
	 */
	private HashMap<Integer, DecoyAS> activeMap;

	/**
	 * Stores the pruned portion of the topology
	 */
	private HashMap<Integer, DecoyAS> purgedMap;

	/**
	 * Stores the set of ASes that are part of the warden
	 */
	private HashSet<DecoyAS> wardenASes;

	/*
	 * Maps indexed by decoy router count that stores the results from a set of
	 * runs in a list. Basically, data from all runs of a given deployment size
	 * live in a single list, this is indexed by decoy router count. There is a
	 * map for the number of ASes that we know are clean, one for the ASes that
	 * we KNOW are dirty, and one map as a sanity check for the ASes we think
	 * are dirty, but actually are not (should NOT happen). The
	 */
	private HashMap<Integer, List<Integer>> dirtyResultMap;
	private HashMap<Integer, List<Integer>> cleanResultMap;
	private HashMap<Integer, List<Integer>> falseResultMap;

	/**
	 * Constant that controls the number of runs done for each deployment size
	 */
	private static int RUN_COUNT = 1;

	/**
	 * Flag that ensures that we're only deploying decoy routers to transit
	 * ASes. Only applies in certain deployment strats.
	 */
	private static boolean ONLY_TRANSIT = true;

	/**
	 * Flag that ensures that we're not deploying to directly connected to the
	 * warden ASes. This only applies in certain deployment strats again.
	 */
	private static final boolean ECONOMIC_DEPLOY = true;

	//NOT IMPLMENTED!  NEVER SET TO TRUE
	private static final boolean SCORE_BY_IP = false;

	private static final String LOG_DIR = "logs/";

	public FindSim(HashMap<Integer, DecoyAS> activeMap,
			HashMap<Integer, DecoyAS> purgedMap) {
		super();
		this.activeMap = activeMap;
		this.purgedMap = purgedMap;
		this.dirtyResultMap = new HashMap<Integer, List<Integer>>();
		this.cleanResultMap = new HashMap<Integer, List<Integer>>();
		this.falseResultMap = new HashMap<Integer, List<Integer>>();
		this.wardenASes = new HashSet<DecoyAS>();
		for (DecoyAS tAS : activeMap.values()) {
			if (tAS.isWardenAS()) {
				this.wardenASes.add(tAS);
			}
		}
	}

	/**
	 * HOOK TO RUN RANDOM DEPLOYMENT. This uses DecoySeeder, and basically
	 * follows the deployment strat from Cirripede. Results are written to a
	 * file.
	 * 
	 * @param logFilename
	 * @throws IOException
	 */
	public void run(String logFilename) throws IOException {
		long fullTimeStart = System.currentTimeMillis();
		System.out.println("Starting decoy hunting sim.");

		// for (int expo = 0; expo < 11; expo++) {
		// int decoyCount = (int) Math.round(Math.pow(2, expo));
		// this.runOneDeployLevel(decoyCount, FindSim.ONLY_TRANSIT);
		// }

		RUN_COUNT = 50;
		ONLY_TRANSIT = false;

		List<Integer> decoyCounts = new LinkedList<Integer>();
		for (int decoyCount = 1; decoyCount < 4100; decoyCount = decoyCount + 500) {
			DecoySeeder seeder = new DecoySeeder(decoyCount, this.activeMap,
					this.purgedMap, FindSim.ONLY_TRANSIT,
					FindSim.ECONOMIC_DEPLOY);
			decoyCounts.add(decoyCount);
			this.runOneDeployLevel(decoyCount, seeder);
		}

		fullTimeStart = (System.currentTimeMillis() - fullTimeStart) / 60000;
		System.out.println("Full run took: " + fullTimeStart + " mins ");

		this.printResults(logFilename, decoyCounts);
	}

	/**
	 * HOOK TO RUN LARGE AS ONLY DEPLOYMENTS. Uses the LargeASDecoyPlacer to
	 * deploy with the flag provided controling large AS deployments. Results
	 * written to a file.
	 * 
	 * @param seedSingle
	 *            - flag to switch between sequential deployment (true) or
	 *            cumulative deployment (false)
	 * @param logFilename
	 *            - log file base
	 * @throws IOException
	 *             - if there is an error doing output
	 */
	public void runLargeASOnlyTests(boolean seedSingle, String logFilename)
			throws IOException {
		long fullTimeStart = System.currentTimeMillis();
		System.out.println("Starting decoy hunting sim.");

		/*
		 * We only need to do one run, as these deployments are determinisitic
		 */
		RUN_COUNT = 1;
		ONLY_TRANSIT = true;

		/*
		 * run from size 1 to 100
		 */
		List<Integer> decoyCounts = new LinkedList<Integer>();
		LargeASDecoyPlacer seeder = new LargeASDecoyPlacer(this.activeMap);
		for (int size = 0; size < 100; size++) {
			this.dirtyResultMap.put(size, new LinkedList<Integer>());
			this.cleanResultMap.put(size, new LinkedList<Integer>());
			this.falseResultMap.put(size, new LinkedList<Integer>());

			decoyCounts.add(size);

			Set<Integer> groundTruth;
			if (seedSingle) {
				groundTruth = seeder.seedSingleDecoyBySize(size);
			} else {
				groundTruth = seeder.seedNLargest(size);
			}
			this.probe(size, groundTruth);
		}
		fullTimeStart = (System.currentTimeMillis() - fullTimeStart) / 60000;
		System.out.println("Full run took: " + fullTimeStart + " mins ");

		this.printResults(logFilename, decoyCounts);
	}

	/**
	 * HOOK TO RUN RINGS EXPERIMENT. This will run fractional deployments in 10%
	 * intervals for a depth 2 ring (the interesting one) for the loaded warden.
	 * Results will be written to a file based on the supplied string.
	 * 
	 * @param country
	 *            - base string for the output file, result will be
	 *            <country>-decoy-hunt-rings.csv
	 * @throws IOException
	 *             - if there is an issue writting to the output file
	 */
	public void runRings(String country) throws IOException {
		long fullTimeStart = System.currentTimeMillis();
		System.out.println("Starting decoy hunting sim.");

		/*
		 * Probabilistic deploy, so multiple runs are needed please
		 */
		RUN_COUNT = 5;

		/*
		 * Setup the ring seeder to look at depth 2 ring
		 */
		Rings ringMaker = new Rings(this.activeMap, this.purgedMap);
		List<Integer> decoyCounts = new LinkedList<Integer>();
		ringMaker.setupSeeder(2);

		/*
		 * run for various fractions
		 */
		for (double size = 0.1; size <= 1.0; size += 0.1) {
			int ringSize = ringMaker.setDecoySeedSizeFraction(size);
			decoyCounts.add(ringSize);
			this.runOneDeployLevel(ringSize, ringMaker);
		}
		fullTimeStart = (System.currentTimeMillis() - fullTimeStart) / 60000;
		System.out.println("Full run took: " + fullTimeStart + " mins ");

		/*
		 * Dump results
		 */
		this.printResults(country + "-decoy-hunt-rings.csv", decoyCounts);
	}

	/**
	 * Runs a very early attempt at trying to have our "down paths" (to borrow
	 * SCION lingo) clean.
	 * 
	 * @param avoidSize
	 * @throws IOException
	 */
	public void runActive(int avoidSize) throws IOException {

		/*
		 * We don't have the seeding in this context, so build it again,
		 * thankfully it is deterministic
		 */
		LargeASDecoyPlacer seeder = new LargeASDecoyPlacer(this.activeMap);
		Set<Integer> groundTruth = seeder.seedNLargest(avoidSize);

		/*
		 * Setup some vars so probe doesn't shit everywhere w/ null pointers
		 */
		this.dirtyResultMap.put(avoidSize, new LinkedList<Integer>());
		this.cleanResultMap.put(avoidSize, new LinkedList<Integer>());
		this.falseResultMap.put(avoidSize, new LinkedList<Integer>());

		/*
		 * do the actual reachability test
		 */
		Set<Integer> reverseSet = this.probeReversePath();
		Set<Integer> forwardSet = this.probe(avoidSize, groundTruth);

		/*
		 * Do some set intersections
		 */
		Set<Integer> tempSet = new HashSet<Integer>();
		tempSet.addAll(reverseSet);
		tempSet.removeAll(forwardSet);
		Set<Integer> otherTempSet = new HashSet<Integer>();
		otherTempSet.addAll(forwardSet);
		otherTempSet.removeAll(reverseSet);

		/*
		 * Output to a file
		 */
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(
				FindSim.LOG_DIR + "active.csv"));
		outBuff
				.write("forward,reverse,size delta,in reverse not forward,in forward not reverse\n");
		outBuff.write("" + forwardSet.size() + "," + reverseSet.size() + ","
				+ (forwardSet.size() - reverseSet.size()) + ","
				+ tempSet.size() + "," + otherTempSet.size() + "\n");
		outBuff.close();
	}

	/**
	 * Function that builds some stats based on the results maps and outputs
	 * them to a file.
	 * 
	 * @param filename
	 *            - the file we're writting to
	 * @param decoyCounts
	 *            - the sizes of deployment, in order that they will be output
	 * @throws IOException
	 *             - if there is an issue writting to the given file
	 */
	public void printResults(String filename, List<Integer> decoyCounts)
			throws IOException {
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(
				FindSim.LOG_DIR + filename));
		int totalASN = this.activeMap.size() + this.purgedMap.size();
		outBuff.write("Decoy hunting sim - full size is," + totalASN + "\n");
		outBuff
				.write("deploy size,mean dirty,std dev dirty,median dirty,mean clean,std dev clean,median clean,mean false, std dev false, median false\n");
		// for (int expo = 0; expo < 11; expo++) {
		// for(int decoyCount = 1500; decoyCount < 4100; decoyCount = decoyCount
		// + 250){
		//for (int decoyCount = 0; decoyCount < 100; decoyCount++) {
		for (int i = 0; i < decoyCounts.size(); i++) {
			int decoyCount = decoyCounts.get(i);
			List<Integer> vals = this.dirtyResultMap.get(decoyCount);
			double meanD = Stats.mean(vals);
			double stdD = Stats.stdDev(vals);
			double medD = Stats.median(vals);
			vals = this.cleanResultMap.get(decoyCount);
			double meanC = Stats.mean(vals);
			double stdC = Stats.stdDev(vals);
			double medC = Stats.median(vals);
			vals = this.falseResultMap.get(decoyCount);
			double meanF = Stats.mean(vals);
			double stdF = Stats.stdDev(vals);
			double medF = Stats.median(vals);
			outBuff.write("" + decoyCount + "," + meanD + "," + stdD + ","
					+ medD + "," + meanC + "," + stdC + "," + medC + ","
					+ meanF + "," + stdF + "," + medF + "\n");
		}
		outBuff.close();

		/*
		 * Clear results for any future runs
		 */
		this.clearResults();
	}

	/**
	 * Resets results data structs
	 */
	public void clearResults() {
		this.dirtyResultMap.clear();
		this.cleanResultMap.clear();
		this.falseResultMap.clear();
	}

	private void runOneDeployLevel(int size, Seeder decoySeeder) {
		System.out.println("starting probe of deployment size: " + size);
		long deploySizeStart = System.currentTimeMillis();
		this.dirtyResultMap.put(size, new LinkedList<Integer>());
		this.cleanResultMap.put(size, new LinkedList<Integer>());
		this.falseResultMap.put(size, new LinkedList<Integer>());

		for (int runs = 0; runs < FindSim.RUN_COUNT; runs++) {
			long runStart = -1;
			/*
			 * run timing if we're low number run (to give us a feel)
			 */
			if (runs < 5) {
				System.out.println("Starting run number: " + runs);
				runStart = System.currentTimeMillis();
			}
			Set<Integer> correctSet = decoySeeder.seedDecoys();
			this.probe(size, correctSet);

			/*
			 * Run timing reporting
			 */
			if (runs < 5) {
				runStart = (System.currentTimeMillis() - runStart) / 1000;
				System.out.println("Run took: " + runStart);
			}
		}

		/*
		 * Deployment timing info
		 */
		deploySizeStart = (System.currentTimeMillis() - deploySizeStart) / 1000;
		System.out.println("Probe of deployment size: " + size + " took "
				+ deploySizeStart + " seconds.");
	}

	/**
	 * Computes which ASes have clean return paths from us.
	 * 
	 * @return - the set of ASNs that have clean return paths to us
	 */
	private Set<Integer> probeReversePath() {
		Set<Integer> cleanASNs = new HashSet<Integer>();

		// look at each source of traffic
		for (int tASN : this.activeMap.keySet()) {

			DecoyAS tempAS = this.activeMap.get(tASN);
			if (tempAS.isDecoy()) {
				// don't accidently count a dirty AS as clean just because it
				// has a path
				continue;
			}
			// see if any path to a china asn exists (and is clean)
			for (AS tChina : this.wardenASes) {
				if (tempAS.getPath(tChina.getASN()) != null) {
					cleanASNs.add(tASN);
					break;
				}
			}
		}
		for (int tASN : this.purgedMap.keySet()) {
			DecoyAS tempAS = this.purgedMap.get(tASN);
			for (AS tProv : tempAS.getProviders()) {
				if (cleanASNs.contains(tProv.getASN())) {
					cleanASNs.add(tASN);
				}
			}
		}

		return cleanASNs;
	}

	/**
	 * Function that does the actual hunting for dirty ASes. Does this by
	 * looking for tainted paths, and walking backward, hunting for the spot at
	 * which the path becomes clean. This will flag one AS, but not clear up all
	 * ASes.
	 * 
	 * @param stopPoint
	 *            - the number of ASes that are dirty, simply used to short
	 *            circuit runs once they are all found as a time saver
	 * @param groundTruth
	 *            - Set of ASNs of decoy routing deployers, used to sanity check
	 *            that we're not somehow flagging clean ASes as dirty
	 * @return
	 */
	private Set<Integer> probe(int stopPoint, Set<Integer> groundTruth) {

		/*
		 * start building the clean set, we'll first look at all ASNs that lie
		 * on clean paths
		 */
		HashSet<Integer> cleanSet = new HashSet<Integer>();
		HashSet<Integer> dirtySet = new HashSet<Integer>();
		Set<BGPPath> tempPathSet = new HashSet<BGPPath>();

		System.out.println("Starting probe of size " + stopPoint);

		/*
		 * Look at each AS (second block deals w/ pruned ASes, and find out what
		 * paths we have to that destination that are clean, mark all ASes on
		 * those paths as clean
		 */
		int noDest = 0;
		long ipScore = 0;
		for (int tASN : this.activeMap.keySet()) {
			tempPathSet.clear();
			for (DecoyAS tChina : this.wardenASes) {
				tempPathSet.addAll(tChina.getAllPathsTo(tASN));
			}
			if (tempPathSet.size() == 0) {
				noDest++;
			}
			for (BGPPath tempPath : tempPathSet) {
				if (!this.pathIsDirty(tempPath, tASN)) {
					if (FindSim.SCORE_BY_IP) {
						ipScore += this.activeMap.get(tASN).getIPCount();
					}
					cleanSet.addAll(tempPath.getPath());
				}
			}
		}
		System.out.println("No dest to transits: " + noDest);
		for (int tASN : this.purgedMap.keySet()) {
			tempPathSet.clear();
			for (DecoyAS tChina : this.wardenASes) {
				for (AS tHook : this.purgedMap.get(tASN).getProviders()) {
					tempPathSet.addAll(tChina.getAllPathsTo(tHook.getASN()));
				}
			}

			for (BGPPath tempPath : tempPathSet) {
				if (!this.pathIsDirty(tempPath, tASN)) {
					if (FindSim.SCORE_BY_IP) {
						ipScore += this.purgedMap.get(tASN).getIPCount();
					}
					cleanSet.addAll(tempPath.getPath());
					cleanSet.add(tASN);
				}
			}
		}

		/*
		 * Now do intersection to mark dirty ASes as best we can
		 */
		for (int tASN : this.activeMap.keySet()) {
			/*
			 * If we know you're clean, skip over you
			 */
			if (cleanSet.contains(tASN)) {
				continue;
			}

			tempPathSet.clear();

			/*
			 * Grab all paths to the possibly tainted destination
			 */
			for (DecoyAS tChina : this.wardenASes) {
				tempPathSet.addAll(tChina.getAllPathsTo(tASN));
			}

			for (BGPPath tPath : tempPathSet) {
				boolean only = true;
				for (int tHop : tPath.getPath()) {
					if ((tHop != tASN) && (!cleanSet.contains(tHop))) {
						only = false;
						break;
					}
				}

				/*
				 * If the dest asn is the only not known clean asn, then it must
				 * be dirty
				 */
				if (only) {
					dirtySet.add(tASN);
					break;
				}
			}

		}

		for (int tASN : this.purgedMap.keySet()) {
			/*
			 * If we know you're clean, skip over you
			 */
			if (cleanSet.contains(tASN)) {
				continue;
			}

			tempPathSet.clear();
			/*
			 * Grab all paths to the possibly tainted destination
			 */
			for (DecoyAS tChina : this.wardenASes) {
				for (AS tHook : this.purgedMap.get(tASN).getProviders()) {
					tempPathSet.addAll(tChina.getAllPathsTo(tHook.getASN()));
				}
			}

			for (BGPPath tPath : tempPathSet) {
				boolean only = true;
				for (int tHop : tPath.getPath()) {
					if ((tHop != tASN) && (!cleanSet.contains(tHop))) {
						only = false;
						break;
					}
				}

				/*
				 * If the dest asn is the only not known clean asn, then it must
				 * be dirty
				 */
				if (only) {
					dirtySet.add(tASN);
					break;
				}
			}
		}

		System.out.println("dirty size: " + dirtySet.size() + " clean size: "
				+ cleanSet.size());
		/*
		 * base size is the number of ASNs we finger, we then remove the ground
		 * truth, leaving any false positives in dirtySet
		 */
		int baseSize = dirtySet.size();
		dirtySet.removeAll(groundTruth);
		this.dirtyResultMap.get(stopPoint).add(baseSize - dirtySet.size());
		this.cleanResultMap.get(stopPoint).add(cleanSet.size());
		this.falseResultMap.get(stopPoint).add(dirtySet.size());

		return cleanSet;
	}

	/**
	 * Predicate that tests if a given path is dirty or not. This is done by
	 * simply walking the path and testing at each hop if the AS is dirty or no.
	 * 
	 * @param path
	 *            - the BGP path to that destination
	 * @param dest
	 *            - the destination itself
	 * @return - true if at least one AS along the path deploys decoy routing,
	 *         false otherwise
	 */
	private boolean pathIsDirty(BGPPath path, int dest) {
		for (int tHop : path.getPath()) {
			if (this.activeMap.get(tHop).isDecoy()) {
				return true;
			}
		}

		if (this.purgedMap.containsKey(dest)) {
			if (this.purgedMap.get(dest).isDecoy()) {
				return true;
			}
		}

		return false;
	}
}
