package econ;

import java.util.*;
import java.io.*;

import decoy.DecoyAS;

import sim.BGPMaster;
import sim.Constants;
import sim.ParallelTrafficStat;
import topo.AS;

public class EconomicEngine {

	private HashMap<Integer, EconomicAgent> theTopo;
	private HashMap<Integer, DecoyAS> activeTopology;
	private HashMap<Integer, Double> cashForThisRound;
	private HashMap<Integer, Double> transitCashForThisRound;

	private HashMap<Integer, Integer> tierMap;
	private HashMap<Integer, Long> tierScaleFactor;

	private BufferedWriter wardenOut;
	private BufferedWriter transitOut;

	private double maxIPCount;

	private static final double TRAFFIC_UNIT_TO_MBYTES = 1.0;
	private static final double COST_PER_MBYTE = 1.0;
	private static final double SCALE_FACTOR_POINT = 0.8;
	private static final double DISCOUNT = 0.75;

	private static final String ROUND_TERMINATOR = "***";
	private static final String SAMPLE_TERMINATOR = "###";
	private static final String SAMPLESIZE_TERMINATOR = "&&&";
	private static final String LOGGING_DIR = "nightwingLogs/";

	private static final boolean APPLY_FANCY_ECON = false;

	public EconomicEngine(HashMap<Integer, DecoyAS> activeMap, HashMap<Integer, DecoyAS> prunedMap) {
		this.theTopo = new HashMap<Integer, EconomicAgent>();
		this.cashForThisRound = new HashMap<Integer, Double>();
		this.transitCashForThisRound = new HashMap<Integer, Double>();
		this.activeTopology = activeMap;

		try {
			this.wardenOut = new BufferedWriter(new FileWriter(EconomicEngine.LOGGING_DIR + "warden.log"));
			this.transitOut = new BufferedWriter(new FileWriter(EconomicEngine.LOGGING_DIR + "/transit.log"));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		for (DecoyAS tAS : prunedMap.values()) {
			this.theTopo
					.put(tAS.getASN(), new TransitProvider(tAS, this.transitOut, TransitProvider.DECOY_STRAT.NEVER));
		}
		for (DecoyAS tAS : activeMap.values()) {
			if (tAS.isWardenAS()) {
				this.theTopo.put(tAS.getASN(), new WardenAgent(tAS, this.wardenOut, activeMap, prunedMap));
			} else {
				this.theTopo.put(tAS.getASN(), new TransitProvider(tAS, this.transitOut,
						TransitProvider.DECOY_STRAT.DICTATED));
			}
		}

		this.calculateCustomerCone();
		this.setupPricingTiers();
		this.maxIPCount = 0.0;
		for (DecoyAS tAS : this.activeTopology.values()) {
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
			this.buildIndividualCustomerCone(tAgent.parent);

			long ipCCSize = 0;
			for (int tASN : tAgent.parent.getCustomerConeASList()) {
				ipCCSize += this.theTopo.get(tASN).parent.getIPCount();
			}
			tAgent.parent.setCustomerIPCone(ipCCSize);
		}
		if (Constants.DEBUG) {
			for (DecoyAS tAS : this.activeTopology.values()) {
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
	private void buildIndividualCustomerCone(AS currentAS) {

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
			this.buildIndividualCustomerCone(nextAS);
			for (int tASN : nextAS.getCustomerConeASList()) {
				currentAS.addOnCustomerConeList(tASN);
			}
		}

		/* count itself */
		currentAS.addOnCustomerConeList(currentAS.getASN());
	}

	private void setupPricingTiers() {
		this.tierMap = new HashMap<Integer, Integer>();
		this.tierScaleFactor = new HashMap<Integer, Long>();

		List<Integer> ccSizes = new ArrayList<Integer>(this.activeTopology.size());
		List<Long> noCustSizes = new ArrayList<Long>(this.theTopo.size() - this.activeTopology.size());
		for (int tASN : this.theTopo.keySet()) {
			if (!this.activeTopology.containsKey(tASN)) {
				this.tierMap.put(tASN, 0);
				noCustSizes.add(this.theTopo.get(tASN).parent.getIPCustomerCone());
				continue;
			}

			boolean placed = false;
			long mySize = this.theTopo.get(tASN).parent.getIPCustomerCone();
			for (int counter = 0; counter < ccSizes.size(); counter++) {
				if (this.theTopo.get(ccSizes.get(counter)).parent.getIPCustomerCone() > mySize) {
					ccSizes.add(counter, tASN);
					placed = true;
					break;
				}
			}
			if (!placed) {
				ccSizes.add(tASN);
			}
		}

		int quarter = (int) Math.ceil((double) ccSizes.size() / 4.0);
		for (int currentTier = 1; currentTier < 5; currentTier++) {
			for (int counter = 0; counter < quarter; counter++) {
				if (counter + (currentTier - 1) * quarter >= ccSizes.size()) {
					break;
				}
				this.tierMap.put(ccSizes.get(counter + (currentTier - 1) * quarter), currentTier);
			}
		}

		/*
		 * Build the tier scale factor points once
		 */
		Collections.sort(noCustSizes);
		this.tierScaleFactor.put(0,
				noCustSizes.get((int) Math.floor(noCustSizes.size() * EconomicEngine.SCALE_FACTOR_POINT)));
		for (int currentTier = 1; currentTier < 5; currentTier++) {
			int startPoint = quarter * (currentTier - 1);
			int range = Math.min(quarter, ccSizes.size() - startPoint);
			this.tierScaleFactor.put(
					currentTier,
					this.theTopo.get(ccSizes.get((int) Math.floor(startPoint + (double) range
							* EconomicEngine.SCALE_FACTOR_POINT))).parent.getIPCustomerCone());
		}
	}

	public void manageCustConeExploration(int start, int end, int step, int trialCount, int deploySize,
			ParallelTrafficStat trafficManager) {
		for (int currentConeSize = start; currentConeSize <= end; currentConeSize += step) {
			this.manageFixedNumberSim(deploySize, deploySize, 1, trialCount, currentConeSize, trafficManager, true);
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
	public void manageFixedNumberSim(int start, int end, int step, int trialCount, int minCCSize,
			ParallelTrafficStat trafficManager, boolean logMinCCSize) {
		Random rng = new Random();
		ArrayList<Integer> validDecoyASes = new ArrayList<Integer>();

		for (DecoyAS tAS : this.activeTopology.values()) {
			if (tAS.getCustomerConeSize() >= minCCSize) {
				validDecoyASes.add(tAS.getASN());
			}
		}

		for (int drCount = start; drCount <= end; drCount += step) {
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
					System.out.println("" + nextMark / tenPercentMark * 10 + "% complete (chunk took: "
							+ (secondTime - time) / 60000 + " minutes)");
					time = secondTime;
					nextMark += tenPercentMark;
				}

				/*
				 * Build decoy set for this round
				 */
				Set<Integer> drSet = new HashSet<Integer>();
				while (drSet.size() != drCount) {
					int arrayPos = rng.nextInt(validDecoyASes.size());
					int testAS = validDecoyASes.get(arrayPos);
					if (drSet.contains(testAS) || this.activeTopology.get(testAS).isWardenAS()) {
						continue;
					}
					drSet.add(testAS);
				}

				for (int counter = 0; counter < 3; counter++) {
					trafficManager.runStat();
					String roundHeader = null;
					if(logMinCCSize){
						roundHeader = "" + minCCSize + "," + counter;
					}else{
						roundHeader = "" + drCount + "," + counter;
					}
					this.driveEconomicTurn(roundHeader, drSet, counter);
				}
			}
		}
	}

	private void driveEconomicTurn(String roundLeader, Object supData, int round) {
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
			this.theTopo.get(tASN).reportMoneyEarned(this.cashForThisRound.get(tASN),
					this.transitCashForThisRound.get(tASN));
		}

		/*
		 * Time to do a bit of logging....
		 */
		for (int tASN : this.theTopo.keySet()) {
			this.theTopo.get(tASN).doRoundLogging();
		}

		/*
		 * Let the agents ponder their move
		 */
		for (int tASN : this.theTopo.keySet()) {
			this.makeAdjustmentHelper(tASN, supData);
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
		BGPMaster.driveBGPProcessing(this.activeTopology);
	}

	public void endSim() {
		try {
			this.wardenOut.close();
			this.transitOut.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	// TODO populate transit cash
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

				if (EconomicEngine.APPLY_FANCY_ECON) {
					double trafficVolume = tAgent.getTrafficOverLinkBetween(tNeighbor);
					double transitTraffic = tAgent.getTransitTrafficOverLink(tNeighbor);
					double adjustedTransitTraffic = transitTraffic - tAgent.getDeliveryTrafficOverLink(tNeighbor);
					double scaleFactor = this.buildScaleFactor(tAgent, this.theTopo.get(tNeighbor), relation);
					double moneyFlow = trafficVolume * scaleFactor;
					if (relation == AS.PROIVDER_CODE) {
						this.updateCashForThisRound(tASN, moneyFlow, transitTraffic * scaleFactor);
						this.updateCashForThisRound(tNeighbor, moneyFlow * -1.0, adjustedTransitTraffic * scaleFactor
								* -1.0);
					} else if (relation == AS.CUSTOMER_CODE) {
						this.updateCashForThisRound(tASN, moneyFlow * -1.0, transitTraffic * scaleFactor * -1.0);
						this.updateCashForThisRound(tNeighbor, moneyFlow, adjustedTransitTraffic * scaleFactor);
					} else {
						throw new RuntimeException("Invalid relationship passed to EconomicEngine: " + relation);
					}
				}else{
					double trafficVolume = tAgent.getTrafficOverLinkBetween(tNeighbor);
					double transitTraffic = tAgent.getTransitTrafficOverLink(tNeighbor);
					if (relation == AS.PROIVDER_CODE) {
						this.updateCashForThisRound(tASN, trafficVolume, transitTraffic);
					} else if (relation == AS.CUSTOMER_CODE) {
						this.updateCashForThisRound(tNeighbor, trafficVolume, transitTraffic);
					} else {
						throw new RuntimeException("Invalid relationship passed to EconomicEngine: " + relation);
					}
				}
			}
		}
	}

	private double buildScaleFactor(EconomicAgent sendingAS, EconomicAgent recievingAS, int relation) {

		int payingASN;
		if (relation == AS.CUSTOMER_CODE) {
			payingASN = sendingAS.parent.getASN();
		} else {
			payingASN = recievingAS.parent.getASN();
		}

		int payingTier = this.tierMap.get(payingASN);
		double ipScale = 5.0 - (double) payingTier;
		double myDiscount = Math.min(EconomicEngine.DISCOUNT * this.theTopo.get(payingASN).parent.getIPCustomerCone()
				/ this.tierScaleFactor.get(payingTier), EconomicEngine.DISCOUNT);
		return (ipScale - myDiscount) * EconomicEngine.TRAFFIC_UNIT_TO_MBYTES * EconomicEngine.COST_PER_MBYTE;
	}

	private void resetForNewRound() {
		this.cashForThisRound.clear();
		this.transitCashForThisRound.clear();
		for (int tASN : this.theTopo.keySet()) {
			this.cashForThisRound.put(tASN, 0.0);
			this.transitCashForThisRound.put(tASN, 0.0);
		}
	}

	private void updateCashForThisRound(int asn, double amount, double transitCash) {
		this.cashForThisRound.put(asn, this.cashForThisRound.get(asn) + amount);
		this.transitCashForThisRound.put(asn, this.transitCashForThisRound.get(asn) + transitCash);
	}

	@SuppressWarnings("unchecked")
	private void makeAdjustmentHelper(int asn, Object supData) {
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
			Set<Integer> drSet = (Set<Integer>) supData;
			this.theTopo.get(asn).makeAdustments(new Boolean(drSet.contains(asn)));
		} else {
			this.theTopo.get(asn).makeAdustments(null);
		}
	}
}
