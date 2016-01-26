package econ;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.TIntObjectMap;

import java.util.*;
import java.io.*;

import decoy.DecoyAS;
import logging.*;
import sim.BGPMaster;
import sim.Constants;
import sim.ParallelTrafficStat;
import sim.PerformanceLogger;
import topo.AS;
import topo.ASRanker;
import topo.BGPPath;

public class EconomicEngine {

	public enum OrderMode {
		PathAppearance, IPWeighted, TrafficWeighted;
	}

	private HashMap<Integer, EconomicAgent> theTopo;
	private TIntObjectMap<DecoyAS> activeTopology;
	private ParallelTrafficStat trafficManger;
	private HashMap<Integer, Double> revenueForThisRound;
	private HashMap<Integer, Double> profitForThisRound;

	private Writer wardenOut;
	private Writer transitOut;
	private Writer pathOut;
	private Writer linkOut;

	private double maxIPCount;

	private boolean parallelLogging = false;

	private static final String ROUND_TERMINATOR = "***";
	private static final String SAMPLE_TERMINATOR = "###";
	private static final String SAMPLESIZE_TERMINATOR = "&&&";

	private PerformanceLogger perfLogger = null;

	public EconomicEngine(TIntObjectMap<DecoyAS> activeMap, TIntObjectMap<DecoyAS> prunedMap,
			ParallelTrafficStat trafficManager, String loggingDir, PerformanceLogger perfLogger,
			boolean supressPathLogging, boolean parallelLogging, boolean turnOnLinkLogging) {
		this.theTopo = new HashMap<Integer, EconomicAgent>();
		this.revenueForThisRound = new HashMap<Integer, Double>();
		this.profitForThisRound = new HashMap<Integer, Double>();
		this.activeTopology = activeMap;
		this.trafficManger = trafficManager;
		this.parallelLogging = parallelLogging;

		try {
			if (this.parallelLogging) {
				this.wardenOut = new ThreadedWriter(loggingDir + "warden.log");
				this.transitOut = new ThreadedWriter(loggingDir + "transit.log");

				Thread wardenOutThread = new Thread((ThreadedWriter) this.wardenOut, "Warden Output Thread");
				Thread transitOutThread = new Thread((ThreadedWriter) this.transitOut, "Transit Output Thread");
				wardenOutThread.start();
				transitOutThread.start();
			} else {
				this.wardenOut = new BufferedWriter(new FileWriter(loggingDir + "warden.log"));
				this.transitOut = new BufferedWriter(new FileWriter(loggingDir + "transit.log"));
			}

			if (supressPathLogging) {
				this.pathOut = new NullOutputWriter();
			} else {
				if (this.parallelLogging) {
					this.pathOut = new ThreadedWriter(loggingDir + "path.log");
					Thread pathOutThread = new Thread((ThreadedWriter) this.pathOut, "Path Output Thread");
					pathOutThread.start();
				} else {
					this.pathOut = new BufferedWriter(new FileWriter(loggingDir + "path.log"));
				}
			}

			if (!turnOnLinkLogging) {
				this.linkOut = new NullOutputWriter();
			} else {
				if (this.parallelLogging) {
					this.linkOut = new ThreadedWriter(loggingDir + "link.log");
					Thread linkOutThread = new Thread((ThreadedWriter) this.linkOut, "Link Output Thread");
					linkOutThread.start();
				} else {
					this.linkOut = new BufferedWriter(new FileWriter(loggingDir + "link.log"));
				}
			}
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
				this.theTopo.put(tAS.getASN(),
						new WardenAgent(tAS, this.transitOut, this.wardenOut, activeMap, prunedMap, this.pathOut));
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
			double trafficFactorCC = 0.0;
			for (int tASN : tAgent.parent.getCustomerConeASList()) {
				ipCCSize += this.theTopo.get(tASN).parent.getIPCount();
				trafficFactorCC += this.theTopo.get(tASN).parent.getBaseTrafficFactor();
			}
			tAgent.parent.setCustomerIPCone(ipCCSize);
			tAgent.parent.setCusomterTrafficCone(trafficFactorCC);
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

	private Set<Integer> loadDictatedDeployerSet(String drFile) {
		Set<Integer> drSet = new HashSet<Integer>();
		Set<Integer> configSet = new HashSet<Integer>();

		/*
		 * Parse the config file to load up each rounds DR deployment
		 */
		try {
			BufferedReader drBuffer = new BufferedReader(new FileReader(drFile));

			while (drBuffer.ready()) {
				String pollStr = drBuffer.readLine().trim();
				if (pollStr.length() > 0) {
					int loadedASN = Integer.parseInt(pollStr);
					/*
					 * Only actually load in active ASes, as all of the pruned
					 * ASes will never be marked as deployers and it clutters up
					 * lying RP and slows BGP processing
					 */
					if (this.activeTopology.containsKey(loadedASN)) {
						configSet.add(Integer.parseInt(pollStr));
					}
				}
			}
			drBuffer.close();
		} catch (IOException e) {
			System.err.println("Error while parsing DR file.");
			e.printStackTrace();
			System.exit(-1);
		}

		/*
		 * Intersect the active topo and the config, sadly since there is trove
		 * vs java collections screwyness we can't just do a simple retainAll
		 */
		for (int tASN : configSet) {
			if (this.activeTopology.keySet().contains(tASN)) {
				drSet.add(tASN);
			}
		}
		System.out.println("Retained " + drSet.size() + " of " + configSet.size() + " deployers");
		return drSet;
	}

	public void manageHonestExploreSim(String deployerConfig) {
		Set<Integer> drSet = this.loadDictatedDeployerSet(deployerConfig);

		DecoyAS testAS = null;
		for (DecoyAS tAS : this.activeTopology.valueCollection()) {
			if (tAS.isWardenAS()) {
				testAS = tAS;
				break;
			}
		}

		if (testAS == null) {
			try {
				FileWriter fw = new FileWriter("/scratch/minerva2/schuch/lyingResult.txt", true);
				fw.write(testAS.getASN() + ",NIT,0\n");
				fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}

		testAS.toggleWardenAS(AS.AvoidMode.NONE, AS.ReversePoisonMode.NONE);
		this.driveEconomicRound(drSet, true);
		double baseProfit = this.sumDepProfit(drSet);

		testAS.toggleWardenAS(AS.AvoidMode.NONE, AS.ReversePoisonMode.LYING);
		this.driveEconomicRound(drSet, true);
		double lyingRedux = this.sumDepProfit(drSet) - baseProfit;

		if (lyingRedux == 0.0) {
			try {
				FileWriter fw = new FileWriter("/scratch/minerva2/schuch/lyingResult.txt", true);
				fw.write(testAS.getASN() + ",NaN," + testAS.getIPCount() + "\n");
				fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {

			testAS.toggleWardenAS(AS.AvoidMode.NONE, AS.ReversePoisonMode.HONEST);
			Set<Integer> candidates = testAS.getActiveNeighbors();
			Set<Integer> current = new HashSet<Integer>();

			HashMap<Integer, Double> reduceMap = new HashMap<Integer, Double>();
			double currentPain = 0.0;
			while (true) {
				int currBest = -1;
				double possNewBestPain = 0.0;

				for (int tASN : candidates) {
					if (reduceMap.containsKey(tASN)) {
						if (reduceMap.get(tASN) >= (possNewBestPain - currentPain)) {
							continue;
						}
					}

					Set<AS> tempSet = new HashSet<AS>();
					for (int curASN : current) {
						tempSet.add(this.activeTopology.get(curASN));
					}
					tempSet.add(this.activeTopology.get(tASN));

					testAS.updateHolepunchSet(tempSet);
					this.driveEconomicRound(drSet, true);
					double measureProfit = this.sumDepProfit(drSet);
					double newPain = measureProfit - baseProfit;
					reduceMap.put(tASN, newPain - currentPain);
					if (newPain < possNewBestPain) {
						possNewBestPain = newPain;
						currBest = tASN;
					}
				}

				System.out.println("done with pass " + currentPain + "," + possNewBestPain);
				if (currBest == -1) {
					break;
				} else if (Math.abs(possNewBestPain - currentPain) / Math.abs(currentPain) <= 0.15) {
					currentPain = possNewBestPain;
					break;
				} else if ((currentPain / lyingRedux) >= 0.9) {
					currentPain = possNewBestPain;
				} else {
					current.add(currBest);
					candidates.remove(currBest);
					currentPain = possNewBestPain;
					System.out.println(testAS.getASN() + "," + (currentPain / lyingRedux) + "," + testAS.getIPCount());
				}
			}

			try {
				FileWriter fw = new FileWriter("/scratch/minerva2/schuch/lyingResult.txt", true);
				System.out.println(
						"final " + testAS.getASN() + "," + (currentPain / lyingRedux) + "," + testAS.getIPCount());
				fw.write(testAS.getASN() + "," + (currentPain / lyingRedux) + "," + testAS.getIPCount() + "\n");
				fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private double sumDepProfit(Set<Integer> drSet) {
		double profitSum = 0.0;
		for (int tASN : drSet) {
			profitSum += this.revenueForThisRound.get(tASN);
		}
		return profitSum;
	}

	public void manageDictatedDRSim(String drFile, boolean defection, boolean partialDefection) {
		if (partialDefection && !defection) {
			throw new RuntimeException("can't have partial defection turned on without defection");
		}

		Set<Integer> drSet = this.loadDictatedDeployerSet(drFile);

		if (defection) {
			Set<Integer> skipSet = null;

			if (partialDefection) {
				Set<Integer> testSet = new HashSet<Integer>();
				for (int counter = 0; counter < 100; counter++) {
					int largestASN = -1;
					int largestDeg = -1;
					for (int tASN : this.activeTopology.keys()) {
						if (testSet.contains(tASN) || !drSet.contains(tASN)) {
							continue;
						}
						if (largestDeg < this.activeTopology.get(tASN).getDegree()) {
							largestDeg = this.activeTopology.get(tASN).getDegree();
							largestASN = tASN;
						}
					}
					testSet.add(largestASN);
				}

				List<Integer> traffic100 = this.buildSortedBase(100, true, null);
				for (int tASN : traffic100) {
					if (drSet.contains(tASN)) {
						testSet.add(tASN);
					}
				}
				skipSet = testSet;
			} else {
				skipSet = drSet;
			}

			for (int tASN : skipSet) {
				Set<Integer> subSet = new HashSet<Integer>();
				subSet.addAll(drSet);
				subSet.remove(tASN);
				this.driveEconomicRound(subSet, false);
			}
		} else {
			this.driveEconomicRound(drSet, false);
		}
	}

	// TODO merege this code with targeted code, as they are basically identical
	public void manageGlobalWardenSim(int startCount, int endCount, int step, boolean coverage, boolean defection) {

		System.out.println("Starting weighting list build.");
		this.perfLogger.resetTimer();
		List<Integer> rankList = null;
		if (coverage) {
			rankList = this.buildSortedSetCoverage(endCount, true);
		} else {
			rankList = this.buildSortedBase(endCount, true, null);
		}
		this.perfLogger.logTime("Weighting list build");
		System.out.println("Done with weighting list build.");

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
			while (drSet.size() != drCount && rankList.size() > listPos) {
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
						this.driveEconomicTurn(roundHeader, drSubset, counter, false);
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
					this.driveEconomicTurn(roundHeader, drSet, counter, false);
				}
			}
		}
	}

	private List<Integer> buildSortedSetCoverage(int size, boolean global) {

		List<Integer> retList = new ArrayList<Integer>();
		Set<Integer> usedSet = new HashSet<Integer>();
		for (int counter = 0; counter < size; counter++) {
			List<Integer> tmpList = this.buildSortedBase(1, global, usedSet);
			if (tmpList.size() > 0) {
				retList.add(tmpList.get(0));
				usedSet.add(tmpList.get(0));
			}
		}

		return retList;
	}

	// TODO make this parallel
	private List<Integer> buildSortedBase(int goalSize, boolean global, Set<Integer> dropList) {

		WeightingSlave[] slaveObjects = new WeightingSlave[Constants.NTHREADS];
		for (int counter = 0; counter < slaveObjects.length; counter++) {
			slaveObjects[counter] = new WeightingSlave(global, this.theTopo, dropList);
		}
		int posCount = 0;
		for (DecoyAS tAS : this.activeTopology.valueCollection()) {
			slaveObjects[posCount % Constants.NTHREADS].giveWork(tAS);
			posCount++;
		}

		Thread[] threadSlaves = new Thread[Constants.NTHREADS];
		for (int counter = 0; counter < threadSlaves.length; counter++) {
			threadSlaves[counter] = new Thread(slaveObjects[counter]);
			threadSlaves[counter].start();
		}

		try {
			for (Thread tThread : threadSlaves) {
				tThread.join();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(-2);
		}

		/*
		 * Merge results
		 */
		HashMap<Integer, Double> valueMap = new HashMap<Integer, Double>();
		for (WeightingSlave tSlave : slaveObjects) {
			HashMap<Integer, Double> tResult = tSlave.getResult();
			for (int tASN : tResult.keySet()) {
				if (!valueMap.containsKey(tASN)) {
					valueMap.put(tASN, 0.0);
				}
				valueMap.put(tASN, valueMap.get(tASN) + tResult.get(tASN));
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
		for (int counter = 0; counter < goalSize && rankList.size() > counter; counter++) {
			retList.add(rankList.get(rankList.size() - (counter + 1)).getASN());
		}

		return retList;
	}

	public void manageSortedWardenSim(int startCount, int endCount, int step, boolean coverage, boolean defection) {

		System.out.println("Starting build of weighting list");
		this.perfLogger.resetTimer();
		List<Integer> rankList = null;
		if (coverage) {
			rankList = this.buildSortedSetCoverage(endCount, false);
		} else {
			rankList = this.buildSortedBase(endCount, false, null);
		}
		this.perfLogger.logTime("weighting list build");
		System.out.println("Finished with build of weighting list");

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
			while (drSet.size() != drCount && rankList.size() > listPos) {
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
						this.driveEconomicTurn(roundHeader, drSubset, counter, false);
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
					this.driveEconomicTurn(roundHeader, drSet, counter, false);
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
	private void manageFixedNumberSim(int start, int end, int step, int trialCount, int minCCSize,
			boolean logMinCCSize) {
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
					this.driveEconomicTurn(roundHeader, drSet, counter, false);
				}
			}

			long sliceEndTime = System.currentTimeMillis();
			long timeDelta = (sliceEndTime - sliceStartTime) / 3600000;
			long estRemaining = timeDelta * (numberOfTrials - sliceCounter);
			System.out.println(
					"Slice completed in " + timeDelta + " hours, estimated remaining sim time: " + estRemaining);
			sliceCounter++;
		}
	}

	private void driveEconomicRound(Set<Integer> drSet, boolean skipLogging) {
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
			this.driveEconomicTurn(roundHeader, drSet, counter, skipLogging);
		}
	}

	private void driveEconomicTurn(String roundLeader, Set<Integer> drSet, int round, boolean skipLogging) {
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
			this.linkOut.write(terminator + roundLeader + "\n");
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
			this.theTopo.get(tASN).reportMoneyEarned(this.revenueForThisRound.get(tASN),
					this.profitForThisRound.get(tASN));
		}

		/*
		 * Time to do a bit of logging....
		 */
		if (!skipLogging) {
			this.perfLogger.resetTimer();
			this.handleLogging();
			this.perfLogger.logTime("logging");
		}

		/*
		 * Let the agents ponder their move
		 */
		for (int tASN : this.theTopo.keySet()) {
			this.makeAdjustmentHelper(tASN, drSet);
		}

		/*
		 * Have folks actually make their move
		 */
		FinalizeAdjustSlave[] tSlaves = new FinalizeAdjustSlave[Constants.NTHREADS];
		for (int counter = 0; counter < tSlaves.length; counter++) {
			tSlaves[counter] = new FinalizeAdjustSlave();
		}
		int pos = 0;
		for (int tASN : this.theTopo.keySet()) {
			tSlaves[pos % Constants.NTHREADS].addWork(this.theTopo.get(tASN));
			pos++;
		}
		Thread[] tThreads = new Thread[Constants.NTHREADS];
		for (int counter = 0; counter < tSlaves.length; counter++) {
			tThreads[counter] = new Thread(tSlaves[counter]);
		}
		for (Thread tThread : tThreads) {
			tThread.start();
		}
		for (Thread tThread : tThreads) {
			try {
				tThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-2);
			}
		}

		/*
		 * Do a fresh round of BGPProcessing
		 */
		BGPMaster.REPORT_TIME = false;
		BGPMaster.driveBGPProcessing(this.activeTopology);
	}

	private void handleLogging() {
		if (this.parallelLogging) {
			this.doParallelLogging();
		} else {
			this.doSerialLogging();
		}
	}

	private void doSerialLogging() {
		for (int tASN : this.theTopo.keySet()) {
			this.theTopo.get(tASN).doRoundLogging();
		}
	}

	private void doParallelLogging() {
		/*
		 * Build slave threads and hand out ASes
		 */
		LoggingSlave[] slaves = new LoggingSlave[Constants.NTHREADS];
		for (int counter = 0; counter < Constants.NTHREADS; counter++) {
			slaves[counter] = new LoggingSlave();
		}
		int pos = 0;
		for (int tASN : this.theTopo.keySet()) {
			slaves[pos % Constants.NTHREADS].giveNode(this.theTopo.get(tASN));
			pos++;
		}

		/*
		 * Start them
		 */
		Thread[] logThreads = new Thread[Constants.NTHREADS];
		for (int counter = 0; counter < Constants.NTHREADS; counter++) {
			logThreads[counter] = new Thread(slaves[counter]);
		}
		for (Thread tThread : logThreads) {
			tThread.start();
		}

		/*
		 * Wait for them to wrap up
		 */
		try {
			for (Thread tThread : logThreads) {
				tThread.join();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(-2);
		}
	}

	public void endSim() {
		try {
			this.wardenOut.close();
			this.transitOut.close();
			this.pathOut.close();
			this.linkOut.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private void runMoneyTransfer() {
		for (Integer tASN : this.theTopo.keySet()) {
			EconomicAgent tAgent = this.theTopo.get(tASN);
			for (int tNeighbor : tAgent.getNeighbors()) {
				double trafficVolume = tAgent.getTrafficOverLinkBetween(tNeighbor);
				try {
					this.linkOut.write(tASN + "," + tNeighbor + "," + trafficVolume + "\n");
				} catch (IOException e1) {
					e1.printStackTrace();
					System.exit(-1);
				}

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

				if (relation == AS.PROIVDER_CODE) {
					this.updateCashForThisRound(tASN, tNeighbor, trafficVolume);
				} else if (relation == AS.CUSTOMER_CODE) {
					this.updateCashForThisRound(tNeighbor, tASN, trafficVolume);
				} else {
					throw new RuntimeException("Invalid relationship passed to EconomicEngine: " + relation);
				}
			}
		}
	}

	private void resetForNewRound() {
		this.revenueForThisRound.clear();
		this.profitForThisRound.clear();
		for (int tASN : this.theTopo.keySet()) {
			this.revenueForThisRound.put(tASN, 0.0);
			this.profitForThisRound.put(tASN, 0.0);
		}
	}

	private void updateCashForThisRound(int provASN, int custASN, double amount) {
		this.revenueForThisRound.put(provASN, this.revenueForThisRound.get(provASN) + amount);
		this.profitForThisRound.put(provASN, this.profitForThisRound.get(provASN) + amount);
		this.profitForThisRound.put(custASN, this.profitForThisRound.get(custASN) - amount);
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
