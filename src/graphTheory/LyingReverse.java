package graphTheory;

import java.util.*;
import java.io.*;

public class LyingReverse {

	private CompositeGraph topo;
	private HashSet<String> resistors;

	public LyingReverse(CompositeGraph topology, HashSet<String> resistorASes) {
		this.topo = topology;
		this.resistors = resistorASes;
	}

	public int buildGraph(HashSet<String> deployerSet) {
		this.resetGraph();

		HashSet<String> visited = new HashSet<String>();
		for (String tDeployer : deployerSet) {
			visited.add(tDeployer + "-customer");
			visited.add(tDeployer + "-peer");
			visited.add(tDeployer + "-provider");
		}
		HashSet<String> nextHorizon = new HashSet<String>();
		HashSet<String> currentHorizon = new HashSet<String>();
		for (String tResistor : this.resistors) {
			currentHorizon.add(tResistor + "-provider");
			this.topo.getNodeByName(tResistor + "-provider").setData(1);
		}

		/*
		 * BFS like a BALLLLLA
		 */
		while (currentHorizon.size() > 0) {
			for (String tASName : currentHorizon) {
				if (!visited.contains(tASName)) {
					CompositeNode<Integer> tNode = this.topo.getNodeByName(tASName);
					for (CompositeNode<Integer> neighbor : tNode.getConnectedNodes()) {
						nextHorizon.add(neighbor.getName());
					}
					tNode.setData(1);
				}
			}

			currentHorizon.clear();
			currentHorizon.addAll(nextHorizon);
			nextHorizon.clear();
		}

		return this.topo.getNodesPartOf(1).size();
	}

	private void resetGraph() {
		this.topo.resetValues(0);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException{
		CompositeGraph internetTopo = SimpleAS.convertToCompositeGraph(SimpleAS.parseFromFile(args[0]));
		HashSet<String> resistorSet = LyingReverse.parseASFile(args[1]);
		HashSet<String> deployerSet = LyingReverse.parseASFile(args[2]);
		
		LyingReverse self = new LyingReverse(internetTopo, resistorSet);
		System.out.println(self.buildGraph(deployerSet) + " reachable ASe");
	}

	private static HashSet<String> parseASFile(String fileName) throws IOException {
		BufferedReader fBuff = new BufferedReader(new FileReader(fileName));
		String line = null;
		
		HashSet<String> returnSet = new HashSet<String>();
		while((line = fBuff.readLine()) != null){
			line = line.trim();
			if(line.length() > 0){
				//do it just to sanity check that we're playing with numerical input
				Integer.parseInt(line);
				returnSet.add(line);
			}
		}
		fBuff.close();
		
		return returnSet;
	}

}
