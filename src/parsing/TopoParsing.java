package parsing;

import decoy.DecoyAS;
import gnu.trove.map.TIntObjectMap;
import topo.AS;
import topo.ASTopoParser;
import java.io.*;

public class TopoParsing {

	public static void main(String[] args) throws Exception {
		String warden = "countryMappings/CN-as.txt";
		TIntObjectMap<DecoyAS> usefulASMap = ASTopoParser.doNetworkBuild(null, AS.AvoidMode.NONE,
				AS.ReversePoisonMode.NONE);
		TIntObjectMap<DecoyAS> prunedASMap = ASTopoParser.doNetworkPrune(usefulASMap, true);


		BufferedWriter outBuff = new BufferedWriter(new FileWriter("realTopo/int.txt"));
		for(int tKey : prunedASMap.keys()){
			outBuff.write("" + tKey + "\n");
		}
		outBuff.close();
	}

}
