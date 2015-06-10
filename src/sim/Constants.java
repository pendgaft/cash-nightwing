package sim;

import econ.EconomicEngine;
import topo.AS;

public class Constants {

	public static final String AS_REL_FILE = "cash-nightwing/realTopo/20150201.as-rel.txt";
	public static final String IP_COUNT_FILE = "cash-nightwing/realTopo/whole-internet-20150101-ip.txt";
	public static final String SUPER_AS_FILE = "cash-nightwing/superAS.txt";
	public static final String TRAFFIC_SPLIT_FILE = "cash-nightwing/trafficSplit.csv";
	
	public static final String BASE_LOG_DIR = "/scratch/minerva2/schuch/nightwingData/rawLogs/";
	
	public static final boolean AGRESSIVE_PRUNE = false;
	
	
//	public static final String AS_REL_FILE = "topo1Test/as-rel.txt";
//	public static final String IP_COUNT_FILE = "topo1Test/ip-count.txt";
//	public static final String SUPER_AS_FILE = "topo1Test/superAS.txt";
	
//	public static final String AS_REL_FILE = "maxTest/5node-rel.txt";
//	public static final String IP_COUNT_FILE = "maxTest/ip-count.csv";
//	public static final String SUPER_AS_FILE = "maxTest/superAS-test.txt";
	
	public static final boolean DEBUG = false;
	public static final boolean ECON_TESTING = false;
	public static final boolean DUMP_READABLE_PATHS = false;
	
	
	public static final int NTHREADS = 10;
	
	public static final int SAMPLE_COUNT = 100;
	
	public static AS.AvoidMode DEFAULT_AVOID_MODE = AS.AvoidMode.IgnoreLpref;
	public static boolean REVERSE_POISON = false;
	public static AS.ReversePoisonMode REVERSE_MODE = AS.ReversePoisonMode.None;
	public static final EconomicEngine.OrderMode DEFAULT_ORDER_MODE = EconomicEngine.OrderMode.IPWeighted;
	
	public static final boolean REFUSE_DIRTY_TRAFFIC = false;
}

