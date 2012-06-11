package sim;

import java.util.*;
import java.io.*;

import decoy.DecoyAS;
import decoy.Rings;

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

	public static void main(String[] args) throws IOException {

		/*
		 * Figure out mode that we're running
		 */
		int mode = 0;
		int avoidSize = 0;
		String country = "china";
		if (args[0].equalsIgnoreCase(Nightwing.FIND_STRING)) {
			mode = Nightwing.FIND_MODE;
			country = args[1];
		} else if (args[0].equalsIgnoreCase(Nightwing.REPEAT_STRING)) {
			mode = Nightwing.REPEAT_MODE;
		} else if (args[0].equalsIgnoreCase(Nightwing.ASYM_STRING)) {
			mode = Nightwing.ASYM_MODE;
		} else if (args[0].equalsIgnoreCase(Nightwing.ACTIVE_AVOID_STRING)) {
			mode = Nightwing.ACTIVE_MODE;
			avoidSize = Integer.parseInt(args[1]);
		} else if (args[0].equalsIgnoreCase(Nightwing.RING_STRING)) {
			mode = Nightwing.RING_MODE;
		} else {
			System.out.println("bad mode: " + args[0]);
			System.exit(-1);
		}
		System.out.println("Mode: " + args[0] + " on country " + country + " looks good, building topo.");

		HashMap<Integer, DecoyAS>[] topoArray = BGPMaster.buildBGPConnection(avoidSize, country + "-as.txt");		
		HashMap<Integer, DecoyAS> liveTopo = topoArray[0];
		HashMap<Integer, DecoyAS> prunedTopo = topoArray[1];
		System.out.println("Topo built and BGP converged.");
			
		/*
		 * Run the correct mode
		 */
		if (mode == Nightwing.FIND_MODE) {
			FindSim simDriver = new FindSim(liveTopo, prunedTopo);
            simDriver.run(country + "-decoy-hunt-random.csv");
            simDriver.runLargeASOnlyTests(true, country + "-decoy-hunt-single.csv");
            simDriver.runLargeASOnlyTests(false, country + "-decoy-hunt-nlargest.csv");
            
			Rings ringDriver = new Rings(liveTopo, prunedTopo);
			ringDriver.runTests(country);
			simDriver.runRings(country);
			//simDriver.printResults();
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
			simDriver.runTests(country);
		} else {
			System.out.println("mode fucked up, wtf.... " + mode);
			System.exit(-2);
		}

	}
}
