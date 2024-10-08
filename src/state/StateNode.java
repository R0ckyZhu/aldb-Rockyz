package state;

import alloy.AlloyConstants;
import alloy.AlloyUtils;
import alloy.ParsingConf;
import alloy.SigData;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import ca.uwaterloo.watform.core.DashStrings;
/**
 * StateNode represents a single execution state of an Alloy transition system.
 */
public class StateNode {
    private List<StateNode> steps; // outgoing edges (states that can be stepped to from this state)
    private SortedMap<String, List<String>> state; // the state that this node represents
    // can aldb support multi arity relations ??
    
    private int id;
    private ParsingConf parsingConf;
    private SigData sigData;
    private List<Integer> path; // redo with List<StateNode>

    public StateNode(SigData data, ParsingConf conf) {
    	//System.out.println("New state node created ");
        sigData = data;
        parsingConf = conf;
        steps = new ArrayList<>();
        state = new TreeMap<>();
        path = new ArrayList<>();
        for (String field : sigData.getFields()) {
        	//System.out.println(field);
            state.put(field, new ArrayList<>());
        }
    }
    //implement a function for look up in state map
    
    
    
    public boolean hasStable() {
    	if (state.get(DashStrings.stableName)!=null) { //refer to strings in dash core 
    		return true;
    	}
    	return false;
    }
    
    public boolean getStable() {
    	if (state.get("dsh_stable")!=null && state.get(DashStrings.stableName).contains(DashStrings.trueName)) {
    		return true;
    	}
    	return false;
    }
    
    public void printId() {
    	System.out.println(id);
    }
    
    public String getTransitionName(){
    	return String.join(", ", state.get(DashStrings.transTakenName+"0"));
    }
    
    public List<String> getControlStateNames(){
    	return state.get(DashStrings.confName+"0");
    }
    		
    public void addValueToField(String field, String value) {
        if (!state.containsKey(field)) {
            return;
        }

        // Ensure values are sorted upon insertion. For the following reasons:
        // 1. For user display.
        // 2. Enables comparison between StateNodes.
        List<String> valuesForField = state.get(field);
        for (int i = 0; i < valuesForField.size(); i++) {
            if (value.compareTo(valuesForField.get(i)) > 0) {
                continue;
            }

            valuesForField.add(i, value);
            return;
        }

        valuesForField.add(value);
    }
    
    public void storePath(List<Integer> input_path) {
    	path=input_path;
    }
    
    public List<Integer> getPath() {
    	return path;
    }

    public List<StateNode> getSteps() {
        return steps;
    }

    public void addStep(StateNode node) {
    	//System.out.println("Adding new node to step:");
    	//node.printId();
        steps.add(node);
    }

    @Override
    public String toString() {
        return String.format("\nS%d\n----%s", id, getStateString());
    }

    public String toHistoryString(int n) {
        return String.format("\nS%d (-%d)\n---------%s", id, n, getStateString());
    }

    public String stringForProperty(String property) {
        if (!state.containsKey(property)) {
            return "Property not found.";
        }

        return String.format(
            "\n%s %s %s\n",
            AlloyConstants.BLOCK_INITIALIZER,
            String.join(", ", state.get(property)),
            AlloyConstants.BLOCK_TERMINATOR
        );
    }

    /**
     * @param StateNode other
     * @return String representation of this, containing fields that are not equal to fields in other
     */
    public String getDiffString(StateNode other) {
        if (other == null) {
            return toString();
        }

        return String.format("\nS%d -> S%d\n------------%s", other.id, id, getDiffStateString(other));
    }

    public String getHistoryDiffString(StateNode other, int n) {
        if (other == null) {
            return toString();
        }

        return String.format("\nS%d -> S%d (-%d)\n-----------------%s", other.id, id, n, getDiffStateString(other));
    }

    /**
     * Two StateNodes are considered to be equivalent if they represent the exact same state.
     * @param other
     * @return true if this is the same as other, else false
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        final StateNode otherNode = (StateNode) other;
        return state.equals(otherNode.state);
    }

    /**
     * Generate an init predicate representing this instance's state in proper Alloy syntax.
     * @return String representation of the state in correct Alloy model syntax
     */
    public String getAlloyInitString() {
        StringBuilder sb = new StringBuilder();
        for (SortedMap.Entry<String, List<String>> entry : state.entrySet()) {
            List<String> vals = entry.getValue();
            StringBuilder alloyFormattedValsBuilder = new StringBuilder();
            String prefix = "";
            for (String val : vals) {
            	//System.out.println(val);
                alloyFormattedValsBuilder.append(prefix);
                prefix = String.format(" %s ", AlloyConstants.PLUS);
                String[] values = val.split(AlloyConstants.SET_DELIMITER);
                alloyFormattedValsBuilder.append(values[0]);
                for (int i = 1; i < values.length; i++) {
                    alloyFormattedValsBuilder.append(AlloyConstants.SET_DELIMITER);
                    alloyFormattedValsBuilder.append(values[i]);
                }
            }

            String value = (alloyFormattedValsBuilder.length() == 0) ?
                               AlloyUtils.getEmptyRelation(sigData.getArityForField(entry.getKey())) :
                               alloyFormattedValsBuilder.toString();
            sb.append(
                String.format(
                   "\ts.%s = %s\n",
                    entry.getKey(),
                    value
                )
            );
        }

        return AlloyUtils.makeStatePredicate(parsingConf.getInitPredicateName(), parsingConf.getStateSigName(), sb.toString());
    }

    public int getIdentifier() {
        return id;
    }

    public void setIdentifier(int id) {
        this.id = id;
    }
    
    public void printState() {
    	for (String key : state.keySet()) {
        	System.out.println(key);
        	System.out.println(state.get(key));
        }
    }
    
    public String getStateString() {
        StringBuilder sb = new StringBuilder();
        //System.out.println("calling getstatestring function");
        //printState();
        for (String key : state.keySet()) {
        	//System.out.println(key);
            sb.append(String.format("\n%s: %s ", key, AlloyConstants.BLOCK_INITIALIZER));
            //System.out.println(sb.toString());
            sb.append(String.format("%s %s", String.join(", ", state.get(key)), AlloyConstants.BLOCK_TERMINATOR));
        }
        sb.append("\n");
        
        return sb.toString();
    }

    private String getDiffStateString(StateNode other) {
        SortedMap<String, List<String>> otherState = other.state;

        StringBuilder sb = new StringBuilder();
        for (String key : state.keySet()) {
            if (!otherState.containsKey(key)) {
                continue;
            }

            List<String> thisKeyContents = state.get(key);
            List<String> otherKeyContents = otherState.get(key);

            if (thisKeyContents.equals(otherKeyContents)) {
                continue;
            }

            sb.append(String.format("\n%s: %s ", key, AlloyConstants.BLOCK_INITIALIZER));
            sb.append(String.format("%s %s", String.join(", ", state.get(key)), AlloyConstants.BLOCK_TERMINATOR));
        }
        sb.append("\n");
        return sb.toString();
    }
}
