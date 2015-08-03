package topo;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import gnu.trove.map.*;
import gnu.trove.map.hash.*;
import gnu.trove.set.hash.TIntHashSet;
import sim.Constants;
import econ.TransitAgent;

/**
 * Class that does two things. First, it deals with the topology bookkeeping the
 * simulator needs to do. Second, it handles BGP processing. So as you might
 * imagine, this is kinda complex and fragile, in general, if your name isn't
 * Max, DON'T TOUCH THIS!!!!
 * 
 * @author pendgaft
 * 
 */

// FIXME honest hole punching is currently fucked
public abstract class AS implements TransitAgent {

	public enum AvoidMode {
		NONE, LOCALPREF, PATHLEN, TIEBREAK, LEGACY;
	}

	public enum ReversePoisonMode {
		NONE, LYING, HONEST;
	}

	private int asn;
	private boolean purged;
	private Set<AS> customers;
	private Set<AS> peers;
	private Set<AS> providers;
	private Set<Integer> purgedNeighbors;

	private boolean wardenAS;
	private boolean activeAvoidance;
	private Set<Integer> avoidSet;
	private Set<AS> holepunchPeers;
	private Set<AS> wardenSet;
	private AvoidMode avoidMode;
	private ReversePoisonMode poisonMode;

	private int numberOfIPs;
	private long sizeOfCustomerIPCone;
	/** the percentage of ip count in the all normal ASes */
	private double ipPercentage;
	/** the amount of traffic sent from each super AS */
	private double trafficFromSuperAS;

	private Set<Integer> customerConeASList;

	private TIntObjectMap<List<BGPPath>> inRib; // all pathes
	private TIntObjectMap<Set<AS>> adjOutRib; // only to adjancy
	private TIntObjectMap<BGPPath> locRib;// best path
	private HashSet<Integer> dirtyDest;

	private TIntIntMap routeStatusMap;
	private TIntObjectMap<BGPPath> mplsRoutes;

	private Queue<BGPUpdate> incUpdateQueue;

	/* store the traffic over each neighbor */
	private TIntDoubleHashMap trafficOverNeighbors;

	private TIntDoubleHashMap volatileTraffic;
	private TIntHashSet volatileDestinations;

	public static final int PROIVDER_CODE = -1;
	public static final int PEER_CODE = 0;
	public static final int CUSTOMER_CODE = 1;

	public static final int RS_NULL = 0;
	public static final int RS_CLEAN = 1;
	public static final int RS_DIRTY = 2;
	public static final int RS_DIRTY_NO_LEGACY = 3;
	public static final int RS_LEGACY = 4;

	public AS(int myASN) {
		this.asn = myASN;
		this.ipPercentage = 0;
		this.trafficFromSuperAS = 0;
		this.wardenAS = false;
		this.routeStatusMap = null;
		this.mplsRoutes = null;
		this.activeAvoidance = false;
		this.avoidMode = AvoidMode.NONE;
		this.poisonMode = ReversePoisonMode.NONE;
		this.holepunchPeers = new HashSet<AS>();
		this.purged = false;
		this.customers = new HashSet<AS>();
		this.peers = new HashSet<AS>();
		this.providers = new HashSet<AS>();
		this.purgedNeighbors = new HashSet<Integer>();

		this.inRib = new TIntObjectHashMap<List<BGPPath>>(7800, (float) 0.8);
		this.adjOutRib = new TIntObjectHashMap<Set<AS>>(10, (float) 0.8);
		this.locRib = new TIntObjectHashMap<BGPPath>(7800, (float) 0.8);

		this.incUpdateQueue = new LinkedBlockingQueue<BGPUpdate>();
		this.dirtyDest = new HashSet<Integer>();

		this.trafficOverNeighbors = new TIntDoubleHashMap();
		this.volatileTraffic = new TIntDoubleHashMap();
		this.volatileDestinations = new TIntHashSet();

		this.customerConeASList = new HashSet<Integer>();
	}

	@SuppressWarnings("unchecked")
	public void loadASFromSerial(ObjectInputStream serialIn) throws IOException, ClassNotFoundException {
		this.inRib = (TIntObjectHashMap<List<BGPPath>>) serialIn.readObject();
		this.locRib = (TIntObjectHashMap<BGPPath>) serialIn.readObject();
		this.adjOutRib = new TIntObjectHashMap<Set<AS>>();

		for (int tDestASN : this.locRib.keys()) {
			Set<AS> tempSet = new HashSet<AS>();
			for (AS tCust : this.customers) {
				tempSet.add(tCust);
			}
			if (tDestASN == this.asn || (this.getRel(this.locRib.get(tDestASN).getNextHop()) == 1)) {
				for (AS tPeer : this.peers) {
					tempSet.add(tPeer);
				}
				for (AS tProv : this.providers) {
					tempSet.add(tProv);
				}
			}
			this.adjOutRib.put(tDestASN, tempSet);
		}

		// HashMap<Integer, Set<Integer>> tempAdjOut = (HashMap<Integer,
		// Set<Integer>>) serialIn.readObject();
		// this.adjOutRib = new HashMap<Integer, Set<AS>>();
		// for (int tASN : tempAdjOut.keySet()) {
		// Set<AS> tempSet = new HashSet<AS>();
		// for (int tAdvNeighbor : tempAdjOut.get(tASN)) {
		// tempSet.add(this.getNeighborByASN(tAdvNeighbor));
		// }
		// this.adjOutRib.put(tASN, tempSet);
		// }
	}

	public void saveASToSerial(ObjectOutputStream serialOut) throws IOException {
		serialOut.writeObject(this.inRib);
		serialOut.writeObject(this.locRib);

		// HashMap<Integer, Set<Integer>> tempAdjOut = new HashMap<Integer,
		// Set<Integer>>();
		// for (int tDest : this.adjOutRib.keySet()) {
		// Set<Integer> tempSet = new HashSet<Integer>();
		// for (AS tAS : this.adjOutRib.get(tDest)) {
		// tempSet.add(tAS.getASN());
		// }
		// tempAdjOut.put(tDest, tempSet);
		// }
		// serialOut.writeObject(tempAdjOut);
	}

	@SuppressWarnings("unchecked")
	public void loadTrafficFromSerial(ObjectInputStream serialIn) throws IOException, ClassNotFoundException {
		this.trafficOverNeighbors = (TIntDoubleHashMap) serialIn.readObject();
		this.volatileTraffic = (TIntDoubleHashMap) serialIn.readObject();
		this.volatileDestinations = (TIntHashSet) serialIn.readObject();
	}

	public void saveTrafficToSerial(ObjectOutputStream serialOut) throws IOException {
		serialOut.writeObject(this.trafficOverNeighbors);
		serialOut.writeObject(this.volatileTraffic);
		serialOut.writeObject(this.volatileDestinations);

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
	 * if (this.adjInRib.get(advPeer) == null) { this.adjInRib.put(advPeer, new
	 * HashMap<Integer, BGPPath>()); } sets the percentage of ipCount in the
	 * total normal ASes' ipCount
	 * 
	 * @param ipP
	 */
	public void setIPPercentage(double ipP) {
		this.ipPercentage = ipP;
	}

	/**
	 * fetches the ipCount percentage
	 * 
	 * @return
	 */
	public double getIPPercentage() {
		return this.ipPercentage;
	}

	/**
	 * sets the amount of traffic sent from a single super AS which is
	 * determined by the total amount of traffic from the super ASes and the
	 * ipCount percentage of each AS.
	 * 
	 * @param traffic
	 */
	public void setTrafficFromEachSuperAS(double traffic) {
		this.trafficFromSuperAS = traffic;
	}

	/**
	 * fetches the amount of traffic sent from a single super AS to each AS.
	 * 
	 * @return
	 */
	public double getTrafficFromEachSuperAS() {
		return this.trafficFromSuperAS;
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
		this.trafficOverNeighbors.put(otherAS.asn, 0.0);
		otherAS.trafficOverNeighbors.put(this.asn, 0.0);
		this.volatileTraffic.put(otherAS.asn, 0.0);
		otherAS.volatileTraffic.put(this.asn, 0.0);
	}

	private AS getNeighborByASN(int asn) {
		for (AS tAS : this.customers) {
			if (tAS.getASN() == asn) {
				return tAS;
			}
		}
		for (AS tAS : this.peers) {
			if (tAS.getASN() == asn) {
				return tAS;
			}
		}
		for (AS tAS : this.providers) {
			if (tAS.getASN() == asn) {
				return tAS;
			}
		}
		return null;
	}

	/**
	 * Remove all references to this as object from other AS objects
	 */
	public void purgeRelations() {
		this.purged = true;
		for (AS tCust : this.customers) {
			tCust.providers.remove(this);
			tCust.purgedNeighbors.add(this.asn);
		}
		for (AS tProv : this.providers) {
			tProv.customers.remove(this);
			tProv.purgedNeighbors.add(this.asn);
		}
		for (AS tPeer : this.peers) {
			tPeer.peers.remove(this);
			tPeer.purgedNeighbors.add(this.asn);
		}
	}

	public boolean isPurged() {
		return this.purged;
	}

	public void handleAllAdvertisements() {
		while (!this.incUpdateQueue.isEmpty()) {
			this.handleAdvertisement();
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
		if (this.inRib.get(dest) == null) {
			this.inRib.put(dest, new ArrayList<BGPPath>());
		}

		/*
		 * If there was a rotue to remove from the adjInRib, clean up the inRib
		 * as well
		 */
		List<BGPPath> destRibList = this.inRib.get(dest);
		for (int counter = 0; counter < destRibList.size(); counter++) {
			if (destRibList.get(counter).getNextHop() == advPeer) {
				destRibList.remove(counter);

				break;
			}
		}

		/*
		 * Add the new route to the ribs, if it is a loop then DON'T add it to
		 * ribs
		 */
		if ((!nextUpdate.isWithdrawal()) && (!nextUpdate.getPath().containsLoop(this.asn))) {
			destRibList.add(nextUpdate.getPath());
		}

		recalcBestPath(dest);
	}

	public void rescanBGPTable() {

		/*
		 * If we're reverse poisoning, re-run export on hole punched routes, or
		 * if it is the first time, actually give us a hole punched route
		 * (prevents the serial file from requiring all ASes to have a hole
		 * punched route)
		 */
		if (this.isWardenAS() && Constants.REVERSE_POISON) {
			if (!this.locRib.containsKey(this.asn * -1)) {
				this.advPath(new BGPPath(this.getASN() * -1));
			} else {
				this.dirtyDest.add(this.getASN() * -1);
			}
		}

		for (int tDest : this.locRib.keys()) {
			this.recalcBestPath(tDest);
		}

		/*
		 * If we're a warden and we're in a mode where we're playing around with
		 * MPLS games clear out the MPLS table for this round
		 */
		if (this.isWardenAS() && this.avoidMode == AS.AvoidMode.LEGACY) {
			this.mplsRoutes.clear();
		}
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

		/*
		 * We need to handle advertisements in one of two cases a) we have found
		 * a new best path and it's not the same as our current best path b) we
		 * had a best path prior, but currently do not
		 */
		changed = (currentBest != null && (currentInstall == null || !currentBest.equals(currentInstall)))
				|| (currentBest == null && currentInstall != null);
		this.locRib.put(dest, currentBest);
		if (this.isWardenAS()) {
			if (currentBest == null) {
				this.routeStatusMap.put(dest, AS.RS_NULL);
			} else if (this.avoidSet == null) {
				this.routeStatusMap.put(dest, AS.RS_CLEAN);
			} else if (currentBest.containsAnyOf(this.avoidSet)) {
				this.routeStatusMap.put(dest, AS.RS_DIRTY);
			} else {
				this.routeStatusMap.put(dest, AS.RS_CLEAN);
			}
		}

		/*
		 * If we have a new path, mark that we have a dirty destination
		 */
		if (changed) {
			this.dirtyDest.add(dest);
		}
	}

	public BGPPath pathSelection(Collection<BGPPath> possList) {
		/*
		 * If we're not doing active avoidance of decoy routers, just find the
		 * best path and move on w/ life
		 */
		if (!this.activeAvoidance) {
			return this.internalPathSelection(possList, false);
		}

		/*
		 * If we are doing active avoidance, jump around the decoy routers if we
		 * can, if not don't lose connectivity
		 */
		BGPPath avoidPath = this.internalPathSelection(possList, true);

		/*
		 * Any other avoidance mode, find a path, even if it is dirty
		 */
		if (avoidPath != null) {
			return avoidPath;
		}
		return this.internalPathSelection(possList, false);
	}

	/**
	 * Method that handles actual BGP path selection. Slightly abbreviated, does
	 * AS relation, path length, then tie break.
	 * 
	 * @param possList
	 *            - the possible valid routes
	 * @return - the "best" of the valid routes by usual BGP metrics
	 */
	private BGPPath internalPathSelection(Collection<BGPPath> possList, boolean avoidDecoys) {
		BGPPath currentBest = null;
		int currentRel = -4;

		/*
		 * TODO while this might not be simple, in many cases I don't think we
		 * need to do this work over the whole list of routes, just the best and
		 * the changed/new/whatever doesn't work in the case of loss of best
		 * route obvi
		 */
		for (BGPPath tPath : possList) {

			/*
			 * If we're doing avoidance based on ignoring local preference, then
			 * for the first pass we're literally just going to throw out all
			 * routes that are NOT clean, this is corrected in path selection if
			 * this leaves us w/ no viable routes
			 */
			if (avoidDecoys && (this.avoidMode == AS.AvoidMode.LOCALPREF || this.avoidMode == AS.AvoidMode.LEGACY)) {
				if (tPath.containsAnyOf(this.avoidSet)) {
					continue;
				}
			}

			/*
			 * If we have no best path currently selected, the first one is best
			 * by default
			 */
			if (currentBest == null) {
				currentBest = tPath;
				currentRel = this.getRel(currentBest.getNextHop());
				continue;
			}

			/*
			 * Local pref based on relationship step
			 */
			int newRel = this.getRel(tPath.getNextHop());
			if (newRel > currentRel) {
				currentBest = tPath;
				currentRel = newRel;
				continue;
			}

			/*
			 * If local pref is the same, move on to the next critera
			 */
			if (newRel == currentRel) {
				/*
				 * If we're inserting the decision to route around decoys after
				 * local pref, but before path length, do so here
				 */
				if (this.avoidMode == AS.AvoidMode.PATHLEN) {
					if (avoidDecoys && currentBest.containsAnyOf(this.avoidSet) && !tPath.containsAnyOf(this.avoidSet)) {
						currentBest = tPath;
						currentRel = newRel;
						continue;
					}
					if (avoidDecoys && !currentBest.containsAnyOf(this.avoidSet) && tPath.containsAnyOf(this.avoidSet)) {
						continue;
					}
				}

				if (currentBest.getPathLength() > tPath.getPathLength()) {
					currentBest = tPath;
					currentRel = newRel;
					continue;
				} else if (currentBest.getPathLength() == tPath.getPathLength()) {
					if (avoidDecoys && this.avoidMode == AS.AvoidMode.TIEBREAK
							&& currentBest.containsAnyOf(this.avoidSet) && !tPath.containsAnyOf(this.avoidSet)) {
						currentBest = tPath;
						currentRel = newRel;
						continue;
					}
					if (avoidDecoys && this.avoidMode == AS.AvoidMode.TIEBREAK
							&& !currentBest.containsAnyOf(this.avoidSet) && tPath.containsAnyOf(this.avoidSet)) {
						continue;
					}

					if (tPath.getNextHop() < currentBest.getNextHop()) {
						currentBest = tPath;
						currentRel = newRel;
					}
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

		/*
		 * If we have a current best path to the destination, build a copy of
		 * it, apply export policy and advertise the route
		 */
		if (pathOfMerit != null) {
			BGPPath pathToAdv = pathOfMerit.deepCopy();

			/*
			 * Lying reverse poison, prepend deployer ASes
			 */
			if (this.poisonMode == AS.ReversePoisonMode.LYING && pathToAdv.getDest() == this.asn * -1) {
				for (Integer tAS : this.avoidSet) {
					pathToAdv.appendASToPath(tAS);
				}
			}
			pathToAdv.prependASToPath(this.asn);

			/*
			 * Special case to cover hole punching
			 */
			if (this.poisonMode == AS.ReversePoisonMode.HONEST && pathToAdv.getDest() == this.asn * -1) {
				for (AS tPeer : this.holepunchPeers) {
					tPeer.advPath(pathToAdv);
					newAdvTo.add(tPeer);
				}
			} else {
				/*
				 * Advertise to all of our customers
				 */
				for (AS tCust : this.customers) {
					tCust.advPath(pathToAdv);
					newAdvTo.add(tCust);
				}

				/*
				 * Check if it's our locale route (NOTE THIS DOES NOT APPLY TO
				 * HOLE PUNCHED ROUTES, so the getDest as opposed to the
				 * getDestinationAS _IS_ correct) or if we learned of it from a
				 * customer
				 */
				if (pathOfMerit.getDest() == this.asn
						|| (pathOfMerit.getDest() == this.asn * -1 && this.poisonMode == AS.ReversePoisonMode.LYING)
						|| (this.getRel(pathOfMerit.getNextHop()) == 1)) {
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
		}

		/*
		 * Handle the case where we had a route at one point, but have since
		 * lost any route, so obviously we should send a withdrawl
		 */
		if (prevAdvedTo != null) {
			prevAdvedTo.removeAll(newAdvTo);
			for (AS tAS : prevAdvedTo) {
				tAS.withdrawPath(this, dest);
			}
		}

		/*
		 * Update the adj-out-rib with the correct set of ASes we have
		 * adverstised the current best path to
		 */
		this.adjOutRib.put(dest, newAdvTo);
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

	public boolean hasCleanRoute(int dest) {
		if (!this.isWardenAS()) {
			throw new RuntimeException("Asked non-warden AS if it had a clean route!");
		}

		if (!this.routeStatusMap.containsKey(dest)) {
			return false;
		}

		return this.routeStatusMap.get(dest) == AS.RS_CLEAN;
	}

	/**
	 * Fetches the currently utilized path to the destination. Note, in some
	 * rare cases (LEGACY RAD ATTACK) this path will differ from what actually
	 * lives in the BGP table.
	 * 
	 * @param dest
	 *            - the ASN of the destination network
	 * @return - the current best path, or null if we have none
	 */
	public BGPPath getPath(int dest) {

		BGPPath installedPath = null;

		/*
		 * Hunt for the hole punched path first, if it exists return it
		 */
		installedPath = this.locRib.get(dest * -1);
		if (installedPath == null) {
			installedPath = this.locRib.get(dest);
		}

		if (this.avoidMode == AS.AvoidMode.LEGACY && this.isWardenAS()) {
			if (installedPath != null && this.routeStatusMap.get(dest) == AS.RS_CLEAN) {
				return installedPath;
			} else {
				if (this.routeStatusMap.get(dest) == AS.RS_LEGACY) {
					return this.mplsRoutes.get(dest);
				} else if (this.routeStatusMap.get(dest) == AS.RS_DIRTY) {
					BGPPath bestCabalPath = null;

					for (AS tWarden : this.wardenSet) {
						if (tWarden.getASN() != this.asn) {
							/*
							 * IMPORTANT NOTE, this has to be a call to the
							 * local rib NOT getPath, otherwise an infinite
							 * recursion can result...
							 */
							if (!(this.hasCleanRoute(tWarden.getASN()) && tWarden.hasCleanRoute(dest))) {
								continue;
							}

							BGPPath theirPath = tWarden.locRib.get(dest);
							BGPPath toThem = this.locRib.get(tWarden.getASN());

							if (bestCabalPath == null
									|| bestCabalPath.getPathLength() > theirPath.getPathLength()
											+ toThem.getPathLength()) {
								bestCabalPath = toThem.stichPaths(theirPath);
							}

						}
					}

					if (bestCabalPath != null) {
						this.mplsRoutes.put(dest, bestCabalPath);
						this.routeStatusMap.put(dest, AS.RS_LEGACY);
						return bestCabalPath;
					} else {
						this.routeStatusMap.put(dest, AS.RS_DIRTY_NO_LEGACY);
					}
				}
			}
		}

		return installedPath;
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
		List<BGPPath> listPossHolePunchedPaths = new LinkedList<BGPPath>();
		for (Integer tHook : hookASNs) {
			BGPPath tempPath = this.getPath(tHook);
			if (tempPath != null) {
				/*
				 * Sort pased on hole punched vs not hole punched
				 */
				if (tempPath.getDest() == tHook * -1) {
					listPossHolePunchedPaths.add(tempPath);
				} else {
					listPossPaths.add(tempPath);
				}
			}
		}

		/*
		 * If we have hole punched routes we'll use those over an aggregate path
		 */
		BGPPath returnPath = null;
		if (listPossHolePunchedPaths.size() > 0) {
			returnPath = this.pathSelection(listPossHolePunchedPaths);
			if (returnPath != null) {
				return returnPath;
			}
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
	public Collection<BGPPath> getAllPathsTo(int dest) {
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

	public String printDebugString() {
		return this.toString() + "\nIN RIB\n" + this.inRib + "\nLOCAL\n" + this.locRib.toString() + "\nADJ OUT\n"
				+ this.adjOutRib.toString();
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
	public int getNonPrunedCustomerCount() {
		return this.customers.size();
	}

	/**
	 * Function that marks this AS as part of the wardern
	 */
	public void toggleWardenAS(AvoidMode avoidMode, ReversePoisonMode rpMode) {
		this.wardenAS = true;
		this.avoidMode = avoidMode;
		this.poisonMode = rpMode;
		this.routeStatusMap = new TIntIntHashMap();
		if (this.avoidMode == AS.AvoidMode.LEGACY) {
			this.mplsRoutes = new TIntObjectHashMap<BGPPath>();
		}
	}

	/**
	 * Predicate to test if this AS is part of the warden.
	 * 
	 * @return - true if the AS is part of the warden, false otherwise
	 */
	public boolean isWardenAS() {
		return this.wardenAS;
	}

	public void setWardenSet(Set<AS> wardenASes) {
		this.wardenSet = wardenASes;
	}

	// TODO do we really save that much turning on and off active avoidance?
	// Maybe just simplify since doesn't govern runtime?
	public void turnOnActiveAvoidance(Set<Integer> avoidList) {
		this.avoidSet = avoidList;
		if (this.avoidSet.size() > 0) {
			this.activeAvoidance = true;
		} else {
			this.activeAvoidance = false;
		}
		this.activeAvoidance = true;
	}

	public void updateHolepunchSet(Set<AS> peersToHolePunchTo) {
		this.holepunchPeers.clear();
		this.holepunchPeers.addAll(peersToHolePunchTo);
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

	public int getRelationship(AS otherAS) {
		return this.getRelationship(otherAS.getASN());
	}

	/**
	 * Fetches the relationship THIS AS has with the other AS. To be clear if I
	 * am his provider, this should return provider, if I am his customer, this
	 * should return customer, etc...
	 * 
	 * @param otherASN
	 *            - the other AS that we are interested in THIS object's
	 *            relationship with
	 * @return - AS.PROVIDER if THIS AS is the provider of otherASN, AS.CUSTOMER
	 *         if THIS AS is the customer of otherASN, AS.PEER if they are peers
	 */
	public int getRelationship(int otherASN) {

		for (AS tAS : this.providers) {
			if (tAS.getASN() == otherASN) {
				return AS.CUSTOMER_CODE;
			}
		}
		for (AS tAS : this.peers) {
			if (tAS.getASN() == otherASN) {
				return AS.PEER_CODE;
			}
		}
		for (AS tAS : this.customers) {
			if (tAS.getASN() == otherASN) {
				return AS.PROIVDER_CODE;
			}
		}

		if (otherASN == this.asn) {
			return 2;
		}

		throw new IllegalArgumentException("asked for relation on non-adj/non-self asn, depending on sim "
				+ "this might be expected, if you're not, you should prob restart this sim...!");
	}

	/**
	 * Fetches the set of ASNs that THIS AS is directly connected to regardless
	 * of relationship which are part of the active routing topology.
	 * 
	 * @return the set of all ASNs THIS AS is directly connected to
	 */
	public Set<Integer> getActiveNeighbors() {
		HashSet<Integer> retSet = new HashSet<Integer>();

		for (AS tAS : this.providers) {
			retSet.add(tAS.getASN());
		}
		for (AS tAS : this.customers) {
			retSet.add(tAS.getASN());
		}
		for (AS tAS : this.peers) {
			retSet.add(tAS.getASN());
		}

		return retSet;
	}

	/**
	 * Fetches the set of all ASNs for ASes that are part of the purged topo
	 * which are adjacent to this AS
	 * 
	 * @return
	 */
	public Set<Integer> getPurgedNeighbors() {
		return this.purgedNeighbors;
	}

	/**
	 * Fetches the set of all ASNs for ASes that are directly connected to it on
	 * both active and purged topo
	 */
	public Set<Integer> getNeighbors() {
		HashSet<Integer> retSet = new HashSet<Integer>();
		retSet.addAll(this.getActiveNeighbors());
		retSet.addAll(this.purgedNeighbors);

		return retSet;
	}

	/**
	 * Fetches the amount of traffic that flows from THIS AS to otherASN. This
	 * is NOT the bi-directional traffic on the link, this is simply the traffic
	 * that flows from THIS to otherASN
	 * 
	 * @param otherASN
	 *            - the ASN of the neighbor that we want to find out how much
	 *            traffic flows from us to them
	 * @return - the amount of traffic in our very arbitrary "units" that
	 *         traveled from THIS AS to otherASN this round
	 */
	public double getTrafficOverLinkBetween(int otherASN) {
		return this.trafficOverNeighbors.get(otherASN);
	}

	public double getVolTraffic(int otherASN) {
		return this.volatileTraffic.get(otherASN);
	}

	/**
	 * update the traffic flowing over its given neighbor which is stored in a
	 * special map
	 * 
	 * atomic operation for parallelization
	 * 
	 * @param neighbor
	 * @param amountOfTraffic
	 * @param isVolatile
	 * @param isTransit
	 */
	public synchronized void updateTrafficOverOneNeighbor(int neighbor, double amountOfTraffic, boolean isVolatile) {
		this.trafficOverNeighbors.put(neighbor, this.trafficOverNeighbors.get(neighbor) + amountOfTraffic);

		if (isVolatile) {
			this.volatileTraffic.put(neighbor, this.volatileTraffic.get(neighbor) + amountOfTraffic);
		}
	}

	public void addVolatileDestionation(int volatileDest) {
		this.volatileDestinations.add(volatileDest);
	}

	public TIntHashSet getVolatileDestinations() {
		return this.volatileDestinations;
	}

	public void resetTraffic() {
		for (int tASN : this.trafficOverNeighbors.keys()) {
			this.trafficOverNeighbors.put(tASN, this.trafficOverNeighbors.get(tASN) - this.volatileTraffic.get(tASN));
			this.volatileTraffic.put(tASN, 0.0);
		}
	}

	public void addOnCustomerConeList(int asn) {
		this.customerConeASList.add(asn);
	}

	public Set<Integer> getCustomerConeASList() {
		return this.customerConeASList;
	}

	public int getCustomerConeSize() {
		return this.customerConeASList.size();
	}

	public void setCustomerIPCone(long custIPCone) {
		this.sizeOfCustomerIPCone = custIPCone;
	}

	public long getIPCustomerCone() {
		return this.sizeOfCustomerIPCone;
	}
}
