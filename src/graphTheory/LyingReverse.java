package graphTheory;

import java.util.*;

public class LyingReverse {

	private HashMap<Integer, SimpleAS> topo;
	private HashSet<Integer> resistors;
	
	public LyingReverse(HashMap<Integer, SimpleAS> topology, HashSet<Integer> resistorASes){
		this.topo = topology;
		this.resistors = resistorASes;
	}
	
	public int buildGraph(HashSet<Integer> deployerSet){
		this.resetGraph();
		
		for(int tResistorASN: this.resistors){
			SimpleAS previous = null;
		}
		
		return this.countSubgraph();
	}
	
	private void resetGraph(){
		for(SimpleAS tAS: this.topo.values()){
			tAS.resetSubgraph();
		}
	}
	
	private int countSubgraph(){
		int count = 0;
		
		for(SimpleAS tAS: this.topo.values()){
			if(tAS.isInSubgraph()){
				count++;
			}
		}
		
		return count;
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
