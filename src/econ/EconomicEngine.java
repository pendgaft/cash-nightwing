package econ;

import java.util.*;
import java.io.*;

import decoy.DecoyAS;

import sim.BGPMaster;
import sim.ParallelTrafficStat;
import topo.AS;

public class EconomicEngine {

	private HashMap<Integer, EconomicAgent> theTopo;
	private HashMap<Integer, DecoyAS> activeTopology;
	private HashMap<Integer, Double> cashForThisRound;

	private BufferedWriter wardenOut;
	private BufferedWriter transitOut;

	private static final double TRAFFIC_UNIT_TO_MBYTES = 1.0;
	private static final double COST_PER_MBYTE = 1.0;

	private static final String ROUND_TERMINATOR = "***";
	private static final String LOGGING_DIR = "nightwingLogs/";

	public EconomicEngine(HashMap<Integer, DecoyAS> activeMap, HashMap<Integer, DecoyAS> prunedMap) {
		this.theTopo = new HashMap<Integer, EconomicAgent>();
		this.cashForThisRound = new HashMap<Integer, Double>();
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
						TransitProvider.DECOY_STRAT.RAND));
			}
		}
	}

	public void manageFixedNumberSim(int start, int end, int step, int trialCount, ParallelTrafficStat trafficManager) {
		Random rng = new Random();
		int[] asArray = new int[this.activeTopology.keySet().size()];
		int arrayPos = 0;
		for(int tAS: this.activeTopology.keySet()){
			asArray[arrayPos] = tAS;
			arrayPos++;
		}
		
		for (int drCount = start; drCount <= end; drCount += step) {
			System.out.println("Starting processing for Decoy Count of: " + drCount);
			int tenPercentMark = (int)Math.floor((double)trialCount / 10.0);
			int nextMark = tenPercentMark;
			for (int trialNumber = 0; trialNumber < trialCount; trialNumber++) {
				/*
				 * Give progress reports
				 */
				if(trialNumber == nextMark){
					System.out.println("" + nextMark / tenPercentMark * 10 + "% complete");
					nextMark += tenPercentMark;
				}
				
				/*
				 * Build decoy set for this round
				 */
				Set<Integer> drSet = new HashSet<Integer>();
				while(drSet.size() != drCount){
					arrayPos = rng.nextInt(asArray.length);
					if(drSet.contains(asArray[arrayPos]) || this.activeTopology.get(asArray[arrayPos]).isWardenAS()){
						continue;
					}
					drSet.add(asArray[arrayPos]);
				}
				
				for (int counter = 0; counter < 3; counter++) {
					trafficManager.runStat(counter+1);
					this.driveEconomicTurn("" + drCount + "," + counter, drSet);
				}
			}
		}
	}

	private void driveEconomicTurn(String roundLeader, Object supData) {
		/*
		 * Write the round terminators to logging files
		 */
		try {
			this.wardenOut.write(EconomicEngine.ROUND_TERMINATOR + roundLeader + "\n");
			this.transitOut.write(EconomicEngine.ROUND_TERMINATOR + roundLeader + "\n");
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
				double scaleFactor = this.buildScaleFactor(tAgent, this.theTopo.get(tNeighbor), relation);
				double moneyFlow = trafficVolume * scaleFactor;
				if (relation == AS.PROIVDER_CODE) {
					this.updateCashForThisRound(tASN, moneyFlow);
					this.updateCashForThisRound(tNeighbor, moneyFlow * -1.0);
				} else if (relation == AS.CUSTOMER_CODE) {
					this.updateCashForThisRound(tASN, moneyFlow * -1.0);
					this.updateCashForThisRound(tNeighbor, moneyFlow);
				} else {
					throw new RuntimeException("Invalid relationship passed to EconomicEngine: " + relation);
				}
			}
		}
	}

	private double buildScaleFactor(EconomicAgent lhs, EconomicAgent rhs, int relation) {
		return EconomicEngine.TRAFFIC_UNIT_TO_MBYTES * EconomicEngine.COST_PER_MBYTE;
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

	@SuppressWarnings("unchecked")
	private void makeAdjustmentHelper(int asn, Object supData) {
		/*
		 * If we're just a stub AS we don't do anything, consequently we don't
		 * need any info
		 */
		if (!this.activeTopology.containsKey(asn)) {
			this.theTopo.get(asn).makeAdustments(null);
		}

		/*
		 * If we're not a warden, do the actual DR computing, otherwise we don't
		 * need to push any added info
		 */
		if (!this.activeTopology.get(asn).isWardenAS()) {
			Set<Integer> drSet = (Set<Integer>)supData;
			this.theTopo.get(asn).makeAdustments(new Boolean(drSet.contains(asn)));
		} else {
			this.theTopo.get(asn).makeAdustments(null);
		}
	}
}

