package decoy;

import java.io.Serializable;

import topo.AS;

/**
 * Simple wrapper around the AS class. This should store information about an AS
 * being a decoy router deploying AS. In the future (e.g. economic simulator)
 * this will have more, for now it's really just an added flag to the AS class
 * 
 * @author pendgaft
 * 
 */
public class DecoyAS extends AS implements Serializable{

	/**
	 * Serialization ID
	 */
	private static final long serialVersionUID = 212255951735594961L;

	/**
	 * Flag for the AS deploying decoy routers
	 */
	private boolean hostsDecoyRouter;
	
	/**
	 * Flag for the super AS
	 */
	private boolean isSuperAS;

	public DecoyAS(int myASN) {
		super(myASN);
		this.hostsDecoyRouter = false;
		this.isSuperAS = false;
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
	
	/**
	 * Function that turns this AS object into a super AS
	 */
	public void toggleSupperAS() {		
		this.isSuperAS = true;
	}
	
	/**
	 * Function that resets the decoy routing flag to false
	 */
	public void resetSuperAS() {
		this.isSuperAS = false;
	}
	
	/**
	 * Predicate to test if this AS is super AS
	 * 
	 * @return - true if the AS is super AS, false otherwise
	 */
	public boolean isSuperAS() {
		return this.isSuperAS;
	}

}
