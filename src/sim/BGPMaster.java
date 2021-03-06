package sim;

import gnu.trove.map.TIntObjectMap;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import decoy.DecoyAS;
import topo.AS;
import topo.ASTopoParser;
import topo.BGPPath;

public class BGPMaster {

	public enum WorkType {
		Adv, Process;
	}

	private int blockCount;
	private Semaphore workSem;
	private Semaphore completeSem;
	private Queue<Set<AS>> workQueue;
	private WorkType currentWorkType;

	private static final int NUM_THREADS = 8;
	private static final int WORK_BLOCK_SIZE = 40;

	public static boolean REPORT_TIME = true;
	public static PerformanceLogger prefLog = null;

	@SuppressWarnings("unchecked")
	public static TIntObjectMap<DecoyAS>[] buildBGPConnection(String wardenFile, AS.AvoidMode avoidMode,
			AS.ReversePoisonMode poisonMode, boolean largeMemEnv) throws IOException {

		/*
		 * Build AS map
		 */
		TIntObjectMap<DecoyAS> usefulASMap = ASTopoParser.doNetworkBuild(wardenFile, avoidMode, poisonMode);
		TIntObjectMap<DecoyAS> prunedASMap = ASTopoParser.doNetworkPrune(usefulASMap, largeMemEnv);

		System.out.println("Live topo size: " + usefulASMap.size());
		System.out.println("Pruned topo size: " + prunedASMap.size());

		/*
		 * Give everyone their self network
		 */
		for (AS tAS : usefulASMap.valueCollection()) {
			tAS.advPath(new BGPPath(tAS.getASN()));
		}

		BGPMaster.driveBGPProcessing(usefulASMap);

		BGPMaster.verifyConnected(usefulASMap);

		//self.tellDone();
		TIntObjectMap<DecoyAS>[] retArray = new TIntObjectMap[2];
		retArray[0] = usefulASMap;
		retArray[1] = prunedASMap;
		return retArray;
	}

	@SuppressWarnings("unchecked")
	public static TIntObjectMap<DecoyAS>[] buildASObjectsOnly(String wardenFile) throws IOException {
		/*
		 * Build AS map
		 */
		TIntObjectMap<DecoyAS> usefulASMap = ASTopoParser.doNetworkBuild(wardenFile, AS.AvoidMode.NONE,
				AS.ReversePoisonMode.NONE);
		TIntObjectMap<DecoyAS> prunedASMap = ASTopoParser.doNetworkPrune(usefulASMap, false);

		TIntObjectMap<DecoyAS>[] retArray = new TIntObjectMap[2];
		retArray[0] = usefulASMap;
		retArray[1] = prunedASMap;
		return retArray;
	}

	public static void driveBGPProcessing(TIntObjectMap<DecoyAS> activeMap) {
		if (BGPMaster.prefLog != null) {
			BGPMaster.prefLog.resetTimer();
		}

		/*
		 * dole out ases into blocks
		 */
		List<Set<AS>> asBlocks = new LinkedList<Set<AS>>();
		int currentBlockSize = 0;
		Set<AS> currentSet = new HashSet<AS>();
		for (AS tAS : activeMap.valueCollection()) {
			currentSet.add(tAS);
			currentBlockSize++;

			/*
			 * if it's a full block, send it to the list
			 */
			if (currentBlockSize >= BGPMaster.WORK_BLOCK_SIZE) {
				asBlocks.add(currentSet);
				currentSet = new HashSet<AS>();
				currentBlockSize = 0;
			}
		}
		/*
		 * add the partial set at the end if it isn't empty
		 */
		if (currentSet.size() > 0) {
			asBlocks.add(currentSet);
		}

		/*
		 * build the master and slaves, spin the slaves up
		 */
		BGPMaster self = new BGPMaster(asBlocks.size());
		List<Thread> slaveThreads = new LinkedList<Thread>();
		for (int counter = 0; counter < BGPMaster.NUM_THREADS; counter++) {
			slaveThreads.add(new Thread(new BGPSlave(self)));
		}
		for (Thread tThread : slaveThreads) {
			tThread.setDaemon(true);
			tThread.start();
		}

		long bgpStartTime = System.currentTimeMillis();
		if (BGPMaster.REPORT_TIME) {
			System.out.println("Starting up the BGP processing.");
		}

		boolean stuffToDo = true;
		boolean skipToMRAI = false;
		while (stuffToDo || skipToMRAI) {
			stuffToDo = false;
			skipToMRAI = false;

			/*
			 * dole out work to slaves
			 */
			for (Set<AS> tempBlock : asBlocks) {
				self.addWork(tempBlock);
			}

			/*
			 * Wait till this round is done
			 */
			try {
				self.wall();
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-2);
			}

			/*
			 * check if nodes still have stuff to do
			 */
			int workToDo = 0;
			int dirtyRoutes = 0;
			for (AS tAS : activeMap.valueCollection()) {
				if (tAS.hasWorkToDo()) {
					stuffToDo = true;
					workToDo++;
				}
				if (tAS.hasDirtyPrefixes()) {
					skipToMRAI = true;
					dirtyRoutes++;
				}
			}

			/*
			 * If we have no pending BGP messages, release all pending updates,
			 * this is slightly different from a normal MRAI, but it gets the
			 * point
			 */
			if (!stuffToDo && skipToMRAI) {
				self.currentWorkType = WorkType.Adv;
			} else {
				self.currentWorkType = WorkType.Process;
			}

			/*
			 * A tiny bit of logging
			 */
			//			stepCounter++;
			//			if (stepCounter % 1000 == 0) {
			//				System.out.println("" + (stepCounter / 1000) + " (1k msgs)");
			//			}
		}

		bgpStartTime = System.currentTimeMillis() - bgpStartTime;
		if (BGPMaster.REPORT_TIME) {
			System.out.println("BGP done, this took: " + (bgpStartTime / 60000) + " minutes.");
		}

		if (BGPMaster.prefLog != null) {
			BGPMaster.prefLog.logTime("BGP run");
		}
	}

	public BGPMaster(int blockCount) {
		this.blockCount = blockCount;
		this.workSem = new Semaphore(0);
		this.completeSem = new Semaphore(0);
		this.workQueue = new LinkedBlockingQueue<Set<AS>>();
		this.currentWorkType = WorkType.Process;
	}

	public void addWork(Set<AS> workSet) {
		this.workQueue.add(workSet);
		this.workSem.release();
	}

	public Set<AS> getWork() throws InterruptedException {

		this.workSem.acquire();
		return this.workQueue.poll();
	}

	public WorkType getCurentWorkType() {
		return this.currentWorkType;
	}

	public void reportWorkDone() {
		this.completeSem.release();
	}

	public void wall() throws InterruptedException {
		for (int counter = 0; counter < this.blockCount; counter++) {
			this.completeSem.acquire();
		}
	}

	private static void verifyConnected(TIntObjectMap<DecoyAS> transitAS) {
		long startTime = System.currentTimeMillis();
		System.out.println("Starting connection verification");

		double examinedPaths = 0.0;
		double workingPaths = 0.0;

		for (DecoyAS tAS : transitAS.valueCollection()) {
			for (DecoyAS tDest : transitAS.valueCollection()) {
				if (tDest.getASN() == tAS.getASN()) {
					continue;
				}

				examinedPaths++;
				if (tAS.getPath(tDest.getASN()) != null) {
					workingPaths++;
				}
			}
		}

		startTime = System.currentTimeMillis() - startTime;
		System.out.println("Verification done in: " + startTime);
		System.out.println("Paths exist for " + workingPaths + " of " + examinedPaths + " possible ("
				+ (workingPaths / examinedPaths * 100.0) + "%)");
	}
}
