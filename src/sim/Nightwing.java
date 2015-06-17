package sim;

import java.util.*;
import java.io.*;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;

import org.xml.sax.SAXException;

import parsing.AStoCountry;
import topo.AS;
import topo.SerializationMaster;

/*import decoy.DecoyAS;
 import decoy.Rings;*/
import decoy.*;
import econ.EconomicEngine;
import gnu.trove.map.TIntObjectMap;
import topo.BGPPath;

//TODO THIS NEEDS TO BE WRITTEN IN AN OO MANER....IT'S SUPER MESSY BEING STATIC C-LIKE SHIT...
public class Nightwing {

	private static final int DUMP_BGP_MODE = 1;
	private static final String DUMP_BGP_PATHS_STRING = "bgpdump";
	private static final int TRAFFICSTAT1_MODE = 6;
	private static final String TRAFFICSTAT1_STRING = "trafficStat1";
	private static final int RANDOM_VARYSIZE_MODE = 7;
	private static final String ECON_P1_STRING = "randomSize";
	private static final int RANDOM_VARYCC_MODE = 8;
	private static final String ECON_M2_STRING = "randomCC";
	private static final int ORDERED_MODE = 9;
	private static final String ECON_M3_STRING = "ordered";
	private static final int DICTATED_MODE = 10;
	private static final String ECON_M4_STRING = "dictated";
	private static final int GLOBAL_MODE = 12;
	private static final String GLOBAL_DEPLOYER_STRING = "global";

	private static int MODE_LOCATION = 0;
	private static int RESIST_FILE_LOCATION = 1;
	private static int RESIST_STRAT_LOCATION = 2;
	private static int REVERSE_STRAT_LOCATION = 3;

	
	
	
	
	private int myMode;
	private String resistorFile;
	
	private TIntObjectMap<AS> liveTopo;
	private TIntObjectMap<AS> prunedTopo;
	
	private AS.AvoidMode resistorStrat;
	private AS.ReversePoisonMode reverseStrat;
	
	private String logDirString;
	private PerformanceLogger perfLogger;
	private SerializationMaster serialControl;
	
	public Nightwing() {
		
		
		
	}
	
	
	public static void main(String[] args) throws IOException {

		long startTime, endTime;
		startTime = System.currentTimeMillis();
		
		ArgumentParser argParse = ArgumentParsers.newArgumentParser("nightwing");
		argParse.addArgument("-m", "--mode").help("sim mode").required(true).nargs(1);
		argParse.addArgument("-r", "--resistor").help("resistor members file").required(true).nargs(1);
		argParse.addArgument("-s", "--strat").help("resistor strat").required(true).nargs(1);
		argParse.addArgument("-p", "--poisoning").help("reverse poisoning strat").required(true).nargs(1);

		if (args.length < 4) {
			System.out
					.println("Usage: ./Nightwing <mode> <wardenFile> <resistorStrat> <reverse poisoning> <mode specific configs....>");
			System.out.println("\t Avail Modes: " + ECON_P1_STRING + "," + ECON_M2_STRING + "," + ECON_M3_STRING + ","
					+ ECON_M4_STRING + "," + GLOBAL_DEPLOYER_STRING);
			return;
		}

		/*
		 * Easy part, find the warden file
		 */
		String wardenFile = args[Nightwing.RESIST_FILE_LOCATION];

		/*
		 * Parse in the resistor strat
		 */
		boolean resistStratParse = false;
		if (args[Nightwing.RESIST_STRAT_LOCATION].equalsIgnoreCase("lpref")
				|| args[Nightwing.RESIST_STRAT_LOCATION].equalsIgnoreCase("localPref")) {
			Constants.DEFAULT_AVOID_MODE = AS.AvoidMode.IgnoreLpref;
			resistStratParse = true;
		} else if (args[Nightwing.RESIST_STRAT_LOCATION].equalsIgnoreCase("plen")
				|| args[Nightwing.RESIST_STRAT_LOCATION].equalsIgnoreCase("path")
				|| args[Nightwing.RESIST_STRAT_LOCATION].equalsIgnoreCase("pathlen")) {
			Constants.DEFAULT_AVOID_MODE = AS.AvoidMode.IgnorePathLen;
			resistStratParse = true;
		} else if (args[Nightwing.RESIST_STRAT_LOCATION].equalsIgnoreCase("tie")
				|| args[Nightwing.RESIST_STRAT_LOCATION].equalsIgnoreCase("tiebreak")) {
			Constants.DEFAULT_AVOID_MODE = AS.AvoidMode.IgnoreTiebreak;
			resistStratParse = true;
		} else if (args[Nightwing.RESIST_STRAT_LOCATION].equalsIgnoreCase("legacy")) {
			Constants.DEFAULT_AVOID_MODE = AS.AvoidMode.Legacy;
			resistStratParse = true;
		}
		if (!resistStratParse) {
			System.out.println("bad resist mode, please choose: legacy, lpref, plen, tiebreak");
			return;
		}

		/*
		 * Parse the reverse mode
		 */
		boolean reverseStratParse = false;
		if (args[Nightwing.REVERSE_STRAT_LOCATION].equalsIgnoreCase("none")) {
			Constants.REVERSE_MODE = AS.ReversePoisonMode.None;
			Constants.REVERSE_POISON = false;
			reverseStratParse = true;
		} else if (args[Nightwing.MODE_LOCATION].equalsIgnoreCase("lying")) {
			Constants.REVERSE_MODE = AS.ReversePoisonMode.Lying;
			Constants.REVERSE_POISON = true;
			reverseStratParse = true;
		} else if (args[Nightwing.MODE_LOCATION].equalsIgnoreCase("honest")) {
			Constants.REVERSE_MODE = AS.ReversePoisonMode.Honest;
			Constants.REVERSE_POISON = true;
			reverseStratParse = true;
		}
		if (!reverseStratParse) {
			System.out.println("bad reverse mode, please choose: none, lying, honest");
			return;
		}

		/*
		 * Figure out mode that we're running
		 */
		int mode = 0;
		if (args[Nightwing.MODE_LOCATION].equalsIgnoreCase(Nightwing.TRAFFICSTAT1_STRING)) {
			mode = Nightwing.TRAFFICSTAT1_MODE;
			if (args.length != 4) {
				System.out.println("Traffic mode usage: ./Nightwing .....");
				return;
			}
		} else if (args[Nightwing.MODE_LOCATION].equalsIgnoreCase(Nightwing.ECON_P1_STRING)) {
			mode = Nightwing.RANDOM_VARYSIZE_MODE;
			if (args.length != 8) {
				System.out
						.println("Random deployment w/ varying deployer count\n usage: ./Nightwing ..... <starting decoy count> <ending decoy count> <step size> <min CC size>");
				return;
			}
		} else if (args[Nightwing.MODE_LOCATION].equalsIgnoreCase(Nightwing.ECON_M2_STRING)) {
			mode = Nightwing.RANDOM_VARYCC_MODE;
			if (args.length != 8) {
				System.out
						.println("Random deployment w/ varying min customer cone\n usage: ./Nightwing ..... <decoy size> <starting cone size> <ending cone size> <step size>");
				return;
			}
		} else if (args[Nightwing.MODE_LOCATION].equalsIgnoreCase(Nightwing.ECON_M3_STRING)) {
			mode = Nightwing.ORDERED_MODE;
			if (args.length != 8) {
				System.out
						.println("Ordered deployment\n usage: ./Nightwing ..... <starting size> <ending size> <step size> <avoid ring 1 (true/false)>");
				return;
			}
		} else if (args[Nightwing.MODE_LOCATION].equalsIgnoreCase(Nightwing.ECON_M4_STRING)) {
			mode = Nightwing.DICTATED_MODE;
			if (args.length != 5) {
				System.out.println("Dictated list of deployers\n usage: ./Nightwing ..... <dr deploy file>");
				return;
			}
		} else if (args[Nightwing.MODE_LOCATION].equalsIgnoreCase(Nightwing.GLOBAL_DEPLOYER_STRING)) {
			mode = Nightwing.GLOBAL_MODE;
			if (args.length != 7) {
				System.out
						.println("Global deployer\n usage: ./Nightwing ..... <starting szie> <ending size> <step size>");
				return;
			}
		} else if (args[Nightwing.MODE_LOCATION].equalsIgnoreCase(Nightwing.DUMP_BGP_PATHS_STRING)) {
			mode = Nightwing.DUMP_BGP_MODE;
			if (args.length != 5) {
				System.out.println("BGP Path dump\n usage: ./Nightwing ..... <out file>");
				return;
			}
		} else {
			System.out.println("bad mode: " + args[Nightwing.MODE_LOCATION]);
			System.exit(-1);
		}
		System.out.println("Mode: " + args[Nightwing.MODE_LOCATION] + " on warden country " + wardenFile
				+ " looks good, building topo.");

		HashMap<Integer, DecoyAS> liveTopo = null;
		HashMap<Integer, DecoyAS> prunedTopo = null;

		/*
		 * Serialization control
		 */
		SerializationMaster serialControl = new SerializationMaster(wardenFile);
		//		if (serialControl.hasValidBGPSerialFile()) {
		//			System.out.println("Valid serial file exists, skipping fresh build.");
		//			HashMap<Integer, DecoyAS>[] topoArray = BGPMaster.buildASObjectsOnly(wardenFile);
		//			liveTopo = topoArray[0];
		//			prunedTopo = topoArray[1];
		//			serialControl.loadBGPSerialFile(liveTopo);
		//			System.out.println("Topology loaded from serial file.");
		//			System.out.println("Live topo size: " + liveTopo.size());
		//			System.out.println("Pruned topo size: " + prunedTopo.size());
		//		} else {
		//System.out.println("No serial file exists, building topology from scratch.");
		System.out.println("starting initial build of BGP topo");
		HashMap<Integer, DecoyAS>[] topoArray = BGPMaster.buildBGPConnection(wardenFile);
		liveTopo = topoArray[0];
		prunedTopo = topoArray[1];
		System.out.println("Topo built and BGP converged.");
		//serialControl.buildBGPSerialFile(liveTopo);
		//System.out.println("Topology saved to serial file.");
		//		}

		if (mode == Nightwing.DUMP_BGP_MODE) {
			System.out.println("Starting BGP dump to " + args[2]);
			BufferedWriter outBuff = new BufferedWriter(new FileWriter(args[2]));
			for (AS tAS : liveTopo.values()) {
				for (AS destAS : liveTopo.values()) {
					if (destAS.getASN() == tAS.getASN()) {
						continue;
					}

					BGPPath tPath = tAS.getPath(destAS.getASN());
					if (tPath != null) {
						outBuff.write(tAS.getASN() + tPath.getLoggingString() + "\n");
					}
				}
			}
			outBuff.close();
			System.out.println("BGP Dump complete.");
			return;
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
		} else if (mode == Nightwing.RANDOM_VARYSIZE_MODE) {
			/*
			 * Build the traffic stat object, and then actually flow the traffic
			 * through the network
			 */
			//TODO build log dir
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo, null);
			/*
			 * Do the actual rounds of simulation
			 */
			//TODO build log dir
			econEngine.manageFixedNumberSim(Integer.parseInt(args[4]), Integer.parseInt(args[5]),
					Integer.parseInt(args[6]), Constants.SAMPLE_COUNT, Integer.parseInt(args[7]), trafficStat, false);
			econEngine.endSim();
		} else if (mode == Nightwing.RANDOM_VARYCC_MODE) {
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo, null);

			econEngine.manageCustConeExploration(Integer.parseInt(args[5]), Integer.parseInt(args[6]),
					Integer.parseInt(args[7]), Constants.SAMPLE_COUNT, Integer.parseInt(args[4]), trafficStat);
			econEngine.endSim();
		} else if (mode == Nightwing.ORDERED_MODE) {
			String logDir = Nightwing.logBuilder(mode, wardenFile, Boolean.parseBoolean(args[7]));
			PerformanceLogger prefLog = new PerformanceLogger(logDir);
			BGPMaster.prefLog = prefLog;
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
			ParallelTrafficStat.prefLog = prefLog;
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo, logDir);
			EconomicEngine.prefLogger = prefLog;
			econEngine.manageSortedWardenSim(Integer.parseInt(args[4]), Integer.parseInt(args[5]),
					Integer.parseInt(args[6]), Boolean.parseBoolean(args[7]), trafficStat);
			econEngine.endSim();
			prefLog.done();
		} else if (mode == Nightwing.DICTATED_MODE) {
			//TODO build log dir
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo, null);
			econEngine.manageDictatedDRSim(args[4], trafficStat);
			econEngine.endSim();
		} else if (mode == Nightwing.GLOBAL_MODE) {
			String logDir = Nightwing.logBuilder(mode, wardenFile, false);
			PerformanceLogger prefLog = new PerformanceLogger(logDir);
			BGPMaster.prefLog = prefLog;
			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
			ParallelTrafficStat.prefLog = prefLog;
			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo, logDir);
			EconomicEngine.prefLogger = prefLog;
			econEngine.manageGlobalWardenSim(Integer.parseInt(args[4]), Integer.parseInt(args[5]),
					Integer.parseInt(args[6]), trafficStat);
			econEngine.endSim();
			prefLog.done();
		} else {
			System.out.println("mode fucked up, wtf.... " + mode);
			System.exit(-2);
		}

		endTime = System.currentTimeMillis();
		System.out.println("\nAll work done, this took: " + (endTime - startTime) / 60000 + " minutes.");

	}

	private static String logBuilder(int mode, String warden, boolean exclude) {
		String[] frags = warden.split("\\/");
		String outStr = Constants.BASE_LOG_DIR + frags[frags.length - 1].split("\\.")[0];

		/*
		 * Encode deploy mode
		 */
		if (mode == Nightwing.GLOBAL_MODE) {
			outStr += "World";
		} else if (mode == Nightwing.ORDERED_MODE) {
			outStr += "Ordered";
			if (exclude) {
				outStr += "Exclude";
			} else {
				outStr += "Include";
			}
		}

		/*
		 * Encode avoid mode
		 */
		if (Constants.DEFAULT_AVOID_MODE == AS.AvoidMode.Legacy) {
			outStr += "Legacy";
		} else if (Constants.DEFAULT_AVOID_MODE == AS.AvoidMode.IgnoreLpref) {
			outStr += "LocalPref";
		} else if (Constants.DEFAULT_AVOID_MODE == AS.AvoidMode.IgnorePathLen) {
			outStr += "PathLen";
		} else if (Constants.DEFAULT_AVOID_MODE == AS.AvoidMode.IgnoreTiebreak) {
			outStr += "Tiebreak";
		}

		/*
		 * Encode reverse mode
		 */
		if (Constants.REVERSE_MODE == AS.ReversePoisonMode.None) {
			outStr += "NoRev";
		} else if (Constants.REVERSE_MODE == AS.ReversePoisonMode.Lying) {
			outStr += "Lying";
		} else if (Constants.REVERSE_MODE == AS.ReversePoisonMode.Honest) {
			outStr += "Honest";
		}

		/*
		 * Make the directory we're going to log to
		 */
		File tFileHook = new File(outStr);
		tFileHook.mkdir();

		return outStr + "/";
	}
}
