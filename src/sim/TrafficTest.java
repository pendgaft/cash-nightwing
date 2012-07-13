package sim;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import decoy.DecoyAS;
import topo.AS;
import topo.BGPPath;
import util.Stats;

public class TrafficTest {
	private HashMap<Integer, DecoyAS> activeMap;
	private HashMap<Integer, DecoyAS> prunedMap;
	DecoyAS origin;
	DecoyAS dest;
	int originIPs;
	int destIPs;
	BGPPath path;
	int pathLength;
	List<AS> originGates; // origin peers/providers
	List<Integer> destGates; // dest peer/provider ASNs
	List<BGPPath> possPaths; 
	List<Integer> pathASNs;
	boolean originWeighted;
	boolean destWeighted;
	String filename;
	/**
	 * 
	 */
	public static void main(String[] args) {
		String[] args2 = new String[1];
		args2[0] = "econ";
		try{
			Nightwing.main(args2);
		} catch(IOException e) {
			return;
		}
	}

	public TrafficTest(HashMap<Integer, DecoyAS> activeMap,
			HashMap<Integer, DecoyAS> prunedMap) {
		super();
		this.activeMap = activeMap;
		this.prunedMap = prunedMap;
	}

	public void runTests(String country, boolean originWeighted, boolean destWeighted) throws IOException {
		this.originWeighted = originWeighted;
		this.destWeighted = destWeighted;

		filename = "logs/" + country;
		if(originWeighted) {
			filename += "_IPW";
		} else {
			filename += "_even";
		}
		if(destWeighted) {
			filename += "_to_IPW";
		} else {
			filename += "_to_even";
		}

		// add up traffic for ASes
		for(DecoyAS originLoop : activeMap.values()) {
			origin = originLoop;
			originIPs = origin.getIPCount();
			// active -> active
			for(DecoyAS destLoop : activeMap.values()) {
				dest = destLoop;

				path = origin.getPath(dest.getASN());

				if(path == null) {
					continue;
				} else {
					pathASNs = path.getPath();
				}

				this.addTraffic();
			}
			// active -> pruned
			for(DecoyAS destLoop : prunedMap.values()) {
				dest = destLoop;
				destGates = new LinkedList<Integer>();
				for(AS as : dest.getProviders()) {
					destGates.add(as.getASN());
				}
				for(AS as : dest.getPeers()) {
					destGates.add(as.getASN());
				}

				path = origin.getPathToPurged(destGates);

				if(path == null){
					continue;
				} else {
					pathASNs = path.getPath();
				}

				this.addTraffic();
			}
		}

		for(DecoyAS originLoop : prunedMap.values()) {
			origin = originLoop;
			originIPs = origin.getIPCount();
			// pruned -> active
			for(DecoyAS destLoop : activeMap.values()) {
				dest = destLoop;

				originGates = new LinkedList<AS>();
				for(AS as : origin.getProviders()) {
					originGates.add(as);
				}
				for(AS as : origin.getPeers()) {
					originGates.add(as);
				}

				if(originGates.isEmpty()) {
					continue;
				}

				while(!originGates.isEmpty()) {
					path = originGates.get(0).getPath(dest.getASN());
					originGates.remove(0);
					if(path == null) continue;
					else {
						pathLength = path.getPathLength();
						break;
					}
				}
				for(AS as : originGates) {
					path = as.getPath(dest.getASN());
					if(path == null) continue;
					if(path.getPathLength() < pathLength){ // check if path is shorter
						pathLength = path.getPathLength();
					}
				}

				if(path == null) {
					continue;
				} else {
					pathASNs = path.getPath();
				}

				this.addTraffic();
			}
			// pruned -> pruned
			for(DecoyAS destLoop : prunedMap.values()) {
				dest = destLoop;
				destGates = new LinkedList<Integer>();
				for(AS as : dest.getProviders()) {
					destGates.add(as.getASN());
				}
				for(AS as : dest.getPeers()) {
					destGates.add(as.getASN());
				}

				path = origin.getPathToPurged(destGates);

				if(path == null){
					continue;
				} else {
					pathASNs = path.getPath();
				}

				this.addTraffic();
			}
		}

		// write data to files
		// .csv
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(filename + ".csv"));

		for (DecoyAS as : activeMap.values()) {
			outBuff.write("" + as.getASN() + "," + as.getWardenTraffic() + "," + as.getTraffic() + "\n");
		}
		outBuff.close();

		// CDFs
		List<Double> values = new LinkedList<Double>();
		for(DecoyAS as : activeMap.values()) {
			values.add((double) as.getTraffic());
		}
		Stats.printCDF(values, filename + "_CDF.csv");

		values = new LinkedList<Double>();
		for(DecoyAS as : activeMap.values()) {
			values.add((double) as.getWardenTraffic());
			as.resetTraffic();
		}
		Stats.printCDF(values, filename + "_wardenCDF.csv");
	}

	public void addTraffic() {
		if(origin.equals(dest)) {
			return;
		}

		if(originWeighted) {
			for(int asn : pathASNs){
				activeMap.get(asn).addTraffic(originIPs);				
			}
			if(origin.isWardenAS()){
				for(int asn : pathASNs){
					activeMap.get(asn).addWardenTraffic(originIPs);				
				}
			}

		} else {
			for(int asn : pathASNs){
				activeMap.get(asn).addTraffic();				
			}
			if(origin.isWardenAS()){
				for(int asn : pathASNs){
					activeMap.get(asn).addWardenTraffic();
				}
			}
		}

		if(destWeighted) {
			for(int asn : pathASNs){
				activeMap.get(asn).addTraffic(destIPs);				
			}
			if(dest.isWardenAS()){
				for(int asn : pathASNs){
					activeMap.get(asn).addWardenTraffic(destIPs);				
				}
			}

		} else {
			for(int asn : pathASNs){
				activeMap.get(asn).addTraffic();				
			}
			if(origin.isWardenAS()){
				for(int asn : pathASNs){
					activeMap.get(asn).addWardenTraffic();
				}
			}
		}
	}
}