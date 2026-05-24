package it.univr.lang;

// Imports
import it.univr.lang.type.*;
import it.univr.lang.value.*;
import java.util.*;

/**
 * Mem – Runtime memory for the BagOfGrammar interpreter.
 *
 * Manages a stack of scopes (frames), where each scope maps variable names
 * to their runtime value and declared type. The stack mirrors the block
 * structure of the language: a new scope is pushed on every block/function
 * entry and popped on exit.
 *
 * Scope lookup follows lexical scoping: the innermost scope is searched
 * first, then outer scopes up to the global one.
 */

public class Mem {

    // Scope
    private static class Scope{
        final Map<String, ExpType> types = new LinkedHashMap<>();
        final Map<String, ExpValue<?>> values = new LinkedHashMap<>();

        void declare(String id, ExpType type){
            types.put(id, type);
            values.put(id, null); // declaration without initialisation
        }

        void declareInit(String id, ExpType type, ExpValue<?> value){
            types.put(id, type);
            values.put(id, value); // declaration with initialisation
        }

        boolean contains(String id){return types.containsKey(id);}
    }

    // State
    private final Deque<Scope> scopes = new ArrayDeque<>();

    public Mem(){
        pushScope(); // global scope
    }

    // Indipendent snapshot for non-deterministic branches
    public Mem(Mem other){
        for(Scope s : other.scopes){
            Scope copy = new Scope();
            copy.types.putAll(s.types);
            copy.values.putAll(s.values);
            this.scopes.addLast(copy); // global at bottom, current at top
        }
    }

    // Scope management
    public void pushScope(){scopes.push(new Scope());} // opens new block scope
    public void popScope(){scopes.pop();} // closes innermost block

    // Variable declaration
    public void declare(String id, ExpType type){
        assert scopes.peek() != null;
        scopes.peek().declare(id, type); // declaration in the current scope without initialisation
    }

    public void declareInit(String id, ExpType type, ExpValue<?> value){
        assert scopes.peek() != null;
        scopes.peek().declareInit(id, type, value);
    }

    // Lookup

    /**
     * Returns true if the variable is declared in any reachable scope and has been
     * assigned a value.
     */
    public boolean contains(String id){
        for(Scope s : scopes){
            if(s.contains(id))
                return s.values.get(id) != null;
        }
        return false;
    }

    /**
     * Returns true if the variable is delcared in any reachable scope even
     * without initialisation.
     */
    public boolean isDeclared(String id){
        for(Scope s : scopes){
            if(s.contains(id))
                return true;
        }
        return false;
    }

    public ExpValue<?> getValue(String id){
        for(Scope s : scopes){
            if(s.contains(id))
                return s.values.get(id); // returns the runtime value of a variable
        }
        return null;
    }

    public ExpType getType(String id){
        for(Scope s : scopes){
            if(s.contains(id))
                return s.types.get(id); // returns the delcared type of a variable
        }
        return null;
    }

    // Update
    public void setValue(String id, ExpValue<?> value){
        for(Scope s : scopes){
            if(s.contains(id)){
                s.values.put(id, value);
                return;
            }
        }
        throw new IllegalArgumentException("Undeclared value: " + id); // if variable is not declared
    }

    /**
     * Reconciles this memory with another after non-deterministic branches:
     * for every variable present in both memories, this memory adopts the
     * value from {@code other} (only for variables already declared here).
     * New variables introduced in {@code other} are ignored.
     */
    public void mergeFrom(Mem other){
        for(Scope s : scopes){
            for(String id : s.types.keySet()){
                ExpValue<?> otherVal = other.getValue(id);
                if(otherVal != null)
                    s.values.put(id, otherVal);
            }
        }
    }

    // Debug
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int depth = scopes.size();
        List<Scope> ordered = new ArrayList<>(); // print from outermost to innermost
        Collections.reverse(ordered);
        for(Scope s : ordered){
            sb.append(" [scope ").append(depth--).append("] {");
            for(String id : s.types.keySet()){
                ExpType type = s.types.get(id);
                ExpValue<?> value = s.values.get(id);
                sb.append(id).append("[").append(type != null ? type.getName() : "?").append("]")
                        .append(": ").append(value != null ? value : "<uninitalised>").append(" ");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }
}
