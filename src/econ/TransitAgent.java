package econ;

import java.util.Set;

/**
 * The interface that should be implemented by "AS" type objects that send
 * network traffic. This provides three basic chunks of functionality to the
 * economic code. While some of these functions are going to be totally basic
 * call through to the correct AS object, this interface will decouple the way
 * the economic code sees AS objects and what is happening in other places in
 * the code. (Yay for proper object oriented coding practices!)
 * 
 * @author pendgaft
 * 
 */
public interface TransitAgent {

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
	public int getRelationship(int otherASN);

	/**
	 * Fetches the set of ASNs that THIS AS is directly connected to regardless
	 * of relationship.
	 * 
	 * @return the set of all ASNs THIS AS is directly connected to
	 */
	public Set<Integer> getNeighbors();

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
	public double getTrafficFromMeToAS(int otherASN);
}
