package sim;

import java.util.*;
import java.io.*;

import topo.BGPPath;
import util.Stats;
import decoy.DecoyAS;
/**
 * This class calculates the CDF graph of even-to-even traffic for both 
 * the active and purged networks.
 * 
 * @author Bowen 
 */

public class TrafficStat1 {
	/**
	 * Stores the active (routing) portion of the topology
	 */
	private HashMap<Integer, DecoyAS> activeMap;

	/**
	 * Stores the pruned portion of the topology
	 */
	private HashMap<Integer, DecoyAS> purgedMap;
	private List<Integer> countTraffic;
	private List<Double> statResult;
	private int totsize;
	private String outputFileName = "stats1";
	
	
	public TrafficStat1(HashMap<Integer, DecoyAS> activeMap,
			HashMap<Integer, DecoyAS> purgedMap) {
		
		this.activeMap = activeMap;
		this.purgedMap = purgedMap;
		totsize = activeMap.size() + purgedMap.size() + 1; 
		initializeCount(); 
	}
	
	public void initializeCount() {
		
		countTraffic = new ArrayList<Integer>(totsize);
		for (int i = 0; i < totsize; ++i)
			countTraffic.add(0);
	}
	
	public void statActiveMap() {

		for (int t1ASN : this.activeMap.keySet()) {
			DecoyAS tAS = this.activeMap.get(t1ASN);
			for (int t2ASN : this.activeMap.keySet()) {
				
				if (t1ASN == t2ASN)	continue;

				BGPPath tpath = tAS.getPath(t2ASN);
				if (tpath == null) continue;
				
				for (int tHop : tpath.getPath())
					countTraffic.set(tHop, countTraffic.get(tHop)+1);
			}	
		}
	}
	
	public void statPurgedMap() {
		
		for (int t1ASN : this.purgedMap.keySet()) {			
			DecoyAS tAS = this.purgedMap.get(t1ASN);			
			for (int t2ASN : this.purgedMap.keySet()) {
				
				if (t1ASN == t2ASN)	continue;
				
				BGPPath tpath = tAS.getPath(t2ASN);				
				if (tpath == null)	continue;
				
				for (int tHop : tpath.getPath())
					countTraffic.set(tHop, countTraffic.get(tHop)+1);
			}
		}
	}
	
	void calcCDF() {
		
		Collections.sort(countTraffic);
		
		int i, ptr, cnt = 0;
		int maxVal = countTraffic.get(totsize-1);
		statResult = new ArrayList<Double>(maxVal+1);
		/* ASN starts with 1 */
		countTraffic.remove(0);
		--totsize;

		/* just use an inefficient way.. */
		/*for (int i = 0; i <= maxVal; ++i) {
			for (int j = 0; j < totsize; ++j)
				if (countTraffic.get(j) == i) ++cnt;
			statResult.add(cnt*1.0 / totsize);
		}*/
		
		/* a more efficient way */
		for (i = 0, ptr = 0; i <= maxVal; ++i) {
			while (ptr < totsize && countTraffic.get(ptr) <= i) {
				if (countTraffic.get(ptr) == i) ++cnt;
				++ptr;
			}
			statResult.add(cnt*1.0 / totsize);
		}
	}
	
	public void runStat() throws IOException {
		
		statActiveMap();
		statPurgedMap();
		
		calcCDF();
		Stats.printCDF(statResult, outputFileName);
		
		System.out.println("The result of the CDF statistic is written into file: "
		+ outputFileName + ".");
	}
}
