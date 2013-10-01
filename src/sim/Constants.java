package sim;

import topo.AS;

public class Constants {

	public static final String AS_REL_FILE = "cash-nightwing/as-rel.txt";
	public static final String IP_COUNT_FILE = "cash-nightwing/ip-count.csv";
	public static final String SUPER_AS_FILE = "cash-nightwing/superAS.txt";
	
//	public static final String AS_REL_FILE = "topo1Test/as-rel.txt";
//	public static final String IP_COUNT_FILE = "topo1Test/ip-count.txt";
//	public static final String SUPER_AS_FILE = "topo1Test/superAS.txt";
	
//	public static final String AS_REL_FILE = "maxTest/5node-rel.txt";
//	public static final String IP_COUNT_FILE = "maxTest/ip-count.csv";
//	public static final String SUPER_AS_FILE = "maxTest/superAS-test.txt";
	
	public static final boolean DEBUG = false;
	public static final boolean ECON_TESTING = false;
	
	public static final int NTHREADS = 10;
	
	public static final int SAMPLE_COUNT = 50;
	
	public static final AS.AvoidMode DEFAULT_AVOID_MODE = AS.AvoidMode.IgnoreLpref;

}

