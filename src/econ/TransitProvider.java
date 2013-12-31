package econ;

import java.io.*;
import java.util.*;

import sim.Constants;

import decoy.DecoyAS;

public class TransitProvider extends EconomicAgent {

	public enum DECOY_STRAT {
		RAND, NEVER, DICTATED
	}

	private double moneyEarned;
	private double transitIncome;
	private boolean flipDecoyState;
	private int lockIn;
	private TransitProvider.DECOY_STRAT myStrat;

	private static double FLIPCHANCE = 0.00;
	private static Random rng = new Random();

	public static void setFlipChance(double chance) {
		TransitProvider.FLIPCHANCE = chance;
	}

	public TransitProvider(DecoyAS parentAS, BufferedWriter log, HashMap<Integer, DecoyAS> activeTopo,
			TransitProvider.DECOY_STRAT strat) {
		super(parentAS, log, activeTopo);
		this.moneyEarned = 0.0;
		this.transitIncome = 0.0;
		this.myStrat = strat;
		this.flipDecoyState = false;
		this.lockIn = 0;
	}

	public void doRoundLogging() {
		try {
			this.logStream.write("" + this.parent.getASN() + ","
					+ this.moneyEarned + "," + this.transitIncome + ","
					+ this.parent.isDecoy() + "\n");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
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
		
		/*
		 * The only case in which non-warden ASes need to scan their BGP tables
		 * is if we're doing reverse poisoning
		 */
		if (Constants.REVERSE_POISON) {
			this.parent.rescanBGPTable();
		}
	}

	public void makeAdustments(Set<Integer> decoyRouterSet) {
		if(Constants.REVERSE_POISON){
			this.parent.turnOnActiveAvoidance(this.buildDecoySet(), Constants.DEFAULT_POISON_MODE);
		}
		
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
			if(decoyRouterSet.contains(this.parent.getASN())){
				this.flipDecoyState = true;
			}
			this.lockIn = 2;
		}
	}

	public void reportMoneyEarned(double moneyEarned, double transitEarned) {
		this.moneyEarned = moneyEarned;
		this.transitIncome = transitEarned;
	}

}
