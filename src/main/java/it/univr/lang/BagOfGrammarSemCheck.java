package it.univr.lang;

// Imports
import it.univr.lang.type.*;
import java.util.*;

/**
 * BagOfGrammarTS – Type-System / Type-Checker
 *
 * Visits the ANTLR parse tree produced by BagOfGrammarParser and performs
 * semantic checking in order to prevent the shadowing risk.
 */

public class BagOfGrammarSemCheck extends BagOfGrammarBaseVisitor<Type>{
    private final Set<String> declaredCreatures;

    public BagOfGrammarSemCheck(Set<String> declaredCreatures) {
        this.declaredCreatures = declaredCreatures;
    }

    @Override
    public Type visitTypeObject(BagOfGrammarParser.TypeObjectContext ctx) {
        String name = ctx.ID().getText();
        if (!declaredCreatures.contains(name)) {
            throw new RuntimeException(
                    "Unknown type: '" + name + "' " + "at line " + ctx.start.getLine()
            );
        }
        return null;
    }
}
