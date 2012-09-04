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
	
	
	//FIXME - MJS - make class vars private always 
	private HashMap<Integer, DecoyAS> activeMap;
	private HashMap<Integer, DecoyAS> prunedMap;
	BGPPath path;
	int pathLength;
	List<AS> originGates; // origin peers/providers
	List<Integer> destGates; // dest peer/provider ASNs
	List<BGPPath> possPaths; 
	List<Integer> pathASNs;
	boolean originWeighted;
	boolean destWeighted;
	boolean logging;
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

	public void runTests(String country, boolean originWeighted, boolean destWeighted, boolean logging) throws IOException {
		this.originWeighted = originWeighted;
		this.destWeighted = destWeighted;
		this.logging = logging;

		filename = "logs/" + country + "_ioTraffic";
		/*
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
		 */

		if(logging) {
			//print Ribs
			
		}
		// add up traffic for ASes
		for(DecoyAS origin : activeMap.values()) {
			if(logging) System.out.println("ORIGIN(active): " + origin.getASN());
			
			// active -> active
			for(DecoyAS dest : activeMap.values()) {
				if(logging) System.out.println("DEST(active): " + dest.getASN());
				
				path = origin.getPath(dest.getASN());

				if(path == null) {
					System.out.println("No path from " + origin.getASN() + " to " + dest.getASN());
					continue;
				} else {
					pathASNs = path.getPath();
				}

				this.printInfo(origin, dest, pathASNs);
				this.addTraffic(origin, dest);
			}
			// active -> pruned
			for(DecoyAS dest : prunedMap.values()) {
				if(logging) System.out.println("DEST(pruned): " + dest.getASN());
				
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

				this.printInfo(origin, dest, pathASNs);
				this.addTraffic(origin, dest);
			}
		}

		for(DecoyAS origin : prunedMap.values()) {
			if(logging) System.out.println("ORIGIN (pruned): " + origin.getASN());
			
			// pruned -> active
			for(DecoyAS dest : activeMap.values()) {
				if(logging) System.out.println("DEST(active): " + dest.getASN());
				
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
					// debug stuff
					if (logging) System.out.println("No path from " + origin.getASN() + " to " + dest.getASN());
					continue;
				} else {
					pathASNs = path.getPath();
				}

				this.printInfo(origin, dest, pathASNs);
				this.addTraffic(origin, dest);
			}
			// pruned -> pruned
			for(DecoyAS dest : prunedMap.values()) {
				if(logging) System.out.println("DEST (pruned): " + dest.getASN());
				
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

				this.printInfo(origin, dest, pathASNs);
				this.addTraffic(origin, dest);
			}
		}

		// write data to files
		// .csv
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(filename + ".csv"));
		outBuff.write("# ASN, outTraffic, inTraffic, wardenTraffic, totalTraffic\n");

		for (DecoyAS as : activeMap.values()) {
			outBuff.write("" + as.getASN() + "," + as.getOutTraffic() + "," + as.getInTraffic() + "," + as.getWardenTraffic() + "," + as.getTraffic() + "\n");
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

	//FIXME - MJS - make private
	//FIXME - MJS - document if/else switches
	//FIXME - MJS - honestly, just remove the code for non-weighted destinations
	public void addTraffic(DecoyAS origin, DecoyAS dest) {
		int originIPs = origin.getIPCount();
		int destIPs = dest.getIPCount();


		if(origin.equals(dest)) {
			return;
		}

		//FIXME - MJS - this is incorrect there should be 4 cases, both origin and dest weighted,
		//one or the other, and lastly neither.  In the first case you need to take into account both
		//so it should be something along the lines of originIPs * destIPs.  (You can normalize it differently
		//if you wanted to, or stick to using large whole numbers
		//FIXME - MJS - but as I just added, honestly, limit this down to one case, both origin and dest weighted
		if(originWeighted) {
			for(int asn : pathASNs){
				activeMap.get(asn).addTraffic(originIPs);				
			}
			if(origin.isWardenAS()){
				for(int asn : pathASNs){
					activeMap.get(asn).addOutTraffic(originIPs);				
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
					activeMap.get(asn).addInTraffic(destIPs);
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

	//prints out info for debugging
	public void printInfo(DecoyAS origin, DecoyAS dest, List<Integer> pathASNs) { 
		if (!logging) return;
		
		//System.out.println("=====================");
		System.out.println("Origin: " + origin.getASN());
		System.out.println("Dest: " + dest.getASN());
		System.out.println("PathASNs: " + pathASNs);
	}
	
	// outputs all ASes RIBs for debugging
	public void printRIBs() {
		if (!logging) return;
		
		System.out.println("*** Active AS RIBs ***");
		for(AS as : activeMap.values()) {
			as.printRib();
		}
		System.out.println("*** Pruned AS RIBs ***");
		for(AS as : prunedMap.values()) {
			as.printRib();
		}
	}
}