package topo;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Class that does two things. First, it deals with the topology bookkeeping the
 * simulator needs to do. Second, it handles BGP processing. So as you might
 * imagine, this is kinda complex and fragile, in general, if your name isn't
 * Max, DON'T TOUCH THIS!!!!
 * 
 * @author pendgaft
 * 
 */
public abstract class AS {

	private int asn;
	private boolean wardenAS;
	private Set<AS> customers;
	private Set<AS> peers;
	private Set<AS> providers;
	private int numberOfIPs;

	private HashMap<Integer, List<BGPPath>> adjInRib;
	private HashMap<Integer, List<BGPPath>> inRib;
	private HashMap<Integer, Set<AS>> adjOutRib;
	private HashMap<Integer, BGPPath> locRib;
	private HashSet<Integer> dirtyDest;

	private Queue<BGPUpdate> incUpdateQueue;

	public static final int PROIVDER_CODE = -1;
	public static final int PEER_CODE = 0;
	public static final int CUSTOMER_CODE = 1;

	public AS(int myASN) {
		this.asn = myASN;
		this.wardenAS = false;
		this.customers = new HashSet<AS>();
		this.peers = new HashSet<AS>();
		this.providers = new HashSet<AS>();

		this.adjInRib = new HashMap<Integer, List<BGPPath>>();
		this.inRib = new HashMap<Integer, List<BGPPath>>();
		this.adjOutRib = new HashMap<Integer, Set<AS>>();
		this.locRib = new HashMap<Integer, BGPPath>();

		this.incUpdateQueue = new LinkedBlockingQueue<BGPUpdate>();
		this.dirtyDest = new HashSet<Integer>();
	}

	/**
	 * Sets the ip count, as it is not parsed at the point of AS object
	 * creation.
	 * 
	 * @param ipCount
	 *            - the number of distinct IP addresses in this AS
	 */
	public void setIPCount(int ipCount) {
		this.numberOfIPs = ipCount;
	}

	/**
	 * Fetches the number of IP address that reside in this AS.
	 * 
	 * @return - the number of distinct IP addresses in this AS
	 */
	public int getIPCount() {
		return this.numberOfIPs;
	}

	/**
	 * Static function that builds a Set of ASNs from a set of AS objects
	 * 
	 * @param asSet
	 *            - a set of AS objects
	 * @return - a set of ASNs, one from each AS in the supplied set
	 */
	public static HashSet<Integer> buildASNSet(HashSet<AS> asSet) {
		HashSet<Integer> outSet = new HashSet<Integer>();
		for (AS tAS : asSet) {
			outSet.add(tAS.getASN());
		}
		return outSet;
	}

	/**
	 * Method that adds a relationship between two ASes. This function ensures
	 * symm and is safe to accidently be called twice.
	 * 
	 * @param otherAS
	 *            - the AS this AS has a relationship with
	 * @param myRelationToThem
	 *            -
	 */
	public void addRelation(AS otherAS, int myRelationToThem) {
		if (myRelationToThem == AS.PROIVDER_CODE) {
			this.customers.add(otherAS);
			otherAS.providers.add(this);
		} else if (myRelationToThem == AS.PEER_CODE) {
			this.peers.add(otherAS);
			otherAS.peers.add(this);
		} else if (myRelationToThem == AS.CUSTOMER_CODE) {
			this.providers.add(otherAS);
			otherAS.customers.add(this);
		} else if (myRelationToThem == 3) {
			// ignore
		} else {
			System.err.println("WTF bad relation: " + myRelationToThem);
			System.exit(-1);
		}
	}

	/**
	 * Remove all references to this as object from other AS objects
	 */
	public void purgeRelations() {
		for (AS tCust : this.customers) {
			tCust.providers.remove(this);
		}
		for (AS tProv : this.providers) {
			tProv.customers.remove(this);
		}
		for (AS tPeer : this.peers) {
			tPeer.peers.remove(this);
		}
	}

	/**
	 * Public interface to force the router to handle one message in it's update
	 * queue. This IS safe if the update queue is empty (the function) returns
	 * immediately. This handles the removal of routes, calculation of best
	 * paths, tolerates the loss of all routes, etc. It marks routes as dirty,
	 * but does not send advertisements, as that is handled at the time of MRAI
	 * expiration.
	 */
	public void handleAdvertisement() {
		BGPUpdate nextUpdate = this.incUpdateQueue.poll();
		if (nextUpdate == null) {
			return;
		}

		/*
		 * Fetch some fields in the correct form
		 */
		int advPeer, dest;
		if (nextUpdate.isWithdrawal()) {
			advPeer = nextUpdate.getWithdrawer().asn;
			dest = nextUpdate.getWithdrawnDest();
		} else {
			advPeer = nextUpdate.getPath().getNextHop();
			dest = nextUpdate.getPath().getDest();
		}

		/*
		 * Setup some objects if this the first time seeing a peer/dest
		 */
		if (this.adjInRib.get(advPeer) == null) {
			this.adjInRib.put(advPeer, new ArrayList<BGPPath>());
		}
		if (this.inRib.get(dest) == null) {
			this.inRib.put(dest, new ArrayList<BGPPath>());
		}

		/*
		 * Hunt for an existing route in the adjInRib. If it's a withdrawl we
		 * want to remove it, and if it is an adv and a route already exists we
		 * then have an implicit withdrawl
		 */
		boolean routeRemoved = false;
		List<BGPPath> advRibList = this.adjInRib.get(advPeer);
		for (int counter = 0; counter < advRibList.size(); counter++) {
			if (advRibList.get(counter).getDest() == dest) {
				advRibList.remove(counter);
				routeRemoved = true;
				break;
			}
		}

		/*
		 * If there was a rotue to remove from the adjInRib, clean up the inRib
		 * as well
		 */
		List<BGPPath> destRibList = this.inRib.get(dest);
		if (routeRemoved) {
			for (int counter = 0; counter < destRibList.size(); counter++) {
				if (destRibList.get(counter).getNextHop() == advPeer) {
					destRibList.remove(counter);
					break;
				}
			}
		}

		/*
		 * If it is a loop don't add it to ribs
		 */
		if ((!nextUpdate.isWithdrawal()) && (!nextUpdate.getPath().containsLoop(this.asn))) {
			advRibList.add(nextUpdate.getPath());
			destRibList.add(nextUpdate.getPath());
		}

		recalcBestPath(dest);
	}

	/**
	 * Currently exposed interface which triggers an expiration of THIS ROUTER'S
	 * MRAI timer, resulting in updates being sent to this router's peers.
	 */
	public void mraiExpire() {
		for (int tDest : this.dirtyDest) {
			this.sendUpdate(tDest);
		}
		this.dirtyDest.clear();
	}

	/**
	 * Public interface to be used by OTHER BGP Speakers to advertise a change
	 * in a route to a destination.
	 * 
	 * @param incRoute
	 *            - the route being advertised
	 */
	public void advPath(BGPPath incPath) {
		this.incUpdateQueue.add(new BGPUpdate(incPath));
	}

	/**
	 * Public interface to be used by OTHER BGPSpeakers to withdraw a route to
	 * this router.
	 * 
	 * @param peer
	 *            - the peer sending the withdrawl
	 * @param dest
	 *            - the destination of the route withdrawn
	 */
	public void withdrawPath(AS peer, int dest) {
		this.incUpdateQueue.add(new BGPUpdate(dest, peer));
	}

	/**
	 * Predicate to test if the incoming work queue is empty or not, used to
	 * accelerate the simulation.
	 * 
	 * @return true if items are in the incoming work queue, false otherwise
	 */
	public boolean hasWorkToDo() {
		return !this.incUpdateQueue.isEmpty();
	}

	/**
	 * Predicate to test if this speaker needs to send advertisements when the
	 * MRAI fires.
	 * 
	 * @return - true if there are advertisements that need to be send, false
	 *         otherwise
	 */
	public boolean hasDirtyPrefixes() {
		return !this.dirtyDest.isEmpty();
	}

	/**
	 * Fetches the number of bgp updates that have yet to be processed.
	 * 
	 * @return the number of pending BGP messages
	 */
	public long getPendingMessageCount() {
		return (long) this.incUpdateQueue.size();
	}

	/**
	 * Function that forces the router to recalculate what our current valid and
	 * best path is. This should be called when a route for the given
	 * destination has changed in any way.
	 * 
	 * @param dest
	 *            - the destination network that has had a route change
	 */
	private void recalcBestPath(int dest) {
		boolean changed;

		List<BGPPath> possList = this.inRib.get(dest);
		BGPPath currentBest = this.pathSelection(possList);

		BGPPath currentInstall = this.locRib.get(dest);
		changed = (currentInstall == null || !currentBest.equals(currentInstall));
		this.locRib.put(dest, currentBest);

		/*
		 * If we have a new path, mark that we have a dirty destination
		 */
		if (changed) {
			this.dirtyDest.add(dest);
		}
	}

	/**
	 * Method that handles actual BGP path selection. Slightly abbreviated, does
	 * AS relation, path length, then tie break.
	 * 
	 * @param possList
	 *            - the possible valid routes
	 * @return - the "best" of the valid routes by usual BGP metrics
	 */
	private BGPPath pathSelection(List<BGPPath> possList) {
		BGPPath currentBest = null;
		int currentRel = -4;
		for (BGPPath tPath : possList) {
			if (currentBest == null) {
				currentBest = tPath;
				currentRel = this.getRel(currentBest.getNextHop());
				continue;
			}

			int newRel = this.getRel(tPath.getNextHop());
			if (newRel > currentRel) {
				currentBest = tPath;
				currentRel = newRel;
				continue;
			}

			if (newRel == currentRel) {
				if (currentBest.getPathLength() > tPath.getPathLength()
						|| (currentBest.getPathLength() == tPath.getPathLength() && tPath.getNextHop() < currentBest
								.getNextHop())) {
					currentBest = tPath;
					currentRel = newRel;
				}
			}
		}

		return currentBest;
	}

	/**
	 * Internal function to deal with the sending of advertisements or explicit
	 * withdrawals of routes. Does valley free routing.
	 * 
	 * @param dest
	 *            - the destination of the route we need to advertise a change
	 *            in
	 */
	private void sendUpdate(int dest) {
		Set<AS> prevAdvedTo = this.adjOutRib.get(dest);
		Set<AS> newAdvTo = new HashSet<AS>();
		BGPPath pathOfMerit = this.locRib.get(dest);

		if (pathOfMerit != null) {
			BGPPath pathToAdv = pathOfMerit.deepCopy();
			pathToAdv.prependASToPath(this.asn);
			for (AS tCust : this.customers) {
				tCust.advPath(pathToAdv);
				newAdvTo.add(tCust);
			}
			if (pathOfMerit.getDest() == this.asn || (this.getRel(pathOfMerit.getNextHop()) == 1)) {
				for (AS tPeer : this.peers) {
					tPeer.advPath(pathToAdv);
					newAdvTo.add(tPeer);
				}
				for (AS tProv : this.providers) {
					tProv.advPath(pathToAdv);
					newAdvTo.add(tProv);
				}
			}
		}

		if (prevAdvedTo != null) {
			prevAdvedTo.removeAll(newAdvTo);
			for (AS tAS : prevAdvedTo) {
				tAS.withdrawPath(this, dest);
			}
		}
	}

	/**
	 * Method to return the code for the relationship between this AS and the
	 * one specified by the ASN.
	 * 
	 * @param asn
	 *            - the ASN of the other AS
	 * @return - a constant matching the relationship
	 */
	private int getRel(int asn) {
		for (AS tAS : this.providers) {
			if (tAS.getASN() == asn) {
				return -1;
			}
		}
		for (AS tAS : this.peers) {
			if (tAS.getASN() == asn) {
				return 0;
			}
		}
		for (AS tAS : this.customers) {
			if (tAS.getASN() == asn) {
				return 1;
			}
		}

		if (asn == this.asn) {
			return 2;
		}

		throw new RuntimeException("asked for relation on non-adj/non-self asn, depending on sim "
				+ "this might be expected, if you're not, you should prob restart this sim...!");
	}

	/**
	 * Fetches the currently installed best path to the destination.
	 * 
	 * @param dest
	 *            - the ASN of the destination network
	 * @return - the current best path, or null if we have none
	 */
	public BGPPath getPath(int dest) {
		return this.locRib.get(dest);
	}

	/**
	 * Fetches what would be the currently installed best path for an AS that is
	 * NOT part of the current topology. In otherwords this fetches a path for
	 * an AS that has been pruned. This is done by supplying providers for that
	 * AS that have not been pruned, and comparing routes.
	 * 
	 * @param hookASNs
	 *            - a list of ASNs of AS that are providers for the pruned AS,
	 *            these AS MUST exist in the current topology
	 * @return - what the currently installed path would be for a destination
	 *         based off of the list of providers
	 */
	public BGPPath getPathToPurged(List<Integer> hookASNs) {
		List<BGPPath> listPossPaths = new LinkedList<BGPPath>();
		for (Integer tHook : hookASNs) {
			listPossPaths.add(this.getPath(tHook));
		}
		return this.pathSelection(listPossPaths);
	}

	/**
	 * Fetches all currently valid BGP paths to the destination AS.
	 * 
	 * @param dest
	 *            - the ASN of the destination AS
	 * @return - a list of all paths to the destination, an empty list if we
	 *         have none
	 */
	public List<BGPPath> getAllPathsTo(int dest) {
		if (!this.inRib.containsKey(dest)) {
			return new LinkedList<BGPPath>();
		}
		return this.inRib.get(dest);
	}

	public Set<AS> getCustomers() {
		return customers;
	}

	public Set<AS> getPeers() {
		return peers;
	}

	public Set<AS> getProviders() {
		return providers;
	}

	public String toString() {
		return "AS: " + this.asn;
	}

	/**
	 * Simple hash code based off of asn
	 */
	public int hashCode() {
		return this.asn;
	}

	/**
	 * Simple equality test done based off of ASN
	 */
	public boolean equals(Object rhs) {
		AS rhsAS = (AS) rhs;
		return this.asn == rhsAS.asn;
	}

	/**
	 * Fetches the ASN of this AS.
	 * 
	 * @return - the AS's ASN
	 */
	public int getASN() {
		return this.asn;
	}

	/**
	 * Fetches the degree of this AS
	 * 
	 * @return - the degree of this AS in the current topology
	 */
	public int getDegree() {
		return this.customers.size() + this.peers.size() + this.providers.size();
	}

	/**
	 * Fetches the number of ASes this AS has as a customer.
	 * 
	 * @return - the number of customers this AS has in the current topology
	 */
	public int getCustomerCount() {
		return this.customers.size();
	}

	/**
	 * Function that marks this AS as part of the wardern
	 */
	public void toggleWardenAS() {
		this.wardenAS = true;
	}

	/**
	 * Predicate to test if this AS is part of the warden.
	 * 
	 * @return - true if the AS is part of the warden, false otherwise
	 */
	public boolean isWardenAS() {
		return this.wardenAS;
	}

	/**
	 * Predicate to test if this AS is connected to the warden. An AS that is
	 * part of the warden is of course trivially connected to the warden
	 * 
	 * @return - true if this AS is part of the warden or is directly connected
	 *         to it
	 */
	public boolean connectedToWarden() {
		if (this.isWardenAS()) {
			return true;
		}

		for (AS tAS : this.customers) {
			if (tAS.isWardenAS()) {
				return true;
			}
		}
		for (AS tAS : this.providers) {
			if (tAS.isWardenAS()) {
				return true;
			}
		}
		for (AS tAS : this.peers) {
			if (tAS.isWardenAS()) {
				return true;
			}
		}
		return false;
	}

}
