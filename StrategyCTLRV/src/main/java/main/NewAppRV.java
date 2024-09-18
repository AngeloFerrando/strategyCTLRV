package main;

import com.google.common.base.Stopwatch;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import parser.ATL;
import parser.Automaton;
import utils.*;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class NewAppRV {

    private static void runExperiments(int minStates, int maxStates, int minAgents, int maxAgents, int nRuns) throws Exception {
//        File file = new File("./tmp/results" + minStates + "_" + maxStates + "_" + minAgents + "_" + maxAgents + "_" + percImperfect1 + ".csv");
//        FileWriter fw = new FileWriter(file);
        Random rnd = new Random();
        for (double percImperfect = 0.0; percImperfect <= 1.4; percImperfect += 0.05) {
            System.out.println("Perc: " + percImperfect);
            int nSuccesses = 0;
            //int nStates = rnd.nextInt(maxStates - minStates + 1) + minStates;
            for (int nStates = minStates; nStates <= maxStates; nStates++) {
                System.out.println("N: " + nStates);
                long avgOursTime = 0, avgOursMCTime = 0, avgOursRVTime = 0;
                for (int i = 0; i < nRuns; i++) {
                    List<State> states = new ArrayList<>();
                    int initial = rnd.nextInt(nStates);
                    for (int j = 0; j < nStates; j++) {
                        State s = new State();
                        s.setName("s" + j);
                        if (initial == j) {
                            s.setInitial(true);
                        }
                        states.add(s);
                    }
                    int nAgents = rnd.nextInt(maxAgents - minAgents + 1) + minAgents;
                    List<Agent> agents = new ArrayList<>();
                    for (int j = 0; j < nAgents; j++) {
                        Agent a = new Agent();
                        a.setName("a" + j);
                        int nActions = rnd.nextInt(maxStates - minStates + 1) + minStates;
                        List<String> actions = new ArrayList<>();
                        for (int k = 0; k < nActions; k++) {
                            actions.add("act" + k);
                        }
                        int nImpStates = (int) (percImperfect * nStates / 2);
                        List<List<String>> indStates = new ArrayList<>();
                        for (int k = 0; k < nImpStates; k+=2) {
                            List<String> ind = new ArrayList<>();
                            int si = rnd.nextInt(nStates);
                            int sj;
                            do {
                                sj = rnd.nextInt(nStates);
                            } while (si == sj);
                            ind.add("s" + si);
                            ind.add("s" + sj);
                            indStates.add(ind);
                        }
                        a.setActions(actions);
                        a.setIndistinguishableStates(indStates);
                        agents.add(a);
                    }
                    int nTransitions = rnd.nextInt(nStates * (nStates - 1)) + 1;
                    List<Transition> transitions = new ArrayList<>();
                    for (int j = 0; j < nStates; j++) {
                        int si = j;
                        int sj = rnd.nextInt(nStates);
                        createTransition(agents, transitions, si, sj);
                    }
                    for (int j = 0; j < nTransitions; j++) {
                        int si = rnd.nextInt(nStates);
                        int sj = rnd.nextInt(nStates);
                        Transition transition = new Transition();
                        transition.setFromState("s" + si);
                        transition.setToState("s" + sj);
                        List<List<AgentAction>> agentActs = new ArrayList<>();
                        List<AgentAction> acts = new ArrayList<>();
                        for (Agent agent : agents) {
                            AgentAction act = new AgentAction();
                            act.setAgent(agent.getName());
                            Collections.shuffle(agent.getActions());
                            act.setAction(agent.getActions().stream().findAny().get());
                            acts.add(act);
                        }
                        agentActs.add(acts);
                        transition.setAgentActions(agentActs);
                        if (!transitions.contains(transition)) {
                            transitions.add(transition);
                        }
                    }
                    int groupSize = rnd.nextInt(nAgents) + 1;
                    Group group = new Group();
                    group.setName("g");
                    group.setAgents(agents.stream().limit(groupSize).map(Agent::getName).collect(Collectors.toList()));

                    int si = rnd.nextInt(nStates);
                    ATL formula = rnd.nextBoolean() ? new ATL.Eventually(new ATL.Atom("s" + si)) : rnd.nextBoolean() ? new ATL.Globally(new ATL.Atom("s" + si)) : new ATL.Next(new ATL.Atom("s" + si));
                    formula = new ATL.Existential("g", formula);
                    ATL fAux = formula.clone();
                    int depth = 5;
                    for (int j = 0; j < depth; j++) {
                        int sj = rnd.nextInt(nStates);
                        ATL subFormula = rnd.nextBoolean() ? new ATL.Eventually(new ATL.Atom("s" + si)) : rnd.nextBoolean() ? new ATL.Globally(new ATL.Atom("s" + sj)) : new ATL.Next(new ATL.Atom("s" + si));
                        subFormula = new ATL.Existential("g", subFormula);
                        fAux = new ATL.And(fAux, subFormula);
                    }
                    AtlModel m = new AtlModel();
                    m.setStates(states);
                    m.setAgents(agents);
                    m.setTransitions(transitions);
                    List<Group> groups = new ArrayList<>();
                    groups.add(group);
                    m.setGroups(groups);
                    m.setATL(formula);
                    m.makeTransitionsUnique();

                    Stopwatch stopwatch = Stopwatch.createStarted();
                    List<List<AgentAction>> actions = new ArrayList<>();
                    for (int j = 0; j < rnd.nextInt(10); j++) {
                        List<AgentAction> action = new ArrayList<>();
                        for (Agent agent : m.getAgents()) {
                            action.add(new AgentAction(agent.getName(), agent.getActions().get(rnd.nextInt(agent.getActions().size()))));
                        }
                        actions.add(action);
                    }
                    Object[] aux = isSatisfiedWithPerfectInformation1(m, actions);
                    List<RVItem> res = (List<RVItem>) aux[0];
                    stopwatch.stop();
                    avgOursTime += stopwatch.elapsed().toMillis();
                    avgOursMCTime += (long) aux[1];
                    avgOursRVTime += (long) aux[2];
                    if (res.stream().anyMatch(s -> s.verdict != Automaton.Outcome.Unknown)) {
                        nSuccesses++;
                    }
                }
                System.out.println(((double)avgOursRVTime/nRuns) / ((double)avgOursRVTime/nRuns + (double)avgOursMCTime/nRuns));
//                System.out.println("[" + (avgOursTime/nRuns) + " ms, of which MC " + avgOursMCTime/nRuns + " ms, and RV " + avgOursRVTime/nRuns + " ms]");
            }
            System.out.println("Number of times our procedure returns a final verdict: " + nSuccesses + " (" + (((double) nSuccesses) / (nRuns*(maxStates-minStates+1)) * 100) + "%)");
        }
    }

//    private static void runExperiments1(int minStates, int maxStates, int minAgents, int maxAgents, int nRuns) throws Exception {
//        Random rnd = new Random();
//        for(double percImperfect = 0.0; percImperfect <= 1; percImperfect+=0.1) {
//            int nSuccesses = 0;
//            int nSuccessesSt = 0;
//            long avgMCMASTime = 0;
//            long avgOursTime = 0;
//            for (int i = 0; i < nRuns; i++) {
//                int nStates = rnd.nextInt(maxStates - minStates + 1) + minStates;
//                int impStates = nStates / 2;
//                int perfState = nStates - impStates;
//                List<State> states = new ArrayList<>();
//                int initial = rnd.nextInt(impStates);
//                for (int j = 0; j < nStates; j++) {
//                    State s = new State();
//                    s.setName("s" + j);
//                    if (initial == j) {
//                        s.setInitial(true);
//                    }
//                    states.add(s);
//                }
//                int nAgents = rnd.nextInt(maxAgents - minAgents + 1) + minAgents;
//                List<Agent> agents = new ArrayList<>();
//                for (int j = 0; j < nAgents; j++) {
//                    Agent a = new Agent();
//                    a.setName("a" + j);
//                    int nActions = rnd.nextInt(maxStates - minStates + 1) + minStates;
//                    List<String> actions = new ArrayList<>();
//                    for (int k = 0; k < nActions; k++) {
//                        actions.add("act" + k);
//                    }
//                    int nImpStates = (int) (percImperfect * impStates / 2);
//                    List<List<String>> indStates = new ArrayList<>();
//                    for (int k = 0; k < nImpStates; k++) {
//                        List<String> ind = new ArrayList<>();
//                        int si = rnd.nextInt(impStates);
//                        int sj;
//                        do {
//                            sj = rnd.nextInt(impStates);
//                        } while (si == sj);
//                        ind.add("s" + si);
//                        ind.add("s" + sj);
//                        indStates.add(ind);
//                    }
//                    a.setActions(actions);
//                    a.setIndistinguishableStates(indStates);
//                    agents.add(a);
//                }
//                List<Transition> transitions = new ArrayList<>();
//                for (int j = 0; j < impStates; j++) {
//                    int si = j;
//                    int sj = rnd.nextInt(impStates);
//                    createTransition(agents, transitions, si, sj);
//                }
//                for (int j = impStates; j < nStates; j++) {
//                    int si = j;
//                    int sj = impStates + rnd.nextInt(perfState) - 1;
//                    createTransition(agents, transitions, si, sj);
//                }
//                createTransition(agents, transitions, rnd.nextInt(impStates), impStates + rnd.nextInt(perfState) - 1);
//
//                int groupSize = rnd.nextInt(nAgents) + 1;
//                Group group = new Group();
//                group.setName("g");
//                group.setAgents(agents.stream().limit(groupSize).map(Agent::getName).collect(Collectors.toList()));
//
//                int si = rnd.nextInt(nStates);
//                ATL formula = rnd.nextBoolean() ? new ATL.Eventually(new ATL.Atom("s" + si)) : rnd.nextBoolean() ? new ATL.Globally(new ATL.Atom("s" + si)) : new ATL.Next(new ATL.Atom("s" + si));
//                formula = new ATL.Existential("g", formula);
//                ATL fAux = formula.clone();
//                int depth = 2;
//                for (int j = 0; j < depth; j++) {
//                    int sj = rnd.nextInt(nStates);
//                    ATL subFormula = rnd.nextBoolean() ? new ATL.Eventually(new ATL.Atom("s" + si)) : rnd.nextBoolean() ? new ATL.Globally(new ATL.Atom("s" + sj)) : new ATL.Next(new ATL.Atom("s" + si));
//                    subFormula = new ATL.Existential("g", subFormula);
//                    fAux = new ATL.And(fAux, subFormula);
//                }
//                AtlModel m = new AtlModel();
//                m.setStates(states);
//                m.setAgents(agents);
//                m.setTransitions(transitions);
//                List<Group> groups = new ArrayList<>();
//                groups.add(group);
//                m.setGroups(groups);
//                m.setATL(formula);
//                m.makeTransitionsUnique();
//                AtlModel m1 = m.clone();
//
//                String csvLine = "m" + i + "; ";
//                String mcmasProgram = AbstractionUtils.generateMCMASProgram(m);
//                String fileName = "./tmp/model.ispl";
//                Files.write(Paths.get(fileName), mcmasProgram.getBytes());
//                Stopwatch stopwatch = Stopwatch.createStarted();
//                String s = AbstractionUtils.modelCheck_IR(fileName);
//                stopwatch.stop();
//                avgMCMASTime += stopwatch.elapsed().toMillis();
//                boolean resStandard;
//                if (AbstractionUtils.doesMCMASReturnTrue(s)) {
//                    csvLine += "true; ";
//                    resStandard = true;
//                    nSuccessesSt++;
//                } else {
//                    csvLine += "false; ";
//                    resStandard = false;
//                }
//
//                stopwatch = Stopwatch.createStarted();
//                Automaton.Outcome res = isSatisfiedWithPerfectInformation(m);
//                stopwatch.stop();
//                avgOursTime += stopwatch.elapsed().toMillis();
//                if (res == Automaton.Outcome.Unknown) {
//                    csvLine += "?";
//                } else if (res == Automaton.Outcome.True) {
//                    csvLine += "true";
//                    nSuccesses++;
//                } else {
//                    csvLine += "false";
//                    nSuccesses++;
//                    if (!resStandard) {
//                        nSuccessesSt++;
//                    }
//                }
//                System.out.println("Perc: " + percImperfect);
//                System.out.println("Number of times standard procedure returns a final verdict: " + nSuccessesSt + " (" + (((double) nSuccessesSt) / nRuns * 100) + "%)" + "[" + (avgMCMASTime / nRuns) + " ms]");
//                System.out.println("Number of times our procedure returns a final verdict: " + nSuccesses + " (" + (((double) nSuccesses) / nRuns * 100) + "%)" + "[" + (avgOursTime / nRuns) + " ms]");
//            }
//        }
//    }

    private static void createTransition(List<Agent> agents, List<Transition> transitions, int si, int sj) {
        Transition transition = new Transition();
        transition.setFromState("s" + si);
        transition.setToState("s" + sj);
        List<List<AgentAction>> agentActs = new ArrayList<>();
        List<AgentAction> acts = new ArrayList<>();
        for (Agent agent : agents) {
            AgentAction act = new AgentAction();
            act.setAgent(agent.getName());
            Collections.shuffle(agent.getActions());
            act.setAction(agent.getActions().stream().findAny().get());
            acts.add(act);
        }
        agentActs.add(acts);
        transition.setAgentActions(agentActs);
        transitions.add(transition);
    }

    public static AtlModel generateATLModelForExperiments(AtlModel model, int nReplications) {
        AtlModel result = model.clone();
        for(int i = 1; i < nReplications; i++) {
            AtlModel aux = model.clone();
            int finalI = i;
            aux.getAgents().forEach(a -> a.setName(a.getName() + finalI));
//            // hmmmmmm
            aux.getAgents().forEach(a -> a.setIndistinguishableStates(new ArrayList<>()));
//            //
            aux.getGroups().forEach(g -> g.setAgents(g.getAgents().stream().map(a -> a + finalI).collect(Collectors.toList())));
            for(String g : aux.getGroups().stream().map(Group::getName).collect(Collectors.toSet())) {
                aux.setFormula(aux.getFormula().replace(g, g + finalI));
                aux.getATL().renameGroup(g, g + finalI);
            }
            List<String> terms = aux.getATL().getTerms();
            for(String term : terms) {
                aux.setATL(aux.getATL().updateFormula(new ATL.Atom(term), new ATL.Atom(term + finalI)));
                aux.setFormula(aux.getFormula().replace(term, term + finalI));
            }
            aux.getGroups().forEach(g -> g.setName(g.getName() + finalI));
            for(State state : aux.getStates()) {
                state.setLabels(state.getLabels().stream().map(l -> l + finalI).collect(Collectors.toList()));
                state.setFalseLabels(state.getFalseLabels().stream().map(l -> l + finalI).collect(Collectors.toList()));
            }
            for(Transition transition : aux.getTransitions()) {
                for(List<AgentAction> actionList : transition.getAgentActions()) {
                    for(AgentAction agentAction : actionList) {
                        agentAction.setAgent(agentAction.getAgent() + finalI);
                    }
                }
                for(MultipleAgentAction multipleAgentAction : transition.getMultipleAgentActions()) {
                    multipleAgentAction.setAgent(multipleAgentAction.getAgent() + finalI);
                }
            }
            aux.setAgentMap(null);
            aux.setStateMap(null);
            result = result.parallel(aux);
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();

//        Option impRecall = new Option("r", "imperfect-recall", false, "find sub-models with imperfect recall");
//        impRecall.setRequired(false);
//        options.addOption(impRecall);
//        Option perfectInfo = new Option("I", "perfect-information", false, "find sub-models with perfect information");
//        perfectInfo.setRequired(false);
//        options.addOption(perfectInfo);
        Option modelOpt = new Option("m", "model", true, "the ATL model");
        modelOpt.setRequired(true);
        options.addOption(modelOpt);
        Option outputFolder = new Option("o", "output", true, "folder where sub-models will be saved");
        outputFolder.setRequired(false);
        options.addOption(outputFolder);
        Option verbose = new Option("s", "silent", false, "disable prints");
        verbose.setRequired(false);
        options.addOption(verbose);
        Option mcmas = new Option("mcmas", "mcmas", true, "installation folder of mcmas");
        mcmas.setRequired(true);
        options.addOption(mcmas);
        Option lamaConv = new Option("lamaconv", "lamaconv", true, "installation folder of lamaconv");
        mcmas.setRequired(true);
        options.addOption(lamaConv);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            File file = new File("./tmp");
            if(!file.exists() && !file.mkdir()) {
                throw new FileSystemException("./tmp folder could not be created");
            }
            cmd = parser.parse(options, args);
            AbstractionUtils.mcmas = cmd.getOptionValue("mcmas");
            Monitor.rv = cmd.getOptionValue("lamaconv");
            System.out.println("Parsing the model..");
            // read json file
            String jsonModel = Files.readString(Paths.get(cmd.getOptionValue("model")), StandardCharsets.UTF_8);
            // load json file to ATL Model Java representation
            AtlModel atlModel = JsonObject.load(jsonModel, AtlModel.class);
//            atlModel.makeTransitionsUnique();
            // validate the model
            AbstractionUtils.validateAtlModel(atlModel);
            // add default transitions to the model
            AbstractionUtils.processDefaultTransitions(atlModel);
            System.out.println("Model successfully parsed");

//            Stopwatch stopwatch = Stopwatch.createStarted();
////             "<g2>F((rm and oc) and <g2>F((pl or pr) and <g2>F(rm and oc)))" False
////             "<g1>F((rm and oc) and <g2>F((pl or pr) and <g2>F(rm and oc)))" True
////             "<g1>F((rp and not(ip)) and <g2>F((pl or pr) and <g2>F(rm and oc)))" False
//            List<List<AgentAction>> actions = new ArrayList<>();
//            actions.add(List.of(new AgentAction("Rover", "chk"), new AgentAction("Mechanic", "ca")));
//            actions.add(List.of(new AgentAction("Rover", "i"), new AgentAction("Mechanic", "ok")));
//            actions.add(List.of(new AgentAction("Rover", "L"), new AgentAction("Mechanic", "i")));
//            actions.add(List.of(new AgentAction("Rover", "mp"), new AgentAction("Mechanic", "i")));
//            List<RVItem> rvItems = isSatisfiedWithPerfectInformation(atlModel, actions);
//            stopwatch.stop();
//            rvItems.forEach(i -> System.out.println("(" + i.verdict + ", " + i.phiMC + ", " + i.phiRV + ", " + i.phiUnchk + ")"));
//            System.out.println("Time: " + stopwatch.elapsed().toMillis() + " [ms]");

            runExperiments(10, 10, 2, 5, 5);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Strategy CTL", options);
            System.exit(1);
        }
    }

    static Object[] isSatisfiedWithPerfectInformation1(AtlModel atlModel, List<List<AgentAction>> actions) throws IOException {
        List<RVItem> rvItems = new ArrayList<>();
        List<AtlModel> candidates = new LinkedList<>();
        candidates.add(atlModel);
        long mcTime = 0, rvTime = 0;
        while(!candidates.isEmpty()) {
//            System.out.println(candidates.size());
            AtlModel candidate = candidates.remove(0);
            boolean valid = true;
            for(Agent agent : candidate.getAgents()){
                if(!agent.getIndistinguishableStates().isEmpty()) {
                    for(List<String> indistinguishableStates : agent.getIndistinguishableStates()) {
                        for (String ind : indistinguishableStates) {
                            AtlModel aux = candidate.clone();
                            State s = new State();
                            s.setName(ind);
                            aux.removeState(s);
                            candidates.add(0, aux);
                        }
                    }
                    valid = false;
                    break;
                }
            }
            if(valid) {
                Stopwatch stopwatch = Stopwatch.createStarted();
                List<Item> result = checkSubFormulas(candidate, atlModel.getATL());
                stopwatch.stop();
                mcTime += stopwatch.elapsed().toMillis();
                stopwatch = Stopwatch.createStarted();
                RVItem k = verification(atlModel, result, actions);
                stopwatch.stop();
                rvTime += stopwatch.elapsed().toMillis();
                rvItems.add(k);
                if(k.verdict != Automaton.Outcome.Unknown) break;
            }
        }
        Object[] aux = new Object[3];
        aux[0] = rvItems;
        aux[1] = mcTime;
        aux[2] = rvTime;
        return aux;
    }
//    private static Automaton.Outcome isSatisfiedWithPerfectInformation(AtlModel atlModel) throws IOException {
//        for(List<State> states : findSubModels(atlModel)) {
//            AtlModel c = atlModel.clone();
//            c.setStates(states);
//            c.setStateMap(null);
//            List<Item> result = checkSubFormulas(c, atlModel.getATL());
//            Automaton.Outcome k = verification(atlModel, result);
//            if(k != Automaton.Outcome.Unknown) {
//                return k;
//            }
//        }
//        return Automaton.Outcome.Unknown;
//    }

    private static RVItem verification(AtlModel atlModel, List<Item> result, List<List<AgentAction>> actions) throws IOException {
        Automaton.Outcome k = Automaton.Outcome.Unknown;
        for(State s : atlModel.getStates()) {
            List<Item> aux = result.stream().filter(r -> r.s.equals(s)).collect(Collectors.toList());
            s.getLabels().addAll(aux.stream().map(r -> r.atom).collect(Collectors.toList()));
        }

        Set<ATL> phiMC = result.stream().map(i -> i.phi).collect(Collectors.toSet());
        for(int i = result.size()-1; i >= 0; i--) {
            int finalI = i;
            phiMC = phiMC.stream().map(j -> j.updateFormula(new ATL.Atom(result.get(finalI).atom), result.get(finalI).phi)).collect(Collectors.toSet());
        }
        Set<ATL> finalPhiMC = phiMC;
        Set<ATL> phiRV = atlModel.getATL().subFormulas().stream().filter(phi -> !finalPhiMC.contains(phi)).collect(Collectors.toSet());

        ATL psiN = atlModel.getATL().clone();
        ATL psiP = atlModel.getATL().clone();

        for(Item item : result) {
            if(item.positive) {
                psiP = psiP.updateFormula(item.phi, new ATL.Atom(item.atom));
            } else {
                psiN = psiN.updateFormula(item.phi, new ATL.Atom(item.atom));
            }
        }

        ATL phiN = psiN.convertToLTL();
        ATL phiP = psiP.convertToLTL();
//        Stopwatch stopwatch = Stopwatch.createStarted();
        Monitor monPhiN = new Monitor(phiN.toString());
        Monitor monPhiP = new Monitor(phiP.toString());
//        stopwatch.stop();
//        long l = stopwatch.elapsed().toMillis();
//        System.out.println(l);
        List<Set<String>> h = new ArrayList<>();
        for(int i = 1; i <= actions.size(); i++) {
            h.add(new HashSet<>(atlModel.next(actions.subList(0, i))));
        }

        if(monPhiN.next(h) == Monitor.Verdict.True) {
            k = Automaton.Outcome.True;
        }
        if(monPhiP.next(h) == Monitor.Verdict.False) {
            k = Automaton.Outcome.False;
        }

        Set<ATL> phiUnchk = new HashSet<>();
        for(ATL phi : phiRV) {
            Monitor monPhi = new Monitor(phi.convertToLTL().toString());
            if(monPhi.next(h) == Monitor.Verdict.Unknown) {
                phiRV.remove(phi);
                phiUnchk.add(phi);
            }
        }

        return new RVItem(k, phiMC, phiRV, phiUnchk);
    }

    static class RVItem {
        private Automaton.Outcome verdict;
        Set<ATL> phiMC;
        Set<ATL> phiRV;
        Set<ATL> phiUnchk;

        public RVItem(Automaton.Outcome verdict, Set<ATL> phiMC, Set<ATL> phiRV, Set<ATL> phiUnchk) {
            this.verdict = verdict;
            this.phiMC = phiMC;
            this.phiRV = phiRV;
            this.phiUnchk = phiUnchk;
        }

        public Automaton.Outcome getVerdict() {
            return verdict;
        }

        public Set<ATL> getPhiMC() {
            return phiMC;
        }

        public Set<ATL> getPhiRV() {
            return phiRV;
        }

        public Set<ATL> getPhiUnchk() {
            return phiUnchk;
        }
    }

    private static class Item {
        private State s;
        private ATL phi;
        private String atom;
        private boolean positive;

        public Item(State s, ATL phi, String atom, boolean positive) {
            this.s = s;
            this.phi = phi;
            this.atom = atom;
            this.positive = positive;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return positive == item.positive && s.equals(item.s) && phi.equals(item.phi) && atom.equals(item.atom);
        }

        @Override
        public int hashCode() {
            return Objects.hash(s, phi, atom, positive);
        }
    }

    private static List<Item> checkSubFormulas(AtlModel candidate, ATL phi) throws IOException {
        List<Item> result = new ArrayList<>();
        AtlModel candidateP = candidate.clone();
        Set<String> labels =  candidateP.getStates().stream().map(State::getLabels).flatMap(List::stream).collect(Collectors.toSet());
        labels.addAll(candidateP.getStates().stream().map(State::getName).collect(Collectors.toSet()));
        if(candidateP.hasState("sink")) {
            candidateP.getState("sink").setLabels(new ArrayList<>(labels));
        }
        AtlModel candidateN = candidate.clone();
        int atom = 0;
        List<Pair<ATL, String>> prevPsiN = new ArrayList<>();
        List<Pair<ATL, String>> prevPsiP = new ArrayList<>();
        for(ATL psi : phi.subFormulas()) {
//            System.out.println("Check subformula: " + psi);
            ATL psiN = psi.clone();
            ATL psiP = psi.clone();
            for(int i = prevPsiN.size()-1; i >= 0; i--) {
                Pair<ATL, String> prev = prevPsiN.get(i);
                psiN = psiN.updateFormula(prev.getLeft(), new ATL.Atom(prev.getRight()));
            }
            for(int i = prevPsiP.size()-1; i >= 0; i--) {
                Pair<ATL, String> prev = prevPsiP.get(i);
                psiP = psiP.updateFormula(prev.getLeft(), new ATL.Atom(prev.getRight()));
            }
            prevPsiN.add(new ImmutablePair<>(psi, "atom" + atom + "n"));
            prevPsiP.add(new ImmutablePair<>(psi, "atom" + atom + "p"));
            for(State state : candidate.getStates()) {
//                System.out.println("Check state in candidate");
                if (state.getName().equals("sink")) continue;
                candidateN.getStates().forEach(s -> s.setInitial(s.getName().equals(state.getName())));
                candidateN.setATL(psiN);
                String mcmasProgram = AbstractionUtils.generateMCMASProgram(candidateN);
                String fileName = "./tmp/subModel.ispl";
                Files.write(Paths.get(fileName), mcmasProgram.getBytes());
                String mcmasRes = AbstractionUtils.modelCheck_IR(fileName);
                if (AbstractionUtils.doesMCMASReturnTrue(mcmasRes)) {
                    candidateN.updateModel("atom" + atom + "n");
                    result.add(new Item(state, psiN, "atom" + atom + "n", false));
//                    System.out.println("Update result");
                }
                candidateP.getStates().forEach(s -> s.setInitial(s.getName().equals(state.getName())));
                candidateP.setATL(psiP);
                mcmasProgram = AbstractionUtils.generateMCMASProgram(candidateP);
                Files.write(Paths.get(fileName), mcmasProgram.getBytes());
                mcmasRes = AbstractionUtils.modelCheck_IR(fileName);
                if (AbstractionUtils.doesMCMASReturnTrue(mcmasRes)) {
                    candidateP.updateModel("atom" + atom + "p");
                    result.add(new Item(state, psiP, "atom" + atom + "p", true));
//                    System.out.println("Update result");
                }
//                else {
//                    result.add(new Item(state, psiP, "atom" + atom + "p", true));
//                }
            }
            if(candidateP.getState("sink") != null) {
                candidateP.getState("sink").getLabels().add("atom" + atom + "p");
            }
            atom++;
        }
        return result;
    }

//    public static List<List<State>> findSubModels(AtlModel model) {
//        List<List<State>> candidates = new LinkedList<>();
//        List<State> aux = new ArrayList<>(model.getStates());
//        candidates.add(aux);
//        List<List<State>> candidatesPP = new LinkedList<>();
//        while(!candidates.isEmpty()) {
//            System.out.println(candidates.size());
//            List<State> candidate = candidates.remove(0);
//            boolean valid = true;
//            for(Agent agent : model.getAgents()){
//                for(List<String> ind : agent.getIndistinguishableStates()) {
//                    List<String> toRemove = new ArrayList<>();
//                    for(State state : candidate) {
//                        if(ind.contains(state.getName())) {
//                            toRemove.add(state.getName());
//                        }
//                    }
//                    if(toRemove.size() >= 2) {
//                        List<State> newCandidate1 = new ArrayList<>(candidate);
//                        newCandidate1.removeIf(s -> s.getName().equals(toRemove.get(0)));
//                        List<State> newCandidate2 = new ArrayList<>(candidate);
//                        newCandidate2.removeIf(s -> s.getName().equals(toRemove.get(1)));
//                        candidates.add(0, newCandidate1);
//                        candidates.add(0, newCandidate2);
//                        valid = false;
//                    }
//                }
//            }
//            if(valid) {
//                candidatesPP.add(candidate);
//            }
//        }
//        return candidatesPP;
//    }

    public static List<AtlModel> findSubModels(AtlModel model) {
        List<AtlModel> candidates = new LinkedList<>();
        candidates.add(model);
        List<AtlModel> candidatesPP = new LinkedList<>();
        while(!candidates.isEmpty()) {
            System.out.println(candidates.size());
            AtlModel candidate = candidates.remove(0);
            boolean valid = true;
            for(Agent agent : candidate.getAgents()){
                if(!agent.getIndistinguishableStates().isEmpty()) {
                    for(List<String> indistinguishableStates : agent.getIndistinguishableStates()) {
                        for (String ind : indistinguishableStates) {
                            AtlModel aux = candidate.clone();
                            State s = new State();
                            s.setName(ind);
                            aux.removeState(s);
                            candidates.add(aux);
                        }
                    }
                    valid = false;
                    break;
                }
            }
            if(valid) {
                if(candidatesPP.stream().noneMatch((m) -> new HashSet<>(m.getStates()).equals(new HashSet<>(candidate.getStates())))) {
                    candidatesPP.add(candidate);
                }
            }
        }
        return candidatesPP;
    }


    public static Object[] isSatisfiedWithPerfectInformation(AtlModel atlModel, List<List<AgentAction>> actions) throws IOException {
        int[][] view = new int[atlModel.getAgents().size()][];
        BigInteger n = BigInteger.ONE;
        long mcTime = 0, rvTime = 0;
        for(int i = 0; i < atlModel.getAgents().size(); i++) {
            Agent agent = atlModel.getAgents().get(i);
            view[i] = new int[agent.getIndistinguishableStates().size()];
            for(int j = 0; j < agent.getIndistinguishableStates().size(); j++) {
                view[i][j] = 0;
                n = n.multiply(BigInteger.valueOf(agent.getIndistinguishableStates().get(j).size()));
//                n *= view[i][j];
//                System.out.println(n);
            }
        }
//        System.out.println(n);
        List<RVItem> rvItems = new ArrayList<>();
        for (BigInteger k = BigInteger.valueOf(0); k.compareTo(n) <= 0; k = k.add(BigInteger.ONE)) {
            Set<String> statesToRemove = new HashSet<>();
            for(int i = 0; i < view.length; i++) {
                Agent agent = atlModel.getAgents().get(i);
                for(int j = 0; j < view[i].length; j++) {
                    List<String> inds = agent.getIndistinguishableStates().get(j);
                    for(int w = 0; w < inds.size(); w++) {
                        if(w != view[i][j]) {
                            statesToRemove.add(inds.get(w));
                        }
                    }
                }
            }
            for(int i = 0; i < view.length; i++) {
                Agent agent = atlModel.getAgents().get(i);
                for(int j = 0; j < view[i].length; j++) {
                    view[i][j]++;
                    if(view[i][j] == agent.getIndistinguishableStates().get(j).size()) {
                        view[i][j] = 0;
                    } else {
                        break;
                    }
                }
            }
            boolean skip = false;
            for(Agent agent : atlModel.getAgents()) {
                for(List<String> inds : agent.getIndistinguishableStates()) {
                    if(statesToRemove.containsAll(inds)) {
                        skip = true;
                        break;
                    }
                }
                if(skip) {
                    break;
                }
            }
            if(skip) {
                continue;
            }
            AtlModel aux = atlModel.clone();
            for(String toRemove : statesToRemove) {
                State state = new State();
                state.setName(toRemove);
                aux.removeState(state);
            }
            Stopwatch stopwatch = Stopwatch.createStarted();
            List<Item> result = checkSubFormulas(aux, aux.getATL());
            stopwatch.stop();
            mcTime += stopwatch.elapsed().toMillis();
            stopwatch = Stopwatch.createStarted();
            RVItem rvItem = verification(atlModel, result, actions);
            stopwatch.stop();
            rvTime += stopwatch.elapsed().toMillis();
            rvItems.add(rvItem);
//            System.out.println("Outcome: " + verdict);
        }
        Object[] aux = new Object[3];
        aux[0] = rvItems;
        aux[1] = mcTime;
        aux[2] = rvTime;
        return aux;
    }

}

