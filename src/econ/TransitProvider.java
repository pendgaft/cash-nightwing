package econ;

import gnu.trove.map.TIntObjectMap;

import java.io.*;
import java.util.*;

import sim.Constants;
import topo.BGPPath;
import decoy.DecoyAS;

public class TransitProvider extends EconomicAgent {

	public enum DECOY_STRAT {
		RAND, NEVER, DICTATED
	}

	private boolean flipDecoyState;
	private int lockIn;
	private TransitProvider.DECOY_STRAT myStrat;

	private static double FLIPCHANCE = 0.00;
	private static Random rng = new Random();

	public static void setFlipChance(double chance) {
		TransitProvider.FLIPCHANCE = chance;
	}

	public TransitProvider(DecoyAS parentAS, BufferedWriter revLog, TIntObjectMap<DecoyAS> activeTopo,
			TransitProvider.DECOY_STRAT strat, BufferedWriter pathLog) {
		super(parentAS, revLog, activeTopo, pathLog);
		this.moneyEarned = 0.0;
		this.myStrat = strat;
		this.flipDecoyState = false;
		this.lockIn = 0;
	}

	public void doRoundLogging() {
		try {
			this.revenueLogStream.write("" + this.parent.getASN() + "," + this.moneyEarned + ","
					+ this.parent.isDecoy() + ",false\n");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		for (DecoyAS tAS : super.activeTopo.valueCollection()) {
			if (tAS.isWardenAS()) {
				BGPPath tPath = super.parent.getPath(tAS.getASN());
				if (tPath != null && EconomicAgent.LOG_PATHS) {
					try {
						super.pathStream.write(super.parent.getASN() + ":" + tAS.getASN() + ","
								+ tPath.getLoggingString() + "\n");
					} catch (IOException e) {
						e.printStackTrace();
						System.exit(-1);
					}
				}
			}
		}
	}

	public void finalizeRoundAdjustments() {

		if (this.flipDecoyState) {
			if (!this.parent.isDecoy()) {
				this.parent.toggleDecoyRouter();
			} else {
				this.parent.resetDecoyRouter();
			}
		}
		this.flipDecoyState = false;
	}

	public void makeAdustments(Set<Integer> decoyRouterSet) {
		if (this.lockIn > 0) {
			this.lockIn--;
			if (this.lockIn == 0 && this.parent.isDecoy()) {
				this.flipDecoyState = true;
			}
			return;
		}

		/*
		 * Testing code
		 */
		if (Constants.ECON_TESTING) {
			if (this.parent.getASN() == 2) {
				this.flipDecoyState = true;
			} else {
				this.flipDecoyState = false;
			}

			this.lockIn = 2;
			return;
		}

		/*
		 * We're not locked in, consider flipping DR state
		 */
		if (this.myStrat == TransitProvider.DECOY_STRAT.RAND) {
			if (TransitProvider.rng.nextDouble() < TransitProvider.FLIPCHANCE) {
				this.flipDecoyState = true;
			}
			this.lockIn = 2;
		} else if (this.myStrat == TransitProvider.DECOY_STRAT.NEVER) {
			this.flipDecoyState = false;
		} else if (this.myStrat == TransitProvider.DECOY_STRAT.DICTATED) {
			if (decoyRouterSet.contains(this.parent.getASN())) {
				this.flipDecoyState = true;
			}
			this.lockIn = 2;
		}
	}
}
