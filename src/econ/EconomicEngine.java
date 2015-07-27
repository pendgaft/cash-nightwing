package econ;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.TIntObjectMap;

import java.util.*;
import java.io.*;

import decoy.DecoyAS;
import sim.BGPMaster;
import sim.Constants;
import sim.ParallelTrafficStat;
import sim.PerformanceLogger;
import topo.AS;
import topo.ASRanker;
import topo.BGPPath;

public class EconomicEngine {

	public enum OrderMode {
		PathAppearance, IPWeighted;
	}

	private HashMap<Integer, EconomicAgent> theTopo;
	private TIntObjectMap<DecoyAS> activeTopology;
	private ParallelTrafficStat trafficManger;
	private HashMap<Integer, Double> cashForThisRound;

	private BufferedWriter wardenOut;
	private BufferedWriter transitOut;
	private BufferedWriter pathOut;

	private double maxIPCount;

	private static final String ROUND_TERMINATOR = "***";
	private static final String SAMPLE_TERMINATOR = "###";
	private static final String SAMPLESIZE_TERMINATOR = "&&&";

	private PerformanceLogger perfLogger = null;

	public EconomicEngine(TIntObjectMap<DecoyAS> activeMap, TIntObjectMap<DecoyAS> prunedMap,
			ParallelTrafficStat trafficManager, String loggingDir, PerformanceLogger perfLogger) {
		this.theTopo = new HashMap<Integer, EconomicAgent>();
		this.cashForThisRound = new HashMap<Integer, Double>();
		this.activeTopology = activeMap;
		this.trafficManger = trafficManager;

		try {
			this.wardenOut = new BufferedWriter(new FileWriter(loggingDir + "warden.log"));
			this.transitOut = new BufferedWriter(new FileWriter(loggingDir + "transit.log"));
			this.pathOut = new BufferedWriter(new FileWriter(loggingDir + "path.log"));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		this.perfLogger = perfLogger;

		for (DecoyAS tAS : prunedMap.valueCollection()) {
			this.theTopo.put(tAS.getASN(), new TransitProvider(tAS, this.transitOut, activeMap,
					TransitProvider.DECOY_STRAT.NEVER, this.pathOut));
		}
		for (DecoyAS tAS : activeMap.valueCollection()) {
			if (tAS.isWardenAS()) {
				this.theTopo.put(tAS.getASN(), new WardenAgent(tAS, this.transitOut, this.wardenOut, activeMap,
						prunedMap, this.pathOut));
			} else {
				this.theTopo.put(tAS.getASN(), new TransitProvider(tAS, this.transitOut, activeMap,
						TransitProvider.DECOY_STRAT.DICTATED, this.pathOut));
			}
		}

		this.calculateCustomerCone();
		this.maxIPCount = 0.0;
		for (DecoyAS tAS : this.activeTopology.valueCollection()) {
			this.maxIPCount = Math.max(this.maxIPCount, (double) tAS.getIPCustomerCone());
		}
	}

	/**
	 * calculate ccSize for each AS the start point of dfs.
	 */
	private void calculateCustomerCone() {
		/*
		 * Do this over all ASes since we'll need the IP cust cones populated
		 * for the econ part of the sim
		 */
		for (EconomicAgent tAgent : this.theTopo.values()) {
			EconomicEngine.buildIndividualCustomerCone(tAgent.parent);

			long ipCCSize = 0;
			for (int tASN : tAgent.parent.getCustomerConeASList()) {
				ipCCSize += this.theTopo.get(tASN).parent.getIPCount();
			}
			tAgent.parent.setCustomerIPCone(ipCCSize);
		}
		if (Constants.DEBUG) {
			for (DecoyAS tAS : this.activeTopology.valueCollection()) {
				System.out.println(tAS.getASN() + ", ccSize: " + tAS.getCustomerConeSize() + ", ccList:"
						+ tAS.getCustomerConeASList());
			}
		}
	}

	/**
	 * dfs the AS tree recursively by using the given AS as the root and get all
	 * the ASes in its customer cone.
	 * 
	 * @param currentAS
	 * @return
	 */
	public static void buildIndividualCustomerCone(AS currentAS) {

		/*
		 * Skip ASes that have already been built at an earlier stage
		 */
		if (currentAS.getCustomerConeSize() != 0) {
			return;
		}

		for (int tASN : currentAS.getPurgedNeighbors()) {
			currentAS.addOnCustomerConeList(tASN);
		}
		for (AS nextAS : currentAS.getCustomers()) {
			EconomicEngine.buildIndividualCustomerCone(nextAS);
			for (int tASN : nextAS.getCustomerConeASList()) {
				currentAS.addOnCustomerConeList(tASN);
			}
		}

		/* count itself */
		currentAS.addOnCustomerConeList(currentAS.getASN());
	}

	public void manageDictatedDRSim(String drFile) {
		List<Set<Integer>> roundConfigs = new LinkedList<Set<Integer>>();

		/*
		 * Parse the config file to load up each rounds DR deployment
		 */
		try {
			BufferedReader drBuffer = new BufferedReader(new FileReader(drFile));
			Set<Integer> currentRoundValues = new HashSet<Integer>();

			while (drBuffer.ready()) {
				String pollStr = drBuffer.readLine().trim();
				/*
				 * End of round will be signified by a line containing only
				 * whitespace
				 */
				if (pollStr.length() == 0) {
					roundConfigs.add(currentRoundValues);
					currentRoundValues = new HashSet<Integer>();
					continue;
				}

				currentRoundValues.add(Integer.parseInt(pollStr));
			}

			if (currentRoundValues.size() > 0) {
				roundConfigs.add(currentRoundValues);
			}

			drBuffer.close();
		} catch (IOException e) {
			System.err.println("Error while parsing DR file.");
			e.printStackTrace();
			return;
		}

		/*
		 * Do some bookkeeping for completion estimation
		 */
		int sliceSize = roundConfigs.size() / 10;
		int sampleCounter = 0;
		int current = 1;
		long startTime = System.currentTimeMillis();

		/*
		 * Actually do the sim now
		 */
		for (Set<Integer> drSet : roundConfigs) {

			/*
			 * Write the size terminators to logging files
			 */
			try {
				this.wardenOut.write(EconomicEngine.SAMPLESIZE_TERMINATOR + "\n");
				this.transitOut.write(EconomicEngine.SAMPLESIZE_TERMINATOR + "\n");
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}

			/*
			 * Actually do the sim rounds
			 */
			for (int counter = 0; counter < 3; counter++) {
				this.trafficManger.runStat();
				String roundHeader = null;
				roundHeader = "" + drSet.size() + "," + counter;
				this.driveEconomicTurn(roundHeader, drSet, counter);
			}

			/*
			 * Do some progress tracking and logging
			 */
			sampleCounter++;
			if (sampleCounter == sliceSize * current) {
				long currentTime = System.currentTimeMillis();
				System.out.println("" + current * 10 + "% complete, this slice took "
						+ Long.toString((currentTime - startTime) / 60000) + " minutes.");
				System.out.println("Estimated time to completion: "
						+ Long.toString((10 - current) * (currentTime - startTime) / 60000) + " minutes.");
				current++;
				startTime = currentTime;
			}
		}
	}

	//TODO merege this code with targeted code, as they are basically identical
	public void manageGlobalWardenSim(int startCount, int endCount, int step, boolean coverage, boolean defection) {
		
		/*
		 * Don't log paths if we're doing defection
		 */
		if(defection){
			EconomicAgent.suppressPathLogging();
		}
		
		List<Integer> rankList = null;
		if (coverage) {
			rankList = this.buildSortedSetCoverage(endCount, true);
		} else {
			rankList = this.buildSortedBase(endCount, true, null);
		}

		/*
		 * Actually do the sim now
		 */
		for (int drCount = startCount; drCount <= endCount; drCount += step) {
			System.out.println("Starting processing for Decoy Count of: " + drCount);

			/*
			 * Write the size terminators to logging files
			 */
			try {
				this.wardenOut.write(EconomicEngine.SAMPLESIZE_TERMINATOR + "\n");
				this.transitOut.write(EconomicEngine.SAMPLESIZE_TERMINATOR + "\n");
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}

			/*
			 * Build decoy set for this round
			 */
			Set<Integer> drSet = new HashSet<Integer>();
			int listPos = 0;
			while (drSet.size() != drCount) {
				drSet.add(rankList.get(listPos));
				listPos++;
			}

			if (defection) {
				for (int tDefector : drSet) {
					/*
					 * Build the DR set less the defector
					 */
					Set<Integer> drSubset = new HashSet<Integer>();
					for (int tDeploy : drSet) {
						if (tDeploy == tDefector) {
							continue;
						}
						drSubset.add(tDeploy);
					}

					/*
					 * Actually sim the rounds
					 */
					for (int counter = 0; counter < 3; counter++) {
						this.trafficManger.runStat();
						String roundHeader = null;
						roundHeader = "" + drCount + "," + counter;
						this.driveEconomicTurn(roundHeader, drSubset, counter);
					}
				}				
			} else {
				/*
				 * Actually do the sim rounds
				 */
				for (int counter = 0; counter < 3; counter++) {
					this.trafficManger.runStat();
					String roundHeader = null;
					roundHeader = "" + drCount + "," + counter;
					this.driveEconomicTurn(roundHeader, drSet, counter);
				}
			}
		}
	}

	private List<Integer> buildSortedSetCoverage(int size, boolean global) {

		List<Integer> retList = new ArrayList<Integer>();
		Set<Integer> usedSet = new HashSet<Integer>();
		for (int counter = 0; counter < size; counter++) {
			List<Integer> tmpList = this.buildSortedBase(1, global, usedSet);
			retList.add(tmpList.get(0));
			usedSet.add(tmpList.get(0));
		}

		return retList;
	}

	private List<Integer> buildSortedBase(int goalSize, boolean global, Set<Integer> dropList) {
		/*
		 * Step 1, build the sizes of these ASes, sort them
		 */
		HashMap<Integer, Double> valueMap = new HashMap<Integer, Double>();
		for (DecoyAS tAS : this.activeTopology.valueCollection()) {
			valueMap.put(tAS.getASN(), 0.0);
		}
		for (DecoyAS tAS : this.activeTopology.valueCollection()) {
			if (global || tAS.isWardenAS()) {
				for (int tDestASN : this.theTopo.keySet()) {
					BGPPath tPath = tAS.getPath(tDestASN);
					if (tPath == null) {
						continue;
					}
					if (dropList != null && tPath.containsAnyOf(dropList)) {
						continue;
					}

					TIntIterator tIter = tPath.getPath().iterator();
					double ipSize = this.theTopo.get(tDestASN).parent.getIPCount();
					while (tIter.hasNext()) {
						int tHop = tIter.next();
						if (valueMap.containsKey(tHop)) {
							if (Constants.DEFAULT_ORDER_MODE == EconomicEngine.OrderMode.PathAppearance) {
								valueMap.put(tHop, valueMap.get(tHop) + 1);
							} else if (Constants.DEFAULT_ORDER_MODE == EconomicEngine.OrderMode.IPWeighted) {
								valueMap.put(tHop, valueMap.get(tHop) + ipSize);
							} else {
								throw new RuntimeException("Bad AS ordering mode!");
							}
						}
					}
				}
			}
		}

		/*
		 * Strip out the wardens, strip out people we're suppose to ignore
		 */
		for (DecoyAS tAS : this.activeTopology.valueCollection()) {
			if (tAS.isWardenAS()) {
				valueMap.remove(tAS.getASN());
			}
		}
		if (dropList != null) {
			for (int tAS : dropList) {
				valueMap.remove(tAS);
			}
		}

		List<ASRanker> rankList = new ArrayList<ASRanker>(valueMap.size());
		for (int tASN : valueMap.keySet()) {
			rankList.add(new ASRanker(tASN, valueMap.get(tASN)));
		}
		Collections.sort(rankList);

		List<Integer> retList = new ArrayList<Integer>();
		for (int counter = 0; counter < goalSize; counter++) {
			retList.add(rankList.get(rankList.size() - (counter + 1)).getASN());
		}

		return retList;
	}

	public void manageSortedWardenSim(int startCount, int endCount, int step, boolean coverage, boolean defection) {

		/*
		 * Don't need to log paths if we're doing a defection run
		 */
		if(defection){
			EconomicAgent.suppressPathLogging();
		}
		
		List<Integer> rankList = null;
		if (coverage) {
			rankList = this.buildSortedSetCoverage(endCount, false);
		} else {
			rankList = this.buildSortedBase(endCount, false, null);
		}

		/*
		 * Actually do the sim now
		 */
		for (int drCount = startCount; drCount <= endCount; drCount += step) {
			System.out.println("Starting processing for Decoy Count of: " + drCount);

			/*
			 * Write the size terminators to logging files
			 */
			try {
				this.wardenOut.write(EconomicEngine.SAMPLESIZE_TERMINATOR + "\n");
				this.transitOut.write(EconomicEngine.SAMPLESIZE_TERMINATOR + "\n");
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}

			/*
			 * Build decoy set for this round
			 */
			Set<Integer> drSet = new HashSet<Integer>();
			int listPos = 0;
			while (drSet.size() != drCount) {
				drSet.add(rankList.get(listPos));
				listPos++;
			}

			if (defection) {
				for (int tDefector : drSet) {
					/*
					 * Build the DR set less the defector
					 */
					Set<Integer> drSubset = new HashSet<Integer>();
					for (int tDeploy : drSet) {
						if (tDeploy == tDefector) {
							continue;
						}
						drSubset.add(tDeploy);
					}

					/*
					 * Actually sim the rounds
					 */
					for (int counter = 0; counter < 3; counter++) {
						this.trafficManger.runStat();
						String roundHeader = null;
						roundHeader = "" + drCount + "," + counter;
						this.driveEconomicTurn(roundHeader, drSubset, counter);
					}
				}
			} else {
				/*
				 * Actually do the sim rounds
				 */
				for (int counter = 0; counter < 3; counter++) {
					this.trafficManger.runStat();
					String roundHeader = null;
					roundHeader = "" + drCount + "," + counter;
					this.driveEconomicTurn(roundHeader, drSet, counter);
				}
			}
		}
	}

	public void manageRandomDeploySizeSim(int start, int end, int step, int trialCount, int minCCSize) {
		this.manageFixedNumberSim(start, end, step, trialCount, minCCSize, false);
	}

	public void manageCustConeExploration(int start, int end, int step, int trialCount, int deploySize) {
		for (int currentConeSize = start; currentConeSize <= end; currentConeSize += step) {
			this.manageFixedNumberSim(deploySize, deploySize, 1, trialCount, currentConeSize, true);
		}
	}

	/**
	 * 
	 * @param start
	 * @param end
	 * @param step
	 * @param trialCount
	 * @param trafficManager
	 * @param randomType
	 *            : only use the ASes whose ccNum is greater and equal to
	 *            randomType
	 */
	private void manageFixedNumberSim(int start, int end, int step, int trialCount, int minCCSize, boolean logMinCCSize) {
		ArrayList<Integer> validDecoyASes = new ArrayList<Integer>();

		for (DecoyAS tAS : this.activeTopology.valueCollection()) {
			if (tAS.getCustomerConeSize() >= minCCSize && !tAS.isWardenAS() && !tAS.isSuperAS()) {
				validDecoyASes.add(tAS.getASN());
			}
		}

		int numberOfTrials = (int) (Math.floor((end - start) / step) + 1);
		long sliceStartTime;
		int sliceCounter = 1;

		for (int drCount = start; drCount <= end; drCount += step) {
			sliceStartTime = System.currentTimeMillis();
			if (drCount > validDecoyASes.size()) {
				System.out.println("DR count exceeds total possible.");
				return;
			}
			System.out.println("Starting processing for Decoy Count of: " + drCount);
			int tenPercentMark = (int) Math.floor((double) trialCount / 10.0);
			int nextMark = tenPercentMark;
			/*
			 * Write the size terminators to logging files
			 */
			try {
				this.wardenOut.write(EconomicEngine.SAMPLESIZE_TERMINATOR + "\n");
				this.transitOut.write(EconomicEngine.SAMPLESIZE_TERMINATOR + "\n");
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}

			long time = System.currentTimeMillis();
			for (int trialNumber = 0; trialNumber < trialCount; trialNumber++) {
				/*
				 * Give progress reports
				 */
				if (trialNumber == nextMark) {
					long secondTime = System.currentTimeMillis();
					int tenFactor = nextMark / tenPercentMark;
					long estTime = (secondTime - time) * (10 - tenFactor) / 60000;
					System.out.println("" + tenFactor * 10 + "% complete, chunk took: " + (secondTime - time) / 60000
							+ " minutes, est remaining time for slice: " + estTime + " minutes based on this chunk");
					time = secondTime;
					nextMark += tenPercentMark;
				}

				/*
				 * Build decoy set for this round
				 */
				Set<Integer> drSet = new HashSet<Integer>();
				Collections.shuffle(validDecoyASes);
				for (int counter = 0; counter < drCount; counter++) {
					drSet.add(validDecoyASes.get(counter));
				}

				for (int counter = 0; counter < 3; counter++) {
					this.trafficManger.runStat();
					String roundHeader = null;
					if (logMinCCSize) {
						roundHeader = "" + minCCSize + "," + counter;
					} else {
						roundHeader = "" + drCount + "," + counter;
					}
					this.driveEconomicTurn(roundHeader, drSet, counter);
				}
			}

			long sliceEndTime = System.currentTimeMillis();
			long timeDelta = (sliceEndTime - sliceStartTime) / 3600000;
			long estRemaining = timeDelta * (numberOfTrials - sliceCounter);
			System.out.println("Slice completed in " + timeDelta + " hours, estimated remaining sim time: "
					+ estRemaining);
			sliceCounter++;
		}
	}

	private void driveEconomicTurn(String roundLeader, Set<Integer> drSet, int round) {
		/*
		 * Write the round terminators to logging files
		 */
		try {
			String terminator = null;
			if (round == 0) {
				terminator = EconomicEngine.SAMPLE_TERMINATOR;
			} else {
				terminator = EconomicEngine.ROUND_TERMINATOR;
			}
			this.wardenOut.write(terminator + roundLeader + "\n");
			this.transitOut.write(terminator + roundLeader + "\n");
			this.pathOut.write(terminator + roundLeader + "\n");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		this.resetForNewRound();
		this.runMoneyTransfer();

		/*
		 * Do money reporting
		 */
		for (int tASN : this.theTopo.keySet()) {
			this.theTopo.get(tASN).reportMoneyEarned(this.cashForThisRound.get(tASN));
		}

		/*
		 * Time to do a bit of logging....
		 */
		this.perfLogger.resetTimer();
		for (int tASN : this.theTopo.keySet()) {
			this.theTopo.get(tASN).doRoundLogging();
		}
		this.perfLogger.logTime("logging");

		/*
		 * Let the agents ponder their move
		 */
		for (int tASN : this.theTopo.keySet()) {
			this.makeAdjustmentHelper(tASN, drSet);
		}

		/*
		 * Have folks actually make their move
		 */
		for (int tASN : this.theTopo.keySet()) {
			this.theTopo.get(tASN).finalizeRoundAdjustments();
		}

		/*
		 * Do a fresh round of BGPProcessing
		 */
		BGPMaster.REPORT_TIME = false;
		BGPMaster.driveBGPProcessing(this.activeTopology);
	}

	public void endSim() {
		try {
			this.wardenOut.close();
			this.transitOut.close();
			this.pathOut.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private void runMoneyTransfer() {
		for (Integer tASN : this.theTopo.keySet()) {
			EconomicAgent tAgent = this.theTopo.get(tASN);
			for (int tNeighbor : tAgent.getNeighbors()) {

				int relation = 0;
				try {
					relation = tAgent.getRelationship(tNeighbor);
				} catch (IllegalArgumentException e) {
					if (tAgent.isPurged()) {
						relation = AS.CUSTOMER_CODE;
					} else if (this.theTopo.get(tNeighbor).isPurged()) {
						relation = AS.PROIVDER_CODE;
					} else {
						throw e;
					}
				}
				if (relation == AS.PEER_CODE) {
					continue;
				}

				double trafficVolume = tAgent.getTrafficOverLinkBetween(tNeighbor);
				if (relation == AS.PROIVDER_CODE) {
					this.updateCashForThisRound(tASN, trafficVolume);
				} else if (relation == AS.CUSTOMER_CODE) {
					this.updateCashForThisRound(tNeighbor, trafficVolume);
				} else {
					throw new RuntimeException("Invalid relationship passed to EconomicEngine: " + relation);
				}
			}
		}
	}

	private void resetForNewRound() {
		this.cashForThisRound.clear();
		for (int tASN : this.theTopo.keySet()) {
			this.cashForThisRound.put(tASN, 0.0);
		}
	}

	private void updateCashForThisRound(int asn, double amount) {
		this.cashForThisRound.put(asn, this.cashForThisRound.get(asn) + amount);
	}

	private void makeAdjustmentHelper(int asn, Set<Integer> decoySet) {
		/*
		 * If we're just a stub AS we don't do anything, consequently we don't
		 * need any info
		 */
		if (!this.activeTopology.containsKey(asn)) {
			this.theTopo.get(asn).makeAdustments(null);
			return;
		}

		/*
		 * If we're not a warden, do the actual DR computing, otherwise we don't
		 * need to push any added info
		 */
		if (!this.activeTopology.get(asn).isWardenAS()) {
			this.theTopo.get(asn).makeAdustments(decoySet);
		} else {
			this.theTopo.get(asn).makeAdustments(null);
		}
	}

}
