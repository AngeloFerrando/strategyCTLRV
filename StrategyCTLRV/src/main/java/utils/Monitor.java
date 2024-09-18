package utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Monitor {
    private Set<String> alphabet;
    private HashMap<String, State> states = new HashMap<>();
    private State currentState;
    private Verdict currentVerdict = Verdict.Unknown;
    private String ltl;
    public static String rv;

    private static class State {
        private String name;
        private HashMap<Set<String>, State> transitions = new HashMap<>();
        private Verdict output;
        private State(String name, Verdict output) {
            this.name = name;
            this.output = output;
        }
    }
    public enum Verdict { True, False, Unknown};

    public String getLtl() {
        return ltl;
    }
    public Verdict getCurrentVerdict() {
        return this.currentVerdict;
    }

    public Monitor(String ltl) throws IOException {
        this.ltl = "LTL=" + ltl.replace("and", "AND").replace("or", "OR").trim();
//        StringBuilder ltlAlphabetCommand = new StringBuilder();
//        ltlAlphabetCommand.append(",ALPHABET=[");
//        for(int i = 0; i < ltlAlphabet.length; i++) {
//            ltlAlphabetCommand.append(ltlAlphabet[i].toLowerCase());
//            if(i < ltlAlphabet.length-1) {
//                ltlAlphabetCommand.append(",");
//            }
//        }
//        ltlAlphabetCommand.append("]");
        String command = "java -jar " + rv + "rltlconv.jar \"" + this.ltl + "\" --formula --props --nbas --min --nfas --dfas --min --moore";
        FileWriter sh = new FileWriter("run.sh");
        sh.write(command);
        sh.close();

        try(Scanner scanner = new Scanner(Runtime.getRuntime().exec("sh run.sh").getInputStream()).useDelimiter("\n")) {
            while(scanner.hasNext()) {
                String mooreString = scanner.next();
                if(mooreString.contains("ALPHABET")) {
                    String[] alphabet = mooreString.split("=")[1].trim().replace("[", "").replace("]", "").split(",");
                    this.alphabet = new HashSet<>();
                    for (String s : alphabet) {
                        String aux = s.trim().replace("\"", "").replace("(", "").replace(")", "");
                        Collections.addAll(this.alphabet, aux.split("&&"));
                    }
                } else if(mooreString.contains("STATES")) {
                    for(String state : mooreString.split("=")[1].split(",")) {
                        state = state.trim().replace("[", "").replace("]", "");
                        String name = state.split(":")[0];
                        String verdictStr = state.split(":")[1];
                        Verdict output = verdictStr.equals("true") ? Verdict.True : (verdictStr.equals("false") ? Verdict.False : Verdict.Unknown);
                        states.put(name, new State(name, output));
                    }
                } else if(mooreString.contains("START")) {
                    currentState = states.get(mooreString.split("=")[1].trim());
                } else if(mooreString.contains("DELTA")) {
                    String[] args = mooreString.substring(mooreString.indexOf("(")+1, mooreString.indexOf(")")).split(",");
                    Set<String> aux = new HashSet<>();
                    Collections.addAll(aux, args[1].trim().replace("\"", "").replace("(", "").replace(")", "").split("&&"));
                    aux.remove(" ");
                    states.get(args[0].trim()).transitions.put(aux, states.get(mooreString.split("=")[1].trim()));
                }
            }
        }
    }

    public Verdict next(Iterable<Set<String>> trace) {
        Verdict result = Verdict.Unknown;
        for(Set<String> event : trace) {
            result = next(event);
            if(result != Verdict.Unknown) {
                return result;
            }
        }
        return result;
    }

    public Verdict next(Set<String> event) {
        Set<String> finalEvent = event.stream().filter(e -> this.alphabet.contains(e)).map(String::toLowerCase).collect(Collectors.toSet());
        if(finalEvent.isEmpty()) {
            finalEvent.add("");
        }
        Set<String> all = new HashSet<>();
        all.add("?");
        if(currentState.transitions.containsKey(finalEvent)) {
            currentState = currentState.transitions.get(finalEvent);
            currentVerdict = currentState.output;
            return currentVerdict;
        } else if(currentState.transitions.containsKey(all)) {
            currentState = currentState.transitions.get(all);
            currentVerdict = currentState.output;
            return currentVerdict;
        }
        return currentVerdict;
        //throw new IllegalArgumentException("event does not belong to the alphabet");
    }

}