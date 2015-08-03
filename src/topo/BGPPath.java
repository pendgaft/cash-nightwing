package topo;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

import java.io.Serializable;
import java.util.*;

/**
 * Class that represents a BPG route in a RIB/Update message.
 * 
 * @author pendgaft
 * 
 */
public class BGPPath implements Serializable {

	/**
	 * Serialization ID
	 */
	private static final long serialVersionUID = 5527061905060650245L;

	private int destASN;
	private TIntArrayList path;

	public static final int CABAL_PATH = 1;

	public BGPPath(int dest) {
		this.destASN = dest;
		this.path = new TIntArrayList(1);
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
	public boolean containsAnyOf(Set<Integer> testASNs) {
		for (int tASN : testASNs) {
			if (this.path.contains(tASN)) {
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
	// XXX make this a clone, as this seems a bit dangerous
	public TIntList getPath() {
		return this.path;
	}

	//TODO stitches with lying revese?
	public BGPPath stichPaths(BGPPath secondPath) {
		BGPPath resultantPath = new BGPPath(secondPath.destASN);
		resultantPath.path.ensureCapacity(this.path.size() + secondPath.path.size());

		TIntIterator pathIter = this.path.iterator();
		while (pathIter.hasNext()) {
			resultantPath.path.add(pathIter.next());
		}
		pathIter = secondPath.path.iterator();
		while (pathIter.hasNext()) {
			resultantPath.path.add(pathIter.next());
		}

		return resultantPath;
	}

	/**
	 * Prepends the given ASN to the path, used to extend paths for
	 * advertisement.
	 * 
	 * @param frontASN
	 *            - the ASN to be added to the front of the path
	 */
	public void prependASToPath(int frontASN) {
		this.path.insert(0, frontASN);
	}

	/**
	 * Applies the given AS to the END of the path. THIS SHOULD ONLY BE USED FOR
	 * LYING REVERSE HOLE PUNCHES.
	 * 
	 * @param lyingASN
	 */
	public void appendASToPath(int lyingASN) {
		this.path.add(lyingASN);
	}

	/**
	 * Predicate that tests if the given ASN is found in the path.
	 * 
	 * @param testASN
	 *            - the ASN to check for looping to
	 * @return - true if the ASN appears in the path already, false otherwise
	 */
	public boolean containsLoop(int testASN) {
		return this.path.contains(testASN);
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
			return this.getDestinationASN();
		}

		return this.path.get(0);
	}

	/**
	 * Fetches the destination network.
	 * 
	 * @return - the ASN of the AS that originated the route
	 */
	public int getDest() {
		return this.destASN;
	}

	public int getDestinationASN() {
		return Math.abs(this.destASN);
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
		newPath.path.ensureCapacity(this.path.size() + 1);
		for (int counter = 0; counter < this.path.size(); counter++) {
			newPath.path.add(this.path.get(counter));
		}
		return newPath;
	}

	public String toString() {
		return "dst: " + this.destASN + " path:" + this.path.toString();
	}

	public String getLoggingString() {
		StringBuilder tBuilder = new StringBuilder();
		TIntIterator tIter = this.path.iterator();
		while (tIter.hasNext()) {
			int tAS = tIter.next();
			tBuilder.append(" ");
			tBuilder.append(tAS);
		}
		return tBuilder.toString();
	}

	/**
	 * Hash code based on hash code of the print string
	 */
	public int hashCode() {
		return this.path.sum() * this.destASN;
	}
}
