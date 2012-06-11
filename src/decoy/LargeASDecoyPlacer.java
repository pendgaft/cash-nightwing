package decoy;

import java.util.*;

/**
 * This class does static deployments based on only selecting large degree ASes.
 * This SHOULD implements Seeder, but hey, I'm super lazy. This class can do two
 * different types of deployments, sequential (deploy to largest, then second,
 * then thrid, et...) and cumulative (deploy to largest, and second, and thrid,
 * etc...)
 * 
 * @author schuch
 * 
 */
public class LargeASDecoyPlacer {

	/**
	 * The hash map of active (routing, non-pruned, whatever) ASes in the
	 * topoogy
	 */
	private HashMap<Integer, DecoyAS> transitMap;

	public LargeASDecoyPlacer(HashMap<Integer, DecoyAS> activeMap) {
		this.transitMap = activeMap;
	}

	/**
	 * This method does the sequential deployment of decoy routers to large
	 * ASes. This has the side effect of turning on and off decoy routing flags
	 * in AS objects.
	 * 
	 * @param currentStep
	 *            - the nth largest AS (0 indexed), e.g. if 0 is supplied the
	 *            largest AS is picked, if 1 is supplied the second largest is
	 *            picked, etc
	 * @return - a set containing a single ASN corrisponding to the size given
	 *         by the param
	 */
	public Set<Integer> seedSingleDecoyBySize(int currentStep) {

		this.reset();

		HashSet<Integer> consideredSet = new HashSet<Integer>();
		while (consideredSet.size() < currentStep) {
			int nextAS = this.findNextLargest(consideredSet);
			consideredSet.add(nextAS);
		}

		HashSet<Integer> seededSet = new HashSet<Integer>();
		int pickedASN = this.findNextLargest(consideredSet);
		this.transitMap.get(pickedASN).toggleDecoyRouter();
		seededSet.add(pickedASN);
		return seededSet;
	}

	/**
	 * Method that does cumulative deployment to the N largest ASes. This will
	 * always start with the largest AS. This has the side effect of turning on
	 * and off decoy routing flags in DecoyAS objects.
	 * 
	 * @param deploySize
	 *            - the number of ASes to deploy decoy routing to
	 * @return - a set of the ASNs of the deploySize largest ASes
	 */
	public Set<Integer> seedNLargest(int deploySize) {

		this.reset();

		HashSet<Integer> seededSet = new HashSet<Integer>();
		while (seededSet.size() < deploySize) {
			int nextLargest = this.findNextLargest(seededSet);
			this.transitMap.get(nextLargest).toggleDecoyRouter();
			seededSet.add(nextLargest);
		}

		return seededSet;
	}

	/**
	 * Internal function to reset all DecoyAS flags to non-decoy routing status.
	 */
	private void reset() {
		/*
		 * clear out the old run
		 */
		for (DecoyAS tAS : this.transitMap.values()) {
			tAS.resetDecoyRouter();
		}
	}

	/**
	 * Internal method that finds the largest non-warden AS (by degree) for all
	 * active ASes, excluding those in the provided set.
	 * 
	 * @param considered
	 *            - the set of ASes that are off limits
	 * @return - the ASN of the largest non-warden AS not in the provided set
	 */
	private int findNextLargest(HashSet<Integer> considered) {
		int currMax = 0;
		int currASN = -1;

		for (DecoyAS tAS : this.transitMap.values()) {
			if (considered.contains(tAS.getASN())) {
				continue;
			}
			if (tAS.isWardenAS()) {
				continue;
			}

			if (tAS.getDegree() > currMax) {
				currMax = tAS.getDegree();
				currASN = tAS.getASN();
			}
		}

		return currASN;
	}

}
