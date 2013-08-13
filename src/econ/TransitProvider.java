package econ;

import java.io.*;
import java.util.Random;

import decoy.DecoyAS;

public class TransitProvider extends EconomicAgent {

	public enum DECOY_STRAT {
		RAND, NEVER
	}

	private double moneyEarned;
	private boolean turnOnDR;
	private TransitProvider.DECOY_STRAT myStrat;

	private static final double FLIPCHANCE = 0.05;
	private static Random rng = new Random();

	private static final boolean TESTING = true;

	public TransitProvider(DecoyAS parentAS, BufferedWriter log, TransitProvider.DECOY_STRAT strat) {
		super(parentAS, log);
		this.moneyEarned = 0.0;
		this.myStrat = strat;
		this.turnOnDR = false;
	}

	public void doRoundLogging() {
		try {
			this.logStream.write("" + this.parent.getASN() + "," + this.moneyEarned + "," + this.parent.isDecoy()
					+ "\n");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void finalizeRoundAdjustments() {
		this.parent.resetDecoyRouter();
		if (this.turnOnDR) {
			this.parent.toggleDecoyRouter();
		}
		this.turnOnDR = false;
	}

	public void makeAdustments() {
		if (TransitProvider.TESTING) {
			if (this.parent.getASN() == 2) {
				this.turnOnDR = true;
			} else {
				this.turnOnDR = false;
			}
			return;
		}

		if (this.myStrat == TransitProvider.DECOY_STRAT.RAND) {
			if (TransitProvider.rng.nextDouble() < TransitProvider.FLIPCHANCE) {
				this.turnOnDR = true;
			}
		} else if (this.myStrat == TransitProvider.DECOY_STRAT.NEVER) {
			this.turnOnDR = false;
		}
	}

	public void reportMoneyEarned(double moneyEarned) {
		this.moneyEarned = moneyEarned;
	}

}
