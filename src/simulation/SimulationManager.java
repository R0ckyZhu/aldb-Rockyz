package simulation;
import core.DashGUI;
import core.DashImageDisplay;
import core.ImageDisplay;
import core.JsonDrawing;
import core.AlloyGUI;
import state.StateGraph;
import state.StateNode;
import state.StatePath;

import alloy.AlloyConstants;
import alloy.AlloyInterface;
import alloy.AlloyUtils;
import alloy.ParsingConf;
import alloy.SigData;
import ca.uwaterloo.watform.core.DashUtilFcns;
import ca.uwaterloo.watform.mainfunctions.MainFunctions;
import ca.uwaterloo.watform.parser.DashModule;
import commands.CommandConstants;
import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.ast.Sig;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.translator.A4Tuple;

import java.io.IOException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.yaml.snakeyaml.error.YAMLException;
import javax.swing.*;
public class SimulationManager {
    private final static String TEMP_FILENAME_PREFIX = "_tmp_";

    private File alloyModelFile;
    private String alloyModelString;
    private String alloyInitString;
    private SortedMap<String, List<String>> scopes;
    private ParsingConf persistentParsingConf;  // Set by set conf - used across multiple models.
    private ParsingConf embeddedParsingConf;  // Set by load - used for the current model only.
    private SigData stateSigData;
    private StatePath statePath;
    private StateGraph stateGraph;
    private Stack<A4Solution> activeSolutions;
    private AliasManager aliasManager;
    private ConstraintManager constraintManager;
    private GraphPrinter gp;
    private ImageDisplay display;
    private AlloyGUI stateTreeViewer;
    private boolean traceMode;
    private boolean diffMode;
    
    
    
    
    
    public SimulationManager() {
        scopes = new TreeMap<>();
        statePath = new StatePath();
        stateGraph = new StateGraph();
        persistentParsingConf = new ParsingConf();
        embeddedParsingConf = null;
        activeSolutions = new Stack<>();
        aliasManager = new AliasManager();
        constraintManager = new ConstraintManager();
        traceMode = false;
        diffMode = true;
        
    }
    
    // checks if a given label represents a state, e.g. is s/S followed by a digit
    public boolean isState(String label) {
    	String regex = "^[sS]\\d+$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(label);
        return matcher.matches();
    }
    
    
    
    //implements logic for handling clicks on transitions/states in GUI
    public void handleClick(String label) { //
    	
    	if ((isState(label))) {
    		String identifier = label.substring(1);
    		moveToState(Integer.parseInt(identifier));
    		return;
    	}
    	
    }
    
    
    public void savePath() { //saves the path for the current node
    	if (statePath.getCurNode()!=null) {
	    	StateNode curr_node_from_path = statePath.getCurNode();
	    	StateNode curr_node=stateGraph.getNodeById(curr_node_from_path.getIdentifier());
	    	if (curr_node.getPath().size()==0) {
	    		curr_node.storePath(statePath.getPath());
	    		//System.out.println(curr_node.getPath());
	    	}
    	}
    }
 
    //builds the .dot file using the information stored in stateGraph and generates the .png image
    public void printGraph() {
    	gp = new GraphPrinter();
    	for (int i = 0; i < stateGraph.size(); i++) {
            StateNode node = stateGraph.getNode(i);
            if (node.getSteps().size()!=0) {
	            for (StateNode next_node : node.getSteps()) {
	            	if (next_node.getTransitionName().isEmpty()) {
	            		gp.addln("S"+Integer.toString(node.getIdentifier()) + " -> " + "S"+Integer.toString(next_node.getIdentifier()));
	            	}
	            	else {
	            		gp.addln("S"+Integer.toString(node.getIdentifier()) + " -> " + "S"+Integer.toString(next_node.getIdentifier())+"[label="+next_node.getTransitionName() +"]");
	            	}
	            	
	            }
            }
            else {
            	gp.addln("S"+Integer.toString(node.getIdentifier()));
            }
            if (node.hasStable()) {
	            if (node.getStable()) {
	        		gp.addln("S"+Integer.toString(node.getIdentifier()) + "[color=green]");
	        	}
	            else {
	            	gp.addln("S"+Integer.toString(node.getIdentifier()) + "[color=red]");
	            }
            }
        }
    	if (statePath.getCurNode()!=null) {
	    	StateNode curr_node = statePath.getCurNode();
	    	gp.addln("S"+Integer.toString(curr_node.getIdentifier())+ "[style=filled, fillcolor=yellow]"); //highlight curr node
	    	
	    	
    	}
	    gp.print("state_tree");
	    gp.generateJson("state_tree");
	    
    }
    
    public void showStateTreeJson() { // starts/refreshes the GUI displaying the interactive state tree
    	
    	if (stateTreeViewer==null) {
    		stateTreeViewer = new AlloyGUI("state_tree.json",this);
    		stateTreeViewer.refreshJsonWithDelay(500);
    		return;
    	}
    	stateTreeViewer.refreshJsonWithDelay(500);
    	
    }
    
    public void loadImage() {
    	
	    showStateTreeJson();
	    if (this.display==null) {
	 	   	this.display=new ImageDisplay();
	 	}
	    display.refresh();
	    display.setVisible(true);
    }
    
    public boolean isTrace() {
        return traceMode;
    }

    public void setDiffMode(boolean b) {
        diffMode = b;
    }

    public boolean isDiffMode() {
        return diffMode;
    }

    /**
     * isInitialized returns True iff a model or trace has been loaded.
     * @return boolean
     */
    public boolean isInitialized() {
        return !statePath.isEmpty();
    }

    public void setParsingConf(ParsingConf conf) {
        persistentParsingConf = conf;
    }
    
    // move to a specified state using the state path stored (which represents the first route taken to reach the state)
    public boolean moveToState(int identifier) {
    	if (identifier > stateGraph.size()) {
    		return false;
    	}
    	else {
    		List<StateNode> targetpath = new ArrayList<>();
    		StateNode targetnode = stateGraph.getNodeById(identifier);
    		for (int i : targetnode.getPath()) {
    			targetpath.add(stateGraph.getNodeById(i));
    		}
    		statePath.setPath(targetpath);
    		printGraph();
    		loadImage();
    		return true;
    	}
    }
    
    /**
     * initialize is a wrapper for initializing a model or trace which cleans up internal state if
     * initialization fails.
     * @return boolean
     */
    public boolean initialize(File file, boolean isTrace) {
        ParsingConf oldEmbeddedParsingConf = embeddedParsingConf;
        
        
        // Ensure any embedded ParsingConf from a previously loaded model is removed.
        embeddedParsingConf = null;

        boolean res = isTrace ? initializeWithTrace(file) : initializeWithModel(file);
        if (!res) {
            // The embedded conf of the new model shouldn't persist if load fails.
            embeddedParsingConf = oldEmbeddedParsingConf;
        }
        if (res) {
	        printGraph();
	        loadImage();
	        savePath();
        }
        return res;
    }
    
  
	// replace the '/'s in dash states and transitions with '_'
	public String formatString(String s) {
		return s.replace('/', '_');
	}
    /**
     * performReverseStep goes backward by `steps` states in the current state traversal path.
     * @param steps
     */
    public void performReverseStep(int steps) {
        int initialPos = statePath.getPosition();
        StateNode targetNode = statePath.getNode(initialPos < steps ? 0 : initialPos - steps);

        if (initialPos <= steps) {
            setToInit();
        } else {
            // To set the internal state properly for an alternate path to be selected, perform
            // a step from the position one step behind the expected final position.
            statePath.decrementPosition(steps + 1, traceMode);
            performStep(1);
        }
 
        // If the user was on some alternate path, we need to perform `alt` until we get back
        // to the correct StateNode.
        while (!statePath.getCurNode().equals(targetNode)) {
            selectAlternatePath(false);
        }

        // Ensure the ID is set when reverse-stepping back to an alternative initial state.
        statePath.getCurNode().setIdentifier(targetNode.getIdentifier());
        //statePath.printPath();
        printGraph();
        loadImage();
    }

    /**
     * performStep steps the transition system forward by `steps` state transitions.
     * @param steps
     * @return boolean
     */
    public boolean performStep(int steps) {
        return performStep(steps, new ArrayList<String>());
    }

    /**
     * performStep steps the transition system forward by `steps` state transitions.
     * The i-th constraint in `constraints` is applied to the i-th transition.
     * @param steps
     * @param constraints
     * @return boolean
     */
    public boolean performStep(int steps, List<String> constraints) {
        if (isTrace()) {
            if (statePath.atEnd()) {
                System.out.println("Cannot perform step. End of trace reached.");
                return false;
            }
            statePath.incrementPosition(steps);
            return true;
        }
        
        statePath.commitNodes();
        
        
        String pathPredicate = AlloyUtils.getPathPredicate(constraints, stateSigData);
        try {
            String curInitString;
            if (stateGraph.size() > 1) {
                curInitString = statePath.getCurNode().getAlloyInitString();
            } else {
                curInitString = alloyInitString;
            }
            AlloyUtils.writeToFile(
                AlloyUtils.annotatedTransitionSystemStep(alloyModelString + curInitString + pathPredicate, getParsingConf(), steps),
                alloyModelFile
            );
        } catch (IOException e) {
            System.out.println("Cannot perform step. I/O failed.");
            return false;
        }

        CompModule compModule = null;
        try {
            compModule = AlloyInterface.compile(alloyModelFile.getAbsolutePath());
        } catch (Err e) {
            System.out.println("Cannot perform step. Internal error.");
            return false;
        }

        A4Solution sol = null;
        try {
            sol = AlloyInterface.run(compModule);
        } catch (Err e) {
            System.out.println("Cannot perform step. Internal error.");
            return false;
        }

        if (!sol.satisfiable()) {
            System.out.println("Cannot perform step. Transition constraint is unsatisfiable.");
            return false;
        }

        StateNode startNode = statePath.getCurNode();

        // For steps > 1, we need to generate all nodes representing the path that the series of state transitions
        // takes within the state graph.
        List<StateNode> stateNodes = getStateNodesForA4Solution(sol);

        // Filter out the initial node to avoid re-adding it to statePath.
        stateNodes.remove(0);
        statePath.setTempPath(stateNodes);

        stateGraph.addNodes(startNode, stateNodes);

        this.activeSolutions.clear();
        this.activeSolutions.push(sol);
        //statePath.printPath();
        
        printGraph();
        loadImage();
        savePath();
        return true;
    }

    public boolean selectAlternatePath(boolean reverse) {
        if (activeSolutions.isEmpty()) {
            return false;
        }

        A4Solution activeSolution = null;
        if (reverse) {
            if (activeSolutions.size() == 1) {
                return false;
            }

            activeSolutions.pop();
            activeSolution = activeSolutions.peek();
        } else {
            activeSolution = activeSolutions.peek();
            if (!activeSolution.next().satisfiable()) {
                return false;
            }

            activeSolution = activeSolution.next();
            activeSolutions.push(activeSolution);
        }

        List<StateNode> stateNodes = getStateNodesForA4Solution(activeSolution);
        StateNode startNode = stateNodes.get(0);
        stateNodes.remove(0);

        statePath.clearTempPath();
        if (stateNodes.isEmpty()) {
            // This branch should only be reached when an alternate path
            // is selected for an initial state.
            statePath.setTempPath(Arrays.asList(startNode));
        } else {
            statePath.setTempPath(stateNodes);
        }

        stateGraph.addNodes(startNode, stateNodes);
        //statePath.printPath();
        
        printGraph();
        loadImage();
        savePath();
        return true;
    }

    /**
     * performUntil steps the transition system up to `limit` state transitions,
     * until at least one of the constraints in the breakpoint list is satisfied.
     * @param limit
     * @return boolean
     */
    public boolean performUntil(int limit) {
        String breakPredicate = AlloyUtils.getBreakPredicate(constraintManager.getConstraints(), stateSigData);
        
        for (int steps = 1; steps <= limit; steps++) {
            try {
                String curInitString;
                if (stateGraph.size() > 1) {
                    curInitString = statePath.getCurNode().getAlloyInitString();
                } else {
                    curInitString = alloyInitString;
                }
                AlloyUtils.writeToFile(
                    AlloyUtils.annotatedTransitionSystemUntil(alloyModelString + curInitString + breakPredicate, getParsingConf(), steps),
                    alloyModelFile
                );
            } catch (IOException e) {
                return false;
            }

            CompModule compModule = null;
            try {
                compModule = AlloyInterface.compile(alloyModelFile.getAbsolutePath());
            } catch (Err e) {
                return false;
            }

            A4Solution sol = null;
            try {
                sol = AlloyInterface.run(compModule);
            } catch (Err e) {
                return false;
            }

            if (!sol.satisfiable()) {
                // Breakpoints not hit for current step size. Try next step size.
                continue;
            }

            statePath.commitNodes();

            StateNode startNode = statePath.getCurNode();

            List<StateNode> stateNodes = getStateNodesForA4Solution(sol);
            stateNodes.remove(0);
            statePath.setTempPath(stateNodes);

            stateGraph.addNodes(startNode, stateNodes);

            this.activeSolutions.clear();
            this.activeSolutions.push(sol);
            printGraph();
            loadImage();
            savePath();
            return true;
        }
        return false;
    }

    /**
     * setToInit sets SimulationManager's internal state to point to the initial state of the
     * active model or trace.
     * @return boolean
     */
    public boolean setToInit() {
        if (traceMode) {
            statePath.decrementPosition(statePath.getPosition(), traceMode);
            return true;
        }
        try {
            AlloyUtils.writeToFile(
                AlloyUtils.annotatedTransitionSystem(
                    this.alloyModelString + this.alloyInitString,
                    getParsingConf(),
                    0
                ),
                alloyModelFile
            );
        } catch (IOException e) {
            System.out.println("error. I/O failed, cannot re-initialize model.");
            return false;
        }

        CompModule compModule = null;
        try {
            compModule = AlloyInterface.compile(alloyModelFile.getAbsolutePath());
        } catch (Err e) {
            System.out.println("internal error.");
            return false;
        }

        A4Solution sol = null;
        try {
            sol = AlloyInterface.run(compModule);
        } catch (Err e) {
            System.out.println("internal error.");
            return false;
        }

        List<StateNode> initialNodes = getStateNodesForA4Solution(sol);
        // We don't re-add this initial node to the StateGraph, so manually set its identifier here.
        initialNodes.get(0).setIdentifier(1);

        statePath.clearPath();
        statePath.setTempPath(initialNodes);

        activeSolutions.clear();
        activeSolutions.push(sol);
        printGraph();
        loadImage();
        return true;
    }

    /**
     * validateConstraint validates a user-entered constraint by transforming
     * the constraint into a predicate and verifying that the model compiles
     * after the introduction of the new predicate.
     * @param String constraint
     * @return boolean
     */
    public boolean validateConstraint(String constraint) {
        String breakPredicate = AlloyUtils.getBreakPredicate(Arrays.asList(constraint), stateSigData);


        try {
            AlloyUtils.writeToFile(
                alloyModelString + alloyInitString + breakPredicate,
                alloyModelFile
            );
        } catch (IOException e) {
            return false;
        }

        try {
            AlloyInterface.compile(alloyModelFile.getAbsolutePath());
        } catch (Err e) {
            return false;
        }

        return true;
    }

    public String getDOTString() {
        return stateGraph.getDOTString();
    }

    public String getHistory(int n) {
        return statePath.getHistory(n, traceMode);
    }

    public Map<String, List<String>> getScopes() {
        return scopes;
    }

    public List<String> getScopeForSig(String sigName) {
        return scopes.get(sigName);
    }
    
    public String getStateString(int id) {
    	if (id<=0 || id-1>stateGraph.size()) {
    		return "state not found!";
    	}
        return stateGraph.getNode(id-1).toString();
    }
    
    public String getCurrentStateString() {
        return statePath.getCurNode().toString();
    }

    public String getCurrentStateStringForProperty(String property) {
        return statePath.getCurNode().stringForProperty(property);
    }

    /**
     * getCurrentStateDiffStringFromLastCommit returns the diff between the current
     * and the previous last-committed state.
     * @return String
     */
    public String getCurrentStateDiffStringFromLastCommit() {
        StateNode prev = statePath.getNode(statePath.getPosition() - statePath.getTempPathSize());
        return statePath.getCurNode().getDiffString(prev);
    }

    /**
     * getCurrentStateDiffStringByDelta returns the diff between the current state
     * and the state at the (current - delta) position in the path.
     * @param int delta
     * @return String
     */
    public String getCurrentStateDiffStringByDelta(int delta) {
        StateNode prev = statePath.getNode(statePath.getPosition() - delta);
        return statePath.getCurNode().getDiffString(prev);
    }

    public AliasManager getAliasManager() {
        return aliasManager;
    }

    public ConstraintManager getConstraintManager() {
        return constraintManager;
    }

    public String getWorkingDirPath() {
        return System.getProperty("user.dir");
    }

    private boolean initializeWithModel(File model) {
        try {
            AlloyInterface.compile(model.getPath());
        } catch (Err e) {
            System.out.printf("error.\n\n%s\n", e.toString());
            return false;
        }

        String tempModelFilename = TEMP_FILENAME_PREFIX + model.getName();
        // Note that the temp model file must be created in the same directory as the input model
        // in order for Alloy to correctly find imported submodules.
        File tempModelFile = new File(model.getParentFile(), tempModelFilename);
        tempModelFile.deleteOnExit();

        try {
            Files.copy(model.toPath(), tempModelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.out.println("error. I/O failed.");
            return false;
        }
        
        alloyModelFile = tempModelFile;

        String modelString;
        try {
            modelString = AlloyUtils.readFromFile(alloyModelFile);
        } catch (IOException e) {
            System.out.println("error. Failed to read file.");
            return false;
        }

        String configString = ParsingConf.getConfStringFromFileString(modelString).trim();
        if (!configString.isEmpty()) {
            try {
                embeddedParsingConf = ParsingConf.initializeWithYaml(configString);
            } catch (YAMLException e) {
                System.out.println("error. Invalid configuration.");
                return false;
            }
        }

        int transRelIndex = modelString.indexOf(String.format("pred %s", getParsingConf().getTransitionRelationName()));
        if (transRelIndex == -1) {
            System.out.printf("error. Predicate %s not found.\n", getParsingConf().getTransitionRelationName());
            return false;
        }

        int initStartIndex = modelString.indexOf(String.format("pred %s", getParsingConf().getInitPredicateName()));
        if (initStartIndex == -1) {
            System.out.printf("error. Predicate %s not found.\n", getParsingConf().getInitPredicateName());
            return false;
        }

        // Count the number of BLOCK_INITIALIZERs and BLOCK_TERMINATORs to
        // determine the end of the init predicate.
        int blocks = 0;
        int initEndIndex = -1;
        for (int i = initStartIndex; i < modelString.length(); i++) {
            String c = String.valueOf(modelString.charAt(i));
            if (c.equals(AlloyConstants.BLOCK_INITIALIZER)) {
                blocks += 1;
            } else if (c.equals(AlloyConstants.BLOCK_TERMINATOR)) {
                blocks -= 1;
                if (blocks == 0) {
                    // When all blocks are closed, the end of the predicate has
                    // been found.
                    initEndIndex = i;
                    break;
                } else if (blocks < 0) {
                    // More BLOCK_TERMINATORs than BLOCK_INITIALIZERs is a
                    // syntax error.
                    break;
                }
            }
        }

        if (initEndIndex == -1) {
            System.out.printf("error. Issue parsing predicate %s.\n", getParsingConf().getInitPredicateName());
            return false;
        }

        this.alloyInitString = modelString.substring(initStartIndex, initEndIndex + 1);
        this.alloyModelString =
            modelString.substring(0, initStartIndex) +
                AlloyUtils.getConcreteSigsDefinition(getParsingConf().getAdditionalSigScopes()) +
                modelString.substring(initEndIndex + 1, modelString.length());

        try {
            AlloyUtils.writeToFile(
                AlloyUtils.annotatedTransitionSystem(
                    this.alloyModelString + this.alloyInitString,
                    getParsingConf(),
                    0
                ),
                alloyModelFile
            );
        } catch (IOException e) {
            System.out.println("error. I/O failed, cannot initialize model.");
            return false;
        }

        CompModule compModule = null;
        try {
            compModule = AlloyInterface.compile(alloyModelFile.getAbsolutePath());
        } catch (Err e) {
            System.out.println("internal error.");
            return false;
        }

        A4Solution sol = null;
        try {
            sol = AlloyInterface.run(compModule);
        } catch (Err e) {
            System.out.printf("error.\n\n%s\n", e.msg.trim());
            return false;
        }

        if (!sol.satisfiable()) {
            System.out.println("error. No instance found. Predicate may be inconsistent.");
            return false;
        }

        evaluateScopes(sol);

        Sig stateSig = AlloyInterface.getSigFromA4Solution(sol, getParsingConf().getStateSigName());
        if (stateSig == null) {
            System.out.printf("error. Sig %s not found.\n", getParsingConf().getStateSigName());
            return false;
        }
        //System.out.println(getParsingConf().getStateSigName());
        
        stateSigData = new SigData(stateSig);

        List<StateNode> initialNodes = getStateNodesForA4Solution(sol);
        statePath.clearPath();
        statePath.setTempPath(initialNodes);
        stateGraph.initWithNodes(initialNodes);

        this.traceMode = false;
        this.activeSolutions.clear();
        this.activeSolutions.push(sol);

        return true;
    }

    private boolean initializeWithTrace(File trace) {
        A4Solution sol;
        try {
            sol = AlloyInterface.solutionFromXMLFile(trace);
        } catch (Err e) {
            System.out.printf("error.\n\n%s\n", e.toString());
            return false;
        } catch (Exception e) {
            System.out.println("error. Could not read XML file.");
            return false;
        }

        evaluateScopes(sol);

        Sig stateSig = AlloyInterface.getSigFromA4Solution(sol, getParsingConf().getStateSigName());
        if (stateSig == null) {
            System.out.printf("error. Sig %s not found.\n", getParsingConf().getStateSigName());
            return false;
        }

        stateSigData = new SigData(stateSig);

        List<StateNode> stateNodes = getStateNodesForA4Solution(sol);
        if (stateNodes.isEmpty()) {
            System.out.println("internal error.");
            return false;
        }

        statePath.initWithPath(stateNodes);
        statePath.setPosition(0);
        stateGraph.initWithNodes(stateNodes);

        this.traceMode = true;
        this.activeSolutions.clear();

        return true;
    }

    private List<StateNode> getStateNodesForA4Solution(A4Solution sol) {
        List<StateNode> stateNodes = new ArrayList<>();

        Sig stateSig = AlloyInterface.getSigFromA4Solution(sol, getParsingConf().getStateSigName());
        if (stateSig == null) {
            return stateNodes;
        }

        if (stateSigData == null) {
            stateSigData = new SigData(stateSig);
        }

        int steps = sol.eval(stateSig).size();
        for (int i = 0; i < steps; i++) {
            stateNodes.add(new StateNode(stateSigData, getParsingConf()));
        }

        for (Sig.Field field : stateSig.getFields()) {
            for (A4Tuple tuple : sol.eval(field)) {
                String atom = tuple.atom(0);
                StateNode node = stateNodes.get(
                    Integer.parseInt(atom.split(AlloyConstants.ALLOY_ATOM_SEPARATOR)[1])
                );
                String tupleString = tuple.toString();
                node.addValueToField(
                    field.label,
                    tupleString
                        .substring(
                            tupleString.indexOf(AlloyConstants.SET_DELIMITER) + 2, tupleString.length()
                        )
                        // Sigs will only ever have $0 as a suffix since we control their scope.
                        .replace(AlloyConstants.VALUE_SUFFIX, "")
                    );
            }
        }

        return stateNodes;
    }

    /**
     * evaluateScopes gets the scope for each reachable sig for an A4Solution and stores it in StateGraph.
     * @param A4Solution sol
     */
    private void evaluateScopes(A4Solution sol) {
        for (Sig s : sol.getAllReachableSigs()) {
            String label = s.label;
            if (label.startsWith(AlloyConstants.THIS)) {
                label = label.substring(AlloyConstants.THIS.length());
            }
            // Ignore the 'univ' sig which itself contains the scope of the entire model.
            if (label.equals(AlloyConstants.UNIV)) {
                continue;
            }
            // Ignore internal concrete sigs that we've injected into the model.
            else if (label.matches(AlloyConstants.CONCRETE_SIG_REGEX)) {
                int i = label.indexOf(AlloyConstants.UNDERSCORE);
                String origSigName = label.substring(0, i);
                Map<String, Integer> sigScopes = getParsingConf().getAdditionalSigScopes();
                if (sigScopes.containsKey(origSigName) &&
                    Integer.parseInt(label.substring(i + 1)) < sigScopes.get(origSigName)) {
                    continue;
                }
            }
            List<String> tuples = new ArrayList<>();
            for (A4Tuple t : sol.eval(s)) {
                tuples.add(t.toString().replace(AlloyConstants.VALUE_SUFFIX, ""));
            }
            scopes.put(label, tuples);
        }
    }

    private ParsingConf getParsingConf() {
        return embeddedParsingConf != null ? embeddedParsingConf : persistentParsingConf;
    }
}
