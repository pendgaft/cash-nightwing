package econ;

import java.util.*;

import sim.Constants;
import topo.BGPPath;
import decoy.DecoyAS;
import gnu.trove.iterator.TIntIterator;

public class WeightingSlave implements Runnable {

	private List<DecoyAS> workList;
	private HashMap<Integer, Double> resultMap;
	private boolean global;
	private HashMap<Integer, EconomicAgent> theTopo;
	private Set<Integer> dropList;

	public WeightingSlave(boolean isGlobal, HashMap<Integer, EconomicAgent> theTopo, Set<Integer> dropList) {
		this.workList = new LinkedList<DecoyAS>();
		this.resultMap = new HashMap<Integer, Double>();
		this.global = isGlobal;
		this.theTopo = theTopo;
		this.dropList = dropList;
	}

	public void giveWork(DecoyAS ownedAS) {
		this.workList.add(ownedAS);
	}

	public HashMap<Integer, Double> getResult() {
		return this.resultMap;
	}

	@Override
	public void run() {
		for (DecoyAS tAS : this.workList) {
			if (global || tAS.isWardenAS()) {
				for (int tDestASN : this.theTopo.keySet()) {
					BGPPath tPath = tAS.getPath(tDestASN);
					if (tPath == null) {
						continue;
					}
					if (dropList != null && tPath.containsAnyOf(dropList)) {
						continue;
					}

					TIntIterator tIter = tPath.getPath().iterator();
					while (tIter.hasNext()) {
						int tHop = tIter.next();
						if (!this.resultMap.containsKey(tHop)) {
							this.resultMap.put(tHop, 0.0);
						}

						if (Constants.DEFAULT_ORDER_MODE == EconomicEngine.OrderMode.PathAppearance) {
							this.resultMap.put(tHop, this.resultMap.get(tHop) + 1);
						} else if (Constants.DEFAULT_ORDER_MODE == EconomicEngine.OrderMode.IPWeighted) {
							this.resultMap.put(tHop,
									this.resultMap.get(tHop) + this.theTopo.get(tDestASN).parent.getIPCount());
						} else if (Constants.DEFAULT_ORDER_MODE == EconomicEngine.OrderMode.TrafficWeighted) {
							this.resultMap.put(tHop, this.resultMap.get(tHop)
									+ this.theTopo.get(tDestASN).parent.getBaseTrafficFactor());
						} else {
							throw new RuntimeException("Bad AS ordering mode!");
						}

					}
				}
			}
		}

	}

}
