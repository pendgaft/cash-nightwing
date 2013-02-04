package topo;

import java.util.*;

/**
 * Class that represents a BPG route in a RIB/Update message.
 * 
 * @author pendgaft
 * 
 */
public class BGPPath {

	private int destASN;
	private LinkedList<Integer> path;

	public BGPPath(int dest) {
		this.destASN = dest;
		this.path = new LinkedList<Integer>();
	}

	/**
	 * Predicate that tests if any of a set of ASNs are found in the path.
	 * 
	 * This was from Nightwing, not sure if it is still needed here.
	 * 
	 * param testASNs - the ASNs to check for existence in the path
	 * 
	 * @return - true if at least one of the ASNs found in testASNs is in the
	 *         path, false otherwise
	 */
	public boolean containsAnyOf(HashSet<Integer> testASNs) {
		for (int tHop : this.path) {
			if (testASNs.contains(tHop)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns the path length in ASes
	 * 
	 * @return - length of the path
	 */
	public int getPathLength() {
		return this.path.size();
	}

	/**
	 * Getter that fetches the List of ASNs on the path. This is a DIRECT
	 * REFERENCE, so for the love of god don't edit it...
	 * 
	 * @return - a direct reference to the list of asns that comprise the path
	 */
	//TODO make this a clone, as this seems a bit dangerous
	public List<Integer> getPath() {
		return this.path;
	}

	/**
	 * Prepends the given ASN to the path, used to extend paths for
	 * advertisement.
	 * 
	 * @param frontASN
	 *            - the ASN to be added to the front of the path
	 */
	public void prependASToPath(int frontASN) {
		this.path.addFirst(frontASN);
	}

	/**
	 * Predicate that tests if the given ASN is found in the path.
	 * 
	 * @param testASN
	 *            - the ASN to check for looping to
	 * @return - true if the ASN appears in the path already, false otherwise
	 */
	public boolean containsLoop(int testASN) {
		for (int tASN : this.path) {
			if (tASN == testASN) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Fetches the next hop used in the route.
	 * 
	 * @return - the next hop in the route, ourself if we're the originating AS
	 */
	public int getNextHop() {
		/*
		 * hack for paths to ourself
		 */
		if (this.path.size() == 0) {
			return this.destASN;
		}

		return this.path.getFirst();
	}

	/**
	 * Fetches the destination network.
	 * 
	 * @return - the ASN of the AS that originated the route
	 */
	public int getDest() {
		return this.destASN;
	}

	/**
	 * Predicate to test if two routes are the same route. This tests that the
	 * destinations are identical and that the paths used are identical. All
	 * comparisons are done based off of ASN.
	 * 
	 * @param rhs
	 *            - the second route to test against
	 * @return - true if the routes have the same destination and path, false
	 *         otherwise
	 */
	public boolean equals(BGPPath rhs) {
		if (rhs.path.size() != this.path.size() || rhs.destASN != this.destASN) {
			return false;
		}

		for (int counter = 0; counter < this.path.size(); counter++) {
			if (this.path.get(counter) != rhs.path.get(counter)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Creates a deep copy of the given BGP route.
	 * 
	 * @return - a copy of the BGP route with copies of all class vars
	 */
	public BGPPath deepCopy() {
		BGPPath newPath = new BGPPath(this.destASN);
		for (int counter = 0; counter < this.path.size(); counter++) {
			newPath.path.addLast(this.path.get(counter));
		}
		return newPath;
	}

	public String toString() {
		String base = "dst: " + this.destASN + " path:";
		for (int tAS : this.path) {
			base = base + " " + tAS;
		}
		return base;
	}

	/**
	 * Hash code based on hash code of the print string
	 */
	public int hashCode() {
		return this.toString().hashCode();
	}
}
