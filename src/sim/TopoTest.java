package sim;

import java.io.*;
import java.util.*;

import topo.ASTopoParser;
import util.Stats;
import decoy.DecoyAS;

public class TopoTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException{
		
		HashMap<Integer, DecoyAS> usefulASMap = ASTopoParser.doNetworkBuild();
		HashMap<Integer, DecoyAS> prunedASMap = ASTopoParser.doNetworkPrune(usefulASMap);
		
		HashSet<Integer> largest = new HashSet<Integer>();
		for(int counter = 0; counter < 200; counter++){
			int val = 0;
			int current = 0;
			
			for(int tASN: usefulASMap.keySet()){
				if(largest.contains(tASN)){
					continue;
				}
				if(usefulASMap.get(tASN).getDegree() > val){
					val = usefulASMap.get(tASN).getDegree();
					current = tASN;
				}
			}
			
			largest.add(current);
		}
		
		HashSet<Integer> smallest = new HashSet<Integer>();
		smallest.addAll(usefulASMap.keySet());
		smallest.removeAll(largest);
		
		HashMap<Integer, Integer> fuck = new HashMap<Integer, Integer>();
		for(int aASN: largest){
			for(int bASN: largest){
				if(bASN == aASN){
					continue;
				}
				
				int val = Math.min(usefulASMap.get(aASN).getDegree(), usefulASMap.get(bASN).getDegree());
				if(!fuck.containsKey(val)){
					fuck.put(val, 0);
				}
				
				fuck.put(val, fuck.get(val) + 1);
			}
		}
		
		List<Integer> sortedKeys = new ArrayList<Integer>();
		sortedKeys.addAll(fuck.keySet());
		Collections.sort(sortedKeys);

		BufferedWriter out = new BufferedWriter(new FileWriter("degbased-t1t2.csv"));
		
		double fracStep = 1.0 / (largest.size() * (largest.size() - 1));
		double currStep = 0.0;
		for(int counter = 0; counter < sortedKeys.size(); counter++){
			int currVal = sortedKeys.get(counter);
			currStep += fracStep * fuck.get(currVal);
			out.write("" + currStep + "," + currVal + "\n");
		}
		
		out.close();
	}

}
