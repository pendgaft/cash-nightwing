package econ;

import java.io.*;

import decoy.DecoyAS;

public class WardenAgent extends EconomicAgent {

	
	public WardenAgent(DecoyAS parentObject, BufferedWriter log){
		super(parentObject, log);
		if(!parentObject.isWardenAS()){
			throw new IllegalArgumentException("Passed a non warden DecoyAS object to WardenAgent constructor.");
		}
	}
	
	@Override
	public void doRoundLogging() {
		// TODO Log the number of dirty paths we're using

	}

	@Override
	public void finalizeRoundAdjustments() {
		// TODO route around as many decoys as we can find

	}

	@Override
	public void makeAdustments() {
		// TODO do hunting for decoy routers here

	}

	public void reportMoneyEarned(double moneyEarned) {
		/*
		 * Currently warden ASes don't care about money
		 */
	}



}
