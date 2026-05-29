package it.univr.lang;

// Imports
import it.univr.lang.type.*;
import java.util.*;

/**
 * BagOfGrammarTS – Type-System / Type-Checker
 *
 * Visits the ANTLR parse tree produced by BagOfGrammarParser and performs
 * symbols collection in order to prevent the shadowing risk.
 */

public class BagOfGrammarSymColl extends BagOfGrammarBaseVisitor<Type>{
    public Set<String> declaredCreatures = new HashSet<>();

    @Override
    public Type visitCreatureDecl(BagOfGrammarParser.CreatureDeclContext ctx) {
        declaredCreatures.add(ctx.ID().getText());
        return visitChildren(ctx);
    }
}
