package sim;

import econ.EconomicEngine;

public class Constants {

	public static final String AS_REL_FILE = "cash-nightwing/realTopo/20150201.as-rel.txt";
	public static final String IP_COUNT_FILE = "cash-nightwing/realTopo/whole-internet-20150101-ip.txt";
	public static final String TRAFFIC_MODEL_FILE = "cash-nightwing/trafficModel/newWeights.txt";
	public static final String SUPER_AS_FILE = "cash-nightwing/superAS.txt";
	public static final String TRAFFIC_SPLIT_FILE = "cash-nightwing/trafficSplit.csv";
	
	public static final String BASE_LOG_DIR = "/export/scratch2/schuch/nightwingData/rawLogs/";
	
	//TODO tune me please?
	public static final int MAX_NON_AGRESSIVE_TOPO = 7800;
	public static final int TOPO_CEIL = 10000;
	
	
//	public static final String AS_REL_FILE = "topo1Test/as-rel.txt";
//	public static final String IP_COUNT_FILE = "topo1Test/ip-count.txt";
//	public static final String SUPER_AS_FILE = "topo1Test/superAS.txt";
	
//	public static final String AS_REL_FILE = "maxTest/5node-rel.txt";
//	public static final String IP_COUNT_FILE = "maxTest/ip-count.csv";
//	public static final String SUPER_AS_FILE = "maxTest/superAS-test.txt";
	
	public static final boolean DEBUG = false;
	public static final boolean ECON_TESTING = false;
	public static final boolean DUMP_READABLE_PATHS = false;
	public static final boolean DONT_MAKE_SERIAL = false;
	
	
	public static final int NTHREADS = 28;
	
	public static int RANDOM_SAMPLE_COUNT = 100;
	
	public static boolean REVERSE_POISON = false;
	public static final EconomicEngine.OrderMode DEFAULT_ORDER_MODE = EconomicEngine.OrderMode.TrafficWeighted;
	
	public static final boolean REFUSE_DIRTY_TRAFFIC = false;
	
	public static int DEFAULT_DEPLOY_START = 10;
	public static int DEFAULT_DEPLOY_STOP = 100;
	public static int DEFAULT_DEPLOY_STEP = 10;
	
	public static int DEFAULT_FIGURE_OF_MERIT = 50;
}

