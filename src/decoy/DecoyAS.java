package decoy;

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

	public DecoyAS(int myASN) {
		super(myASN);
		this.hostsDecoyRouter = false;
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
	 * Predicate to test if this AS is currently deploying decoy routers
	 * 
	 * @return - true if the AS is deploying decoy routers, false otherwise
	 */
	public boolean isDecoy() {
		return this.hostsDecoyRouter;
	}

}
