package sim;

import java.io.*;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import topo.AS;
import topo.SerializationMaster;

import decoy.*;
import econ.EconomicEngine;
import gnu.trove.map.TIntObjectMap;

public class Nightwing implements Runnable {

	public enum SimMode {
		DUMP, RANDOMNUMBER, RANDOMSIZE, ORDERED, GLOBAL, VS
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

	private boolean largeMemoryEnv;
	private static long LARGE_MEM_THRESH = 1024 * 1024 * 1024 * 100;

	public Nightwing(Namespace ns) throws IOException {

		/*
		 * Store the name space, as optional args might need to be fetched later
		 */
		this.myArgs = ns;

		this.largeMemoryEnv = Runtime.getRuntime().maxMemory() >= Nightwing.LARGE_MEM_THRESH;
		System.out.println("Large memory env: " + this.largeMemoryEnv);
		if(ns.getBoolean("forceLowMem")){
			System.out.println("Overriding memory enviornment FORCING LOW");
			this.largeMemoryEnv = false;
		}

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
		// TODO make bgp master OO as well please...
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
				this.perfLogger, ns.getBoolean("defection"), this.largeMemoryEnv);

		/*
		 * We're ready to simulate at this point
		 */
	}

	private String buildLoggingPath(Namespace ns) {

		String[] frags = this.resistorFile.split("\\/");
		String outStr = Constants.BASE_LOG_DIR + frags[frags.length - 1].split("\\.")[0];

		if (ns.getBoolean("defection")) {
			outStr += "DEFECTION";
		}

		/*
		 * Encode deploy mode
		 */
		if (this.myMode == Nightwing.SimMode.GLOBAL) {
			outStr += "World";
			if (ns.getBoolean("coverageOrdering")) {
				outStr += "Coverage";
			} else {
				outStr += "Weighting";
			}
		} else if (this.myMode == Nightwing.SimMode.ORDERED) {
			outStr += "Ordered";
			if (ns.getBoolean("coverageOrdering")) {
				outStr += "Coverage";
			} else {
				outStr += "Weighting";
			}
		} else if (this.myMode == Nightwing.SimMode.VS) {
			String[] dictatedFrags = ns.getString("deployers").split("\\/");
			String depFile = dictatedFrags[frags.length - 1].split("\\.")[0];
			outStr += "VS" + depFile;
		} else if (this.myMode == Nightwing.SimMode.RANDOMNUMBER) {
			outStr += "RandomDeployCount";
			outStr += Constants.DEFAULT_DEPLOY_START + "-" + Constants.DEFAULT_DEPLOY_STOP + "-"
					+ Constants.DEFAULT_DEPLOY_STEP + "-" + Constants.DEFAULT_FIGURE_OF_MERIT;
		} else if (this.myMode == Nightwing.SimMode.RANDOMSIZE) {
			outStr += "RandomDeploySize";
			outStr += Constants.DEFAULT_DEPLOY_START + "-" + Constants.DEFAULT_DEPLOY_STOP + "-"
					+ Constants.DEFAULT_DEPLOY_STEP + "-" + Constants.DEFAULT_FIGURE_OF_MERIT;
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
					Constants.DEFAULT_DEPLOY_STEP, this.myArgs.getBoolean("coverageOrdering"),
					this.myArgs.getBoolean("defection"));
		} else if (this.myMode == Nightwing.SimMode.GLOBAL) {
			this.econManager.manageGlobalWardenSim(Constants.DEFAULT_DEPLOY_START, Constants.DEFAULT_DEPLOY_STOP,
					Constants.DEFAULT_DEPLOY_STEP, this.myArgs.getBoolean("coverageOrdering"),
					this.myArgs.getBoolean("defection"));
		} else if (this.myMode == Nightwing.SimMode.VS) {
			this.econManager.manageDictatedDRSim(this.myArgs.getString("deployers"));
		} else if (this.myMode == Nightwing.SimMode.RANDOMNUMBER) {
			this.econManager.manageRandomDeploySizeSim(Constants.DEFAULT_DEPLOY_START, Constants.DEFAULT_DEPLOY_STOP,
					Constants.DEFAULT_DEPLOY_STEP, Constants.RANDOM_SAMPLE_COUNT, Constants.DEFAULT_FIGURE_OF_MERIT);
		} else if (this.myMode == Nightwing.SimMode.RANDOMSIZE) {
			this.econManager.manageCustConeExploration(Constants.DEFAULT_DEPLOY_START, Constants.DEFAULT_DEPLOY_STOP,
					Constants.DEFAULT_DEPLOY_STEP, Constants.RANDOM_SAMPLE_COUNT, Constants.DEFAULT_FIGURE_OF_MERIT);
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

		/*
		 * Optional flags for reconfiguring simulation constants
		 */
		argParse.addArgument("--dcountstart").help("Deploy count start point").required(false).type(Integer.class)
				.setDefault(Constants.DEFAULT_DEPLOY_START);
		argParse.addArgument("--dcountend").help("Deployer count end point").required(false).type(Integer.class)
				.setDefault(Constants.DEFAULT_DEPLOY_STOP);
		argParse.addArgument("--dcountstep").help("Deployer count step size").required(false).type(Integer.class)
				.setDefault(Constants.DEFAULT_DEPLOY_STEP);
		argParse.addArgument("--randomCount").help("Random sample size").required(false).type(Integer.class)
				.setDefault(Constants.RANDOM_SAMPLE_COUNT);
		argParse.addArgument("--forceLowMem").help("Forces simulator into low mem enviornment").required(false)
				.action(Arguments.storeTrue());

		/*
		 * Mode specific optional arguments
		 */
		argParse.addArgument("--coverageOrdering").help("tells simulator to use coverage ordering").required(false)
				.action(Arguments.storeTrue());
		argParse.addArgument("--deployers").help("deployer list file").required(false);
		argParse.addArgument("--defection").help("Turns on defection expoloration").required(false)
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

		/*
		 * Respect any optional args that reconfigure constants
		 */
		Constants.DEFAULT_DEPLOY_START = ns.getInt("dcountstart");
		Constants.DEFAULT_DEPLOY_STOP = ns.getInt("dcountend");
		Constants.DEFAULT_DEPLOY_STEP = ns.getInt("dcountstep");
		Constants.RANDOM_SAMPLE_COUNT = ns.getInt("randomCount");

		Nightwing self = new Nightwing(ns);
		self.run();
	}
}
