package decoy;

import java.util.*;
import java.io.*;

import topo.AS;
import topo.BGPPath;

/**
 * Class that implements a static deployment strategy which aims to encircle the
 * warden in a "ring" of decoy routers. Deployment can be done to rings various
 * "depths" (distances) away, and partial deployments can be done.
 * 
 * @author schuch
 * 
 */
public class Rings implements Seeder {

	/**
	 * Certain functions will attempt to look
	 */
	private static final int MAX_DEPTH = 20;

	private HashMap<Integer, DecoyAS> activeMap;
	private HashMap<Integer, DecoyAS> prunedMap;
	private HashSet<AS> wardenASes;

	private HashMap<Integer, DecoyAS> seederSet = null;
	private int size;

	public Rings(HashMap<Integer, DecoyAS> activeMap,
			HashMap<Integer, DecoyAS> prunedMap) {
		super();
		this.activeMap = activeMap;
		this.prunedMap = prunedMap;
		this.wardenASes = new HashSet<AS>();
		this.size = -1;

		for (AS tAS : this.activeMap.values()) {
			if (tAS.isWardenAS()) {
				this.wardenASes.add(tAS);
			}
		}
	}

	/**
	 * Method that does some basic computations on ring size and coverage for a
	 * given warden. This finds both the size of each ring (up to the max ring
	 * size) and computes how much of the internet sits behind a ring of this
	 * size. Results are written to a file at the end.
	 * 
	 * @param country
	 *            - the warden file for a given country
	 * @throws IOException
	 *             - if there is an error reading country file or writing
	 *             results
	 */
	public void runTests(String country) throws IOException {
		HashMap<Integer, HashSet<AS>> rings = this.computeRingSize(country);
		this.computeOutOfShadowSizes(rings, country);
	}

	/**
	 * Method that preps the seeder for functioning at a given ring depth. This
	 * pre-builds the ring, which sadly requires us to build the prior rings,
	 * but this cost is only paid once.
	 * 
	 * @param depth
	 *            - the depth of ring we'll be working at
	 */
	public void setupSeeder(int depth) {
		HashSet<AS> previous = new HashSet<AS>();
		HashSet<AS> visited = new HashSet<AS>();
		previous.addAll(this.wardenASes);

		/*
		 * Populates every ring
		 */
		for (int currDepth = 1; currDepth <= depth; currDepth++) {
			visited.addAll(previous);
			System.out.println("Size of previous: " + previous.size());
			previous = this.computeRingMembers(previous, visited);
		}

		/*
		 * Now build the actual ring we're going to use
		 */
		this.seederSet = new HashMap<Integer, DecoyAS>();
		for (AS tAS : previous) {
			this.seederSet.put(tAS.getASN(), (DecoyAS) tAS);
		}
	}

	/**
	 * Sets the number of decoy routers we are going to have returned by a call
	 * to seedDecoys.
	 * 
	 * @param decoySize
	 *            - the number of decoy routers we want in the system
	 */
	public void setDecoySeedSize(int decoySize) {
		this.size = decoySize;
	}

	/**
	 * Sets the number of decoy routers we are going to have in terms of a
	 * fraction of the selected ring size. This of course means that the ring
	 * must be build before calling this function, done with a call to
	 * setupSeeder.
	 * 
	 * @param fraction
	 *            - the fraction of the currently selected ring we want to be
	 *            decoy routers
	 * @return - the actual number of decoy routers that will be present
	 */
	public int setDecoySeedSizeFraction(double fraction) {
		this.size = (int) Math.ceil(this.seederSet.size() * fraction);
		/* Just in case screwyness of size * 1.0 = (size - 1) happens again */
		if (fraction == 1.0) {
			this.size = this.seederSet.size();
		}
		return this.size;
	}

	/**
	 * Before calling seedDecoys two functions MUST be called. setupSeeder needs
	 * to be called to ensure that we've got a valid ring selected. After that
	 * one of the setDecoySeedSize functions needs to be called to select the
	 * decoy router count.
	 */
	public Set<Integer> seedDecoys() {
		/*
		 * Catch if this isn't setup correctly yet.
		 */
		if (this.seederSet == null) {
			throw new RuntimeException(
					"Ring Seed Decoys called before a ring was selected!");
		}

		/*
		 * Sanity checks on size
		 */
		if (size > this.seederSet.size() || size == -1) {
			throw new RuntimeException("Invalid seed size: " + this.size
					+ " (ring size is: " + this.seederSet.size() + ")");
		}

		/*
		 * Reset decoy flags
		 */
		for (DecoyAS tAS : this.seederSet.values()) {
			tAS.resetDecoyRouter();
		}

		/*
		 * Yay selection that doesn't suffer from last block problem (thanks
		 * John!)
		 */
		Set<Integer> retSet;
		if (size < this.seederSet.size()) {
			List<Integer> randomList = new LinkedList<Integer>(this.seederSet
					.keySet());
			Collections.shuffle(randomList);
			retSet = new HashSet<Integer>(randomList.subList(0, size));
		} else {
			retSet = new HashSet<Integer>(this.seederSet.keySet());
		}
		for (Integer as : retSet) {
			this.seederSet.get(as).toggleDecoyRouter();
		}

		return retSet;
	}

	/**
	 * Function that computes the rings for a given warden. The results are
	 * written to logs directory in the <country file>-ringSize.csv file.
	 * 
	 * @param country
	 *            - the file holding the warden's ASNs
	 * @return - a mapping (by ring number) of ASes in a given ring.
	 * @throws IOException
	 *             - if there is an error writing to the output file
	 */
	private HashMap<Integer, HashSet<AS>> computeRingSize(String country)
			throws IOException {

		long startTime = System.currentTimeMillis();
		System.out.println("starting ring compuatation.");

		HashSet<AS> visited = new HashSet<AS>();
		HashMap<Integer, HashSet<AS>> rings = new HashMap<Integer, HashSet<AS>>();

		/*
		 * Start with ring "0" the warden ASes
		 */
		HashSet<AS> previous = new HashSet<AS>();
		previous.addAll(this.wardenASes);

		/*
		 * Populates every ring
		 */
		for (int depth = 1; depth < MAX_DEPTH; depth++) {
			visited.addAll(previous);
			previous = this.computeRingMembers(previous, visited);
			rings.put(depth, previous);
		}

		BufferedWriter outBuff = new BufferedWriter(new FileWriter("logs/"
				+ country + "-ringSize.csv"));
		for (int depth = 1; depth < 20; depth++) {
			outBuff.write("" + depth + "," + rings.get(depth).size() + "\n");
		}
		outBuff.close();

		startTime = System.currentTimeMillis() - startTime;
		System.out.println("Rings computed in: " + startTime / 1000
				+ " seconds.");

		return rings;
	}

	/**
	 * Function that computes a ring given the previous ring and all ASes that
	 * have been seen in rings prior.
	 * 
	 * @param previousRing
	 *            - the directly previous ring's members (AS objects)
	 * @param visited
	 *            - set of all AS objects that have been seen in any prior ring
	 * @return - the set of all ASes directly connected to the previousRing set
	 *         that were not in a prior ring
	 */
	private HashSet<AS> computeRingMembers(HashSet<AS> previousRing,
			HashSet<AS> visited) {
		HashSet<AS> nextRing = new HashSet<AS>();

		for (AS tAS : previousRing) {

			/*
			 * Grab all neighbors
			 */
			HashSet<AS> considerSet = new HashSet<AS>();
			considerSet.addAll(tAS.getCustomers());
			considerSet.addAll(tAS.getPeers());
			considerSet.addAll(tAS.getProviders());

			/*
			 * Prune out those that we've already seen or that are in the pruned
			 * portion of the topology
			 */
			considerSet.removeAll(visited);
			HashSet<AS> nonTransitSet = new HashSet<AS>();
			for (AS tConsider : considerSet) {
				if (this.prunedMap.containsKey(tConsider.getASN())) {
					nonTransitSet.add(tConsider);
				}
			}
			considerSet.removeAll(nonTransitSet);

			nextRing.addAll(considerSet);
		}

		return nextRing;
	}

	/**
	 * Computes the number of ASes that are NOT behind each ring, in otherwords,
	 * if the ring refused the warden service, this finds the ASes that WOULD
	 * still be reachable. This result is written to a file. This is reported as
	 * a fraction all ASes that are NOT the warden or in a given ring that are
	 * still reachable.
	 * 
	 * @param rings
	 *            - mapping of ring depths to AS objects found therein
	 * @param country
	 *            - the name of the country file used for the warden, used to
	 *            name the output file
	 * @throws IOException
	 *             - if there is an issue writting the output
	 */
	private void computeOutOfShadowSizes(HashMap<Integer, HashSet<AS>> rings,
			String country) throws IOException {
		HashMap<Integer, Double> ooShadowSizes = new HashMap<Integer, Double>();

		for (int counter = 1; counter < MAX_DEPTH; counter++) {
			long startTime = System.currentTimeMillis();
			System.out.println("computing shadow for ring: " + counter);
			ooShadowSizes.put(counter, this.evaluateShadow(rings.get(counter)));
			startTime = System.currentTimeMillis() - startTime;
			System.out.println("shadow comuation took " + startTime / 1000
					+ " seconds");
		}

		BufferedWriter outBuff = new BufferedWriter(new FileWriter("logs/"
				+ country + "-ooShadow.csv"));
		for (int counter = 1; counter < MAX_DEPTH; counter++) {
			outBuff.write("" + counter + "," + ooShadowSizes.get(counter)
					+ "\n");
		}
		outBuff.close();
	}

	/**
	 * Function that actually computes reachability with denial of service from
	 * a given ring. This is returned as a fraction of ASes that are NOT either
	 * part of the ring, or part of the warden.
	 * 
	 * @param ring
	 *            - the AS objects found in a given ring
	 * @return - the fraction of the internet (by AS count) that is still
	 *         reachable
	 */
	private double evaluateShadow(HashSet<AS> ring) {

		HashSet<Integer> ringASNs = AS.buildASNSet(ring);

		int consideredCount = 0;
		int outOfShadowCount = 0;
		for (AS tDest : this.activeMap.values()) {
			/*
			 * don't consider warden ASes and those in the ring
			 */
			if (tDest.isWardenAS() || ring.contains(tDest)) {
				continue;
			}

			consideredCount++;
			HashSet<BGPPath> allPaths = new HashSet<BGPPath>();
			for (AS tChina : this.wardenASes) {
				allPaths.addAll(tChina.getAllPathsTo(tDest.getASN()));
			}

			for (BGPPath tPath : allPaths) {
				if (!tPath.containsAnyOf(ringASNs)) {
					outOfShadowCount++;
					break;
				}
			}
		}

		return (double) outOfShadowCount / (double) consideredCount;
	}

}
