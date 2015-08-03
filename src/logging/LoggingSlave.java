package logging;

import econ.EconomicAgent;
import java.util.*;

public class LoggingSlave implements Runnable {

	private LinkedList<EconomicAgent> ownedNodes;
	
	public LoggingSlave() {
		this.ownedNodes = new LinkedList<EconomicAgent>();
	}
	
	public void giveNode(EconomicAgent agent){
		this.ownedNodes.add(agent);
	}

	@Override
	public void run() {
		for(EconomicAgent tAgent: this.ownedNodes){
			tAgent.doRoundLogging();
		}
	}

}
