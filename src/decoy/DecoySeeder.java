package decoy;

import java.util.*;

/**
 * Class that implements a random AS based deployment strategy. Basically, this
 * class implements the deployment strategy put forward in Cirripede (well, one
 * of them at least).
 * 
 * @author pendgaft
 * 
 */
public class DecoySeeder implements Seeder {

	/**
	 * The number of decoys we wish to deploy
	 */
	private int decoyCount;

	/**
	 * Map of ASes that are part of our actively router topology.
	 */
	private HashMap<Integer, DecoyAS> liveAS;

	/**
	 * Map of ASes that have been purged from our routed topology.
	 */
	private HashMap<Integer, DecoyAS> purgedAS;

	/**
	 * Flag for ensuring that only transit ASes get selected to deploy decoy
	 * routers (sane strategy for the decoy router deployer).
	 */
	private boolean onlyTransit;

	/**
	 * Flag for removing ASes directly connected to the warden from
	 * consideration. This represents a business/policy decision on their part.
	 */
	private boolean noWardenNeighbor;

	/**
	 * Constructor that sets up the decoy seeder for functionality
	 * 
	 * @param count
	 *            - The number of decoys we wish to deploy
	 * @param liveAS
	 *            - Map of ASes that are part of our actively router topology.
	 * @param purgedAS
	 *            - Map of ASes that have been purged from our routed topology.
	 * @param onlyTranist
	 *            - Flag for ensuring that only transit ASes get selected
	 * @param noWardenNeighbor
	 *            - Flag for removing ASes directly connected to the warden from
	 *            consideration.
	 */
	public DecoySeeder(int count, HashMap<Integer, DecoyAS> liveAS, HashMap<Integer, DecoyAS> purgedAS,
			boolean onlyTranist, boolean noWardenNeighbor) {
		this.decoyCount = count;
		this.liveAS = liveAS;
		this.purgedAS = purgedAS;
		this.onlyTransit = onlyTranist;
		this.noWardenNeighbor = noWardenNeighbor;
	}

	public Set<Integer> seedDecoys() {
		Random rng = new Random();

		/*
		 * Sanity check that ASes are set to false
		 */
		for (DecoyAS tAS : liveAS.values()) {
			tAS.resetDecoyRouter();
		}
		for (DecoyAS tAS : purgedAS.values()) {
			tAS.resetDecoyRouter();
		}

		/*
		 * Step through seeding decoy routing ASes
		 */
		HashSet<Integer> markedSet = new HashSet<Integer>();
		while (markedSet.size() < this.decoyCount) {
			/*
			 * Get a random ASN, we'll see if it hasn't been picked, exists, and
			 * meets our critera (yes there are better ways to do this (read:
			 * more efficient), but meh, this will get us to a valid deploy,
			 * just a little slowly)
			 */
			int test = rng.nextInt(40000);
			if (markedSet.contains(test)) {
				continue;
			}

			/*
			 * Hunt in transit ASes for our ASN, if not there, check the
			 * customer ASNs
			 */
			DecoyAS focus = null;
			focus = liveAS.get(test);
			if (focus == null) {
				focus = purgedAS.get(test);
			}

			/*
			 * We picked an ASN randomly that doesn't exist...
			 */
			if (focus == null) {
				continue;
			}

			/*
			 * China doesn't deploy DRs....
			 */
			if (focus.isWardenAS()) {
				continue;
			}

			/*
			 * Clause to prevent us for selecting non-transit ASes as deployment
			 * points for deflecting routers
			 */
			if (onlyTransit && !liveAS.containsKey(test)) {
				continue;
			}

			/*
			 * Clause to prevent us from selecting a provider that directly
			 * abutts China as a decoy, as they have a STRONG economic incentive
			 * to not do such
			 */
			if (noWardenNeighbor && focus.connectedToWarden()) {
				continue;
			}

			/*
			 * Yay! It is valid, mark it as such and record that fact
			 */
			focus.toggleDecoyRouter();
			markedSet.add(test);
		}

		return markedSet;
	}

}
