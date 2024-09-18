package utils;

import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import parser.ATL;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.text.Normalizer;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AbstractionUtils {

    private static final String MODEL_JSON_FILE_NAME = "";
    private final static Log logger = LogFactory.getLog(AbstractionUtils.class);
    public static String mcmas;

    public static List<StateCluster> getStateClusters(AtlModel atlModel) {
        List<StateCluster> stateClusters = new ArrayList<>();
        for (State state : atlModel.getStates()) {
            stateClusters.add(state.toStateCluster());
        }

        for (Agent agent : atlModel.getAgents()) {
            for (List<String> indistinguishableStateNameList : agent.getIndistinguishableStates()) {
                for (StateCluster stateCluster : stateClusters) {
                    List<State> indistinguishableStateList =
                            indistinguishableStateNameList.parallelStream()
                                    .map(stateName->atlModel.getState(stateName)).collect(Collectors.toList());
                    if (stateCluster.containsAnyChildState(indistinguishableStateList)) {
                        for (State state : indistinguishableStateList) {
                            if (!stateCluster.containsChildState(state)) {
                                stateCluster.addChildState(state);
                            }
                        }
                    }
                }
            }
        }

        for (StateCluster stateCluster1 : stateClusters) {
            for (StateCluster stateCluster2 : stateClusters) {
                if (stateCluster1.containsAnyChildState(stateCluster2)) {
                    stateCluster1.addChildStates(stateCluster2);
                }
            }
        }

        return stateClusters.stream().distinct().collect(Collectors.toList());
    }



    public static List<Transition> getMayTransitions(final AtlModel atlModel, final List<StateCluster> stateClusters) {
        List<Transition> transitions = new ArrayList<>();
        for (StateCluster fromStateCluster : stateClusters) {
            for (StateCluster toStateCluster : stateClusters) {
                List<List<AgentAction>> agentActions = fromStateCluster.hasMayTransition(toStateCluster, atlModel);
                if (!agentActions.isEmpty()) {
                    removeDuplicates(agentActions);
                    Transition transition = new Transition();
                    transition.setFromState(fromStateCluster.getName());
                    transition.setToState(toStateCluster.getName());
                    transition.setAgentActions(agentActions);
                    transitions.add(transition);
                }
            }
        }

        return transitions;
    }

    public static List<Transition> getMustTransitions(final AtlModel atlModel, final List<StateCluster> stateClusters) {
        List<Transition> transitions = new ArrayList<>();
        for (StateCluster fromStateCluster : stateClusters) {
            for (StateCluster toStateCluster : stateClusters) {
                List<List<AgentAction>> agentActions = fromStateCluster.hasMustTransition(toStateCluster, atlModel);
                if (!agentActions.isEmpty()) {
                    removeDuplicates(agentActions);
                    Transition transition = new Transition();
                    transition.setFromState(fromStateCluster.getName());
                    transition.setToState(toStateCluster.getName());
                    transition.setAgentActions(agentActions);
                    transitions.add(transition);
                }
            }
        }

        return transitions;
    }

    public static void removeDuplicates(List<List<AgentAction>> agentActions) {
        Map<String, List<AgentAction>> actionMap = new HashMap<>();
        for (List<AgentAction> actionList : agentActions) {
            actionMap.put(actionList.toString(), actionList);
        }
        agentActions.clear();
        agentActions.addAll(actionMap.values());
    }

    public static String generateDotGraph(AtlModel atlModel) {
        StringBuilder stringBuilder = new StringBuilder("digraph G {").append(System.lineSeparator());
        List<Transition> transitions = atlModel.getTransitions();
        for (Transition transition : transitions) {
            if (CollectionUtils.isEmpty(atlModel.getState(transition.getFromState()).getLabels())) {
                stringBuilder.append(transition.getFromState());
            } else {
                stringBuilder
                        .append("\"").append(transition.getFromState()).append("(").append(String.join(", ", atlModel.getState(transition.getFromState()).getLabels())).append(")\"");
            }
            stringBuilder.append("->");
            if (CollectionUtils.isEmpty(atlModel.getState(transition.getToState()).getLabels())) {
                stringBuilder.append(transition.getToState());
            } else {
                stringBuilder
                        .append("\"").append(transition.getToState()).append("(").append(String.join(", ", atlModel.getState(transition.getToState()).getLabels())).append(")\"");
            }
            List<String> list1 = new ArrayList<>();
            for(List<AgentAction> agentActionList: transition.getAgentActions()) {
                List<String> list2 = new ArrayList<>();
                for (AgentAction agentAction : agentActionList) {
                    list2.add(agentAction.getAgent()+ "." +agentAction.getAction());
                }
                list1.add(MessageFormat.format("({0})", String.join(",", list2)));
            }
            stringBuilder.append("[ label = \"" + String.join("\\n", list1) + "\" ];").append(System.lineSeparator());
        }
        stringBuilder.append("}").append(System.lineSeparator());
        return stringBuilder.toString();
    }

    public static void validateAtlModel(AtlModel atlModel) throws Exception {
        validateTransitions(atlModel);
        validateGroup(atlModel);
        atlModel.setATL(atlModel.getATL().negationNormalForm());
        Set<String> atoms = new HashSet<>();
        atlModel.setATL(atlModel.getATL().makePositive(atoms));
        for(String atom : atoms) {
            atlModel.setFormula(atlModel.getFormula().replace("!" + atom, "n" + atom));
            atlModel.setFormula(atlModel.getFormula().replace("not(" + atom + ")", "n" + atom));
        }
        for(State state : atlModel.getStates()) {
            for(String atom : atoms){
                if(!state.getLabels().contains(atom)) {
                    state.getLabels().add("n" + atom);
                }
            }
        }
    }

    private static void validateTransitions(AtlModel atlModel) throws Exception {
        for(Transition transition : atlModel.getTransitions()) {
            if (!atlModel.getStateMap().containsKey(transition.getFromState())) {
                throw new Exception(MessageFormat.format("invalid state {0} in transition : {1} {2}",
                        transition.getFromState(), System.lineSeparator(), transition));
            }
            if (!atlModel.getStateMap().containsKey(transition.getToState())) {
                throw new Exception(MessageFormat.format("invalid state {0} in transition : {1} {2}",
                        transition.getToState(), System.lineSeparator(), transition));
            }
            if (transition.isDefaultTransition() && CollectionUtils.isNotEmpty(transition.getAgentActions())) {
                throw new Exception(MessageFormat.format("The transition cannot be a default one and have explicit agent actions : {0} {1}",
                        System.lineSeparator(), transition));
            }
            for(List<AgentAction> agentActionList : transition.getAgentActions()) {
                validateAgentActionList(atlModel, transition, agentActionList);
            }
        }
    }

    private static void validateAgentActionList(AtlModel atlModel, Transition transition, List<AgentAction> agentActionList) throws Exception {
        for (AgentAction agentAction : agentActionList) {
            validateAgentAction(atlModel, transition, agentAction);
        }
        List<String> agents = agentActionList.parallelStream().map(AgentAction::getAgent).collect(Collectors.toList());
        Collection<String> agentNotDefinedList = CollectionUtils.subtract(agents, atlModel.getAgentMap().keySet());
        if (CollectionUtils.isNotEmpty(agentNotDefinedList)) {
            throw new Exception (MessageFormat.format("Some agents have not been defined : {0} for the transition : {1} {2}",
                    agentNotDefinedList, System.lineSeparator(), transition));
        }
        Collection<String> missingAgentActionsList = CollectionUtils.subtract(atlModel.getAgentMap().keySet(), agents);
        if (CollectionUtils.isNotEmpty(missingAgentActionsList)) {
            throw new Exception (MessageFormat.format("Some agent actions have not been defined : {0} for the transition : {1} {2}",
                    missingAgentActionsList, System.lineSeparator(), transition));
        }
    }

    private static void validateAgentAction(AtlModel atlModel, Transition transition, AgentAction agentAction) throws Exception {
        if (!atlModel.getAgentMap().containsKey(agentAction.getAgent())) {
            throw new Exception (MessageFormat.format("Invalid agent {0} in agentAction : {1} for the transition : {2} {3}",
                    agentAction.getAgent(), agentAction, System.lineSeparator(), transition));
        }
        Agent agent = atlModel.getAgentMap().get(agentAction.getAgent());
        if (!agent.getActions().contains(agentAction.getAction())) {
            throw new Exception (MessageFormat.format("Invalid action {0} in agentAction : {1} for the transition : {2} {3}",
                    agentAction.getAction(), agentAction, System.lineSeparator(), transition));
        }
    }

    private static void validateGroup(AtlModel atlModel) throws Exception {
        for(Group g : atlModel.getGroups()) {
            List<String> groupAgents = g.getAgents();
            Collection<String> agentNotDefinedList = CollectionUtils.subtract(groupAgents, atlModel.getAgentMap().keySet());
            if (CollectionUtils.isNotEmpty(agentNotDefinedList)) {
                throw new Exception(MessageFormat.format("Some agents in the group have not been defined : {0}",
                        agentNotDefinedList, System.lineSeparator(), atlModel));
            }
        }
    }


    /*
    public static String readSampleFile() {
        try {
            File sampleFile = new ClassPathResource(MODEL_JSON_FILE_NAME).getFile();
            return new String(FileUtils.readFileToByteArray(sampleFile));
        } catch (IOException ioe) {
            logger.error("Error while trying to read the sample file.", ioe);
        }

        return null;
    }
    */

    public static void processDefaultTransitions(AtlModel atlModel) throws Exception {
        for(Entry<String, List<Transition>> entry : atlModel.getTransitionMap().entrySet()) {
            List<Transition> transitions = entry.getValue();
            List<Transition> defaultTransitions = transitions.parallelStream().filter(transition -> transition.isDefaultTransition()).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(defaultTransitions)) {
                continue;
            }
            if (defaultTransitions.size() > 1) {
                throw new Exception (MessageFormat.format("The state {0} has {1} default transition, only one is allowed. Transitions : {2} {3}",
                        entry.getKey(), defaultTransitions.size(), System.lineSeparator(), defaultTransitions));
            }
            Collection<Transition> explicitTransitions = CollectionUtils.subtract(transitions, defaultTransitions);
            List<List<List<AgentAction>>> existingActionLists = explicitTransitions.parallelStream().map(Transition::getAgentActions).collect(Collectors.toList());
            Set<List<AgentAction>> actions = defaultTransitions.get(0).getMultipleAgentActions()
                    .parallelStream()
                    .map(
                            multipleAgentAction->multipleAgentAction.getActions()
                                    .stream()
                                    .map(action -> new AgentAction(multipleAgentAction.getAgent(), action))
                                    .collect(Collectors.toList()))
                    .collect(Collectors.toSet());
            List<List<AgentAction>> possibleActions = Lists.cartesianProduct(actions.toArray(new ArrayList<?>[actions.size()])).parallelStream().map(list->list.stream().map(action->(AgentAction) action).collect(Collectors.toList())).collect(Collectors.toList());
            for (List<List<AgentAction>> agentActionsList : existingActionLists) {
                for (List<AgentAction> agentActionList : agentActionsList) {
                    Iterator<List<AgentAction>> iterator = possibleActions.iterator();
                    while (iterator.hasNext()) {
                        if (CollectionUtils.isEqualCollection(iterator.next(), agentActionList)) {
                            iterator.remove();
                        }
                    }
                }
            }
            atlModel.getTransitions().add(new Transition(entry.getKey(), defaultTransitions.get(0).getToState(), possibleActions));
            atlModel.setTransitions(Lists.newLinkedList(CollectionUtils.removeAll(atlModel.getTransitions(), defaultTransitions)));
        }
    }

    public static String generateMCMASProgram1(AtlModel atlModel) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(System.lineSeparator()).append("Agent Environment").append(System.lineSeparator());
        stringBuilder.append("\t").append("Vars :").append(System.lineSeparator());
        Set<String> alreadyAddedLabels = new HashSet<>();
        for (State state: atlModel.getStates()) {
            stringBuilder.append("\t").append("\t").append(state.getName()).append(" : boolean;").append(System.lineSeparator());
            for (String label: state.getLabels()) {
                if(label.endsWith("_tt") || label.endsWith("_ff")) {
                    label = label.substring(0, label.length() - 3);
                    if (!alreadyAddedLabels.contains(label)) {
                        stringBuilder.append("\t").append("\t").append(label).append("_tt").append(" : boolean;").append(System.lineSeparator());
                        stringBuilder.append("\t").append("\t").append(label).append("_ff").append(" : boolean;").append(System.lineSeparator());
                        stringBuilder.append("\t").append("\t").append(label).append("_uu").append(" : boolean;").append(System.lineSeparator());
                        alreadyAddedLabels.add(label);
                    }
                } else {
                    if (!alreadyAddedLabels.contains(label)) {
                        stringBuilder.append("\t").append("\t").append(label).append(" : boolean;").append(System.lineSeparator());
                        alreadyAddedLabels.add(label);
                    }
                }
            }
            for (String label: state.getFalseLabels()) {
                if(!label.contains("atom")) {
                    label = label.substring(0, label.length() - 3);
                    if(label.endsWith("_tt") || label.endsWith("_ff")) {
                        stringBuilder.append("\t").append("\t").append(label).append("_tt").append(" : boolean;").append(System.lineSeparator());
                        stringBuilder.append("\t").append("\t").append(label).append("_ff").append(" : boolean;").append(System.lineSeparator());
                        stringBuilder.append("\t").append("\t").append(label).append("_uu").append(" : boolean;").append(System.lineSeparator());
                        alreadyAddedLabels.add(label);
                    }
                } else {
                    if (!alreadyAddedLabels.contains(label)) {
                        stringBuilder.append("\t").append("\t").append(label).append("_tt").append(" : boolean;").append(System.lineSeparator());
                        alreadyAddedLabels.add(label);
                    }
                }
            }
        }
        stringBuilder.append("\t").append("end Vars").append(System.lineSeparator());
        stringBuilder.append("\t").append("Actions = {};").append(System.lineSeparator());
        stringBuilder.append("\t").append("Protocol :").append(System.lineSeparator());
        stringBuilder.append("\t").append("end Protocol").append(System.lineSeparator());
        stringBuilder.append("\t").append("Evolution :").append(System.lineSeparator());
        for (Transition transition: atlModel.getTransitions()) {
            State toState = atlModel.getState(transition.getToState());
            State fromState = atlModel.getState(transition.getFromState());
            stringBuilder.append("\t").append("\t");
            if (!toState.equals(fromState)) {
                stringBuilder.append(fromState.getName()).append(" = false ");
                if (CollectionUtils.isNotEmpty(fromState.getLabels())) {
                    for (String label: fromState.getLabels()) {
                        if(!toState.getLabels().contains(label)) {
                            stringBuilder.append("and ").append(label).append(" = false ");
                        }
                    }
                }
                stringBuilder.append("and ");
            }

            stringBuilder.append(toState.getName()).append(" = true ");


            if (CollectionUtils.isNotEmpty(toState.getLabels())) {
                for (String label : toState.getLabels()) {
                    stringBuilder.append("and ").append(label).append(" = true ");
                }
            }
            stringBuilder.append(" if ");

            stringBuilder.append(fromState.getName()).append(" = true ");
            if (CollectionUtils.isNotEmpty(fromState.getLabels())) {
                for (String label: fromState.getLabels()) {
                    stringBuilder.append("and ").append(label).append(" = true ");
                }
            }

            if (!toState.equals(fromState)) {
                stringBuilder.append("and ").append(toState.getName()).append(" = false ");
                if (CollectionUtils.isNotEmpty(toState.getLabels())) {
                    for (String label : toState.getLabels()) {
                        if(!fromState.getLabels().contains(label)) {
                            stringBuilder.append("and ").append(label).append(" = false ");
                        }
                    }
                }
            }

            stringBuilder.append("and ");
            if (transition.getAgentActions().size()>1)
                stringBuilder.append("(");
            for (int i = 0; i < transition.getAgentActions().size(); i++) {
                List<AgentAction> agentActionList = transition.getAgentActions().get(i);
                stringBuilder.append("(");
                for (int j = 0; j < agentActionList.size(); j++) {
                    AgentAction agentAction = agentActionList.get(j);
                    stringBuilder.append(agentAction.getAgent()).append(".Action").append(" = ").append(agentAction.getAction());
                    if (j<agentActionList.size()-1)
                        stringBuilder.append(" and ");
                }
                stringBuilder.append(")");
                if (i<transition.getAgentActions().size()-1)
                    stringBuilder.append(" or ").append(System.lineSeparator()).append("\t\t\t\t\t");
            }
            if (transition.getAgentActions().size()>1)
                stringBuilder.append(")");

            if (transition.getMultipleAgentActions().size()>1)
                stringBuilder.append("(");
            for (int i = 0; i < transition.getMultipleAgentActions().size(); i++) {
                MultipleAgentAction multiAction = transition.getMultipleAgentActions().get(i);
                stringBuilder.append("(");
                for (int j = 0; j < multiAction.getActions().size(); j++) {
                    String agentAction = multiAction.getActions().get(j);
                    stringBuilder.append(multiAction.getAgent()).append(".Action").append(" = ").append(agentAction);
                    if (j<multiAction.getActions().size()-1)
                        stringBuilder.append(" or ");
                }
                stringBuilder.append(")");
                if (i<transition.getMultipleAgentActions().size()-1)
                    stringBuilder.append(" and ").append(System.lineSeparator()).append("\t\t\t\t\t");
            }
            if (transition.getMultipleAgentActions().size()>1)
                stringBuilder.append(")");

            stringBuilder.append(";").append(System.lineSeparator());
        }
        stringBuilder.append("\t").append("end Evolution").append(System.lineSeparator());
        stringBuilder.append("end Agent").append(System.lineSeparator());

        for (Agent agent : atlModel.getAgents()) {
            stringBuilder.append("Agent ").append(agent.getName()).append(System.lineSeparator());
            alreadyAddedLabels.clear();
            List<String> lobsvars = new ArrayList<>();
            for (State state : atlModel.getStates()) {
                boolean consider = true;
                for(List<String> lIndS : agent.getIndistinguishableStates()){
                    if(lIndS.contains(state.getName())) {
                        consider = false;
                        break;
                    }
                }
                if(consider) {
                    lobsvars.add(state.getName());
                }
                for (String label : state.getLabels()) {
                    if(!alreadyAddedLabels.contains(label)) {
                        lobsvars.add(label);
                        alreadyAddedLabels.add(label);
                    }
                }
            }
            stringBuilder.append("\t").append("Lobsvars = {").append(String.join(", ", lobsvars)).append("};").append(System.lineSeparator());
            stringBuilder.append("\t").append("Vars : ").append(System.lineSeparator());
            if(CollectionUtils.isNotEmpty(agent.getIndistinguishableStates())) {
//				boolean first = true;
                for (List<String> lIndS : agent.getIndistinguishableStates()) {
//					if (first) {
//						first = false;
//					} else {
//						stringBuilder.append(",").append(System.lineSeparator());
//					}
                    stringBuilder.append("\t").append("\t").append("imp_").append(String.join("_", lIndS)).append(": boolean;");
                }
            }
            stringBuilder.append(System.lineSeparator()).append("\t").append("\t").append("play : boolean;");

            stringBuilder.append(System.lineSeparator()).append("\t").append("end Vars").append(System.lineSeparator());
            stringBuilder.append("\t").append("Actions = {").append(String.join(",", agent.getActions())).append("};");
            Map<String, List<String>> availableActionMap = getAvailableActions(atlModel, agent);
            stringBuilder.append(System.lineSeparator()).append("\t").append("Protocol : ").append(System.lineSeparator());
//            stringBuilder.append("Other : {").append(String.join(",", agent.getActions())).append("};");
            for (Entry<String, List<String>> availableActionsEntry: availableActionMap.entrySet()) {
                stringBuilder.append("\t").append("\t");
                if(!availableActionsEntry.getKey().startsWith("imp_")) {
                    stringBuilder.append("Environment.");
                }
                stringBuilder.append(availableActionsEntry.getKey()).append(" = true");
                if(!availableActionsEntry.getKey().startsWith("imp_")) {
                    State state = atlModel.getState(availableActionsEntry.getKey());
                    if (CollectionUtils.isNotEmpty(state.getLabels())) {
                        for (String label : state.getLabels())
                            stringBuilder.append(" and ").append("Environment.").append(label).append(" = true");
                    }
                    stringBuilder.append(" : {")
                            .append(String.join(",", availableActionsEntry.getValue())).append("};").append(System.lineSeparator());
                } else {
                    for(String s : availableActionsEntry.getKey().substring(4).split("_")) {
                        State state = atlModel.getState(s);
                        if (CollectionUtils.isNotEmpty(state.getLabels())) {
                            for (String label : state.getLabels())
                                stringBuilder.append(" and ").append("Environment.").append(label).append(" = true");
                        }
                    }
                    stringBuilder.append(" : {")
                            .append(String.join(",", availableActionsEntry.getValue())).append("};").append(System.lineSeparator());
                }
            }
            stringBuilder.append("\t").append("end Protocol").append(System.lineSeparator());
            stringBuilder.append("\t").append("Evolution : ").append(System.lineSeparator());
            stringBuilder.append("\t").append("\t").append("play = true if play = true;").append(System.lineSeparator());
            if(CollectionUtils.isNotEmpty(agent.getIndistinguishableStates())) {
                for (Transition transition : atlModel.getTransitions()) {
                    State toState = atlModel.getState(transition.getToState());
                    State fromState = atlModel.getState(transition.getFromState());
                    State toStateAux = toState, fromStateAux = fromState;
                    for (List<String> lIndS : agent.getIndistinguishableStates()) {
                        if (lIndS.contains(toState.getName())) {
                            toState = new State();
                            toState.setName("imp_" + String.join("_", lIndS));
                            toState.setInitial(atlModel.getStates().stream().anyMatch(s -> lIndS.contains(s.getName()) && s.isInitial()));
                            toState.setLabels(new ArrayList<>());
                            for (List<String> labels : atlModel.getStates().stream().filter(s -> lIndS.contains(s.getName())).map(State::getLabels).collect(Collectors.toSet())) {
                                toState.getLabels().addAll(labels);
                            }
                        }
                        if (lIndS.contains(fromState.getName())) {
                            fromState = new State();
                            fromState.setName("imp_" + String.join("_", lIndS));
                            fromState.setInitial(atlModel.getStates().stream().anyMatch(s -> lIndS.contains(s.getName()) && s.isInitial()));
                            fromState.setLabels(new ArrayList<>());
                            for (List<String> labels : atlModel.getStates().stream().filter(s -> lIndS.contains(s.getName())).map(State::getLabels).collect(Collectors.toSet())) {
                                fromState.getLabels().addAll(labels);
                            }
                        }
                    }
                    if (toState == toStateAux) { // && fromState == fromStateAux) {
                        continue;
                    }
//                if(toState == toStateAux) {
//                toState.setName("Environment." + toState.getName());
//                }
                    if (fromState == fromStateAux) {
                        fromState.setName("Environment." + fromState.getName());
                    }
                    stringBuilder.append("\t").append("\t");
//                if (!fromState.equals(toState)) {
//                    stringBuilder.append("(").append(fromState.getName()).append(" = false ").append(")");
//                    if (CollectionUtils.isNotEmpty(fromState.getLabels())) {
//                        for (String label: fromState.getLabels()) {
//                            if(!toState.getLabels().contains(label)) {
//                                stringBuilder.append("and ").append("(").append(label).append(" = false ").append(")");
//                            }
//                        }
//                    }
//                    stringBuilder.append("and ");
//                }


                    stringBuilder.append("(").append(toState.getName()).append(" = true ").append(")");
//                    if (CollectionUtils.isNotEmpty(toState.getLabels())) {
//                        for (String label : toState.getLabels()) {
//                            stringBuilder.append("and ").append("(").append(label).append(" = true ").append(")");
//                        }
//                    }
                    stringBuilder.append(" if ");

                    stringBuilder.append("(").append(fromState.getName()).append(" = true ").append(")");
                    if (CollectionUtils.isNotEmpty(fromState.getLabels())) {
                        for (String label : fromState.getLabels()) {
                            stringBuilder.append("and ").append("(").append("Environment.").append(label).append(" = true ").append(")");
                        }
                    }

                    if (!toState.equals(fromState)) {
                        stringBuilder.append("and ").append("(").append(toState.getName()).append(" = false ").append(")");
                        if (CollectionUtils.isNotEmpty(toState.getLabels())) {
                            for (String label : toState.getLabels()) {
                                if (!fromState.getLabels().contains(label)) {
                                    stringBuilder.append("and ").append("(").append("Environment.").append(label).append(" = false ").append(")");
                                }
                            }
                        }
                    }

                    stringBuilder.append("and ");
                    if (transition.getAgentActions().size() > 1)
                        stringBuilder.append("(");
                    for (int i = 0; i < transition.getAgentActions().size(); i++) {
                        List<AgentAction> agentActionList = transition.getAgentActions().get(i);
                        stringBuilder.append("(");
                        for (int j = 0; j < agentActionList.size(); j++) {
                            AgentAction agentAction = agentActionList.get(j);
                            stringBuilder.append(agentAction.getAgent()).append(".Action").append(" = ").append(agentAction.getAction());
                            if (j < agentActionList.size() - 1)
                                stringBuilder.append(" and ");
                        }
                        stringBuilder.append(")");
                        if (i < transition.getAgentActions().size() - 1)
                            stringBuilder.append(" or ").append(System.lineSeparator()).append("\t\t\t\t\t");
                    }
                    if (transition.getAgentActions().size() > 1)
                        stringBuilder.append(")");

                    if (transition.getMultipleAgentActions().size() > 1)
                        stringBuilder.append("(");
                    for (int i = 0; i < transition.getMultipleAgentActions().size(); i++) {
                        MultipleAgentAction multiAction = transition.getMultipleAgentActions().get(i);
                        stringBuilder.append("(");
                        for (int j = 0; j < multiAction.getActions().size(); j++) {
                            String agentAction = multiAction.getActions().get(j);
                            stringBuilder.append(multiAction.getAgent()).append(".Action").append(" = ").append(agentAction);
                            if (j < multiAction.getActions().size() - 1)
                                stringBuilder.append(" or ");
                        }
                        stringBuilder.append(")");
                        if (i < transition.getMultipleAgentActions().size() - 1)
                            stringBuilder.append(" and ").append(System.lineSeparator()).append("\t\t\t\t\t");
                    }
                    if (transition.getMultipleAgentActions().size() > 1)
                        stringBuilder.append(")");

                    stringBuilder.append(";").append(System.lineSeparator());
//                if(toState == toStateAux) {
//                toState.setName(toState.getName().substring(12));
//                }
                    if (fromState == fromStateAux) {
                        fromState.setName(fromState.getName().substring(12));
                    }
                }
            }
            stringBuilder.append("\t").append("end Evolution").append(System.lineSeparator());
            stringBuilder.append("end Agent").append(System.lineSeparator());
        }

        stringBuilder.append("Evaluation").append(System.lineSeparator());
        for (String term: atlModel.getATL().getTerms()) {
            stringBuilder.append("\t").append(term).append(" if (Environment.").append(term).append(" = true);").append(System.lineSeparator());
        }
        stringBuilder.append("\t").append("end Evaluation").append(System.lineSeparator());

        alreadyAddedLabels.clear();
        HashMap<String, Boolean> initialLabels = new HashMap<>();
//        HashMap<String, Boolean> initialLabelsAgents = new HashMap<>();
        stringBuilder.append("\t").append("InitStates").append(System.lineSeparator());
        for (int i = 0; i < atlModel.getStates().size(); i++) {
            State state = atlModel.getStates().get(i);
            stringBuilder.append("\t").append("\t").append("Environment.").append(state.getName()).append(" = ").append(state.isInitial());
            if (CollectionUtils.isNotEmpty(state.getLabels())) {
                for (int j = 0; j < state.getLabels().size(); j++) {
                    String label = state.getLabels().get(j);
                    if(initialLabels.containsKey(label)) {
                        if(state.isInitial()) {
                            initialLabels.put(label, state.isInitial());
                        }
                    } else {
                        initialLabels.put(label, state.isInitial());
                    }
                }
            }

            if (CollectionUtils.isNotEmpty(state.getFalseLabels())) {
                for (int j = 0; j < state.getFalseLabels().size(); j++) {
                    String label = state.getFalseLabels().get(j);
                    initialLabels.putIfAbsent(label, false);
                }
            }

            if (i<atlModel.getStates().size()-1) {
                stringBuilder.append(" and ").append(System.lineSeparator());
            }
        }

        for(Agent agent : atlModel.getAgents()) {
            for(List<String> lIndS : agent.getIndistinguishableStates()) {
                stringBuilder.append(" and ").append(System.lineSeparator());
                stringBuilder.append("\t").append("\t").append(agent.getName()).append(".").append("imp_").append(String.join("_", lIndS)).append(" = ").append(atlModel.getStates().stream().anyMatch(s -> lIndS.contains(s.getName()) && s.isInitial()));
//                for(String s : lIndS) {
//                    List<String> l = atlModel.getState(s).getLabels();
//                    if(CollectionUtils.isNotEmpty(l)) {
//                        for (String label : l) {
//                            if (initialLabelsAgents.containsKey(label)) {
//                                if (atlModel.getState(s).isInitial()) {
//                                    initialLabelsAgents.put(agent.getName() + "." + label, atlModel.getState(s).isInitial());
//                                }
//                            } else {
//                                initialLabelsAgents.put(agent.getName() + "." + label, atlModel.getState(s).isInitial());
//                            }
//                        }
//                    }
//                }
            }
        }

        for(Entry<String, Boolean> initialLabel : initialLabels.entrySet()) {
            stringBuilder.append(" and ").append(System.lineSeparator());
            stringBuilder.append("\t").append("\t").append("Environment.").append(initialLabel.getKey()).append(" = ").append(initialLabel.getValue());
        }
//        for(Entry<String, Boolean> initialLabel : initialLabelsAgents.entrySet()) {
//            stringBuilder.append(" and ").append(System.lineSeparator());
//            stringBuilder.append("\t").append("\t").append(initialLabel.getKey()).append(" = ").append(initialLabel.getValue());
//        }

//        if (CollectionUtils.isNotEmpty(atlModel.getAgents())) {
//            stringBuilder.append(" and ").append(System.lineSeparator());
//            for (int i = 0; i < atlModel.getAgents().size(); i++) {
//                Agent agent = atlModel.getAgents().get(i);
//                stringBuilder.append("\t").append("\t").append(agent.getName()).append(".play = true");
//                if (i<atlModel.getAgents().size()-1)
//                    stringBuilder.append(" and ").append(System.lineSeparator());
//            }
//        }

        stringBuilder.append(";").append(System.lineSeparator()).append("\t").append("end InitStates").append(System.lineSeparator());

        stringBuilder.append("Groups").append(System.lineSeparator());
        for(Group g : atlModel.getGroups()) {
            stringBuilder.append("\t").append(g.getName()).append("=").append("{").append(String.join(",", g.getAgents())).append("};").append(System.lineSeparator());
        }
        stringBuilder.append("end Groups").append(System.lineSeparator());

        stringBuilder.append("Formulae").append(System.lineSeparator());
        stringBuilder.append("\t");
//		if (isMayModel)
//			stringBuilder.append("!(");
        stringBuilder.append(atlModel.getATL());
//		stringBuilder.append("<").append(atlModel.getGroup().getName()).append(">").append(atlModel.getFormula().getSubformula());
//		if (isMayModel)
//			stringBuilder.append(")");
        stringBuilder.append(";").append(System.lineSeparator());
        stringBuilder.append("end Formulae").append(System.lineSeparator());



        return stringBuilder.toString();
    }

    public static String generateMCMASProgram(AtlModel atlModel) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(System.lineSeparator()).append("Agent Environment").append(System.lineSeparator());
        stringBuilder.append("\t").append("Vars :").append(System.lineSeparator());
        Set<String> alreadyAddedLabels = new HashSet<>();
        for (State state: atlModel.getStates()) {
            stringBuilder.append("\t").append("\t").append(state.getName()).append(" : boolean;").append(System.lineSeparator());
            if(state.getName().equals("sink")) continue;
            for (String label: state.getLabels()) {
                if(!alreadyAddedLabels.contains(label)) {
                    stringBuilder.append("\t").append("\t").append(label).append(" : boolean;").append(System.lineSeparator());
                    alreadyAddedLabels.add(label);
                }
            }
            for (String label: state.getFalseLabels()) {
                if(!alreadyAddedLabels.contains(label)) {
                    stringBuilder.append("\t").append("\t").append(label).append(" : boolean;").append(System.lineSeparator());
                    alreadyAddedLabels.add(label);
                }
            }
        }
        stringBuilder.append("\t").append("end Vars").append(System.lineSeparator());
        stringBuilder.append("\t").append("Actions = {};").append(System.lineSeparator());
        stringBuilder.append("\t").append("Protocol :").append(System.lineSeparator());
        stringBuilder.append("\t").append("end Protocol").append(System.lineSeparator());
        stringBuilder.append("\t").append("Evolution :").append(System.lineSeparator());
        for (Transition transition: atlModel.getTransitions()) {
            Set<String> alreadyAddedLabels1 = new HashSet<>();
            State toState = atlModel.getState(transition.getToState());
            State fromState = atlModel.getState(transition.getFromState());
            alreadyAddedLabels1.add(fromState.getName());
            alreadyAddedLabels1.add(toState.getName());
            stringBuilder.append("\t").append("\t");
            if (!toState.equals(fromState)) {
                stringBuilder.append(fromState.getName()).append(" = false ");
                if (CollectionUtils.isNotEmpty(fromState.getLabels())) {
                    for (String label: fromState.getLabels()) {
                        if(!toState.getLabels().contains(label) && !alreadyAddedLabels1.contains(label)) {
                            stringBuilder.append("and ").append(label).append(" = false ");
                            alreadyAddedLabels1.add(label);
                        }
                    }
                }
                stringBuilder.append("and ");
            }

            stringBuilder.append(toState.getName()).append(" = true ");
            if (CollectionUtils.isNotEmpty(toState.getLabels())) {
                for (String label : toState.getLabels()) {
                    if(!alreadyAddedLabels1.contains(label)) {
                        stringBuilder.append("and ").append(label).append(" = true ");
                        alreadyAddedLabels1.add(label);
                    }
                }
            }
            stringBuilder.append(" if ");

            stringBuilder.append(fromState.getName()).append(" = true ");
            if (CollectionUtils.isNotEmpty(fromState.getLabels())) {
                for (String label: fromState.getLabels()) {
                    stringBuilder.append("and ").append(label).append(" = true ");
                }
            }

            if (!toState.equals(fromState)) {
                stringBuilder.append("and ").append(toState.getName()).append(" = false ");
                if (CollectionUtils.isNotEmpty(toState.getLabels())) {
                    for (String label : toState.getLabels()) {
                        if(!fromState.getLabels().contains(label)) {
                            stringBuilder.append("and ").append(label).append(" = false ");
                        }
                    }
                }
            }

            stringBuilder.append("and ");
            if (transition.getAgentActions().size()>1)
                stringBuilder.append("(");
            for (int i = 0; i < transition.getAgentActions().size(); i++) {
                List<AgentAction> agentActionList = transition.getAgentActions().get(i);
                stringBuilder.append("(");
                for (int j = 0; j < agentActionList.size(); j++) {
                    AgentAction agentAction = agentActionList.get(j);
                    stringBuilder.append(agentAction.getAgent()).append(".Action").append(" = ").append(agentAction.getAction());
                    if (j<agentActionList.size()-1)
                        stringBuilder.append(" and ");
                }
                stringBuilder.append(")");
                if (i<transition.getAgentActions().size()-1)
                    stringBuilder.append(" or ").append(System.lineSeparator()).append("\t\t\t\t\t");
            }
            if (transition.getAgentActions().size()>1)
                stringBuilder.append(")");

            if (transition.getMultipleAgentActions().size()>1)
                stringBuilder.append("(");
            for (int i = 0; i < transition.getMultipleAgentActions().size(); i++) {
                MultipleAgentAction multiAction = transition.getMultipleAgentActions().get(i);
                stringBuilder.append("(");
                for (int j = 0; j < multiAction.getActions().size(); j++) {
                    String agentAction = multiAction.getActions().get(j);
                    stringBuilder.append(multiAction.getAgent()).append(".Action").append(" = ").append(agentAction);
                    if (j<multiAction.getActions().size()-1)
                        stringBuilder.append(" or ");
                }
                stringBuilder.append(")");
                if (i<transition.getMultipleAgentActions().size()-1)
                    stringBuilder.append(" or ").append(System.lineSeparator()).append("\t\t\t\t\t");
            }
            if (transition.getMultipleAgentActions().size()>1)
                stringBuilder.append(")");

            stringBuilder.append(";").append(System.lineSeparator());
        }
        stringBuilder.append("\t").append("end Evolution").append(System.lineSeparator());
        stringBuilder.append("end Agent").append(System.lineSeparator());

        for (Agent agent : atlModel.getAgents()) {
            stringBuilder.append("Agent ").append(agent.getName()).append(System.lineSeparator());
            alreadyAddedLabels.clear();
            List<String> lobsvars = new ArrayList<>();
            for (State state : atlModel.getStates()) {
                boolean consider = true;
                for(List<String> lIndS : agent.getIndistinguishableStates()){
                    if(lIndS.contains(state.getName())) {
                        consider = false;
                        break;
                    }
                }
                if(consider) {
                    lobsvars.add(state.getName());
                    alreadyAddedLabels.add(state.getName());
                }
                for (String label : state.getLabels()) {
                    if(!alreadyAddedLabels.contains(label)) {
                        lobsvars.add(label);
                        alreadyAddedLabels.add(label);
                    }
                }
            }
            stringBuilder.append("\t").append("Lobsvars = {").append(String.join(", ", lobsvars)).append("};").append(System.lineSeparator());
            stringBuilder.append("\t").append("Vars : ").append(System.lineSeparator());
            if(CollectionUtils.isNotEmpty(agent.getIndistinguishableStates())) {
//                boolean first = true;
                for (List<String> lIndS : agent.getIndistinguishableStates()) {
//                    if (first) {
//                        first = false;
//                    } else {
//                        stringBuilder.append(",").append(System.lineSeparator());
//                    }
                    stringBuilder.append("\t").append("\t").append("imp_").append(String.join("_", lIndS)).append(": boolean;").append(System.lineSeparator());
                }
            }
            stringBuilder.append(System.lineSeparator()).append("\t").append("\t").append("play : boolean;");

            stringBuilder.append(System.lineSeparator()).append("\t").append("end Vars").append(System.lineSeparator());
            stringBuilder.append("\t").append("Actions = {").append(String.join(",", agent.getActions())).append("};");
            Map<String, List<String>> availableActionMap = getAvailableActions(atlModel, agent);
            stringBuilder.append(System.lineSeparator()).append("\t").append("Protocol : ").append(System.lineSeparator());
//            stringBuilder.append("Other : {").append(String.join(",", agent.getActions())).append("};");
            for (Entry<String, List<String>> availableActionsEntry: availableActionMap.entrySet()) {
                stringBuilder.append("\t").append("\t");
                if(!availableActionsEntry.getKey().startsWith("imp_")) {
                    stringBuilder.append("Environment.");
                }
                stringBuilder.append(availableActionsEntry.getKey()).append(" = true");
                if(!availableActionsEntry.getKey().startsWith("imp_")) {
                    State state = atlModel.getState(availableActionsEntry.getKey());
                    if (CollectionUtils.isNotEmpty(state.getLabels())) {
                        for (String label : state.getLabels())
                            stringBuilder.append(" and ").append("Environment.").append(label).append(" = true");
                    }
                    stringBuilder.append(" : {")
                            .append(String.join(",", availableActionsEntry.getValue())).append("};").append(System.lineSeparator());
                } else {
                    for(String s : availableActionsEntry.getKey().substring(4).split("_")) {
                        State state = atlModel.getState(s);
                        if (CollectionUtils.isNotEmpty(state.getLabels())) {
                            for (String label : state.getLabels())
                                stringBuilder.append(" and ").append("Environment.").append(label).append(" = true");
                        }
                    }
                    stringBuilder.append(" : {")
                            .append(String.join(",", availableActionsEntry.getValue())).append("};").append(System.lineSeparator());
                }
            }
            stringBuilder.append("\t").append("end Protocol").append(System.lineSeparator());
            stringBuilder.append("\t").append("Evolution : ").append(System.lineSeparator());
            stringBuilder.append("\t").append("\t").append("play = true if play = true;").append(System.lineSeparator());
            if(CollectionUtils.isNotEmpty(agent.getIndistinguishableStates())) {
                for (Transition transition : atlModel.getTransitions()) {
                    State toState = atlModel.getState(transition.getToState());
                    State fromState = atlModel.getState(transition.getFromState());
                    State toStateAux = toState, fromStateAux = fromState;
                    for (List<String> lIndS : agent.getIndistinguishableStates()) {
                        if (lIndS.contains(toState.getName())) {
                            toState = new State();
                            toState.setName("imp_" + String.join("_", lIndS));
                            toState.setInitial(atlModel.getStates().stream().anyMatch(s -> lIndS.contains(s.getName()) && s.isInitial()));
                            toState.setLabels(new ArrayList<>());
                            for (List<String> labels : atlModel.getStates().stream().filter(s -> lIndS.contains(s.getName())).map(State::getLabels).collect(Collectors.toSet())) {
                                toState.getLabels().addAll(labels);
                            }
                        }
                        if (lIndS.contains(fromState.getName())) {
                            fromState = new State();
                            fromState.setName("imp_" + String.join("_", lIndS));
                            fromState.setInitial(atlModel.getStates().stream().anyMatch(s -> lIndS.contains(s.getName()) && s.isInitial()));
                            fromState.setLabels(new ArrayList<>());
                            for (List<String> labels : atlModel.getStates().stream().filter(s -> lIndS.contains(s.getName())).map(State::getLabels).collect(Collectors.toSet())) {
                                fromState.getLabels().addAll(labels);
                            }
                        }
                    }
                    if (toState == toStateAux) { // && fromState == fromStateAux) {
                        continue;
                    }
//                if(toState == toStateAux) {
//                toState.setName("Environment." + toState.getName());
//                }
                    if (fromState == fromStateAux) {
                        fromState.setName("Environment." + fromState.getName());
                    }
                    stringBuilder.append("\t").append("\t");
//                if (!fromState.equals(toState)) {
//                    stringBuilder.append("(").append(fromState.getName()).append(" = false ").append(")");
//                    if (CollectionUtils.isNotEmpty(fromState.getLabels())) {
//                        for (String label: fromState.getLabels()) {
//                            if(!toState.getLabels().contains(label)) {
//                                stringBuilder.append("and ").append("(").append(label).append(" = false ").append(")");
//                            }
//                        }
//                    }
//                    stringBuilder.append("and ");
//                }

                    stringBuilder.append("(").append(toState.getName()).append(" = true ").append(")");
//                    if (CollectionUtils.isNotEmpty(toState.getLabels())) {
//                        for (String label : toState.getLabels()) {
//                            stringBuilder.append("and ").append("(").append(label).append(" = true ").append(")");
//                        }
//                    }
                    stringBuilder.append(" if ");

                    stringBuilder.append("(").append(fromState.getName()).append(" = true ").append(")");
                    if (CollectionUtils.isNotEmpty(fromState.getLabels())) {
                        for (String label : fromState.getLabels()) {
                            stringBuilder.append("and ").append("(").append("Environment.").append(label).append(" = true ").append(")");
                        }
                    }

                    if (!toState.equals(fromState)) {
                        stringBuilder.append("and ").append("(").append(toState.getName()).append(" = false ").append(")");
                        if (CollectionUtils.isNotEmpty(toState.getLabels())) {
                            for (String label : toState.getLabels()) {
                                if (!fromState.getLabels().contains(label)) {
                                    stringBuilder.append("and ").append("(").append("Environment.").append(label).append(" = false ").append(")");
                                }
                            }
                        }
                    }

                    stringBuilder.append("and ");
                    if (transition.getAgentActions().size() > 1)
                        stringBuilder.append("(");
                    for (int i = 0; i < transition.getAgentActions().size(); i++) {
                        List<AgentAction> agentActionList = transition.getAgentActions().get(i);
                        stringBuilder.append("(");
                        for (int j = 0; j < agentActionList.size(); j++) {
                            AgentAction agentAction = agentActionList.get(j);
                            stringBuilder.append(agentAction.getAgent()).append(".Action").append(" = ").append(agentAction.getAction());
                            if (j < agentActionList.size() - 1)
                                stringBuilder.append(" and ");
                        }
                        stringBuilder.append(")");
                        if (i < transition.getAgentActions().size() - 1)
                            stringBuilder.append(" or ").append(System.lineSeparator()).append("\t\t\t\t\t");
                    }
                    if (transition.getAgentActions().size() > 1)
                        stringBuilder.append(")");

                    if (transition.getMultipleAgentActions().size() > 1)
                        stringBuilder.append("(");
                    for (int i = 0; i < transition.getMultipleAgentActions().size(); i++) {
                        MultipleAgentAction multiAction = transition.getMultipleAgentActions().get(i);
                        stringBuilder.append("(");
                        for (int j = 0; j < multiAction.getActions().size(); j++) {
                            String agentAction = multiAction.getActions().get(j);
                            stringBuilder.append(multiAction.getAgent()).append(".Action").append(" = ").append(agentAction);
                            if (j < multiAction.getActions().size() - 1)
                                stringBuilder.append(" or ");
                        }
                        stringBuilder.append(")");
                        if (i < transition.getMultipleAgentActions().size() - 1)
                            stringBuilder.append(" or ").append(System.lineSeparator()).append("\t\t\t\t\t");
                    }
                    if (transition.getMultipleAgentActions().size() > 1)
                        stringBuilder.append(")");

                    stringBuilder.append(";").append(System.lineSeparator());
//                if(toState == toStateAux) {
//                toState.setName(toState.getName().substring(12));
//                }
                    if (fromState == fromStateAux) {
                        fromState.setName(fromState.getName().substring(12));
                    }
                }
            }
            stringBuilder.append("\t").append("end Evolution").append(System.lineSeparator());
            stringBuilder.append("end Agent").append(System.lineSeparator());
        }

        stringBuilder.append("Evaluation").append(System.lineSeparator());
        for (String term: atlModel.getATL().getTerms()) {
            stringBuilder.append("\t").append(term).append(" if (Environment.").append(term).append(" = true);").append(System.lineSeparator());
        }
        stringBuilder.append("\t").append("end Evaluation").append(System.lineSeparator());

        alreadyAddedLabels.clear();
        HashMap<String, Boolean> initialLabels = new HashMap<>();
//        HashMap<String, Boolean> initialLabelsAgents = new HashMap<>();
        stringBuilder.append("\t").append("InitStates").append(System.lineSeparator());
        List<State> auxStates = atlModel.getStates().stream().filter(s -> !s.getName().equals("sink")).collect(Collectors.toList());
        for (int i = 0; i < auxStates.size(); i++) {
            State state = auxStates.get(i);
            if(state.getName().equals("sink")) continue;
            stringBuilder.append("\t").append("\t").append("Environment.").append(state.getName()).append(" = ").append(state.isInitial());
            if (CollectionUtils.isNotEmpty(state.getLabels())) {
                for (int j = 0; j < state.getLabels().size(); j++) {
                    String label = state.getLabels().get(j);
                    if(initialLabels.containsKey(label)) {
                        if(state.isInitial()) {
                            initialLabels.put(label, state.isInitial());
                        }
                    } else {
                        initialLabels.put(label, state.isInitial());
                    }
                }
            }

            if (CollectionUtils.isNotEmpty(state.getFalseLabels())) {
                for (int j = 0; j < state.getFalseLabels().size(); j++) {
                    String label = state.getLabels().get(j);
                    initialLabels.putIfAbsent(label, false);
                }
            }

            if (i<auxStates.size()-1) {
                stringBuilder.append(" and ").append(System.lineSeparator());
            }
        }

        for(Agent agent : atlModel.getAgents()) {
            for(List<String> lIndS : agent.getIndistinguishableStates()) {
                stringBuilder.append(" and ").append(System.lineSeparator());
                stringBuilder.append("\t").append("\t").append(agent.getName()).append(".").append("imp_").append(String.join("_", lIndS)).append(" = ").append(atlModel.getStates().stream().anyMatch(s -> lIndS.contains(s.getName()) && s.isInitial()));
//                for(String s : lIndS) {
//                    List<String> l = atlModel.getState(s).getLabels();
//                    if(CollectionUtils.isNotEmpty(l)) {
//                        for (String label : l) {
//                            if (initialLabelsAgents.containsKey(label)) {
//                                if (atlModel.getState(s).isInitial()) {
//                                    initialLabelsAgents.put(agent.getName() + "." + label, atlModel.getState(s).isInitial());
//                                }
//                            } else {
//                                initialLabelsAgents.put(agent.getName() + "." + label, atlModel.getState(s).isInitial());
//                            }
//                        }
//                    }
//                }
            }
        }

        for(Entry<String, Boolean> initialLabel : initialLabels.entrySet()) {
            stringBuilder.append(" and ").append(System.lineSeparator());
            stringBuilder.append("\t").append("\t").append("Environment.").append(initialLabel.getKey()).append(" = ").append(initialLabel.getValue());
        }
//        for(Entry<String, Boolean> initialLabel : initialLabelsAgents.entrySet()) {
//            stringBuilder.append(" and ").append(System.lineSeparator());
//            stringBuilder.append("\t").append("\t").append(initialLabel.getKey()).append(" = ").append(initialLabel.getValue());
//        }

//        if (CollectionUtils.isNotEmpty(atlModel.getAgents())) {
//            stringBuilder.append(" and ").append(System.lineSeparator());
//            for (int i = 0; i < atlModel.getAgents().size(); i++) {
//                Agent agent = atlModel.getAgents().get(i);
//                stringBuilder.append("\t").append("\t").append(agent.getName()).append(".play = true");
//                if (i<atlModel.getAgents().size()-1)
//                    stringBuilder.append(" and ").append(System.lineSeparator());
//            }
//        }

        stringBuilder.append(";").append(System.lineSeparator()).append("\t").append("end InitStates").append(System.lineSeparator());

        stringBuilder.append("Groups").append(System.lineSeparator());
        for(Group g : atlModel.getGroups()) {
            stringBuilder.append("\t").append(g.getName()).append("=").append("{").append(String.join(",", g.getAgents())).append("};").append(System.lineSeparator());
        }
        stringBuilder.append("end Groups").append(System.lineSeparator());

        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append("Formulae").append(System.lineSeparator());
        stringBuilder1.append("\t");

//        ATL aux = atlModel.getATL();
        stringBuilder1.append(atlModel.getATL());
//        while(aux.getSubformula() != null) {
//            if(aux.getName() != null) {
//                //if(aux.getName().equals("A")) {
//                //    stringBuilder.append(aux.getName());
//                //} else {
//                stringBuilder1.append("<").append(aux.getName()).append(">");
//                //}
//            }
//            stringBuilder1.append(aux.getLTLFormula().replace("F", "F((").replace("G", "G((").replace("X", "X((")).append(")").append(aux.getOperator()).append(" ");
//            aux = aux.getSubformula();
//        }
//        if(aux.getName() != null) {
//            //if(aux.getName().equals("A")) {
//            //    stringBuilder.append(aux.getName());
//            //} else {
//            stringBuilder1.append("<").append(aux.getName()).append(">");
//            //}
//        }
//        if(aux.getLTLFormula().contains("F") || aux.getLTLFormula().contains("G") || aux.getLTLFormula().contains("X")) {
//            stringBuilder1.append(aux.getLTLFormula().replace("F", "F((").replace("G", "G((").replace("X", "X((")).append(")");
//        } else {
//            stringBuilder1.append(aux.getLTLFormula());
//        }
//        int parCount = StringUtils.countMatches(stringBuilder1.toString(), "(") - StringUtils.countMatches(stringBuilder1.toString(), ")");
//        stringBuilder1.append(")".repeat(Math.max(0, parCount)));
//        // stringBuilder.append("<").append(atlModel.getGroup().getName()).append(">").append(atlModel.getFormula().getSubformula());
        stringBuilder1.append(";").append(System.lineSeparator());
        stringBuilder1.append("end Formulae").append(System.lineSeparator());
        stringBuilder.append(stringBuilder1);

        return stringBuilder.toString();
    }


    private static Map<String, List<String>> getAvailableActions(AtlModel atlModel, Agent agent) {
        Map<String, List<String>> availableActionMap = new HashMap<>();
        for (Transition transition : atlModel.getTransitions()) {
            String from = transition.getFromState();
            for(List<String> lIndS : agent.getIndistinguishableStates()) {
                if(lIndS.contains(from)) {
                    from = "imp_" + String.join("_", lIndS);
                    break;
                }
            }
            if (!availableActionMap.containsKey(from)) {
                availableActionMap.put(from, new ArrayList<>());
            }
            for (List<AgentAction> agentActionList : transition.getAgentActions()) {
                for (AgentAction agentAction : agentActionList) {
                    if (agentAction.getAgent().equals(agent.getName()) &&
                            !availableActionMap.get(from).contains(agentAction.getAction())) {
                        availableActionMap.get(from).add(agentAction.getAction());
                    }
                }
            }
            for (MultipleAgentAction multipleAgentAction : transition.getMultipleAgentActions()) {
                if (multipleAgentAction.getAgent().equals(agent.getName())) {
                    for(String action : multipleAgentAction.getActions()) {
                        if(!availableActionMap.get(from).contains(action)) {
                            availableActionMap.get(from).add(action);
                        }
                    }
                }
            }
        }

        return availableActionMap;
    }

    public static String modelCheck_ir(String mcmasFilePath) throws IOException { // -atlk -uniform
        try(Scanner scanner = new Scanner(Runtime.getRuntime().exec(mcmas + "/mcmas -atlk -uniform " + mcmasFilePath).getInputStream()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    public static String modelCheck_IR(String mcmasFilePath) throws IOException {
        try(Scanner scanner = new Scanner(Runtime.getRuntime().exec(mcmas + "/mcmas " + mcmasFilePath).getInputStream()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    public static boolean doesMCMASReturnTrue(String mcmasOutput) {
        return  (mcmasOutput.contains("is TRUE in the model"));
    }

    public static boolean doesMCMASReturnFalse(String mcmasOutput) {
        return  (mcmasOutput.contains("is FALSE in the model"));
    }


//    public static List<AtlModel> allModels(AtlModel model) {
//        List<AtlModel> allModels = new ArrayList<>();
//        for(int k = 1; k <= model.getStates().size(); k++) {
//            Set<Set<State>> combinations = allCombinations(model, k);
//            for (Set<State> combinationK : combinations) {
//                for (State initialState : combinationK) {
//                    AtlModel modelK = new AtlModel();
//                    List<State> auxList = new ArrayList<>();
//                    for (State stateAux : combinationK) {
//                        State newState = new State(stateAux.getName(), initialState.equals(stateAux));
//                        newState.setLabels(new ArrayList<>(stateAux.getLabels()));
//                        newState.setFalseLabels(new ArrayList<>(stateAux.getFalseLabels()));
//                        auxList.add(newState);
//                    }
//                    modelK.setStates(auxList);
//                    List<Agent> agentsAuxList = new ArrayList<>();
//                    for(Agent agent : model.getAgents()) {
//                        Agent newAgent = new Agent();
//                        newAgent.setName(agent.getName());
//                        newAgent.setActions(new ArrayList<>(agent.getActions()));
//                        newAgent.setIndistinguishableStates(new ArrayList<>());
//                        for(List<String> indS : agent.getIndistinguishableStates()) {
//                            List<String> indSAux = indS.stream().filter(modelK::hasState).collect(Collectors.toList());
//                            if(indSAux.size() >= 2){
//                                newAgent.getIndistinguishableStates().add(indSAux);
//                            }
//                        }
//                        agentsAuxList.add(newAgent);
//                    }
//                    modelK.setAgents(agentsAuxList);
//                    modelK.setFormula(null); // will be set using innermost formula in Alg1 and Alg2
//                    modelK.setGroup(model.getGroup());
//                    Set<Transition> transitionsK = new HashSet<>();
//                    boolean firstSink = true;
//                    for (Transition trans : model.getTransitions()) {
//                        if (modelK.hasState(trans.getFromState())) {
//                            if(modelK.hasState(trans.getToState())) {
//                                transitionsK.add(trans);
//                            }
//                            else {
//                                if(firstSink) {
//                                    State sinkState = new State();
//                                    sinkState.setName("sink");
//                                    auxList.add(sinkState);
//                                    modelK.setStates(auxList);
//                                    firstSink = false;
//                                }
//                                Transition trSink = new Transition();
//                                trSink.setFromState(trans.getFromState());
//                                trSink.setToState("sink");
//                                trSink.setAgentActions(trans.copyAgentActions());
//                                trSink.setMultipleAgentActions(trans.copyMultiAgentActions());
//                                trSink.setDefaultTransition(trans.isDefaultTransition());
//                                transitionsK.add(trSink);
//                            }
//                        }
//                    }
//                    modelK.setTransitions(new ArrayList<>(transitionsK));
//                    modelK.setStateMap(null);
//                    allModels.add(modelK);
//                }
//            }
//        }
//        return new ArrayList<>(allModels);
//    }
//
//    private static Set<Set<State>> allCombinations(AtlModel model, int k) {
//        Set<Set<State>> groupsOfK = new HashSet<>();
//        if(k <= 1) {
//            for (State state : model.getStates()) {
//                State newState = new State(state.getName(), false);
//                newState.setLabels(state.getLabels());
//                HashSet<State> aux = new HashSet<>();
//                aux.add(newState);
//                groupsOfK.add(aux);
//            }
//        } else {
//            for (Set<State> groupOfKMinus1 : allCombinations(model, k - 1)) {
//                for (State state : model.getStates()) {
//                    if (!groupOfKMinus1.contains(state)) {
//                        Set<State> groupOfK = new HashSet<>();
//                        for (State stateAux : groupOfKMinus1) {
//                            State newState = new State(stateAux.getName(), false);
//                            newState.setLabels(stateAux.getLabels());
//                            groupOfK.add(newState);
//                        }
//                        State newState = new State(state.getName(), false);
//                        newState.setLabels(state.getLabels());
//                        groupOfK.add(newState);
//                        groupsOfK.add(groupOfK);
//                    }
//                }
//            }
//        }
//        return groupsOfK;
//    }

    public static Iterable<AtlModel> allModels(AtlModel model) {
        return () -> {
            //List<AtlModel> models = new LinkedList<>();
            boolean[] state = new boolean[model.getStates().size()];
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    //if (!models.isEmpty()) return true;
                    for (boolean b : state) if (!b) return true;
                    return false;
                }

                @Override
                public AtlModel next() {
                    //if (!models.isEmpty()) {
                    //    return models.remove(0);
                    //}
                    for (int i = 0; i < state.length; i++) {
                        if (!state[i]) {
                            state[i] = true;
                            break;
                        } else {
                            state[i] = false;
                        }
                    }
                    Set<State> combination = new HashSet<>();
                    for (int i = 0; i < state.length; i++) {
                        if (state[i]) {
                            State newState = new State(model.getStates().get(i).getName(), false);
                            newState.setLabels(model.getStates().get(i).getLabels());
                            combination.add(newState);
                        }
                    }
                    //for (State initialState : combination) {
                    AtlModel modelK = new AtlModel();
                    List<State> auxList = new ArrayList<>();
                    for (State stateAux : combination) {
                        State newState = new State(stateAux.getName(), false);
                        newState.setLabels(new ArrayList<>(stateAux.getLabels()));
                        newState.setFalseLabels(new ArrayList<>(stateAux.getFalseLabels()));
                        auxList.add(newState);
                    }
                    modelK.setStates(auxList);
                    List<Agent> agentsAuxList = new ArrayList<>();
                    for (Agent agent : model.getAgents()) {
                        Agent newAgent = new Agent();
                        newAgent.setName(agent.getName());
                        newAgent.setActions(new ArrayList<>(agent.getActions()));
                        newAgent.setIndistinguishableStates(new ArrayList<>());
                        for (List<String> indS : agent.getIndistinguishableStates()) {
                            List<String> indSAux = indS.stream().filter(modelK::hasState).collect(Collectors.toList());
                            if (indSAux.size() >= 2) {
                                newAgent.getIndistinguishableStates().add(indSAux);
                            }
                        }
                        agentsAuxList.add(newAgent);
                    }
                    modelK.setAgents(agentsAuxList);
                    modelK.setFormula(null); // will be set using innermost formula in Alg1 and Alg2
                    modelK.setGroups(model.getGroups());
                    Set<Transition> transitionsK = new HashSet<>();
                    boolean firstSink = true;
                    for (Transition trans : model.getTransitions()) {
                        if (modelK.hasState(trans.getFromState())) {
                            if (modelK.hasState(trans.getToState())) {
                                transitionsK.add(trans);
                            } else {
                                if (firstSink) {
                                    State sinkState = new State();
                                    sinkState.setName("sink");
                                    auxList.add(sinkState);
                                    modelK.setStates(auxList);
                                    firstSink = false;
                                    Transition trSink = new Transition();
                                    trSink.setFromState("sink");
                                    trSink.setToState("sink");
                                    trSink.setAgentActions(new ArrayList<>());
                                    List<MultipleAgentAction> multipleAgentActions = new ArrayList<>();
                                    for(Agent a : model.getAgents()) {
                                        MultipleAgentAction multipleAgentAction = new MultipleAgentAction();
                                        multipleAgentAction.setAgent(a.getName());
                                        multipleAgentAction.setActions(a.getActions());
                                        multipleAgentActions.add(multipleAgentAction);
                                    }
                                    trSink.setMultipleAgentActions(multipleAgentActions);
                                    trSink.setDefaultTransition(false);
                                    transitionsK.add(trSink);
                                }
                                Transition trSink = new Transition();
                                trSink.setFromState(trans.getFromState());
                                trSink.setToState("sink");
                                trSink.setAgentActions(trans.copyAgentActions());
                                trSink.setMultipleAgentActions(trans.copyMultiAgentActions());
                                trSink.setDefaultTransition(trans.isDefaultTransition());
                                transitionsK.add(trSink);
                            }
                        }
                    }
                    modelK.setTransitions(new ArrayList<>(transitionsK));
                    modelK.setStateMap(null);
                    return modelK;
                    //models.add(modelK);
                    //}
                    //return models.remove(0);
                }
            };
        };
    }

    public enum Verification { ImperfectRecall, PerfectInformation, Both };

    public static Map<ATL, Set<String>> validateSubModels(List<AtlModel> models, ATL phi, Verification verification) throws IOException {
        Map<ATL, Set<String>> res = new HashMap<>();
        int index = 0;
        boolean satisfied;
        List<AtlModel> toKeep = new ArrayList<>();
        ATL phiPrime;
        do {
            phiPrime = phi.innermostFormula();
            ATL phiAux = phi.clone();
            phiAux = phiAux.updateInnermostFormula("atom");

            for (AtlModel model : models) {
                model.setATL(phiPrime);
                Set<String> lbls = new HashSet<>();
                for (int ii = 0; ii < model.getStates().size(); ii++) {
                    for (int jj = 0; jj < model.getStates().size(); jj++) {
                        model.getStates().get(jj).setInitial(ii == jj);
                    }
                    String mcmasProgram = AbstractionUtils.generateMCMASProgram(model);
                    // write temporary ispl file
                    String fileName = "./tmp/subModel.ispl";
                    Files.write(Paths.get(fileName), mcmasProgram.getBytes());
                    // model check the ispl model
                    String s;
                    satisfied = false;
                    if (verification == Verification.ImperfectRecall) {
                        s = AbstractionUtils.modelCheck_ir(fileName);
                        if(!AbstractionUtils.doesMCMASReturnFalse(s) && !AbstractionUtils.doesMCMASReturnTrue(s)) {
                            String pippo = "pippo";
                        }
                        if (AbstractionUtils.doesMCMASReturnTrue(s)) {
                            satisfied = true;
                        }
                    } else if(verification == Verification.PerfectInformation){
                        s = AbstractionUtils.modelCheck_IR(fileName);
                        if (AbstractionUtils.doesMCMASReturnTrue(s)) {
                            satisfied = true;
                        }
                    } else {
                        s = AbstractionUtils.modelCheck_ir(fileName);
                        if (AbstractionUtils.doesMCMASReturnTrue(s)) {
                            satisfied = true;
                        } else {
                            s = AbstractionUtils.modelCheck_IR(fileName);
                            if (AbstractionUtils.doesMCMASReturnTrue(s)) {
                                satisfied = true;
                            }
                        }
                    }
                    if (satisfied) {
                        model.getStates().get(ii).getLabels().add("atom" + index);
                        lbls.add(model.getStates().get(ii).getName());
                    }
                }
                if (!lbls.isEmpty()) {
                    toKeep.add(model);
                    if (res.containsKey(phiAux)) {
                        res.get(phiAux).addAll(lbls);
                    } else {
                        res.put(phiAux, lbls);
                    }
                }
            }
            models.removeIf(m -> !toKeep.contains(m));
            toKeep.clear();
            phi.updateInnermostFormula("atom" + index++);
        } while(!(phiPrime instanceof ATL.Atom)); //while(!phiPrime.getLTLFormula().equals("atom" + index++));

        return res;
    }

    public static Boolean verify(AtlModel model, Map<ATL, Set<String>> verifiedSubProp, boolean silent) throws IOException {
        for(Entry<ATL, Set<String>> entry : verifiedSubProp.entrySet()) {
            if(!silent) System.out.println("Verifying formula " + entry.getKey());
            AtlModel aux = model.clone();
            for(String s : entry.getValue()) {
                aux.getState(s).getLabels().add("atom");
            }
            ATL ctl = entry.getKey().clone();
            ctl.convertToCTL(true);
            aux.setATL(ctl);
            aux.setGroups(null);
            String mcmasProgram = AbstractionUtils.generateMCMASProgram(aux);
            String fileName = "./tmp/model.ispl";
            Files.write(Paths.get(fileName), mcmasProgram.getBytes());
            String s = AbstractionUtils.modelCheck_IR(fileName);
            if (AbstractionUtils.doesMCMASReturnTrue(s)) {
                //System.out.println("TRUE");
                return true;
            }
            ctl = entry.getKey().clone();
            ctl.convertToCTL(false);
            aux.setATL(ctl);
            mcmasProgram = AbstractionUtils.generateMCMASProgram(aux);
            fileName = "./tmp/model.ispl";
            Files.write(Paths.get(fileName), mcmasProgram.getBytes());
            s = AbstractionUtils.modelCheck_IR(fileName);
            if (AbstractionUtils.doesMCMASReturnFalse(s)) {
                //System.out.println("FALSE");
                return false;
            }
        }
        return null;
    }

}