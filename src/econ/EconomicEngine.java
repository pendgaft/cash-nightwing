package econ;

import java.util.*;

import topo.AS;
import decoy.DecoyAS;

public class EconomicEngine {

	private HashMap<Integer, EconomicAgent> theTopo;
	private HashMap<Integer, Double> cashForThisRound;

	public EconomicEngine(HashMap<Integer, DecoyAS> activeTopo, HashMap<Integer, DecoyAS> prunedTopo) {

	}

	public EconomicEngine(HashMap<Integer, EconomicAgent> fullTopo) {
		this.theTopo = fullTopo;
	}

	public void driveEconomicTurn() {
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

	private void runMoneyTransfer() {
		for (Integer tASN : this.theTopo.keySet()) {
			EconomicAgent tAgent = this.theTopo.get(tASN);
			for (int tNeighbor : tAgent.getNeighbors()) {
				
				int relation = tAgent.getRelationship(tNeighbor);
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
		return 1.0;
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
