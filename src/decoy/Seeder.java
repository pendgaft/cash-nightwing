package decoy;

import java.util.Set;

/**
 * Interface that should be implemented by all classes that contain code
 * intended to decided where to place decoy routers. This is for static
 * strategies, NOT dynamic strategies, so this should NOT be used for economic
 * deployments in the future.
 * 
 * @author pendgaft
 * 
 */
public interface Seeder {

	/**
	 * Seeds decoy routers. Assumes that all information has been pre-propagated
	 * to the object doing this (e.g. refs to AS objects and how many ASes we're
	 * looking for). This function MUST have the side effect of throwing the
	 * decoy flags.
	 * 
	 * @return - a set containing the ASNs of all ASes that have deployed decoy
	 *         routers
	 */
	public Set<Integer> seedDecoys();
}
