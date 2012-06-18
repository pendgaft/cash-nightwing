package sim;
import java.io.IOException;
import java.util.HashMap;
import decoy.DecoyAS;

public class TrafficTest {

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
	
	public static void test(HashMap<Integer, DecoyAS> topo){
		
		for(DecoyAS origin : topo.values()){
			for(DecoyAS dest : topo.values()){
				if(origin.equals(dest)){
					continue;
				} else {
					for(int asn : origin.getPath(dest.getASN()).getPath()){
						topo.get(asn).addTraffic();
					}
				}
			}
		}
		
		for(DecoyAS as : topo.values()){
			System.out.println("AS " +as.getASN()+ ": " +as.getTraffic());
		}
	}
}
