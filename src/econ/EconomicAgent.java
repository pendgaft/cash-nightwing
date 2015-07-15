package econ;

import gnu.trove.map.TIntObjectMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.io.BufferedWriter;

import decoy.DecoyAS;

/**
 * The extension of TransitAgent into an interface that actually does economic
 * behavior. While TransitAgent simply deals with who sent data to who and who
 * should be paying, this provides information flow the OTHER way. Telling ASes
 * that they made or lost money, and giving them a chance to act.
 * 
 * @author pendgaft
 * 
 */
public abstract class EconomicAgent implements TransitAgent {

	protected DecoyAS parent;
	protected BufferedWriter revenueLogStream;
	protected BufferedWriter pathStream;
	protected TIntObjectMap<DecoyAS> activeTopo;
	protected double moneyEarned;
	protected double transitIncome;
	
	public EconomicAgent(DecoyAS parentAS, BufferedWriter log, TIntObjectMap<DecoyAS> activeTopo, BufferedWriter pathLog){
		this.parent = parentAS;
		this.revenueLogStream = log;
		this.activeTopo = activeTopo;
		this.pathStream = pathLog;
	}
	
	/**
	 * Method for pushing to the AS type object exactly how much money it made
	 * or lost this round
	 * 
	 * @param moneyEarned
	 *            - the amount of money gained or lost this past round
	 */
	public void reportMoneyEarned(double moneyEarned, double transitEarned) {
		this.moneyEarned = moneyEarned;
		this.transitIncome = transitEarned;
	}

	/**
	 * Function that prompts the AS like object to make any changes to its
	 * behavior for this round. It will always be called AFTER pushing the
	 * amount of money gained or lost. This functions should NOT actually change
	 * anything, it should just stage changes, the finalize function will do the
	 * actual pushing of changes.
	 */
	public abstract void makeAdustments(Set<Integer> decoyRouterSet);

	/**
	 * Method for informing the AS type object to do any logging for this round,
	 * allows for object specific logging since the logging stream should
	 * ALREADY be passed to the object, so objects of different type can log to
	 * different files.
	 * 
	 */
	public abstract void doRoundLogging();

	/**
	 * Function that takes the changes staged by the makeAdjustments function
	 * and actually pushes them into the object itself, this way we don't have
	 * "weirdness" from some ASes getting to see what other ASes are going to do
	 * ahead of time.
	 */
	public abstract void finalizeRoundAdjustments();
	
	public int getRelationship(int otherASN){
		return this.parent.getRelationship(otherASN);
	}
	
	public Set<Integer> getNeighbors(){
		return this.parent.getNeighbors();
	}
	
	public double getTrafficOverLinkBetween(int otherASN){
		return this.parent.getTrafficOverLinkBetween(otherASN);
	}
	
	public double getTransitTrafficOverLink(int otherASN){
		return this.parent.getTransitTrafficOverLink(otherASN);
	}
	
	public double getDeliveryTrafficOverLink(int otherASN){
		return this.parent.getDeliveryTrafficOverLink(otherASN);
	}
	
	public boolean isPurged(){
		return this.parent.isPurged();
	}
	
	public void resetTraffic(){
		this.parent.resetTraffic();
	}
	
	protected Set<Integer> buildDecoySet() {
		HashSet<Integer> decoySet = new HashSet<Integer>();
		for (DecoyAS tAS : this.activeTopo.valueCollection()) {
			if (tAS.isDecoy()) {
				decoySet.add(tAS.getASN());
			}
		}
		return decoySet;
	}
}
