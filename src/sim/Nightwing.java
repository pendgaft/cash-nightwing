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

	private static final int TRAFFICSTAT1_MODE = 6;
	private static final String TRAFFICSTAT1_STRING = "trafficStat1";
	private static final int ECON_PHASE_1 = 7;
	private static final String ECON_P1_STRING = "randomSize";
	private static final int ECON_MODE_2 = 8;
	private static final String ECON_M2_STRING = "randomCC";
	private static final int ECON_MODE_3 = 9;
	private static final String ECON_M3_STRING = "ordered";
	private static final int ECON_MODE_4 = 10;
	private static final String ECON_M4_STRING = "dictated";

	public static void main(String[] args) throws IOException {

		long startTime, endTime;
		startTime = System.currentTimeMillis();

		if (args.length < 2) {
			System.out.println("Usage: ./Nightwing <mode> <wardenFile> <mode specific configs....>");
			System.out.println("\t Avail Modes: " + ECON_P1_STRING + "," + ECON_M2_STRING + "," + ECON_M3_STRING + ","
					+ ECON_M4_STRING);
			return;
		}

		/*
		 * Figure out mode that we're running
		 */
		int mode = 0;
		String wardenFile = args[1];
		if (args[0].equalsIgnoreCase(Nightwing.TRAFFICSTAT1_STRING)) {
			mode = Nightwing.TRAFFICSTAT1_MODE;
			if (args.length != 2) {
				System.out.println("Traffic mode usage: ./Nightwing <mode> <warden file>");
				return;
			}
		} else if (args[0].equalsIgnoreCase(Nightwing.ECON_P1_STRING)) {
			mode = Nightwing.ECON_PHASE_1;
			if (args.length != 6) {
				System.out
						.println("Random deployment w/ varying deployer count\n usage: ./Nightwing <mode> <warden file> <starting decoy count> <ending decoy count> <step size> <min CC size>");
				return;
			}
		} else if (args[0].equalsIgnoreCase(Nightwing.ECON_M2_STRING)) {
			mode = Nightwing.ECON_MODE_2;
			if (args.length != 6) {
				System.out
						.println("Random deployment w/ varying min customer cone\n usage: ./Nightwing <mode> <warden file> <decoy size> <starting cone size> <ending cone size> <step size>");
				return;
			}
		} else if (args[0].equalsIgnoreCase(Nightwing.ECON_M3_STRING)) {
			mode = Nightwing.ECON_MODE_3;
			if (args.length != 6) {
				System.out
						.println("Ordered deployment\n usage: ./Nightwing <mode> <warden file> <starting size> <ending size> <step size> <avoid ring 1 (true/false)>");
				return;
			}
		} else if (args[0].equalsIgnoreCase(Nightwing.ECON_M4_STRING)) {
			mode = Nightwing.ECON_MODE_4;
			if (args.length != 3) {
				System.out
						.println("Dictated list of deployers\n usage: ./Nightwing <mode> <warden file> <dr deploy file>");
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

		System.out.println("Live topo size: " + liveTopo.size());
		System.out.println("Pruned topo size: " + prunedTopo.size());

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
			ParallelTrafficStat statInParallel = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
			AStoCountry asCountryMapper = null;
			try {
				asCountryMapper = new AStoCountry(liveTopo,
						"/scratch/waterhouse/schuch/workspace/cash-nightwing/rawCountryData.xml");
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
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo);
			/*
			 * Do the actual rounds of simulation
			 */
			econEngine.manageFixedNumberSim(Integer.parseInt(args[3]), Integer.parseInt(args[4]),
					Integer.parseInt(args[5]), Constants.SAMPLE_COUNT, Integer.parseInt(args[6]), trafficStat, false);
			econEngine.endSim();
		} else if (mode == Nightwing.ECON_MODE_2) {
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo);

			econEngine.manageCustConeExploration(Integer.parseInt(args[4]), Integer.parseInt(args[5]),
					Integer.parseInt(args[6]), Constants.SAMPLE_COUNT, Integer.parseInt(args[3]), trafficStat);
			econEngine.endSim();
		} else if (mode == Nightwing.ECON_MODE_3) {
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo);

			econEngine.manageSortedWardenSim(Integer.parseInt(args[3]), Integer.parseInt(args[4]),
					Integer.parseInt(args[5]), Boolean.parseBoolean(args[6]), trafficStat);
			econEngine.endSim();
		} else if (mode == Nightwing.ECON_MODE_4) {
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
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
