package utils;

import com.google.common.collect.Sets;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.checkerframework.checker.units.qual.A;
import parser.ATL;
import parser.ATLLexer;
import parser.ATLParser;
import parser.ATLVisitorImpl;

import java.util.*;
import java.util.stream.Collectors;

public class AtlModel extends JsonObject implements Cloneable {

	@SerializedName("states")
	@Expose
	private List<State> states = null;
	@SerializedName("agents")
	@Expose
	private List<Agent> agents = new ArrayList<>();
	@SerializedName("transitions")
	@Expose
	private List<Transition> transitions = new ArrayList<>();
	@SerializedName("groups")
	@Expose
	private List<Group> groups = new ArrayList<>();
	@SerializedName("formula")
	@Expose
	private String formula;
	private ATL atl;
	
	private transient Map<String, State> stateMap;
	
	private transient Map<String, Agent> agentMap;
	
	private transient Map<String, List<Transition>> transitionMap;

	private transient MultiKeyMap<String, List<List<AgentAction>>> agentActionsByStates;

	public List<? extends State> getStates() {
		return states;
	}

	public void setStates(List<State> states) {
		this.states = states;
	}

	public List<Agent> getAgents() {
		return agents;
	}

	public void setAgents(List<Agent> agents) {
		this.agents = agents;
	}

	public List<Transition> getTransitions() {
		return transitions;
	}

	public void setTransitions(List<Transition> transitions) {
		this.transitions = transitions;
	}

	public List<Group> getGroups() {
		return groups;
	}

	public void setGroups(List<Group> groups) {
		this.groups = groups;
	}

	public String getFormula() {return formula; }

	public void setFormula(String formula) { this.formula = formula; }

	public ATL getATL() {
		if(atl == null) {
			CharStream codePointCharStream = CharStreams.fromString(formula);
			ATLLexer lexer = new ATLLexer(codePointCharStream);
			ATLParser parser = new ATLParser(new CommonTokenStream(lexer));
			ParseTree tree = parser.atlExpr();
			ATLVisitorImpl visitor = new ATLVisitorImpl();
			atl = visitor.visit(tree);
		}
		return atl;
	}

	public void setATL(ATL formula) {
		this.atl = formula;
	}

	public void makeTransitionsUnique() {
		int id = 0;
		for(Transition tr : transitions) {
			for(List<AgentAction> acts : tr.getAgentActions()) {
				for(AgentAction act : acts) {
					String actAux = act.getAction();
					act.setAction(act.getAction() + "_" + id++);
					for(Agent ag : agents) {
						if(ag.getName().equals(act.getAgent())) {
							ag.getActions().remove(actAux);
							ag.getActions().add(act.getAction());
						}
					}
				}
			}
			List<MultipleAgentAction> multActs = new ArrayList<>();
			for(MultipleAgentAction acts : tr.getMultipleAgentActions()) {
				MultipleAgentAction newAct = new MultipleAgentAction();
				newAct.setAgent(acts.getAgent());
				newAct.setActions(new ArrayList<>());
				for(String act : acts.getActions()) {
					newAct.getActions().add(act + "_" + id++);
				}
				multActs.add(newAct);
			}
			tr.setMultipleAgentActions(multActs);
		}
	}

	public Map<String, State> getStateMap() {
		if (stateMap == null) {
			stateMap = new HashMap<>();
			for (State state : getStates()) {
				stateMap.put(state.getName(), state);
			}
		}
		return stateMap;
	}

	public boolean hasState(String stateName) {
		return getStateMap().containsKey(stateName);
	}

	public State getState(String stateName) {
		return getStateMap().get(stateName);
	}
	
	public Map<String, Agent> getAgentMap() {
		if (agentMap == null) {
			agentMap = new HashMap<>();
			for (Agent agent : getAgents()) {
				agentMap.put(agent.getName(), agent);
			}
		}
		return agentMap;
	}

	public void setAgentMap(Map<String, Agent> map) { this.agentMap = map; }

	public void setStateMap(Map<String, State> map) {
		this.stateMap = map;
	}
	
	public MultiKeyMap<String, List<List<AgentAction>>> getAgentActionsByStates() {
		if (agentActionsByStates == null) {
			agentActionsByStates = new MultiKeyMap<>();
			for (Transition transition : getTransitions()) {
				if (!agentActionsByStates.containsKey(transition.getFromState(), transition.getToState())) {
					agentActionsByStates.put(transition.getFromState(), transition.getToState(), new ArrayList<>());
				}
				agentActionsByStates.get(transition.getFromState(), transition.getToState()).addAll(transition.getAgentActions());
			}
		}
		
		return agentActionsByStates;
	}
	
	public Map<String, List<Transition>> getTransitionMap() {
		if (MapUtils.isEmpty(transitionMap)) {
			transitionMap = new HashMap<>();
			for (Transition transition : getTransitions()) {
				if (!transitionMap.containsKey(transition.getFromState())) {
					transitionMap.put(transition.getFromState(), new ArrayList<>());
				}
				transitionMap.get(transition.getFromState()).add(transition);
			}
		}
		
		return transitionMap;
	}
	
	public void updateModel(String atom) {
		for(State state : states) {
			if(state.isInitial()) {
				state.getLabels().add(atom);
			}
		}
	}

	public void removeState(State state) {
		if(states.remove(state)) {
			List<Transition> list = new ArrayList<>();
			if(!hasState("sink")){
				State sinkState = new State();
				sinkState.setName("sink");
				states.add(sinkState);
			}
			for (Transition t : transitions) {
				if (!t.getFromState().equals(state.getName())) {
					if(!t.getToState().equals(state.getName())) {
						list.add(t);
					} else {
						Transition trSink = new Transition();
						trSink.setFromState(t.getFromState());
						trSink.setToState("sink");
						trSink.setAgentActions(t.copyAgentActions());
						trSink.setMultipleAgentActions(t.copyMultiAgentActions());
						list.add(trSink);
					}
				}
			}
			transitions = list;
			transitionMap = null;
			stateMap = null;
			for(Agent agent : agents) {
				for(List<String> ind : agent.getIndistinguishableStates()) {
					ind.remove(state.getName());
				}
				agent.setIndistinguishableStates(agent.getIndistinguishableStates().stream().filter(ind -> ind.size() > 1).collect(Collectors.toList()));
			}
		}
	}

	@Override
	public AtlModel clone() {
		AtlModel clone;
		try {
			clone = (AtlModel) super.clone();
		}
		catch (CloneNotSupportedException ex) {
			throw new RuntimeException("Superclass messed up", ex);
		}

		List<State> statesAuxList = new ArrayList<>();
		for (State state : states) {
			State newState = new State(state.getName(), state.isInitial());
			newState.setLabels(new ArrayList<>(state.getLabels()));
			newState.setFalseLabels(new ArrayList<>(state.getFalseLabels()));
			statesAuxList.add(newState);
		}
		clone.states = statesAuxList;
		List<Agent> agentsAuxList = new ArrayList<>();
		for(Agent agent : agents) {
			Agent newAgent = new Agent();
			newAgent.setName(agent.getName());
			newAgent.setActions(new ArrayList<>(agent.getActions()));
			newAgent.setIndistinguishableStates(new ArrayList<>());
			for(List<String> indS : agent.getIndistinguishableStates()) {
				newAgent.getIndistinguishableStates().add(new ArrayList<>(indS));
			}
			agentsAuxList.add(newAgent);
		}
		clone.agents = agentsAuxList;
		List<Transition> transitionsAuxList = new ArrayList<>();
		for(Transition tr : transitions) {
			Transition newTransition = new Transition();
			newTransition.setFromState(tr.getFromState());
			newTransition.setToState(tr.getToState());
			newTransition.setAgentActions(new ArrayList<>());
			for(List<AgentAction> aal : tr.getAgentActions()) {
				List<AgentAction> aalAux = new ArrayList<>();
				for(AgentAction aa : aal) {
					AgentAction newAa = new AgentAction();
					newAa.setAgent(aa.getAgent());
					newAa.setAction(aa.getAction());
					aalAux.add(newAa);
				}
				newTransition.getAgentActions().add(aalAux);
			}
			List<MultipleAgentAction> maalAux = new ArrayList<>();
			for(MultipleAgentAction maa : tr.getMultipleAgentActions()) {
				MultipleAgentAction newMaa = new MultipleAgentAction();
				newMaa.setAgent(maa.getAgent());
				newMaa.setActions(new ArrayList<>(maa.getActions()));
				maalAux.add(newMaa);
			}
			newTransition.setMultipleAgentActions(maalAux);
			newTransition.setDefaultTransition(tr.isDefaultTransition());
			transitionsAuxList.add(newTransition);
		}
		clone.transitions = transitionsAuxList;
		clone.groups = new ArrayList<>();
		for(Group g : groups) {
			Group ng = new Group();
			ng.setName(g.getName());
			ng.setAgents(new ArrayList<>(g.getAgents()));
			clone.groups.add(ng);
		}
		clone.formula = formula;
		clone.atl = getATL().clone();
		clone.agentMap = null;
		clone.stateMap = null;
		return clone;
	}

	public boolean isConnected() {
		State initialState = states.stream().filter(State::isInitial).findFirst().orElseGet(null);
		if(initialState == null) {
			return false;
		} else {
			return reachableStates(initialState.getName(), new HashSet<>()).size() == states.size();
		}
	}

	private Set<String> reachableStates(String state, Set<String> visitedStates) {
		visitedStates.add(state);
		List<String> nextStates = transitions
				.stream()
				.filter(t -> t.getFromState().equals(state) && !visitedStates.contains(t.getToState()))
				.map(Transition::getToState)
				.collect(Collectors.toList());
		if (!nextStates.isEmpty()) {
			for (String s : nextStates) {
				visitedStates.addAll(reachableStates(s, visitedStates));
			}
		}
		return visitedStates;
	}

	public void removeSubModel(AtlModel subModel, String atom) {
		State metaState = new State();
		metaState.setName("metastate");
		metaState.getLabels().add(atom);
		states.add(metaState);
		stateMap = null;
		for(State state : subModel.getStates()) {
			if(this.getState(state.getName()) != null && this.getState(state.getName()).isInitial()) {
				metaState.setInitial(true);
			}
			this.states.remove(state);
			if (state.isInitial()) {
				List<Transition> list = new ArrayList<>();
				for (Transition t : transitions) {
					if (!t.getFromState().equals(state.getName()) && t.getToState().equals(state.getName())) {
						Transition trMeta = new Transition();
						trMeta.setFromState(t.getFromState());
						trMeta.setToState("metastate");
						trMeta.setAgentActions(t.copyAgentActions());
						trMeta.setMultipleAgentActions(t.copyMultiAgentActions());
						list.add(trMeta);
					}
				}
				transitions.addAll(list);
			}
			transitions.removeIf(t -> t.getFromState().equals(state.getName()) || t.getToState().equals(state.getName()));
		}
	}

	public AtlModel parallel(AtlModel other) {
		AtlModel result = new AtlModel();
		result.states = new ArrayList<>();
		for(State state1 : this.states) {
			for(State state2 : other.states) {
				State state = new State();
				state.setName(state1.getName() + state2.getName());
				state.setInitial(state1.isInitial() && state2.isInitial());
				List<String> labels = new ArrayList<>(state1.getLabels());
				labels.addAll(state2.getLabels());
				state.setLabels(labels);
				List<String> falseLabels = new ArrayList<>(state1.getFalseLabels());
				labels.addAll(state2.getFalseLabels());
				state.setFalseLabels(falseLabels);
				result.states.add(state);
			}
		}
		result.transitions = new ArrayList<>();
		for(Transition transition1 : this.transitions) {
			for(Transition transition2 : other.transitions) {
				Transition transition = new Transition();
				transition.setFromState(transition1.getFromState() + transition2.getFromState());
				transition.setToState(transition1.getToState() + transition2.getToState());
				transition.setDefaultTransition(transition1.isDefaultTransition() && transition2.isDefaultTransition());
				List<List<AgentAction>> agentActionsList = new ArrayList<>();
				for(List<AgentAction> agentActions1 : transition1.getAgentActions()) {
					for(List<AgentAction> agentActions2 : transition2.getAgentActions()) {
						List<AgentAction> agentActions = new ArrayList<>(agentActions1);
						boolean skip = false;
						for(AgentAction agentAction2 : agentActions2) {
							Optional<AgentAction> optAct = agentActions.stream().filter(a -> a.getAgent().equals(agentAction2.getAgent())).findFirst();
							if(optAct.isPresent()) {
								if(!optAct.get().getAction().equals(agentAction2.getAction())) {
									skip = true;
									break;
								}
							} else {
								agentActions.add(agentAction2);
							}
						}
						if(!skip) {
							agentActionsList.add(agentActions);
						}
					}
				}
				transition.setAgentActions(agentActionsList);
				List<MultipleAgentAction> multipleAgentActionsList = new ArrayList<>();
				for(MultipleAgentAction action1 : transition1.getMultipleAgentActions()) {
					Optional<MultipleAgentAction> optAct = transition2.getMultipleAgentActions().stream().filter(a -> a.getAgent().equals(action1.getAgent())).findFirst();
					if(optAct.isPresent()) {
						MultipleAgentAction multipleAgentAction = new MultipleAgentAction();
						multipleAgentAction.setActions(action1.getActions().stream().filter(a1 -> optAct.get().getActions().stream().anyMatch(a2 -> a2.equals(a1))).collect(Collectors.toList()));

					} else {
						multipleAgentActionsList.add(action1);
					}
				}
				for(MultipleAgentAction action2 : transition2.getMultipleAgentActions()) {
					Optional<MultipleAgentAction> optAct = transition1.getMultipleAgentActions().stream().filter(a -> a.getAgent().equals(action2.getAgent())).findFirst();
					if (optAct.isEmpty()) {
						multipleAgentActionsList.add(action2);
					}
				}
				transition.setMultipleAgentActions(multipleAgentActionsList);
				result.transitions.add(transition);
			}
		}
		result.setAgents(new ArrayList<>());
		for(Agent agent : this.agents) {
			Agent newAgent = new Agent();
			newAgent.setName(agent.getName());
			newAgent.setActions(new ArrayList<>(agent.getActions()));
			newAgent.setIndistinguishableStates(new ArrayList<>());
			for(List<String> ind : agent.getIndistinguishableStates()) {
				List<String> alreadySeen = new ArrayList<>();
				for(String i : ind) {
					alreadySeen.add(i);
					for (State state1 : other.getStates()) {
						List<String> newInd = new ArrayList<>();
						newInd.add(i + state1.getName());
						for(String j : ind.stream().filter(k -> !alreadySeen.contains(k)).collect(Collectors.toList())) {
							for (State state2 : other.getStates()) {
								newInd.add(j + state2.getName());
							}
						}
						if(newInd.size() > 1) {
							newAgent.getIndistinguishableStates().add(newInd);
						}
					}
				}
			}
			result.getAgents().add(newAgent);
		}
		for(Agent agent : other.agents) {
			Agent newAgent = new Agent();
			newAgent.setName(agent.getName());
			newAgent.setActions(new ArrayList<>(agent.getActions()));
			newAgent.setIndistinguishableStates(new ArrayList<>());
			for(List<String> ind : agent.getIndistinguishableStates()) {
				List<String> alreadySeen = new ArrayList<>();
				for(String i : ind) {
					alreadySeen.add(i);
					for (State state1 : this.getStates()) {
						List<String> newInd = new ArrayList<>();
						newInd.add(state1.getName() + i);
						for(String j : ind.stream().filter(k -> !alreadySeen.contains(k)).collect(Collectors.toList())) {
							for (State state2 : this.getStates()) {
								newInd.add(state2.getName() + j);
							}
						}
						if(newInd.size() > 1) {
							newAgent.getIndistinguishableStates().add(newInd);
						}
					}
				}
			}
			result.getAgents().add(newAgent);
		}
//		result.setAgents(new ArrayList<>());
//		for(Agent agent : this.agents) {
//			Agent newAgent = new Agent();
//			newAgent.setName(agent.getName());
//			newAgent.setActions(new ArrayList<>(agent.getActions()));
//			newAgent.setIndistinguishableStates(new ArrayList<>());
//			for(List<String> ind : agent.getIndistinguishableStates()) {
//				List<String> newInd = new ArrayList<>();
//				for (State state : other.getStates()) {
//					newInd.addAll(ind.stream().map(i -> i + state.getName()).collect(Collectors.toList()));
//				}
//				newAgent.getIndistinguishableStates().add(newInd);
//			}
//			result.getAgents().add(newAgent);
//		}
//		for(Agent agent : other.agents) {
//			Agent newAgent = new Agent();
//			newAgent.setName(agent.getName());
//			newAgent.setActions(new ArrayList<>(agent.getActions()));
//			newAgent.setIndistinguishableStates(new ArrayList<>());
//			for(List<String> ind : agent.getIndistinguishableStates()) {
//				List<String> newInd = new ArrayList<>();
//				for (State state : this.getStates()) {
//					newInd.addAll(ind.stream().map(i -> state.getName() + i).collect(Collectors.toList()));
//				}
//				newAgent.getIndistinguishableStates().add(newInd);
//			}
//			result.getAgents().add(newAgent);
//		}
//		result.setAgents(new ArrayList<>());
//		for(Agent agent : this.agents) {
//			Agent newAgent = new Agent();
//			newAgent.setName(agent.getName());
//			newAgent.setActions(new ArrayList<>(agent.getActions()));
//			newAgent.setIndistinguishableStates(new ArrayList<>());
//			for (List<String> ind : agent.getIndistinguishableStates()) {
//				for(State s : other.getStates()) {
//					newAgent.getIndistinguishableStates().add(ind.stream().map(i -> i + s.getName()).collect(Collectors.toList()));
//				}
//			}
//			result.getAgents().add(newAgent);
//		}
//		for(Agent agent : other.agents) {
//			Agent newAgent = new Agent();
//			newAgent.setName(agent.getName());
//			newAgent.setActions(new ArrayList<>(agent.getActions()));
//			newAgent.setIndistinguishableStates(new ArrayList<>());
//			for (List<String> ind : agent.getIndistinguishableStates()) {
//				for(State s : this.getStates()) {
//					newAgent.getIndistinguishableStates().add(ind.stream().map(i -> s.getName() + i).collect(Collectors.toList()));
//				}
//			}
//			Optional<Agent> optAg = result.getAgents().stream().filter(a -> a.getName().equals(agent.getName())).findFirst();
//			if(optAg.isEmpty()) {
//				result.getAgents().add(newAgent);
//			} else {
//				optAg.get().getActions().addAll(newAgent.getActions().stream().filter(a1 -> optAg.get().getActions().stream().noneMatch(a2 -> a2.equals(a1))).collect(Collectors.toList()));
//				optAg.get().getIndistinguishableStates().addAll(newAgent.getIndistinguishableStates());
//			}
//		}
//		for(Agent agent : this.agents) {
//			Agent newAgent = new Agent();
//			newAgent.setName(agent.getName());
//			newAgent.setActions(new ArrayList<>(agent.getActions()));
//			newAgent.setIndistinguishableStates(new ArrayList<>());
//			for(List<String> ind : agent.getIndistinguishableStates()) {
//				List<List<String>> newInd = new ArrayList<>();
//				int n = 1;
//				for(String indState : ind) {
//					List<String> indStateAux = other.getStates().stream().map(s -> indState + s.getName()).collect(Collectors.toList());
//					newInd.add(indStateAux);
//					n *= indStateAux.size();
//				}
//				int[] combination = new int[newInd.size()];
//				List<List<String>> indRes = new ArrayList<>();
//				for(int i = 0; i < n; i++) {
//					List<String> sAux = new ArrayList<>();
//					for(int k = 0; k < newInd.size(); k++) {
//						sAux.add(newInd.get(k).get(combination[k]));
//					}
//					indRes.add(sAux);
//					for(int k = 0; k < combination.length; k++) {
//						combination[k]++;
//						if(combination[k] == newInd.get(k).size()) {
//							combination[k] = 0;
//						} else {
//							break;
//						}
//					}
//				}
//				newAgent.getIndistinguishableStates().addAll(indRes);
//			}
//			result.getAgents().add(newAgent);
//		}
//		for(Agent agent : other.agents) {
//			Agent newAgent = new Agent();
//			newAgent.setName(agent.getName());
//			newAgent.setActions(new ArrayList<>(agent.getActions()));
//			newAgent.setIndistinguishableStates(new ArrayList<>());
//			for(List<String> ind : agent.getIndistinguishableStates()) {
//				List<List<String>> newInd = new ArrayList<>();
//				int n = 1;
//				for(String indState : ind) {
//					List<String> indStateAux = other.getStates().stream().map(s -> s.getName() + indState).collect(Collectors.toList());
//					newInd.add(indStateAux);
//					n *= indStateAux.size();
//				}
//				int[] combination = new int[newInd.size()];
//				List<List<String>> indRes = new ArrayList<>();
//				for(int i = 0; i < n; i++) {
//					List<String> sAux = new ArrayList<>();
//					for(int k = 0; k < newInd.size(); k++) {
//						sAux.add(newInd.get(k).get(combination[k]));
//					}
//					indRes.add(sAux);
//					for(int k = 0; k < combination.length; k++) {
//						combination[k]++;
//						if(combination[k] == newInd.get(k).size()) {
//							combination[k] = 0;
//						} else {
//							break;
//						}
//					}
//				}
//				newAgent.getIndistinguishableStates().addAll(indRes);
//			}
//			Optional<Agent> optAg = result.getAgents().stream().filter(a -> a.getName().equals(agent.getName())).findFirst();
//			if(optAg.isEmpty()) {
//				result.getAgents().add(newAgent);
//			} else {
//				optAg.get().getActions().addAll(newAgent.getActions().stream().filter(a1 -> optAg.get().getActions().stream().noneMatch(a2 -> a2.equals(a1))).collect(Collectors.toList()));
//				optAg.get().getIndistinguishableStates().addAll(newAgent.getIndistinguishableStates());
//			}
//		}
		result.setGroups(new ArrayList<>(this.groups));
		for(Group group2 : other.getGroups()) {
			Optional<Group> optGroup = result.getGroups().stream().filter(g -> g.getName().equals(group2.getName())).findFirst();
			if(optGroup.isEmpty()) {
				result.getGroups().add(group2);
			} else {
				if(optGroup.get().getAgents().stream().anyMatch(a -> !group2.getAgents().contains(a)) ||
						group2.getAgents().stream().anyMatch(a -> !optGroup.get().getAgents().contains(a))) {
					throw new RuntimeException("The two ATL models' formulas are not compatible (because of the groups)");
				}
			}
		}
//		result.setFormula("(" + this.formula + ") and (" + other.formula + ")");
//		result.setATL(new ATL.And(this.atl, other.atl));
		result.setFormula(this.formula);
		result.setATL(this.atl);
		return result;
	}

	public List<String> next(List<List<AgentAction>> actions) {
		State currentState = this.getStates().stream().filter(State::isInitial).findFirst().get();
		for(List<AgentAction> action : actions) {
			State finalCurrentState = currentState;
			List<Transition> transitions = this.getTransitions().stream().filter(t -> t.getFromState().equals(finalCurrentState.getName()) && t.getAgentActions().stream().anyMatch(a -> a.containsAll(action))).collect(Collectors.toList());
			if(transitions.isEmpty()) {
				transitions = this.getTransitions().stream().filter(t -> t.getFromState().equals(finalCurrentState.getName())).collect(Collectors.toList());
				currentState = this.getState(transitions.get(new Random().nextInt(transitions.size())).getToState());
//				throw new RuntimeException("Actions not available in the current state of the model");
			} else {
				currentState = this.getState(transitions.get(0).getToState());
			}
		}
		return currentState.getLabels();
	}

}
