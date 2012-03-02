import java.util.*;

public class AS {
	private int asn;
	private Set<AS> customers;
	private Set<AS> peers;
	private Set<AS> providers;
	
	public static final int PROIVDER_CODE = -1;
	public static final int PEER_CODE = 0;
	public static final int CUSTOMER_CODE = 1;
	
	public AS(int asn) {
		this.asn = asn;
		customers = new HashSet<AS>();
		peers = new HashSet<AS>();
		providers = new HashSet<AS>();
	}
	
	public void addRelation(AS otherAS, int myRelationToThem) {
		if (myRelationToThem == AS.PROIVDER_CODE) {
			this.customers.add(otherAS);
			otherAS.providers.add(this);
		} else if (myRelationToThem == AS.PEER_CODE) {
			this.peers.add(otherAS);
			otherAS.peers.add(this);
		} else if (myRelationToThem == AS.CUSTOMER_CODE) {
			this.providers.add(otherAS);
			otherAS.customers.add(this);
		} else if (myRelationToThem == 3) {
			// ignore
		} else {
			System.err.println("WTF bad relation: " + myRelationToThem);
			System.exit(-1);
		}
	}
	
	public Set<AS> getCustomers() {
		return customers;
	}

	public Set<AS> getPeers() {
		return peers;
	}

	public Set<AS> getProviders() {
		return providers;
	}
	
	public int getASN() {
		return asn;
	}
	
	public int hashCode() {
		return asn;
	}
	
	public void summary() {
		System.out.println("ASN: " + asn);
		System.out.println("Customers: " + customers);
		System.out.println("Peers: " + peers);
		System.out.println("Providers: " + providers);
		System.out.println();
	}
}
