package econ;

import java.util.*;
import java.io.*;

import decoy.DecoyAS;

import topo.AS;

public class EconomicEngine {

	private HashMap<Integer, EconomicAgent> theTopo;
	private HashMap<Integer, Double> cashForThisRound;

	private BufferedWriter wardenOut;
	private BufferedWriter transitOut;

	private static final double TRAFFIC_UNIT_TO_MBYTES = 1.0;
	private static final double COST_PER_MBYTE = 1.0;

	public EconomicEngine(HashMap<Integer, DecoyAS> activeMap, HashMap<Integer, DecoyAS> prunedMap) {
		this.theTopo = new HashMap<Integer, EconomicAgent>();
		this.cashForThisRound = new HashMap<Integer, Double>();

		try {
			this.wardenOut = new BufferedWriter(new FileWriter("logs/warden.log"));
			this.transitOut = new BufferedWriter(new FileWriter("logs/transit.log"));
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
				this.theTopo.put(tAS.getASN(), new WardenAgent(tAS, this.wardenOut));
			} else {
				this.theTopo.put(tAS.getASN(), new TransitProvider(tAS, this.transitOut,
						TransitProvider.DECOY_STRAT.RAND));
			}
		}
	}

	public void driveEconomicTurn() {
		System.out.println("Starting econ part");
		this.resetForNewRound();
		this.runMoneyTransfer();

		/*
		 * Do money reporting
		 */
		for (int tASN : this.theTopo.keySet()) {
			this.theTopo.get(tASN).reportMoneyEarned(this.cashForThisRound.get(tASN));
		}

		/*
		 * Let the agents ponder thier move
		 */
		for (int tASN : this.theTopo.keySet()) {
			this.theTopo.get(tASN).makeAdustments();
		}

		/*
		 * Time to do a bit of logging....
		 */
		for (int tASN : this.theTopo.keySet()) {
			this.theTopo.get(tASN).doRoundLogging();
		}

		/*
		 * Have folks actually make their move
		 */
		for (int tASN : this.theTopo.keySet()) {
			this.theTopo.get(tASN).finalizeRoundAdjustments();
		}
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
					if(tAgent.isPurged()){
						relation = AS.CUSTOMER_CODE;
					}else if(this.theTopo.get(tNeighbor).isPurged()){
						relation = AS.PROIVDER_CODE;
					} else{
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

}
