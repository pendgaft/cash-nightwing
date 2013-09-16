package sim;

import java.util.*;
import java.io.*;

import topo.AS;
import topo.SerializationMaster;

/*import decoy.DecoyAS;
 import decoy.Rings;*/
import decoy.*;
import econ.EconomicEngine;

public class Nightwing {

	private static final String FIND_STRING = "find";
	private static final int FIND_MODE = 1;
	private static final String REPEAT_STRING = "repeat";
	private static final int REPEAT_MODE = 2;
	private static final String ASYM_STRING = "asym";
	private static final int ASYM_MODE = 3;
	private static final String RING_STRING = "ring";
	private static final int RING_MODE = 5;
	private static final int TRAFFICSTAT1_MODE = 6;
	private static final String TRAFFICSTAT1_STRING = "trafficStat1";
	private static final int ECON_PHASE_1 = 7;
	private static final String ECON_P1_STRING = "econ1";

	public static void main(String[] args) throws IOException {

		long startTime, endTime;
		startTime = System.currentTimeMillis();

		if (args.length < 2) {
			System.out.println("Usage: ./Nightwing <mode> <wardenFile> <mode specific configs....>");
			return;
		}

		/*
		 * Figure out mode that we're running
		 */
		int mode = 0;
		String wardenFile = args[1];
		if (args[0].equalsIgnoreCase(Nightwing.FIND_STRING)) {
			mode = Nightwing.FIND_MODE;

		} else if (args[0].equalsIgnoreCase(Nightwing.REPEAT_STRING)) {
			mode = Nightwing.REPEAT_MODE;
		} else if (args[0].equalsIgnoreCase(Nightwing.ASYM_STRING)) {
			mode = Nightwing.ASYM_MODE;
		} else if (args[0].equalsIgnoreCase(Nightwing.RING_STRING)) {
			mode = Nightwing.RING_MODE;
		} else if (args[0].equalsIgnoreCase(Nightwing.TRAFFICSTAT1_STRING)) {
			mode = Nightwing.TRAFFICSTAT1_MODE;
			if (args.length != 3) {
				System.out.println("Traffic mode usage: ./Nightwing <mode> <warden file> <traffic split file>");
				return;
			}
		} else if (args[0].equalsIgnoreCase(Nightwing.ECON_P1_STRING)) {
			mode = Nightwing.ECON_PHASE_1;
			if (args.length != 6) {
				System.out.println("Economic mode usage: ./Nightwing <mode> <warden file> <traffic split file> <starting decoy count> <ending decoy count> <step size>");
				return;
			}
		} else {
			System.out.println("bad mode: " + args[0]);
			System.exit(-1);
		}
		System.out.println("Mode: " + args[0] + " on warden country " + wardenFile + " looks good, building topo.");

		HashMap<Integer, DecoyAS> liveTopo = null;
		HashMap<Integer, DecoyAS> prunedTopo = null;

		/*
		 * Serialization control
		 */
		SerializationMaster serialControl = new SerializationMaster(wardenFile);
		if (serialControl.hasValidBGPSerialFile()) {
			System.out.println("Valid serial file exists, skipping fresh build.");
			HashMap<Integer, DecoyAS>[] topoArray = BGPMaster.buildASObjectsOnly(wardenFile);
			liveTopo = topoArray[0];
			prunedTopo = topoArray[1];
			serialControl.loadBGPSerialFile(liveTopo);
			System.out.println("Topology loaded from serial file.");
		} else {
			System.out.println("No serial file exists, building topology from scratch.");
			HashMap<Integer, DecoyAS>[] topoArray = BGPMaster.buildBGPConnection(wardenFile);
			liveTopo = topoArray[0];
			prunedTopo = topoArray[1];
			System.out.println("Topo built and BGP converged.");
			serialControl.buildBGPSerialFile(liveTopo);
			System.out.println("Topology saved to serial file.");
		}
		
		if (Constants.DEBUG) {
			System.out.println("liveTopo:");
			for (AS tAS : liveTopo.values()) {
				System.out.println(tAS.getASN());
			}
			System.out.println("prunedTopo:");
			for (AS tAS : prunedTopo.values()) {
				System.out.println(tAS.getASN());
			}

			System.out.println("live ip count:");
			for (AS tAS : liveTopo.values())
				System.out.println(tAS.getASN() + ", " + tAS.getIPCount());
			System.out.println("pruned ip count:");
			for (AS tAS : prunedTopo.values())
				System.out.println(tAS.getASN() + ", " + tAS.getIPCount());
			
			System.out.println("routing tables:");
			for(AS tAS: liveTopo.values()){
				System.out.println(tAS.printDebugString());
			}
		}

		/*
		 * Run the correct mode
		 */
		if (mode == Nightwing.FIND_MODE) {
			FindSim simDriver = new FindSim(liveTopo, prunedTopo);

			simDriver.run(wardenFile + "-decoy-hunt-random.csv");
			simDriver.runLargeASOnlyTests(true, wardenFile + "-decoy-hunt-single.csv");
			simDriver.runLargeASOnlyTests(false, wardenFile + "-decoy-hunt-nlargest.csv");

			Rings ringDriver = new Rings(liveTopo, prunedTopo);
			ringDriver.runTests(wardenFile);
			simDriver.runRings(wardenFile);
			// simDriver.printResults();
		} else if (mode == Nightwing.REPEAT_MODE) {
			System.out.println("NOT IMPLEMENTED YET");
			System.exit(-2);
		} else if (mode == Nightwing.ASYM_MODE) {
			PathAsym simDriver = new PathAsym(liveTopo, prunedTopo);
			simDriver.buildPathSymCDF();
		} else if (mode == Nightwing.RING_MODE) {
			Rings simDriver = new Rings(liveTopo, prunedTopo);
			simDriver.runTests(wardenFile);
		} else if (mode == Nightwing.TRAFFICSTAT1_MODE) {
			ParallelTrafficStat statInParallel = new ParallelTrafficStat(liveTopo, prunedTopo, args[2]);
			statInParallel.runStat();
		} else if (mode == Nightwing.ECON_PHASE_1) {
			/*
			 * Build the traffic stat object, and then actually flow the traffic
			 * through the network
			 */
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, args[2]);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo);
			/*
			 * Do the actual rounds of simulation
			 */
			econEngine.manageFixedNumberSim(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer
					.parseInt(args[5]), Constants.SAMPLE_COUNT, trafficStat);
			econEngine.endSim();
		} else {
			System.out.println("mode fucked up, wtf.... " + mode);
			System.exit(-2);
		}

		endTime = System.currentTimeMillis();
		System.out.println("\nAll work done, this took: " + (endTime - startTime) / 60000 + " minutes.");

	}
}

