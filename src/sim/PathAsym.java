package sim;

import java.io.IOException;
import java.util.*;

import topo.BGPPath;
import util.Stats;
import decoy.DecoyAS;

public class PathAsym {

	private HashMap<Integer, DecoyAS> activeMap;
	private HashMap<Integer, DecoyAS> purgedMap;
	private HashSet<DecoyAS> chinaAS;

	private static final String LOG_DIR = "logs/";

	public PathAsym(HashMap<Integer, DecoyAS> activeMap, HashMap<Integer, DecoyAS> purgedMap) {
		super();
		this.activeMap = activeMap;
		this.purgedMap = purgedMap;

		this.chinaAS = new HashSet<DecoyAS>();
		for (DecoyAS tAS : this.activeMap.values()) {
			if (tAS.isWardenAS()) {
				chinaAS.add(tAS);
			}
		}
	}

	public void buildPathSymCDF() {
		List<Double> asymList = new ArrayList<Double>();

		for (DecoyAS tAS : this.activeMap.values()) {
			int seen = 0;
			int asym = 0;

			for (DecoyAS tDest : this.activeMap.values()) {
				if (tDest.getASN() == tAS.getASN()) {
					continue;
				}

				seen++;
				if (this.isAsym(tAS, tDest)) {
					asym++;
				}
			}
			for (DecoyAS tDest : this.purgedMap.values()) {
				seen++;
				if (this.isAsym(tAS, tDest)) {
					asym++;
				}
			}

			asymList.add((double) asym / (double) seen);
		}
		for (DecoyAS tAS : this.purgedMap.values()) {
			int seen = 0;
			int asym = 0;

			for (DecoyAS tDest : this.activeMap.values()) {
				seen++;
				if (this.isAsym(tAS, tDest)) {
					asym++;
				}
			}
			for (DecoyAS tDest : this.purgedMap.values()) {
				if (tDest.getASN() == tAS.getASN()) {
					continue;
				}

				seen++;
				if (this.isAsym(tAS, tDest)) {
					asym++;
				}
			}

			asymList.add((double) asym / (double) seen);
		}

		try {
			Stats.printCDF(asymList, PathAsym.LOG_DIR + "asym.csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean isAsym(DecoyAS outAS, DecoyAS destAS) {
		BGPPath outPath = outAS.getPath(destAS.getASN());
		BGPPath inPath = destAS.getPath(outAS.getASN());
		
		if(outPath == null || inPath == null){
			return false;
		}
		
		return !(outPath.equals(inPath));
	}

}
