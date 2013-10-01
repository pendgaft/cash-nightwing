package econ;

import java.io.*;
import java.util.*;

import sim.Constants;
import topo.AS;
import topo.BGPPath;

import decoy.DecoyAS;

public class WardenAgent extends EconomicAgent {

	private HashMap<Integer, DecoyAS> activeTopo;
	private HashMap<Integer, DecoyAS> prunedTopo;

	public WardenAgent(DecoyAS parentObject, BufferedWriter log, HashMap<Integer, DecoyAS> activeTopo,
			HashMap<Integer, DecoyAS> prunedTopo) {
		super(parentObject, log);
		if (!parentObject.isWardenAS()) {
			throw new IllegalArgumentException("Passed a non warden DecoyAS object to WardenAgent constructor.");
		}

		this.activeTopo = activeTopo;
		this.prunedTopo = prunedTopo;
	}

	/**
	 * Current logging logs the fraction of IP ADDRESS SPACE that the warden has
	 * a clean path to followed by the fraction of the AS SPACE that the warden
	 * has a clean path to
	 */
	@Override
	public void doRoundLogging() {
		Set<Integer> decoySet = this.buildDecoySet();
		double totalIPCount = 0.0;
		double cleanIPCount = 0.0;
		double totalASCount = 0.0;
		double cleanASCount = 0.0;
		int nullCount = 0;

		for (int tDest : this.activeTopo.keySet()) {
			if (tDest == this.parent.getASN()) {
				continue;
			}

			BGPPath tPath = this.parent.getPath(tDest);
			if (tPath == null) {
				nullCount++;
				continue;
			}
			int ipCount = this.activeTopo.get(tDest).getIPCount();
			if (!tPath.containsAnyOf(decoySet)) {
				cleanIPCount += ipCount;
				cleanASCount += 1;
			}
			totalIPCount += ipCount;
			totalASCount += 1;
		}

		for (int tDest : this.prunedTopo.keySet()) {
			List<Integer> hookASNs = new ArrayList<Integer>();
			for (AS tParent : this.prunedTopo.get(tDest).getProviders()) {
				hookASNs.add(tParent.getASN());
			}
			BGPPath tPath = this.parent.getPathToPurged(hookASNs);
			if (tPath == null) {
				continue;
			}
			int ipCount = this.prunedTopo.get(tDest).getIPCount();
			if (!tPath.containsAnyOf(decoySet)) {
				cleanIPCount += ipCount;
				cleanASCount += 1;
			}
			totalIPCount += ipCount;
			totalASCount += 1;
		}

		try {
			this.logStream.write("" + this.parent.getASN() + "," + cleanIPCount / totalIPCount + "," + cleanASCount
					/ totalASCount + "\n");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	public void finalizeRoundAdjustments() {
		this.parent.rescanBGPTable();
	}

	@Override
	public void makeAdustments(Object supplementalInfo) {
		/*
		 * Turns on decoy router avoidance code for this round with the known
		 * set of decoy routers as the avoidance set
		 */
		this.parent.turnOnActiveAvoidance(this.buildDecoySet(), Constants.DEFAULT_AVOID_MODE);
	}

	public void reportMoneyEarned(double moneyEarned) {
		/*
		 * Currently warden ASes don't care about money
		 */
	}

	private Set<Integer> buildDecoySet() {
		HashSet<Integer> decoySet = new HashSet<Integer>();
		for (DecoyAS tAS : this.activeTopo.values()) {
			if (tAS.isDecoy()) {
				decoySet.add(tAS.getASN());
			}
		}
		return decoySet;
	}
}
