package sim;

import java.io.*;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import topo.AS;
import topo.SerializationMaster;

/*import decoy.DecoyAS;
 import decoy.Rings;*/
import decoy.*;
import econ.EconomicEngine;
import gnu.trove.map.TIntObjectMap;

//TODO need to plug in random modes still
public class Nightwing implements Runnable {

	public enum SimMode {
		DUMP, RANDOMNUMBER, RANDOMSIZE, ORDERED, GLOBAL, DICTATED
	}

	private Namespace myArgs;

	private SimMode myMode;
	private String resistorFile;

	private TIntObjectMap<DecoyAS> liveTopo;
	private TIntObjectMap<DecoyAS> prunedTopo;

	private ParallelTrafficStat trafficManager;
	private EconomicEngine econManager;

	private AS.AvoidMode resistorStrat;
	private AS.ReversePoisonMode reverseStrat;

	private String logDirString;
	private PerformanceLogger perfLogger;
	private SerializationMaster serialControl;

	public Nightwing(Namespace ns) throws IOException {

		/*
		 * Store the name space, as optional args might need to be fetched later
		 */
		this.myArgs = ns;

		/*
		 * Load in the required arguments from name space
		 */
		this.myMode = ns.get("mode");
		this.resistorFile = ns.getString("resistor");
		this.resistorStrat = ns.get("strat");
		this.reverseStrat = ns.get("poisoning");

		/*
		 * Build objects required
		 */
		this.serialControl = new SerializationMaster(this.resistorFile);
		this.logDirString = this.buildLoggingPath(ns);
		this.perfLogger = new PerformanceLogger(this.logDirString);
		//TODO make bgp master OO as well please...
		BGPMaster.prefLog = this.perfLogger;

		/*
		 * Bootstrap our topology
		 */
		System.out.println("starting initial build of BGP topo");
		TIntObjectMap<DecoyAS>[] topoArray = BGPMaster.buildBGPConnection(this.resistorFile, this.resistorStrat,
				this.reverseStrat);
		this.liveTopo = topoArray[0];
		this.prunedTopo = topoArray[1];
		System.out.println("Topo built and BGP converged.");
		if (Constants.DEBUG) {
			System.out.println("liveTopo:");
			for (AS tAS : liveTopo.valueCollection()) {
				System.out.println(tAS.getASN());
			}
			System.out.println("prunedTopo:");
			for (AS tAS : prunedTopo.valueCollection()) {
				System.out.println(tAS.getASN());
			}

			System.out.println("live ip count:");
			for (AS tAS : liveTopo.valueCollection())
				System.out.println(tAS.getASN() + ", " + tAS.getIPCount());
			System.out.println("pruned ip count:");
			for (AS tAS : prunedTopo.valueCollection())
				System.out.println(tAS.getASN() + ", " + tAS.getIPCount());

			System.out.println("routing tables:");
			for (AS tAS : liveTopo.valueCollection()) {
				System.out.println(tAS.printDebugString());
			}
		}

		/*
		 * Last but not lease, build our traffic flow manager...
		 */
		this.trafficManager = new ParallelTrafficStat(this.liveTopo, this.prunedTopo, this.serialControl,
				this.perfLogger);
		this.econManager = new EconomicEngine(this.liveTopo, this.prunedTopo, this.trafficManager, this.logDirString,
				this.perfLogger);

		/*
		 * We're ready to simulate at this point
		 */
	}

	private String buildLoggingPath(Namespace ns) {

		String[] frags = this.resistorFile.split("\\/");
		String outStr = Constants.BASE_LOG_DIR + frags[frags.length - 1].split("\\.")[0];

		/*
		 * Encode deploy mode
		 */
		if (this.myMode == Nightwing.SimMode.GLOBAL) {
			outStr += "World";
		} else if (this.myMode == Nightwing.SimMode.ORDERED) {
			outStr += "Ordered";
			if (ns.getBoolean("avoidRing1")) {
				outStr += "Exclude";
			} else {
				outStr += "Include";
			}
		}

		/*
		 * Encode avoid mode
		 */
		if (this.resistorStrat == AS.AvoidMode.LEGACY) {
			outStr += "Legacy";
		} else if (this.resistorStrat == AS.AvoidMode.LOCALPREF) {
			outStr += "LocalPref";
		} else if (this.resistorStrat == AS.AvoidMode.PATHLEN) {
			outStr += "PathLen";
		} else if (this.resistorStrat == AS.AvoidMode.TIEBREAK) {
			outStr += "Tiebreak";
		}

		/*
		 * Encode reverse mode
		 */
		if (this.reverseStrat == AS.ReversePoisonMode.NONE) {
			outStr += "NoRev";
		} else if (this.reverseStrat == AS.ReversePoisonMode.LYING) {
			outStr += "Lying";
		} else if (this.reverseStrat == AS.ReversePoisonMode.HONEST) {
			outStr += "Honest";
		}

		/*
		 * Make the directory we're going to log to
		 */
		File tFileHook = new File(outStr);
		tFileHook.mkdir();

		/*
		 * Dump the namespace to a file just in case
		 */
		try {
			BufferedWriter outFP = new BufferedWriter(new FileWriter(outStr + "/ns.txt"));
			outFP.write(ns.toString());
			outFP.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return outStr + "/";
	}

	public void run() {
		System.out.println("Starting actual simulation...");
		long startTime = System.currentTimeMillis();
		if (this.myMode == Nightwing.SimMode.ORDERED) {
			this.econManager.manageSortedWardenSim(Constants.DEFAULT_DEPLOY_START, Constants.DEFAULT_DEPLOY_STOP,
					Constants.DEFAULT_DEPLOY_STEP, this.myArgs.getBoolean("avoidRing1"));
		} else if (this.myMode == Nightwing.SimMode.GLOBAL) {
			this.econManager.manageGlobalWardenSim(Constants.DEFAULT_DEPLOY_START, Constants.DEFAULT_DEPLOY_STOP,
					Constants.DEFAULT_DEPLOY_STEP);
		} else {
			System.out.println(this.myMode + " not implemented fully!");
		}

		this.econManager.endSim();
		this.perfLogger.done();

		long endTime = System.currentTimeMillis();
		System.out.println("\nAll work done, this took: " + (endTime - startTime) / 60000 + " minutes.");
	}

	public static void main(String[] args) throws IOException {

		ArgumentParser argParse = ArgumentParsers.newArgumentParser("nightwing");
		argParse.addArgument("-m", "--mode").help("sim mode").required(true).type(Nightwing.SimMode.class);
		argParse.addArgument("-r", "--resistor").help("resistor members file").required(true);
		argParse.addArgument("-s", "--strat").help("resistor strat").required(true).type(AS.AvoidMode.class);
		argParse.addArgument("-p", "--poisoning").help("reverse poisoning strat").required(true)
				.type(AS.ReversePoisonMode.class);

		//TODO optional way of changing default sim sizes

		//TODO way to change if ring 1 excluded in ordered sim
		argParse.addArgument("--avoidRing1").help("tells simulator to avoid ring 1 ASes if applicable").required(false)
				.action(Arguments.storeTrue());

		/*
		 * Actually parse
		 */
		Namespace ns = null;
		try {
			ns = argParse.parseArgs(args);
		} catch (ArgumentParserException e1) {
			argParse.handleError(e1);
			System.exit(-1);
		}

		Nightwing self = new Nightwing(ns);
		self.run();

		/*
		 * Legacy code underneath here
		 */
		//TODO clear out code benath me when done w/ it

		//		if (mode == Nightwing.DUMP_BGP_MODE) {
		//			System.out.println("Starting BGP dump to " + args[2]);
		//			BufferedWriter outBuff = new BufferedWriter(new FileWriter(args[2]));
		//			for (AS tAS : liveTopo.values()) {
		//				for (AS destAS : liveTopo.values()) {
		//					if (destAS.getASN() == tAS.getASN()) {
		//						continue;
		//					}
		//
		//					BGPPath tPath = tAS.getPath(destAS.getASN());
		//					if (tPath != null) {
		//						outBuff.write(tAS.getASN() + tPath.getLoggingString() + "\n");
		//					}
		//				}
		//			}
		//			outBuff.close();
		//			System.out.println("BGP Dump complete.");
		//			return;
		//		}

		/*
		 * Run the correct mode //
		 */
		//		if (mode == Nightwing.TRAFFICSTAT1_MODE) {
		//			ParallelTrafficStat statInParallel = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
		//			AStoCountry asCountryMapper = null;
		//			try {
		//				asCountryMapper = new AStoCountry(liveTopo,
		//						"/scratch/waterhouse/schuch/workspace/cash-nightwing/rawCountryData.xml");
		//			} catch (SAXException e) {
		//				e.printStackTrace();
		//			}
		//			statInParallel.doCountryExperiment(asCountryMapper.getMap());
		//			//statInParallel.runStat();
		//		} else if (mode == Nightwing.RANDOM_VARYSIZE_MODE) {
		//			/*
		//			 * Build the traffic stat object, and then actually flow the traffic
		//			 * through the network
		//			 */
		//			//TODO build log dir
		//			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
		//			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo, null);
		//			/*
		//			 * Do the actual rounds of simulation
		//			 */
		//			//TODO build log dir
		//			econEngine.manageFixedNumberSim(Integer.parseInt(args[4]), Integer.parseInt(args[5]),
		//					Integer.parseInt(args[6]), Constants.SAMPLE_COUNT, Integer.parseInt(args[7]), trafficStat, false);
		//			econEngine.endSim();
		//		} else if (mode == Nightwing.RANDOM_VARYCC_MODE) {
		//			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
		//			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo, null);
		//
		//			econEngine.manageCustConeExploration(Integer.parseInt(args[5]), Integer.parseInt(args[6]),
		//					Integer.parseInt(args[7]), Constants.SAMPLE_COUNT, Integer.parseInt(args[4]), trafficStat);
		//			econEngine.endSim();
		//		} else if (mode == Nightwing.ORDERED_MODE) {
		//			String logDir = Nightwing.logBuilder(mode, wardenFile, Boolean.parseBoolean(args[7]));
		//			PerformanceLogger prefLog = new PerformanceLogger(logDir);
		//			BGPMaster.prefLog = prefLog;
		//			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
		//			ParallelTrafficStat.prefLog = prefLog;
		//			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo, logDir);
		//			EconomicEngine.prefLogger = prefLog;
		//			econEngine.manageSortedWardenSim(Integer.parseInt(args[4]), Integer.parseInt(args[5]),
		//					Integer.parseInt(args[6]), Boolean.parseBoolean(args[7]), trafficStat);
		//			econEngine.endSim();
		//			prefLog.done();
		//		} else if (mode == Nightwing.DICTATED_MODE) {
		//			//TODO build log dir
		//			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
		//			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo, null);
		//			econEngine.manageDictatedDRSim(args[4], trafficStat);
		//			econEngine.endSim();
		//		} else if (mode == Nightwing.GLOBAL_MODE) {
		//			String logDir = Nightwing.logBuilder(mode, wardenFile, false);
		//			PerformanceLogger prefLog = new PerformanceLogger(logDir);
		//			BGPMaster.prefLog = prefLog;
		//			ParallelTrafficStat trafficStat = new ParallelTrafficStat(liveTopo, prunedTopo, serialControl);
		//			ParallelTrafficStat.prefLog = prefLog;
		//			EconomicEngine econEngine = new EconomicEngine(liveTopo, prunedTopo, logDir);
		//			EconomicEngine.prefLogger = prefLog;
		//			econEngine.manageGlobalWardenSim(Integer.parseInt(args[4]), Integer.parseInt(args[5]),
		//					Integer.parseInt(args[6]), trafficStat);
		//			econEngine.endSim();
		//			prefLog.done();
		//		} else {
		//			System.out.println("mode fucked up, wtf.... " + mode);
		//			System.exit(-2);
		//		}

	}
}
