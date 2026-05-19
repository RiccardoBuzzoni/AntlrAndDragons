package it.univr.lang.errors;

/**
 * Exception thrown for runtime errors in the Antlr&Dragons language
 * (division by zero, undeclared variable, incompatible type,
 * index out of bounds, etc.).
 *
 * It is caught in the visitor and printed to the screen in a readable format,
 * without crashing the JVM with a stack trace.
 */

public class RuntimeError extends RuntimeException{

    private final int line;

    public RuntimeError(String msg, int line){
        super(msg);
        this.line = line;
    }

    // Line index not available.
    public RuntimeError(String message){
        this(message, -1);
    }

    public int getLine(){
        return line;
    }

    @Override
    public String toString() {
        if(line >= 0){
            return "[Runtime Error] line " + line + ": " + getMessage();
        }
        else{
            return "[Runtime Error] " + getMessage();
        }
    }
}
