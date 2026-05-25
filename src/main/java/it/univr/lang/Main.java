package it.univr.lang;

// Imports
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.IOException;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws IOException{

        // Read program file from programs/ folder
        if (args.length < 1) {
            System.err.println("Usage: Main <program-file>");
            System.err.println("Example: Main programs/goblin.bag");
            System.exit(1);
        }

        CharStream cs = CharStreams.fromPath(Path.of(args[0]));

        // Lexer + Parser
        BagOfGrammarLexer lexer = new BagOfGrammarLexer(cs);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        BagOfGrammarParser parser = new BagOfGrammarParser(tokens);
        ParseTree tree = parser.program();

        // Stop if there are syntax errors
        if (parser.getNumberOfSyntaxErrors() > 0) {
            System.err.println("Syntax error(s) found. Execution aborted.");
            System.exit(1);
        }

        // Type checking
        BagOfGrammarTS typeSystem = new BagOfGrammarTS();
        typeSystem.visit(tree);

        if (typeSystem.getErrorCount() > 0) {
            System.err.println("Type error(s) found. Execution aborted.");
            System.exit(1);
        }

        // Interpretation
        BagOfGrammarIntp interpreter = new BagOfGrammarIntp();
        interpreter.visit(tree);
    }
}
