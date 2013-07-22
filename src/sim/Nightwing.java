package sim;

import java.util.*;
import java.io.*;

import topo.AS;

/*import decoy.DecoyAS;
 import decoy.Rings;*/
import decoy.*;

public class Nightwing {

	private static final String FIND_STRING = "find";
	private static final int FIND_MODE = 1;
	private static final String REPEAT_STRING = "repeat";
	private static final int REPEAT_MODE = 2;
	private static final String ASYM_STRING = "asym";
	private static final int ASYM_MODE = 3;
	private static final String ACTIVE_AVOID_STRING = "avoid";
	private static final int ACTIVE_MODE = 4;
	private static final String RING_STRING = "ring";
	private static final int RING_MODE = 5;
	private static final int TRAFFICSTAT1_MODE = 6;
	private static final String TRAFFICSTAT1_STRING = "trafficStat1";

	public static void main(String[] args) throws IOException {
		/*
		 * Figure out mode that we're running
		 */
		int mode = 0;
		int avoidSize = 0;
		String wardenFile = args[1];
		if (args[0].equalsIgnoreCase(Nightwing.FIND_STRING)) {
			mode = Nightwing.FIND_MODE;

		} else if (args[0].equalsIgnoreCase(Nightwing.REPEAT_STRING)) {
			mode = Nightwing.REPEAT_MODE;
		} else if (args[0].equalsIgnoreCase(Nightwing.ASYM_STRING)) {
			mode = Nightwing.ASYM_MODE;
		} else if (args[0].equalsIgnoreCase(Nightwing.ACTIVE_AVOID_STRING)) {
			mode = Nightwing.ACTIVE_MODE;
			avoidSize = Integer.parseInt(args[1]);
		} else if (args[0].equalsIgnoreCase(Nightwing.RING_STRING)) {
			mode = Nightwing.RING_MODE;
		} else if (args[0].equalsIgnoreCase(Nightwing.TRAFFICSTAT1_STRING)) {
			mode = Nightwing.TRAFFICSTAT1_MODE;
		} else {
			System.out.println("bad mode: " + args[0]);
			System.exit(-1);
		}
		System.out.println("Mode: " + args[0] + " on warden country " + wardenFile + " looks good, building topo.");

		// HashMap<Integer, DecoyAS>[] topoArray =
		// BGPMaster.buildBGPConnection(avoidSize, country + "-as.txt", country
		// + "-superAS.txt");
		HashMap<Integer, DecoyAS>[] topoArray = BGPMaster.buildBGPConnection(avoidSize, wardenFile);
		HashMap<Integer, DecoyAS> liveTopo = topoArray[0];
		HashMap<Integer, DecoyAS> prunedTopo = topoArray[1];
		System.out.println("Topo built and BGP converged.");

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
		} else if (mode == Nightwing.ACTIVE_MODE) {
			FindSim simDriver = new FindSim(liveTopo, prunedTopo);
			simDriver.runActive(avoidSize);
		} else if (mode == Nightwing.RING_MODE) {
			Rings simDriver = new Rings(liveTopo, prunedTopo);
			simDriver.runTests(wardenFile);
		} else if (mode == Nightwing.TRAFFICSTAT1_MODE) {
			TrafficStat1 stat = new TrafficStat1(liveTopo, prunedTopo, args[2]);
			stat.runStat();
		} else {
			System.out.println("mode fucked up, wtf.... " + mode);
			System.exit(-2);
		}

	}
}
