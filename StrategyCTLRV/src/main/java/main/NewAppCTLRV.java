package main;

import com.google.common.base.Stopwatch;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import parser.ATL;
import parser.Automaton;
import utils.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class NewAppCTLRV {
//    private static void runExperiments(int minStates, int maxStates, int minAgents, int maxAgents, int nRuns) throws Exception {
////        File file = new File("./tmp/results" + minStates + "_" + maxStates + "_" + minAgents + "_" + maxAgents + "_" + percImperfect1 + ".csv");
////        FileWriter fw = new FileWriter(file);
//        Random rnd = new Random();
//        for(double percImperfect = 0.0; percImperfect <= 1.0; percImperfect+=0.1) {
//            int nSuccesses = 0;
//            int nSuccessesRV = 0;
//            int nSuccessesSt = 0;
//            long avgMCMASTime = 0;
//            long avgOursTime = 0;
//            for (int i = 0; i < nRuns; i++) {
//                //System.out.println("Run n. " + i + " of " + nRuns);
//                int nStates = rnd.nextInt(maxStates - minStates + 1) + minStates;
//                List<State> states = new ArrayList<>();
//                int initial = rnd.nextInt(nStates);
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
//                    int nImpStates = (int) (percImperfect * nStates / 2);
//                    List<List<String>> indStates = new ArrayList<>();
//                    for (int k = 0; k < nImpStates; k++) {
//                        List<String> ind = new ArrayList<>();
//                        int si = rnd.nextInt(nStates);
//                        int sj;
//                        do {
//                            sj = rnd.nextInt(nStates);
//                        } while (si == sj);
//                        ind.add("s" + si);
//                        ind.add("s" + sj);
//                        indStates.add(ind);
//                    }
//                    a.setActions(actions);
//                    a.setIndistinguishableStates(indStates);
//                    agents.add(a);
//                }
//                int nTransitions = rnd.nextInt(nStates * (nStates - 1)) + 1;
//                List<Transition> transitions = new ArrayList<>();
//                for (int j = 0; j < nStates; j++) {
//                    int si = j;
//                    int sj = rnd.nextInt(nStates);
//                    createTransition(agents, transitions, si, sj);
//                }
//                for (int j = 0; j < nTransitions; j++) {
//                    int si = rnd.nextInt(nStates);
//                    int sj = rnd.nextInt(nStates);
//                    Transition transition = new Transition();
//                    transition.setFromState("s" + si);
//                    transition.setToState("s" + sj);
//                    List<List<AgentAction>> agentActs = new ArrayList<>();
//                    List<AgentAction> acts = new ArrayList<>();
//                    for (Agent agent : agents) {
//                        AgentAction act = new AgentAction();
//                        act.setAgent(agent.getName());
//                        Collections.shuffle(agent.getActions());
//                        act.setAction(agent.getActions().stream().findAny().get());
//                        acts.add(act);
//                    }
//                    agentActs.add(acts);
//                    transition.setAgentActions(agentActs);
//                    if (!transitions.contains(transition)) {
//                        transitions.add(transition);
//                    }
//                }
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
//                if(!AbstractionUtils.doesMCMASReturnFalse(s) && !AbstractionUtils.doesMCMASReturnTrue(s)) {
//                    String pippo = "pippo";
//                }
//
//                if (AbstractionUtils.doesMCMASReturnTrue(s)) {
//                    csvLine += "true; ";
//                    resStandard = true;
//                    nSuccessesSt++;
//                } else {
//                    csvLine += "false; ";
//                    resStandard = false;
//                }
//
//                List<List<AgentAction>> actions = new ArrayList<>();
//                for (int j = 0; j < rnd.nextInt(10); j++) {
//                    List<AgentAction> action = new ArrayList<>();
//                    for (Agent agent : m.getAgents()) {
//                        action.add(new AgentAction(agent.getName(), agent.getActions().get(rnd.nextInt(agent.getActions().size()))));
//                    }
//                    actions.add(action);
//                }
//
//                stopwatch = Stopwatch.createStarted();
//                Pair<Automaton.Outcome, List<NewAppRV.RVItem>> res = modelCheckingProcedure(m, actions);
//                stopwatch.stop();
//                avgOursTime += stopwatch.elapsed().toMillis();
//                if (res.getLeft() == Automaton.Outcome.True && res.getRight() == null) {
//                    nSuccesses++;
//                } else if(res.getLeft() == Automaton.Outcome.False && res.getRight() == null) {
//                    nSuccesses++;
//                    if (!resStandard) {
//                        nSuccessesSt++;
//                    }
//                } else if(res.getLeft() != Automaton.Outcome.Unknown){
//                    nSuccessesRV++;
//                }
//
////                if (resStandard) {
////                    String pippo = "";
////                }
////
//                if (resStandard && res.getLeft() == Automaton.Outcome.Unknown) {
//                    String pippo = "";
//                }
////                fw.write(csvLine + "\n");
////                fw.flush();
//            }
//            //}
////            fw.flush();
////            fw.close();
//            System.out.println("Perc: " + percImperfect);
//            System.out.println("Number of times standard procedure returns a final verdict: " + nSuccessesSt + " (" + (((double) nSuccessesSt) / nRuns * 100) + "%)" + "[" + (avgMCMASTime/nRuns) + " ms]");
//            System.out.println("Number of times our procedure returns a final verdict: " + nSuccesses + " (" + (((double) nSuccesses) / nRuns * 100) + "%)" + "[" + (avgOursTime/nRuns) + " ms]");
//            System.out.println("Number of times our procedure returns a final verdict at Runtime: " + nSuccessesRV + " (" + (((double) nSuccessesRV) / nRuns * 100) + "%)" + "[" + (avgOursTime/nRuns) + " ms]");
//            //System.out.println("Number of times our procedure is right (and standard one is wrong): " + nRights + " (" + (((double) nRights) / nRuns * 100) + "%)");
//        }
//    }

    private static void runExperiments(int minStates, int maxStates, int minAgents, int maxAgents, int nRuns) throws Exception {
        File file = new File("./tmp/results" + minStates + "_" + maxStates + "_" + minAgents + "_" + maxAgents + ".csv");
        FileWriter fw = new FileWriter(file);
        Random rnd = new Random();

        int nSuccesses = 0;
        int nSuccessesRV = 0;
        int nSuccessesSt = 0;
        long avgMCMASTime = 0;
        long avgOursTime = 0;
        for (int nStates = minStates; nStates <= maxStates; nStates++){
            for (int nAgents = minAgents; nAgents <= maxAgents; nAgents++) {
                for (int i = 0; i < nRuns; i++) {
                    double percImperfect = rnd.nextDouble() * 0.3;
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
                        for (int k = 0; k < nImpStates; k++) {
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
                    int depth = 2;
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
                    AtlModel m1 = m.clone();

                    String mcmasProgram = AbstractionUtils.generateMCMASProgram(m);
                    String fileName = "./tmp/model.ispl";
                    Files.write(Paths.get(fileName), mcmasProgram.getBytes());
                    Stopwatch stopwatch = Stopwatch.createStarted();
                    String s = AbstractionUtils.modelCheck_IR(fileName);
                    stopwatch.stop();
                    avgMCMASTime += stopwatch.elapsed().toMillis();
                    boolean resStandard;

                    if (AbstractionUtils.doesMCMASReturnTrue(s)) {
                        resStandard = true;
                        nSuccessesSt++;
                    } else {
                        resStandard = false;
                    }

                    List<List<AgentAction>> actions = new ArrayList<>();
                    for (int j = 0; j < rnd.nextInt(10); j++) {
                        List<AgentAction> action = new ArrayList<>();
                        for (Agent agent : m.getAgents()) {
                            action.add(new AgentAction(agent.getName(), agent.getActions().get(rnd.nextInt(agent.getActions().size()))));
                        }
                        actions.add(action);
                    }

                    stopwatch = Stopwatch.createStarted();
                    Pair<Automaton.Outcome, List<NewAppRV.RVItem>> res = modelCheckingProcedure(m, actions);
                    stopwatch.stop();
                    avgOursTime += stopwatch.elapsed().toMillis();
                    if (res.getLeft() == Automaton.Outcome.True && res.getRight() == null) {
                        nSuccesses++;
                    } else if (res.getLeft() == Automaton.Outcome.False && res.getRight() == null) {
                        nSuccesses++;
                        if (!resStandard) {
                            nSuccessesSt++;
                        }
                    } else if (res.getLeft() != Automaton.Outcome.Unknown) {
                        nSuccessesRV++;
                    }
                }
                fw.write(nStates + ";" + nAgents + ";" + (avgMCMASTime/nRuns) + ";" + (avgOursTime/nRuns) + "\n");
                fw.flush();
            }
        }
        fw.close();
    }

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

    public static void main(String[] args) throws Exception {
        Options options = new Options();
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
//            System.out.println("Parsing the model..");
//            // read json file
//            String jsonModel = Files.readString(Paths.get(cmd.getOptionValue("model")), StandardCharsets.UTF_8);
//            // load json file to ATL Model Java representation
//            AtlModel atlModel = JsonObject.load(jsonModel, AtlModel.class);
//            // validate the model
//            AbstractionUtils.validateAtlModel(atlModel);
//            // add default transitions to the model
//            AbstractionUtils.processDefaultTransitions(atlModel);
//            System.out.println("Model successfully parsed");
//            Stopwatch stopwatch = Stopwatch.createStarted();
//            System.out.println("Result: " + modelCheckingProcedure(atlModel, new ArrayList<>()));
//            stopwatch.stop();
//            System.out.println("### " + stopwatch.elapsed().toMillis() + "[ms]");

            runExperiments(5, 20, 8, 8, 5);
        } catch (ParseException | FileSystemException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Strategy CTLRV", options);
            System.exit(1);
        }
    }

    private static ATL checkSubFormulasWithImperfectInformation(AtlModel candidate, ATL phi) throws IOException {
        Set<String> labels =  candidate.getStates().stream().map(State::getLabels).flatMap(List::stream).collect(Collectors.toSet());
        labels.addAll(candidate.getStates().stream().map(State::getName).collect(Collectors.toSet()));

        State init = candidate.getStates().stream().filter(State::isInitial).findFirst().get();

        int atom = 0;
        List<Pair<ATL, String>> prevPsi = new ArrayList<>();
        for(ATL psi : phi.subFormulas()) {
            ATL psiCopy = psi.clone();
            for (int i = prevPsi.size() - 1; i >= 0; i--) {
                Pair<ATL, String> prev = prevPsi.get(i);
                psiCopy = psiCopy.updateFormula(prev.getLeft(), new ATL.Atom(prev.getRight()));
                phi = phi.updateFormula(prev.getLeft(), new ATL.Atom(prev.getRight()));
            }
            prevPsi.add(new ImmutablePair<>(psi, "atomir" + atom));
            boolean found = false;
            for (State state : candidate.getStates()) {
                candidate.getStates().forEach(s -> s.setInitial(s.getName().equals(state.getName())));
                candidate.setATL(psiCopy);
                String mcmasProgram = AbstractionUtils.generateMCMASProgram(candidate);
                String fileName = "./tmp/subModel.ispl";
                Files.write(Paths.get(fileName), mcmasProgram.getBytes());
                String mcmasRes = AbstractionUtils.modelCheck_IR(fileName);
                if (AbstractionUtils.doesMCMASReturnTrue(mcmasRes)) {
                    candidate.updateModel("atomir" + atom);
                    found = true;
                }
            }
            if(!found) break;
            atom++;
        }

        candidate.getStates().forEach(s -> s.setInitial(s.getName().equals(init.getName())));
        return phi;
    }

    public static Pair<Automaton.Outcome,List<NewAppRV.RVItem>> modelCheckingProcedure(AtlModel model, List<List<AgentAction>> actions) throws IOException {
        String mcmasProgram = AbstractionUtils.generateMCMASProgram(model);
        String fileName = "./tmp/subModel.ispl";
        Files.write(Paths.get(fileName), mcmasProgram.getBytes());
        // We try Model Checking with imperfect info and imperfect recall
//        System.out.println("Try Model Checking with imperfect information and imperfect recall");
//        String mcmasRes = AbstractionUtils.modelCheck_IR(fileName);
//        if (AbstractionUtils.doesMCMASReturnTrue(mcmasRes)) {
//            return new ImmutablePair<>(Automaton.Outcome.True, null);
//        }
        // Preprocessing of sub-models with imperfect information and imperfect recall
//        System.out.println("Check sub-formulas with imperfect information and imperfect recall");
//        model.setATL(checkSubFormulasWithImperfectInformation(model, model.getATL()));
        // CTL
        Automaton.Outcome result = NewAppCTL.isSatisfiedWithPerfectInformation(model);
        if(result != Automaton.Outcome.Unknown) {
            return new ImmutablePair<>(result, null);
        }
        // RV
        Object[] aux = NewAppRV.isSatisfiedWithPerfectInformation1(model, actions);
        List<NewAppRV.RVItem> res = (List<NewAppRV.RVItem>) aux[0];
        return new ImmutablePair<>(Automaton.Outcome.Unknown, res);
    }
}
