package econ;

import java.util.*;

public class FinalizeAdjustSlave implements Runnable {

	private List<EconomicAgent> workList;
	
	public FinalizeAdjustSlave(){
		this.workList = new LinkedList<EconomicAgent>();
	}
	
	public void addWork(EconomicAgent inWork){
		this.workList.add(inWork);
	}
	
	
	@Override
	public void run() {
		for (EconomicAgent tAgent: this.workList){
			tAgent.finalizeRoundAdjustments();
		}
	}

}
