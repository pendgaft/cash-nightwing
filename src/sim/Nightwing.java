package sim;

import java.util.*;
import java.io.*;

import org.xml.sax.SAXException;

import parsing.AStoCountry;

import topo.AS;
import topo.SerializationMaster;

/*import decoy.DecoyAS;
 import decoy.Rings;*/
import decoy.*;
import econ.EconomicEngine;
import topo.BGPPath;

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
	private static final int ECON_MODE_2 = 8;
	private static final String ECON_M2_STRING = "econ2";
	private static final int ECON_MODE_3 = 9;
	private static final String ECON_M3_STRING = "econ3";
	private static final int ECON_MODE_4 = 10;
	private static final String ECON_M4_STRING = "econ4";

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
			if (args.length != 7) {
				System.out
						.println("Economic mode 1 (only provide, number of DR exploration) usage: ./Nightwing <mode> <warden file> <traffic split file> <starting decoy count> <ending decoy count> <step size> <min CC size>");
				return;
			}
		} else if (args[0].equalsIgnoreCase(Nightwing.ECON_M2_STRING)) {
			mode = Nightwing.ECON_MODE_2;
			if (args.length != 7) {
				System.out
						.println("Economic mode 2 (min cust cone exploration) usage: ./Nightwing <mode> <warden file> <traffic split file> <decoy size> <starting cone size> <ending cone size> <step size>");
				return;
			}
		} else if (args[0].equalsIgnoreCase(Nightwing.ECON_M3_STRING)) {
			mode = Nightwing.ECON_MODE_3;
			if (args.length != 7) {
				System.out
						.println("Economic mode 3 (amir's deploy style) usage: ./Nightwing <mode> <warden file> <traffic split file> <starting size> <ending size> <step size> <avoid ring 1 (true/false)>");
				return;
			}
		} else if (args[0].equalsIgnoreCase(Nightwing.ECON_M4_STRING)) {
			mode = Nightwing.ECON_MODE_4;
			if (args.length != 4) {
				System.out
						.println("Economic mode 4 (dictated list) usage: ./Nightwing <mode> <warden file> <traffic split file> <dr deploy file>");
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
			for (AS tAS : liveTopo.values()) {
				System.out.println(tAS.printDebugString());
			}
		}

		/*
		 * Run the correct mode
		 */

		if (mode == Nightwing.TRAFFICSTAT1_MODE) {
			ParallelTrafficStat statInParallel = new ParallelTrafficStat(liveTopo, prunedTopo, args[2], serialControl);
			AStoCountry asCountryMapper = null;
			try {
				asCountryMapper = new AStoCountry(liveTopo, "/scratch/waterhouse/schuch/workspace/cash-nightwing/rawCountryData.xml");
			} catch (SAXException e) {
				e.printStackTrace();
			}	
			statInParallel.doCountryExperiment(asCountryMapper.getMap());
			//statInParallel.runStat();
		} else if (mode == Nightwing.ECON_PHASE_1) {
			/*
			 * Build the traffic stat object, and then actually flow the traffic
			 * through the network
			 */
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, args[2], serialControl);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo);
			/*
			 * Do the actual rounds of simulation
			 */
			econEngine.manageFixedNumberSim(Integer.parseInt(args[3]), Integer.parseInt(args[4]),
					Integer.parseInt(args[5]), Constants.SAMPLE_COUNT, Integer.parseInt(args[6]), trafficStat, false);
			econEngine.endSim();
		} else if (mode == Nightwing.ECON_MODE_2) {
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, args[2], serialControl);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo);

			econEngine.manageCustConeExploration(Integer.parseInt(args[4]), Integer.parseInt(args[5]),
					Integer.parseInt(args[6]), Constants.SAMPLE_COUNT, Integer.parseInt(args[3]), trafficStat);
			econEngine.endSim();
		} else if (mode == Nightwing.ECON_MODE_3) {
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, args[2], serialControl);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo);

			econEngine.manageSortedWardenSim(Integer.parseInt(args[3]), Integer.parseInt(args[4]),
					Integer.parseInt(args[5]), Boolean.parseBoolean(args[6]), trafficStat);
			econEngine.endSim();
		} else if (mode == Nightwing.ECON_MODE_4) {
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, args[2], serialControl);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo);
			econEngine.manageDictatedDRSim(args[3], trafficStat);
			econEngine.endSim();
		} else {
			System.out.println("mode fucked up, wtf.... " + mode);
			System.exit(-2);
		}

		endTime = System.currentTimeMillis();
		System.out.println("\nAll work done, this took: " + (endTime - startTime) / 60000 + " minutes.");

	}
}
