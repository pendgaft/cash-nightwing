package graphTheory;

import java.util.*;
import java.io.*;

public class SimpleAS {

	private HashSet<Integer> customers;
	private HashSet<Integer> peers;
	private HashSet<Integer> providers;

	private int myASN;

	public SimpleAS(int asn) {
		this.myASN = asn;

		this.customers = new HashSet<Integer>();
		this.peers = new HashSet<Integer>();
		this.providers = new HashSet<Integer>();
	}

	public void addCustomer(SimpleAS myNewCustomer) {
		this.customers.add(myNewCustomer.getASN());
		myNewCustomer.providers.add(this.getASN());
	}
	
	public Set<Integer> getCustomers(){
		return this.customers;
	}

	public void addPeer(SimpleAS myNewPeer) {
		this.peers.add(myNewPeer.getASN());
		myNewPeer.peers.add(this.getASN());
	}
	
	public Set<Integer> getPeers(){
		return this.peers;
	}

	public void addProvider(SimpleAS myNewProvider) {
		this.providers.add(myNewProvider.getASN());
		myNewProvider.customers.add(this.getASN());
	}
	
	public Set<Integer> getProviders(){
		return this.providers;
	}

	public int getASN() {
		return this.myASN;
	}
	
	public int hashCode(){
		return this.myASN;
	}
	
	public boolean equals(Object rhs){
		if (!(rhs instanceof SimpleAS)){
			return false;
		}
		
		SimpleAS rhsVert = (SimpleAS)rhs;
		return this.myASN == rhsVert.myASN;
	}

	public static HashMap<Integer, SimpleAS> parseFromFile(String fileName) throws IOException {
		HashMap<Integer, SimpleAS> returnMap = new HashMap<Integer, SimpleAS>();

		BufferedReader fileBuffer = new BufferedReader(new FileReader(fileName));
		String line = null;
		while ((line = fileBuffer.readLine()) != null) {
			line = line.trim();
			if (line.length() == 0 || line.charAt(0) == '#') {
				continue;
			}

			String[] tokens = line.split("|");
			int lhsAS = Integer.parseInt(tokens[0]);
			int rhsAS = Integer.parseInt(tokens[1]);
			int rel = Integer.parseInt(tokens[2]);

			if (rel > 1 || rel < -1) {
				continue;
			}

			if (!returnMap.containsKey(lhsAS)) {
				returnMap.put(lhsAS, new SimpleAS(lhsAS));
			}
			if (!returnMap.containsKey(rhsAS)) {
				returnMap.put(rhsAS, new SimpleAS(rhsAS));
			}

			if (rel == -1) {
				returnMap.get(lhsAS).addCustomer(returnMap.get(rhsAS));
			} else if (rel == 0) {
				returnMap.get(lhsAS).addPeer(returnMap.get(rhsAS));
			} else if (rel == 1) {
				returnMap.get(lhsAS).addProvider(returnMap.get(rhsAS));
			}
		}
		
		fileBuffer.close();
		return returnMap;
	}
	
	public static CompositeGraph convertToCompositeGraph(HashMap<Integer, SimpleAS> asObjects){
		/*
		 * Generate verticies
		 */
		CompositeGraph returnGraph = new CompositeGraph();
		for(SimpleAS tAS: asObjects.values()){
			String base = "" + tAS.myASN;
			returnGraph.addNewNode(base + "-peer", base);
			returnGraph.addNewNode(base + "-provider", base);
			returnGraph.addNewNode(base + "-customer", base);
		}
		
		/*
		 * Generate directed edges
		 */
		for(SimpleAS tAS: asObjects.values()){
			String base = "" + tAS.myASN;
			/*
			 * Customers learn about all of our routes
			 */
			for(Integer tCustomer: tAS.customers){
				CompositeNode<Integer> custVert = returnGraph.getNodeByName(tCustomer + "-customer");
				returnGraph.getNodeByName(base + "-peer").connectToNeighbor(custVert);
				returnGraph.getNodeByName(base + "-customer").connectToNeighbor(custVert);
				returnGraph.getNodeByName(base + "-provider").connectToNeighbor(custVert);
			}
			
			/*
			 * Routes we learn about as a provider we send to our peers and our providers
			 */
			for(Integer tPeer: tAS.peers){
				returnGraph.getNodeByName(base + "-provider").connectToNeighbor(returnGraph.getNodeByName(tPeer + "-peer"));
			}
			for(Integer tProvider: tAS.providers){
				returnGraph.getNodeByName(base + "-provider").connectToNeighbor(returnGraph.getNodeByName(tProvider + "-provider"));
			}
		}
		
		return returnGraph;
	}
}
