package decoy;
import topo.AS;

/**
 * this is a Supper AS class which inherits AS class,
 * and add a boolean flag. more functions can be added.
 *   
 * @author bobo
 */
public class SuperAS extends AS {
	/**
	 * Flag for the super AS
	 */
	private boolean isSuperAS;
	
	public SuperAS(int myASN) {
		super(myASN);
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


