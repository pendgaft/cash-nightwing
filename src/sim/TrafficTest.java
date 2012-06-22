package sim;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import decoy.DecoyAS;

public class TrafficTest {
	private HashMap<Integer, DecoyAS> activeMap;
	private HashMap<Integer, DecoyAS> prunedMap;
	int numIPs;
	/**
	 * 
	 */
	public static void main(String[] args) {
		String[] args2 = {"econ"};
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
	
	public void runTests(String country) throws IOException {
		// add up traffic for ASes
		for(DecoyAS origin : activeMap.values()){
			for(DecoyAS dest : activeMap.values()){
				numIPs = origin.getIPCount();
				if(origin.equals(dest)){
					continue;
				} else if(origin.isWardenAS()) {
					for(int asn : origin.getPath(dest.getASN()).getPath()){
						activeMap.get(asn).addTraffic(numIPs);
						activeMap.get(asn).addWardenTraffic(numIPs);
					}
				} else {
					for(int asn : origin.getPath(dest.getASN()).getPath()){
						activeMap.get(asn).addTraffic(numIPs);
					}
				}
			}
		}
		
		BufferedWriter outBuff = new BufferedWriter(new FileWriter("logs/"
				+ country + "-econ.csv"));
		for (DecoyAS as : activeMap.values()) {
			outBuff.write("" + as.getASN() + "," + as.getWardenTraffic() + "," + as.getTraffic() + "\n");
		}
		outBuff.close();

	}
}
