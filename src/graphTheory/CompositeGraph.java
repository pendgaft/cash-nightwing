package graphTheory;

import java.util.*;

public class CompositeGraph {

	private HashMap<String, CompositeNode<Integer>> nodeMap;
	private HashSet<String> compositeNameSet;
	
	public CompositeGraph(){
		this.nodeMap = new HashMap<String, CompositeNode<Integer>>();
		this.compositeNameSet = new HashSet<String>();
	}
	
	public void addNewNode(String name, String parentName){
		if(this.nodeMap.containsKey(name)){
			throw new RuntimeException("Already have a node with that name!");
		}
		
		this.nodeMap.put(name, new CompositeNode<Integer>(name, parentName));
		this.compositeNameSet.add(parentName);
	}
	
	public CompositeNode<Integer> getNodeByName(String name){
		return this.nodeMap.get(name);
	}
	
	public Set<String> getNodesPartOf(Integer testValue){
		HashSet<String> matchingNodes = new HashSet<String>();
		for(CompositeNode<Integer> tNode: nodeMap.values()){
			if(tNode.getData().equals(testValue)){
				matchingNodes.add(tNode.getCompositeName());
			}
		}
		
		return matchingNodes;
	}
	
	public void resetValues(Integer resetValue){
		for(CompositeNode<Integer> tNode: nodeMap.values()){
			tNode.setData(resetValue);
		}
	}
}
