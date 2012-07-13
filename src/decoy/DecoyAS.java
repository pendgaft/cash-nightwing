package decoy;

import sim.Nightwing;
import topo.AS;

/**
 * Simple wrapper around the AS class. This should store information about an AS
 * being a decoy router deploying AS. In the future (e.g. economic simulator)
 * this will have more, for now it's really just an added flag to the AS class
 * 
 * @author pendgaft
 * 
 */
public class DecoyAS extends AS {

	/**
	 * Flag for the AS deploying decoy routers
	 */
	private boolean hostsDecoyRouter;
	private long traffic; // how many times a packet has gone through this AS
	private long wardenTraffic;

	public DecoyAS(int myASN) {
		super(myASN);
		this.hostsDecoyRouter = false;
		this.traffic = 0;
		this.wardenTraffic = 0;
	}

	/**
	 * Function that turns this AS object into an AS that DOES deploy decoy
	 * routers
	 */
	public void toggleDecoyRouter() {
		/*
		 * Quick sanity check that we're not deploying decoy routers to the
		 * warden
		 */
		if (this.isWardenAS()) {
			throw new RuntimeException("Attempted to deploy decoy routers to the warden!  " + super.toString());
		}

		this.hostsDecoyRouter = true;
	}

	/**
	 * Function that resets the decoy routing flag to false
	 */
	public void resetDecoyRouter() {
		this.hostsDecoyRouter = false;
	}
	
	/**
	 * Function that increments the traffic variable
	 */
	public void addTraffic() {
		traffic++;
	}
	
	public void addTraffic(int numIPs) {
		traffic += numIPs;
	}
	
	public void addWardenTraffic(){
		wardenTraffic++;
	}
	
	public void addWardenTraffic(int numIPs) {
		wardenTraffic += numIPs;
	}

	/**
	 * Predicate to test if this AS is currently deploying decoy routers
	 * 
	 * @return - true if the AS is deploying decoy routers, false otherwise
	 */
	public boolean isDecoy() {
		return this.hostsDecoyRouter;
	}
	
	public long getTraffic(){
		return traffic;
	}
	
	public long getWardenTraffic() {
		return wardenTraffic;
	}
	
	public void resetTraffic() {
		traffic = 0;
		wardenTraffic = 0;
	}
}
