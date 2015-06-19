package econ;

import gnu.trove.map.TIntObjectMap;

import java.io.*;
import java.util.*;

import sim.Constants;
import topo.AS;
import topo.BGPPath;
import decoy.DecoyAS;

public class WardenAgent extends EconomicAgent {

	private TIntObjectMap<DecoyAS> prunedTopo;

	public WardenAgent(DecoyAS parentObject, BufferedWriter log, TIntObjectMap<DecoyAS> activeTopo,
			TIntObjectMap<DecoyAS> prunedTopo, BufferedWriter pathLog) {
		super(parentObject, log, activeTopo, pathLog);
		if (!parentObject.isWardenAS()) {
			throw new IllegalArgumentException("Passed a non warden DecoyAS object to WardenAgent constructor.");
		}

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

		for (int tDest : this.activeTopo.keys()) {
			if (tDest == this.parent.getASN()) {
				continue;
			}

			BGPPath tPath = this.parent.getPath(tDest);
			if (tPath == null) {
				continue;
			}

			/*
			 * Log the actual path used by the resistor to each destination
			 */
			try {
				super.pathStream.write(super.parent.getASN() + ":" + tDest + "," + tPath.getLoggingString() + "\n");
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}

			int ipCount = this.activeTopo.get(tDest).getIPCount();
			if (!tPath.containsAnyOf(decoySet)) {
				cleanIPCount += ipCount;
				cleanASCount += 1;
			}
			totalIPCount += ipCount;
			totalASCount += 1;
		}

		for (int tDest : this.prunedTopo.keys()) {
			List<Integer> hookASNs = new ArrayList<Integer>();
			for (AS tParent : this.prunedTopo.get(tDest).getProviders()) {
				hookASNs.add(tParent.getASN());
			}
			BGPPath tPath = this.parent.getPathToPurged(hookASNs);
			if (tPath == null) {
				continue;
			}

			/*
			 * Log the path the warden uses to get to each destination
			 */
			try {
				super.pathStream.write(super.parent.getASN() + ":" + tDest + "," + tPath.getLoggingString() + "\n");
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
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
	public void makeAdustments(Set<Integer> decoyRouterSet) {
		/*
		 * Turns on decoy router avoidance code for this round with the known
		 * set of decoy routers as the avoidance set
		 */
		this.parent.turnOnActiveAvoidance(this.buildDecoySet());
	}

	public void reportMoneyEarned(double moneyEarned, double transitEarned) {
		/*
		 * Currently warden ASes don't care about money
		 */
	}
}
