package econ;

/**
 * The extension of TransitAgent into an interface that actually does economic
 * behavior. While TransitAgent simply deals with who sent data to who and who
 * should be paying, this provides information flow the OTHER way. Telling ASes
 * that they made or lost money, and giving them a chance to act.
 * 
 * @author pendgaft
 * 
 */
public interface EconomicAgent extends TransitAgent {

	/**
	 * Method for pushing to the AS type object exactly how much money it made
	 * or lost this round
	 * 
	 * @param moneyEarned
	 *            - the amount of money gained or lost this past round
	 */
	public void reportMoneyEarned(double moneyEarned);

	/**
	 * Function that prompts the AS like object to make any changes to its
	 * behavior for this round. It will always be called AFTER pushing the
	 * amount of money gained or lost. This functions should NOT actually change
	 * anything, it should just stage changes, the finalize function will do the
	 * actual pushing of changes.
	 */
	public void makeAdustments();

	/**
	 * Method for informing the AS type object to do any logging for this round,
	 * allows for object specific logging since the logging stream should
	 * ALREADY be passed to the object, so objects of different type can log to
	 * different files.
	 * 
	 */
	public void doRoundLogging();

	/**
	 * Function that takes the changes staged by the makeAdjustments function
	 * and actually pushes them into the object itself, this way we don't have
	 * "weirdness" from some ASes getting to see what other ASes are going to do
	 * ahead of time.
	 */
	public void finalizeRoundAdjustments();
}
