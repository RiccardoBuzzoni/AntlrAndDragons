package it.univr.lang;

// Imports
import it.univr.lang.type.*;
import it.univr.lang.value.*;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.*;

/**
 * BagOfGrammarTS – Type-System / Type-Checker
 *
 * Visits the ANTLR parse tree produced by BagOfGrammarParser and performs
 * static type-checking. Returns a {@link Type} from every expression visitor;
 * statement visitors return {@code null}.
 *
 * High-level structure of this file:
 *   1. Fields & environment (scopes, registries, checker state)
 *   2. Error reporting
 *   3. Scoping helpers (push/pop/declare/lookup)
 *   4. Type compatibility helpers (isAssignable, isCastable, isNumeric, ...)
 *   5. Program entry point & two-pass signature registration
 *   6. Creatures (classes)
 *   7. Spellbook (top-level functions) & function-body checking
 *   8. Quest block (program entry) & generic block
 *   9. Statements
 *  10. Variable declarations
 *  11. Assignments (simple, field, compound)
 *  12. Control flow (if / until / for / switch)
 *  13. Function & method calls
 *  14. Expressions (literals, arithmetic, logical, relational, ternary, cast, inc/dec, declare)
 *  15. Type visitors (parse-tree type node -> Type instance)
 */
public class BagOfGrammarTS extends BagOfGrammarBaseVisitor<Type> {

    // =====================================================================
    // 1. Fields & environment
    // =====================================================================

    /** A single lexical scope: maps variable names declared in it to their static type. */
    private static class Scope {
        final Map<String, Type> vars = new LinkedHashMap<>();
    }

    /** Stack of active lexical scopes; the head is the innermost (current) scope. */
    private final Deque<Scope> scopes = new ArrayDeque<>();

    /** Per-creature field table: creature name -> (field name -> declared type). */
    private final Map<String, Map<String, Type>> creatureFields = new LinkedHashMap<>();

    /** Per-creature method table: creature name -> (method name -> signature as FuncType). */
    private final Map<String, Map<String, Type>> creatureMethods = new LinkedHashMap<>();

    /** Global spellbook (top-level function) table: spell name -> signature. */
    private final Map<String, FuncType> spells = new LinkedHashMap<>();

    /** Expected return type of the function/method currently being checked, or null if not inside one. */
    private Type currentReturnType = null;

    /** Nesting depth of loop/switch constructs; used to validate that 'break' is only used inside one. */
    private int loopDepth = 0;

    /** Running count of type errors found so far. */
    private int errorCount = 0;

    // =====================================================================
    // 2. Error reporting
    // =====================================================================

    /** Reports a type error with source position (line:col) on stderr and bumps the error counter. */
    private void error(org.antlr.v4.runtime.ParserRuleContext ctx, String msg) {
        int line = ctx.getStart().getLine();
        int col  = ctx.getStart().getCharPositionInLine();
        System.err.printf("[TypeCheck] line %d:%d – %s%n", line, col, msg);
        errorCount++;
    }

    /** Returns how many type errors were collected during the check (0 = program is well-typed). */
    public int getErrorCount() { return errorCount; }

    // =====================================================================
    // 3. Scoping helpers
    // =====================================================================

    /** Opens a new, empty lexical scope (e.g. entering a block, function body, or loop body). */
    private void pushScope() { scopes.push(new Scope()); }

    /** Closes the innermost lexical scope, discarding the variables declared in it. */
    private void popScope()  { scopes.pop(); }

    /** Declares a variable with the given type in the current (innermost) scope. */
    private void declare(String name, Type type) {
        assert scopes.peek() != null;
        scopes.peek().vars.put(name, type);
    }

    /**
     * Looks up a variable's static type, searching from the innermost scope outward
     * (mirrors the interpreter's dynamic scoping for name resolution order).
     * If the variable is undeclared, reports an error and returns ErrType so that
     * type-checking of the surrounding expression can continue without cascading.
     */
    private Type lookup(String name, ParserRuleContext ctx) {
        for (Scope s : scopes)
            if (s.vars.containsKey(name)) {
                return s.vars.get(name);
            }
        error(ctx, "Undeclared variable: " + name);
        return ErrType.INSTANCE;
    }

    // =====================================================================
    // 4. Type compatibility
    // =====================================================================

    /**
     * Is a value of type {@code from} assignable to a location of type {@code to}?
     * This is the *implicit widening only* relation (used for var-decl init, simple
     * assignment, arguments, return values, ...). Rules, in order:
     *   - ErrType on either side is always compatible (avoids cascading false errors)
     *   - 'to' == Any accepts anything
     *   - exact type match
     *   - subtyping: HP / Damage / Level <: Int
     *   - subtyping: QuestName / Die <: String
     *   - numeric widening: Int / HP / Damage / Level -> Float
     *   - object types: same creature name
     * Anything else is not assignable.
     */
    private boolean isAssignable(Type from, Type to) {
        if (from instanceof ErrType || to instanceof ErrType) return true;
        if (to == SimpleType.ANY) return true;
        if (from.equals(to)) return true;

        // Subtyping: HP, Damage, Level subtypes of Int
        if ((from == SimpleType.HP || from == SimpleType.DAMAGE || from == SimpleType.LEVEL)
                && to == SimpleType.INT) return true;

        // Subtyping: QuestName, Die subtypes of String
        if ((from == SimpleType.QUESTNAME || from == SimpleType.DIE)
                && to == SimpleType.STRING) return true;

        // Numeric widening
        if ((from == SimpleType.INT || from == SimpleType.HP ||
                from == SimpleType.DAMAGE || from == SimpleType.LEVEL)
                && to == SimpleType.FLOAT) return true;

        if (from instanceof ObjectType && to instanceof ObjectType)
            return ((ObjectType) from).getName().equals(((ObjectType) to).getName());

        return false;
    }

    /**
     * Is an explicit cast from {@code from} to {@code to} legal (the 'as' operator)?
     * Superset of {@link #isAssignable}: also allows narrowing conversions:
     *   - Int -> HP / Damage / Level
     *   - String -> QuestName / Die
     *   - Float -> Int
     *   - Any -> any type
     */
    private boolean isCastable(Type from, Type to) {
        if (isAssignable(from, to)) return true;

        // Narrowing: Int -> HP, Damage, Level
        if (from == SimpleType.INT &&
                (to == SimpleType.HP || to == SimpleType.DAMAGE || to == SimpleType.LEVEL))
            return true;

        // Narrowing: String -> QuestName, Die
        if (from == SimpleType.STRING &&
                (to == SimpleType.QUESTNAME || to == SimpleType.DIE))
            return true;

        // Narrowing numerico: Float -> Int
        if (from == SimpleType.FLOAT && to == SimpleType.INT) return true;

        // Any -> any type
        if (from == SimpleType.ANY) return true;

        return false;
    }

    /** True if {@code t} can be used where a numeric value is expected (Int, Float, and Int-subtypes; ErrType/Any pass permissively). */
    private boolean isNumeric(Type t) {
        if (t instanceof ErrType || t == SimpleType.ANY) return true;
        return t == SimpleType.INT || t == SimpleType.FLOAT ||
                t == SimpleType.HP || t == SimpleType.DAMAGE || t == SimpleType.LEVEL;
    }

    /** True if {@code t} can be used where a Bool value is expected (ErrType/Any pass permissively). */
    private boolean isBool(Type t) {
        return t instanceof ErrType || t == SimpleType.BOOL || t == SimpleType.ANY;
    }

    /** Result type of a binary numeric operation: Float if either operand is Float, otherwise Int. */
    private Type numericJoin(Type a, Type b) {
        if (a instanceof ErrType || b instanceof ErrType) return ErrType.INSTANCE;
        if (a.equals(b)) return a;
        if (a == SimpleType.FLOAT || b == SimpleType.FLOAT) return SimpleType.FLOAT;
        return SimpleType.INT;
    }

    // =====================================================================
    // 5. Program entry point & two-pass signature registration
    // =====================================================================

    /**
     * Entry point of the type-checker. Uses two passes so forward references work
     * (a spell calling a spell declared later, a creature referencing another creature, ...):
     *   Pass 1 – register every creature's and spell's *signature* only (no bodies checked yet)
     *   Pass 2 – type-check creature method bodies, spell bodies, then the quest block (the "main")
     * Both passes run inside a single global scope, which also hosts the 'world:' globals.
     */
    @Override
    public Type visitProgram(BagOfGrammarParser.ProgramContext ctx) {
        pushScope(); // global scope
        // Pass 1: register all signatures before type-checking bodies
        if (ctx.creatureSection() != null) registerCreatures(ctx.creatureSection());
        if (ctx.globalSection() != null) visit(ctx.globalSection());
        if (ctx.spellbookSection() != null) registerSpells(ctx.spellbookSection());
        // Pass 2: check bodies
        if (ctx.creatureSection() != null) visit(ctx.creatureSection());
        if (ctx.spellbookSection() != null) visit(ctx.spellbookSection());
        visit(ctx.questBlock());
        popScope();
        return null;
    }

    /**
     * Pass-1 helper: walks every creature declaration and records its fields and method
     * signatures into {@link #creatureFields} / {@link #creatureMethods}, without visiting
     * method bodies (those are checked later, in pass 2).
     */
    private void registerCreatures(BagOfGrammarParser.CreatureSectionContext ctx) {
        for (BagOfGrammarParser.CreatureDeclContext cd : ctx.creatureDecl()) {
            String name = cd.ID().getText();
            creatureFields.put(name, new LinkedHashMap<>());
            creatureMethods.put(name, new LinkedHashMap<>());
            for (BagOfGrammarParser.CreatureMemberContext m : cd.creatureMember()) {
                if (m instanceof BagOfGrammarParser.ClassFieldContext) {
                    BagOfGrammarParser.ClassFieldContext f = (BagOfGrammarParser.ClassFieldContext) m;
                    creatureFields.get(name).put(f.ID().getText(), visitType(f.type()));
                } else if (m instanceof BagOfGrammarParser.ClassMethodReturnContext) {
                    BagOfGrammarParser.ClassMethodReturnContext mr = (BagOfGrammarParser.ClassMethodReturnContext) m;
                    creatureMethods.get(name).put(mr.ID().getText(),
                            new it.univr.lang.type.FuncType(collectParamTypes(mr.paramList()), visitType(mr.type())));
                } else if (m instanceof BagOfGrammarParser.ClassMethodVoidContext) {
                    BagOfGrammarParser.ClassMethodVoidContext mv = (BagOfGrammarParser.ClassMethodVoidContext) m;
                    creatureMethods.get(name).put(mv.ID().getText(),
                            new it.univr.lang.type.FuncType(collectParamTypes(mv.paramList()), VoidType.INSTANCE));
                }
            }
        }
    }

    /**
     * Pass-1 helper: walks every top-level spell (function) declaration and records its
     * signature into {@link #spells}, without visiting the body (checked later, in pass 2).
     */
    private void registerSpells(BagOfGrammarParser.SpellbookSectionContext ctx) {
        for (BagOfGrammarParser.SpellDeclContext sd : ctx.spellDecl()) {
            if (sd instanceof BagOfGrammarParser.FuncDeclReturnContext) {
                BagOfGrammarParser.FuncDeclReturnContext fr = (BagOfGrammarParser.FuncDeclReturnContext) sd;
                spells.put(fr.ID().getText(),
                        new FuncType(collectParamTypes(fr.paramList()), visitType(fr.type())));
            } else {
                BagOfGrammarParser.FuncDeclVoidContext fv = (BagOfGrammarParser.FuncDeclVoidContext) sd;
                spells.put(fv.ID().getText(),
                        new FuncType(collectParamTypes(fv.paramList()), VoidType.INSTANCE));
            }
        }
    }

    /** Extracts the declared parameter types, in order, from a parameter list (empty list if {@code ctx} is null). */
    private List<Type> collectParamTypes(BagOfGrammarParser.ParamListContext ctx) {
        List<Type> result = new ArrayList<>();
        if (ctx != null)
            for (BagOfGrammarParser.ParamContext p : ctx.param())
                result.add(visitType(p.type()));
        return result;
    }

    // =====================================================================
    // 6. Creatures (classes)
    // =====================================================================

    /** Pass-2: visits every creature declaration to type-check its method bodies. */
    @Override
    public Type visitCreatureSection(BagOfGrammarParser.CreatureSectionContext ctx) {
        for (BagOfGrammarParser.CreatureDeclContext cd : ctx.creatureDecl())
            visit(cd);
        return null;
    }

    /** Pass-2: type-checks every member of a single creature (only methods have bodies to check). */
    @Override
    public Type visitCreatureDecl(BagOfGrammarParser.CreatureDeclContext ctx) {
        for (BagOfGrammarParser.CreatureMemberContext m : ctx.creatureMember())
            visitCreatureMember(m);
        return null;
    }

    /** Dispatches a creature member to {@link #checkFunctionBody} if it is a method (fields need no body check). */
    private void visitCreatureMember(BagOfGrammarParser.CreatureMemberContext m) {
        if (m instanceof BagOfGrammarParser.ClassMethodReturnContext) {
            BagOfGrammarParser.ClassMethodReturnContext mr = (BagOfGrammarParser.ClassMethodReturnContext) m;
            checkFunctionBody(mr.paramList(), mr.block(), visitType(mr.type()), mr);
        } else if (m instanceof BagOfGrammarParser.ClassMethodVoidContext) {
            BagOfGrammarParser.ClassMethodVoidContext mv = (BagOfGrammarParser.ClassMethodVoidContext) m;
            checkFunctionBody(mv.paramList(), mv.block(), VoidType.INSTANCE, mv);
        }
    }

    // Signatures were already registered in pass 1 (registerCreatures); nothing left to do here.
    @Override public Type visitClassField(BagOfGrammarParser.ClassFieldContext ctx) { return null; }
    @Override public Type visitClassMethodReturn(BagOfGrammarParser.ClassMethodReturnContext ctx) { return null; }
    @Override public Type visitClassMethodVoid(BagOfGrammarParser.ClassMethodVoidContext ctx) { return null; }

    // =====================================================================
    // 7. Spellbook (top-level functions) & function-body checking
    // =====================================================================

    /** Pass-2: visits every top-level spell declaration to type-check its body. */
    @Override
    public Type visitSpellbookSection(BagOfGrammarParser.SpellbookSectionContext ctx) {
        for (BagOfGrammarParser.SpellDeclContext sd : ctx.spellDecl())
            visit(sd);
        return null;
    }

    /** Type-checks the body of a spell that declares a return type. */
    @Override
    public Type visitFuncDeclReturn(BagOfGrammarParser.FuncDeclReturnContext ctx) {
        checkFunctionBody(ctx.paramList(), ctx.block(), visitType(ctx.type()), ctx);
        return null;
    }

    /** Type-checks the body of a void spell. */
    @Override
    public Type visitFuncDeclVoid(BagOfGrammarParser.FuncDeclVoidContext ctx) {
        checkFunctionBody(ctx.paramList(), ctx.block(), VoidType.INSTANCE, ctx);
        return null;
    }

    /**
     * Shared routine for type-checking any function/method body (spell or creature method):
     *   1. saves the current expected return type and installs the new one, so nested
     *      'return' statements (and nested function declarations, if any) are checked correctly
     *   2. opens a fresh scope and declares each parameter in it
     *   3. visits the body
     *   4. closes the scope and restores the previous expected return type
     */
    private void checkFunctionBody(BagOfGrammarParser.ParamListContext params, BagOfGrammarParser.BlockContext body,
                                   Type returnType, org.antlr.v4.runtime.ParserRuleContext ctx) {
        Type prevReturn = currentReturnType;
        currentReturnType = returnType;
        pushScope();
        if (params != null)
            for (BagOfGrammarParser.ParamContext p : params.param())
                declare(p.ID().getText(), visitType(p.type()));
        visit(body);
        popScope();
        currentReturnType = prevReturn;
    }

    @Override public Type visitParamList(BagOfGrammarParser.ParamListContext ctx) { return null; }
    @Override public Type visitParam(BagOfGrammarParser.ParamContext ctx) { return null; }

    // =====================================================================
    // 8. Quest block (program entry) & generic block
    // =====================================================================

    /** The quest block is the program's "main": just type-check its block of statements. */
    @Override
    public Type visitQuestBlock(BagOfGrammarParser.QuestBlockContext ctx) {
        visit(ctx.block());
        return null;
    }

    /** Type-checks a block: opens a fresh scope for locals declared inside it, visits each statement, then closes it. */
    @Override
    public Type visitBlock(BagOfGrammarParser.BlockContext ctx) {
        pushScope();
        for (BagOfGrammarParser.StatContext s : ctx.stat())
            visit(s);
        popScope();
        return null;
    }

    // =====================================================================
    // 9. Statements
    // =====================================================================

    // Simple statements just delegate to the relevant sub-rule's visitor.
    @Override public Type visitStatVarDecl(BagOfGrammarParser.StatVarDeclContext ctx) { visit(ctx.varDecl()); return null; }
    @Override public Type visitStatAssign(BagOfGrammarParser.StatAssignContext ctx) { visit(ctx.assign()); return null; }
    @Override public Type visitStatIf(BagOfGrammarParser.StatIfContext ctx) { visit(ctx.ifStat()); return null; }
    @Override public Type visitStatWhile(BagOfGrammarParser.StatWhileContext ctx) { visit(ctx.untilStat()); return null; }
    @Override public Type visitStatFor(BagOfGrammarParser.StatForContext ctx) { visit(ctx.forStat()); return null; }
    @Override public Type visitStatSwitch(BagOfGrammarParser.StatSwitchContext ctx) { visit(ctx.switchStat()); return null; }
    @Override public Type visitStatBlock(BagOfGrammarParser.StatBlockContext ctx) { visit(ctx.block()); return null; }
    @Override public Type visitStatExpr(BagOfGrammarParser.StatExprContext ctx) { visit(ctx.expr()); return null; }

    /** 'print <expr>' — any type is printable, so the expression is just visited (for side-effect errors) and discarded. */
    @Override
    public Type visitStatPrint(BagOfGrammarParser.StatPrintContext ctx) {
        visit(ctx.expr()); // any type is printable
        return null;
    }

    /** 'return <expr>' — must be inside a function/method, and expr's type must be assignable to the expected return type. */
    @Override
    public Type visitStatReturn(BagOfGrammarParser.StatReturnContext ctx) {
        Type exprType = visit(ctx.expr());
        if (currentReturnType == null)
            error(ctx, "'return' used outside of a function.");
        else if (!isAssignable(exprType, currentReturnType))
            error(ctx, "Return type mismatch: expected " + currentReturnType + ", got " + exprType);
        return null;
    }

    /** Bare 'return' (no value) — must be inside a function/method whose declared return type is Void. */
    @Override
    public Type visitStatReturnVoid(BagOfGrammarParser.StatReturnVoidContext ctx) {
        if (currentReturnType == null)
            error(ctx, "'return' used outside of a function.");
        else if (!currentReturnType.equals(VoidType.INSTANCE))
            error(ctx, "Void return in non-void function (expected " + currentReturnType + ").");
        return null;
    }

    /** 'break' — only legal inside a loop or switch, tracked via {@link #loopDepth}. */
    @Override
    public Type visitStatBreak(BagOfGrammarParser.StatBreakContext ctx) {
        if (loopDepth == 0)
            error(ctx, "'break' used outside of a loop or switch.");
        return null;
    }

    /** 'flee' (program exit) — always valid, no type to check. */
    @Override
    public Type visitStatExit(BagOfGrammarParser.StatExitContext ctx) {
        return null; // 'flee' is always valid
    }

    /** A spell call used as a statement (its result, if any, is discarded). */
    @Override
    public Type visitStatFuncCall(BagOfGrammarParser.StatFuncCallContext ctx) {
        visit(ctx.spellCall());
        return null;
    }

    // =====================================================================
    // 10. Variable declarations
    // =====================================================================

    /** 'var x: T = expr' — checks expr's type is assignable to T, then declares x: T in the current scope. */
    @Override
    public Type visitVarDeclInit(BagOfGrammarParser.VarDeclInitContext ctx) {
        Type declared = visitType(ctx.type());
        Type actual   = visit(ctx.expr());
        if (!isAssignable(actual, declared))
            error(ctx, "Cannot assign " + actual + " to variable of type " + declared);
        declare(ctx.ID().getText(), declared);
        return null;
    }

    /** 'var x: T' (no initializer) — just declares x: T with the language's default value for T. */
    @Override
    public Type visitVarDeclDefault(BagOfGrammarParser.VarDeclDefaultContext ctx) {
        declare(ctx.ID().getText(), visitType(ctx.type()));
        return null;
    }

    // =====================================================================
    // 11. Assignments
    // =====================================================================

    /** 'x = expr' — checks expr's type is assignable to x's already-declared type. */
    @Override
    public Type visitAssignSimple(BagOfGrammarParser.AssignSimpleContext ctx) {
        Type varType  = lookup(ctx.ID().getText(), ctx);
        Type exprType = visit(ctx.expr());
        if (!isAssignable(exprType, varType))
            error(ctx, "Cannot assign " + exprType + " to '" + ctx.ID().getText() + "' (type " + varType + ")");
        return null;
    }

    /** 'obj.field = expr' — resolves the field's declared type on obj's creature, then checks assignability. */
    @Override
    public Type visitAssignField(BagOfGrammarParser.AssignFieldContext ctx) {
        Type objType  = lookup(ctx.ID(0).getText(), ctx);
        Type exprType = visit(ctx.expr());
        Type fieldType = resolveField(objType, ctx.ID(1).getText(), ctx);
        if (!isAssignable(exprType, fieldType))
            error(ctx, "Cannot assign " + exprType + " to field '" +
                    ctx.ID(1).getText() + "' of type " + fieldType);
        return null;
    }

    // Compound assignments on plain variables (+= -= *= /= %=) – advanced feature: syntactic sugar.
    // All five forms share the same numeric/numeric check, performed by checkCompound.
    @Override public Type visitAssignAdd(BagOfGrammarParser.AssignAddContext ctx) { return checkCompound(ctx, ctx.ID().getText(), ctx.expr()); }
    @Override public Type visitAssignSub(BagOfGrammarParser.AssignSubContext ctx) { return checkCompound(ctx, ctx.ID().getText(), ctx.expr()); }
    @Override public Type visitAssignMul(BagOfGrammarParser.AssignMulContext ctx) { return checkCompound(ctx, ctx.ID().getText(), ctx.expr()); }
    @Override public Type visitAssignDiv(BagOfGrammarParser.AssignDivContext ctx) { return checkCompound(ctx, ctx.ID().getText(), ctx.expr()); }
    @Override public Type visitAssignMod(BagOfGrammarParser.AssignModContext ctx) { return checkCompound(ctx, ctx.ID().getText(), ctx.expr()); }

    /** Shared check for 'x op= expr' on a plain variable: both the variable and expr must be numeric. */
    private Type checkCompound(org.antlr.v4.runtime.ParserRuleContext ctx,
                               String varName, BagOfGrammarParser.ExprContext exprCtx) {
        Type varType  = lookup(varName, ctx);
        Type exprType = visit(exprCtx);
        if (!isNumeric(varType))
            error(ctx, "Compound assignment requires a numeric variable, got " + varType);
        if (!isNumeric(exprType))
            error(ctx, "Compound assignment requires a numeric expression, got " + exprType);
        return null;
    }

    // Compound assignments on object fields (obj.field += expr, etc.) – same idea as above, but on a field.
    @Override
    public Type visitAssignFieldAdd(BagOfGrammarParser.AssignFieldAddContext ctx) {
        return checkCompoundField(ctx, ctx.ID(0).getText(), ctx.ID(1).getText(), ctx.expr());
    }
    @Override
    public Type visitAssignFieldSub(BagOfGrammarParser.AssignFieldSubContext ctx) {
        return checkCompoundField(ctx, ctx.ID(0).getText(), ctx.ID(1).getText(), ctx.expr());
    }
    @Override
    public Type visitAssignFieldMul(BagOfGrammarParser.AssignFieldMulContext ctx) {
        return checkCompoundField(ctx, ctx.ID(0).getText(), ctx.ID(1).getText(), ctx.expr());
    }
    @Override
    public Type visitAssignFieldDiv(BagOfGrammarParser.AssignFieldDivContext ctx) {
        return checkCompoundField(ctx, ctx.ID(0).getText(), ctx.ID(1).getText(), ctx.expr());
    }
    @Override
    public Type visitAssignFieldMod(BagOfGrammarParser.AssignFieldModContext ctx) {
        return checkCompoundField(ctx, ctx.ID(0).getText(), ctx.ID(1).getText(), ctx.expr());
    }

    /** Shared check for 'obj.field op= expr': both the resolved field type and expr must be numeric. */
    private Type checkCompoundField(org.antlr.v4.runtime.ParserRuleContext ctx,
                                    String objName, String fieldName, BagOfGrammarParser.ExprContext exprCtx) {
        Type objType   = lookup(objName, ctx);
        Type fieldType = resolveField(objType, fieldName, ctx);
        Type exprType  = visit(exprCtx);
        if (!isNumeric(fieldType))
            error(ctx, "Compound assignment requires a numeric field, got " + fieldType);
        if (!isNumeric(exprType))
            error(ctx, "Compound assignment requires a numeric expression, got " + exprType);
        return null;
    }

    // =====================================================================
    // 12. Control flow
    // =====================================================================

    /** 'if <cond> {...} [else if <cond> {...}]* [else {...}]' — every condition must be Bool; every block is checked independently. */
    @Override
    public Type visitIfStat(BagOfGrammarParser.IfStatContext ctx) {
        for (BagOfGrammarParser.ExprContext e : ctx.expr()) {
            Type cond = visit(e);
            if (!isBool(cond))
                error(ctx, "Condition of 'if'/'else if' must be Bool, got " + cond);
        }
        for (BagOfGrammarParser.BlockContext b : ctx.block())
            visit(b);
        return null;
    }

    /** 'until <cond> { ... }' (checked loop, the language's while-equivalent) — condition must be Bool. */
    @Override
    public Type visitUntilStat(BagOfGrammarParser.UntilStatContext ctx) {
        Type cond = visit(ctx.expr());
        if (!isBool(cond))
            error(ctx, "Condition of 'until' must be Bool, got " + cond);
        loopDepth++;
        visit(ctx.block());
        loopDepth--;
        return null;
    }

    /**
     * 'for id from <expr> to <expr> { ... } [else { ... }]'
     * Both bounds must be numeric; the loop variable is implicitly typed Int and scoped only
     * to the loop (declared in its own pushed scope); the optional 'else' block, if present,
     * is also visited within that same scope.
     */
    @Override
    public Type visitForStat(BagOfGrammarParser.ForStatContext ctx) {
        Type fromType = visit(ctx.expr(0));
        Type toType   = visit(ctx.expr(1));
        if (!isNumeric(fromType))
            error(ctx, "'from' expression must be numeric, got " + fromType);
        if (!isNumeric(toType))
            error(ctx, "'to' expression must be numeric, got " + toType);
        pushScope();
        declare(ctx.ID().getText(), SimpleType.INT); // loop variable is implicitly Int
        loopDepth++;
        visit(ctx.block(0));
        loopDepth--;
        if (ctx.block().size() > 1) // optional 'else' block
            visit(ctx.block(1));
        popScope();
        return null;
    }

    /**
     * 'switch <expr> { case <expr>: stat* ... default: stat* }'
     * Each case label's type must be compatible with the switch expression's type in at
     * least one assignability direction; case/default bodies are visited as plain statement lists
     * (not as their own block, so they share the switch's surrounding scope).
     */
    @Override
    public Type visitSwitchStat(BagOfGrammarParser.SwitchStatContext ctx) {
        Type switchType = visit(ctx.expr());
        loopDepth++;
        for (BagOfGrammarParser.CaseClauseContext cc : ctx.caseClause()) {
            Type caseType = visit(cc.expr());
            if (!isAssignable(caseType, switchType) && !isAssignable(switchType, caseType))
                error(cc, "Case type " + caseType + " is incompatible with switch type " + switchType);
            for (BagOfGrammarParser.StatContext s : cc.stat()) visit(s);
        }
        if (ctx.defaultClause() != null)
            for (BagOfGrammarParser.StatContext s : ctx.defaultClause().stat()) visit(s);
        loopDepth--;
        return null;
    }

    @Override public Type visitCaseClause(BagOfGrammarParser.CaseClauseContext ctx) { return null; }
    @Override public Type visitDefaultClause(BagOfGrammarParser.DefaultClauseContext ctx) { return null; }

    // =====================================================================
    // 13. Function & method calls
    // =====================================================================
    // (advanced features: Funzioni / Classi e oggetti)

    /** Resolves a spell (function) call by name, then validates its arguments against the registered signature. */
    @Override
    public Type visitSpellCall(BagOfGrammarParser.SpellCallContext ctx) {
        String name = ctx.ID().getText();
        FuncType ft = spells.get(name);
        if (ft == null) {
            error(ctx, "Unknown spell (function): " + name);
            return ErrType.INSTANCE;
        }
        checkArgs(ctx.argList(), ft, ctx);
        return ft.getReturnType();
    }

    @Override public Type visitArgList(BagOfGrammarParser.ArgListContext ctx) { return null; }

    /**
     * Validates a call's arguments against a function signature: checks the argument
     * count matches, then checks each argument's type is assignable to the corresponding
     * declared parameter type.
     */
    private void checkArgs(BagOfGrammarParser.ArgListContext argCtx, FuncType ft,
                           org.antlr.v4.runtime.ParserRuleContext ctx) {
        List<BagOfGrammarParser.ExprContext> args = (argCtx == null)
                ? Collections.emptyList() : argCtx.expr();
        if (args.size() != ft.getParameterTypes().size()) {
            error(ctx, "Argument count mismatch: expected " + ft.getParameterTypes().size() +
                    ", got " + args.size());
            return;
        }
        for (int i = 0; i < args.size(); i++) {
            Type actual   = visit(args.get(i));
            Type expected = ft.getParameterTypes().get(i);
            if (!isAssignable(actual, expected))
                error(ctx, "Argument " + (i + 1) + " type mismatch: expected " +
                        expected + ", got " + actual);
        }
    }

    // =====================================================================
    // 14. Expressions
    // =====================================================================

    // --- Literals & primary expressions: their type is fixed by the literal/token kind. ---
    @Override public Type visitExprParen(BagOfGrammarParser.ExprParenContext ctx) { return visit(ctx.expr()); }
    @Override public Type visitExprInt(BagOfGrammarParser.ExprIntContext ctx) { return SimpleType.INT; }
    @Override public Type visitExprFloat(BagOfGrammarParser.ExprFloatContext ctx) { return SimpleType.FLOAT; }
    @Override public Type visitExprBool(BagOfGrammarParser.ExprBoolContext ctx) { return SimpleType.BOOL; }
    @Override public Type visitExprString(BagOfGrammarParser.ExprStringContext ctx) { return SimpleType.STRING; }
    @Override public Type visitExprInterpString(BagOfGrammarParser.ExprInterpStringContext ctx) { return SimpleType.STRING; }

    /** Die literal (e.g. d6, d20) — always typed Die regardless of the face count. */
    @Override
    public Type visitExprDie(BagOfGrammarParser.ExprDieContext ctx) {
        return SimpleType.DIE;
    }

    /** 'roll <die-expr>' — the result of rolling is always an Int. */
    @Override
    public Type visitExprRoll(BagOfGrammarParser.ExprRollContext ctx) {
        return SimpleType.INT;
    }

    /** Variable reference — delegates straight to scope lookup. */
    @Override
    public Type visitExprId(BagOfGrammarParser.ExprIdContext ctx) {
        return lookup(ctx.ID().getText(), ctx);
    }

    /** 'new Creature(...)' — checks the creature is a known one and yields an ObjectType for it. */
    @Override
    public Type visitExprNew(BagOfGrammarParser.ExprNewContext ctx) {
        String className = ctx.ID().getText();
        if (!creatureFields.containsKey(className))
            error(ctx, "Unknown creature (class): " + className);
        return new ObjectType(className);
    }

    @Override
    public Type visitExprFuncCall(BagOfGrammarParser.ExprFuncCallContext ctx) {
        return visit(ctx.spellCall());
    }

    /**
     * 'obj.method(...)' — the receiver must type to an ObjectType, the method must exist
     * on that creature, and the call's arguments are checked against the method's signature.
     */
    @Override
    public Type visitExprMethodCall(BagOfGrammarParser.ExprMethodCallContext ctx) {
        Type receiverType = visit(ctx.expr());
        if (receiverType instanceof ErrType) return ErrType.INSTANCE;
        if (!(receiverType instanceof ObjectType)) {
            error(ctx, "Method call on non-object type: " + receiverType);
            return ErrType.INSTANCE;
        }
        String className  = ((ObjectType) receiverType).getName();
        String methodName = ctx.spellCall().ID().getText();
        Map<String, Type> methods = creatureMethods.get(className);
        if (methods == null || !methods.containsKey(methodName)) {
            error(ctx, "Unknown method '" + methodName + "' on creature " + className);
            return ErrType.INSTANCE;
        }
        FuncType ft = (FuncType) methods.get(methodName);
        checkArgs(ctx.spellCall().argList(), ft, ctx);
        return ft.getReturnType();
    }

    /** 'obj.field' — delegates field-type resolution to {@link #resolveField}. */
    @Override
    public Type visitExprFieldAccess(BagOfGrammarParser.ExprFieldAccessContext ctx) {
        Type objType = visit(ctx.expr());
        return resolveField(objType, ctx.ID().getText(), ctx);
    }

    /**
     * Resolves the declared type of {@code fieldName} on an object type. Reports an error
     * (and returns ErrType) if {@code objType} isn't an object, or if the field is unknown
     * on that creature. Shared by field access, field assignment, and field inc/dec.
     */
    private Type resolveField(Type objType, String fieldName, org.antlr.v4.runtime.ParserRuleContext ctx) {
        if (objType instanceof ErrType) return ErrType.INSTANCE;
        if (!(objType instanceof ObjectType)) {
            error(ctx, "Field access on non-object type: " + objType);
            return ErrType.INSTANCE;
        }
        String className = ((ObjectType) objType).getName();
        Map<String, Type> fields = creatureFields.get(className);
        if (fields == null || !fields.containsKey(fieldName)) {
            error(ctx, "Unknown field '" + fieldName + "' on creature " + className);
            return ErrType.INSTANCE;
        }
        return fields.get(fieldName);
    }

    // --- Arithmetic ---

    /** '+' / '-' between two expressions. '+' also supports String concatenation if either side is a String. */
    @Override
    public Type visitExprAddSub(BagOfGrammarParser.ExprAddSubContext ctx) {
        Type left  = visit(ctx.expr(0));
        Type right = visit(ctx.expr(1));
        String op  = ctx.op.getText();
        // String concatenation with '+'
        if (op.equals("+") && (left == SimpleType.STRING || right == SimpleType.STRING))
            return SimpleType.STRING;
        if (!isNumeric(left))
            error(ctx, "Operator '" + op + "' requires numeric left operand, got " + left);
        if (!isNumeric(right))
            error(ctx, "Operator '" + op + "' requires numeric right operand, got " + right);
        return numericJoin(left, right);
    }

    /** '*' / '/' / '%' — both operands must be numeric; result type follows {@link #numericJoin}. */
    @Override
    public Type visitExprMulDivMod(BagOfGrammarParser.ExprMulDivModContext ctx) {
        Type left  = visit(ctx.expr(0));
        Type right = visit(ctx.expr(1));
        if (!isNumeric(left))
            error(ctx, "Operator '" + ctx.op.getText() + "' requires numeric left operand, got " + left);
        if (!isNumeric(right))
            error(ctx, "Operator '" + ctx.op.getText() + "' requires numeric right operand, got " + right);
        return numericJoin(left, right);
    }

    /** Unary '-' — operand must be numeric; result type equals the operand's type. */
    @Override
    public Type visitExprNeg(BagOfGrammarParser.ExprNegContext ctx) {
        Type t = visit(ctx.expr());
        if (!isNumeric(t))
            error(ctx, "Unary '-' requires numeric operand, got " + t);
        return t;
    }

    // --- Logical ---

    /** 'and' — both operands must be Bool; result is always Bool. */
    @Override
    public Type visitExprLogicalAnd(BagOfGrammarParser.ExprLogicalAndContext ctx) {
        Type left  = visit(ctx.expr(0));
        Type right = visit(ctx.expr(1));
        if (!isBool(left))
            error(ctx, "'and' requires Bool left operand, got " + left);
        if (!isBool(right))
            error(ctx, "'and' requires Bool right operand, got " + right);
        return SimpleType.BOOL;
    }

    /** 'or' — both operands must be Bool; result is always Bool. */
    @Override
    public Type visitExprLogicalOr(BagOfGrammarParser.ExprLogicalOrContext ctx) {
        Type left  = visit(ctx.expr(0));
        Type right = visit(ctx.expr(1));
        if (!isBool(left))
            error(ctx, "'or' requires Bool left operand, got " + left);
        if (!isBool(right))
            error(ctx, "'or' requires Bool right operand, got " + right);
        return SimpleType.BOOL;
    }

    /** 'not' — operand must be Bool; result is always Bool. */
    @Override
    public Type visitExprNot(BagOfGrammarParser.ExprNotContext ctx) {
        Type t = visit(ctx.expr());
        if (!isBool(t))
            error(ctx, "'not' requires Bool operand, got " + t);
        return SimpleType.BOOL;
    }

    // --- Relational & equality ---

    /** Relational operators (< <= > >=) — both operands must be numeric; result is always Bool. */
    @Override
    public Type visitExprRelational(BagOfGrammarParser.ExprRelationalContext ctx) {
        Type left  = visit(ctx.expr(0));
        Type right = visit(ctx.expr(1));
        if (!isNumeric(left) || !isNumeric(right))
            error(ctx, "Relational operator '" + ctx.op.getText() +
                    "' requires numeric operands, got " + left + " and " + right);
        return SimpleType.BOOL;
    }

    /** Equality operators (== !=) — operands must be compatible in at least one assignability direction; result is always Bool. */
    @Override
    public Type visitExprEquality(BagOfGrammarParser.ExprEqualityContext ctx) {
        Type left  = visit(ctx.expr(0));
        Type right = visit(ctx.expr(1));
        if (!isAssignable(left, right) && !isAssignable(right, left))
            error(ctx, "Equality operator '" + ctx.op.getText() +
                    "' applied to incompatible types: " + left + " and " + right);
        return SimpleType.BOOL;
    }

    /**
     * Ternary 'cond ? then : else' (advanced feature: syntactic sugar) — cond must be Bool;
     * the two branches must be compatible in at least one assignability direction; the
     * result type is the "wider" of the two (i.e. the one the other is assignable to).
     */
    @Override
    public Type visitExprTernary(BagOfGrammarParser.ExprTernaryContext ctx) {
        Type cond  = visit(ctx.expr(0));
        Type then  = visit(ctx.expr(1));
        Type else_ = visit(ctx.expr(2));
        if (!isBool(cond))
            error(ctx, "Ternary condition must be Bool, got " + cond);
        if (!isAssignable(then, else_) && !isAssignable(else_, then))
            error(ctx, "Ternary branches have incompatible types: " + then + " and " + else_);
        return isAssignable(then, else_) ? else_ : then;
    }

    /**
     * Explicit cast 'expr as T' (advanced feature: type conversion) — validated via
     * {@link #isCastable}; the static result type is always the target type T,
     * regardless of whether the cast was legal (so checking can continue afterwards).
     */
    @Override
    public Type visitExprCast(BagOfGrammarParser.ExprCastContext ctx) {
        Type targetType = visitType(ctx.type());
        Type exprType = visit(ctx.expr());

        if (!(exprType instanceof ErrType) && !(targetType instanceof ErrType)
                && !isCastable(exprType, targetType))
            error(ctx, "Invalid cast from " + exprType + " to " + targetType);

        return targetType;
    }

    // --- Increment / decrement used inside expressions ---

    // Pre/post increment/decrement on a plain variable (++x, x++, --x, x--).
    @Override public Type visitExprPreInc(BagOfGrammarParser.ExprPreIncContext ctx) { return checkIncDecExpr(ctx, ctx.ID().getText()); }
    @Override public Type visitExprPreDec(BagOfGrammarParser.ExprPreDecContext ctx) { return checkIncDecExpr(ctx, ctx.ID().getText()); }
    @Override public Type visitExprPostInc(BagOfGrammarParser.ExprPostIncContext ctx) { return checkIncDecExpr(ctx, ctx.ID().getText()); }
    @Override public Type visitExprPostDec(BagOfGrammarParser.ExprPostDecContext ctx) { return checkIncDecExpr(ctx, ctx.ID().getText()); }

    /** Shared check for variable inc/dec: the variable's type must be numeric; result type equals it. */
    private Type checkIncDecExpr(ParserRuleContext ctx, String varName) {
        Type t = lookup(varName, ctx);
        if (!isNumeric(t))
            error(ctx, "Increment/decrement requires numeric type, got " + t);
        return t;
    }

    // Pre/post increment/decrement on an object field (++obj.field, obj.field++, --obj.field, obj.field--).
    @Override public Type visitExprPreIncField(BagOfGrammarParser.ExprPreIncFieldContext ctx) {
        return checkIncDecField(ctx, ctx.ID(0).getText(), ctx.ID(1).getText());
    }
    @Override
    public Type visitExprPreDecField(BagOfGrammarParser.ExprPreDecFieldContext ctx) {
        return checkIncDecField(ctx, ctx.ID(0).getText(), ctx.ID(1).getText());
    }
    @Override
    public Type visitExprPostIncField(BagOfGrammarParser.ExprPostIncFieldContext ctx) {
        return checkIncDecField(ctx, ctx.ID(0).getText(), ctx.ID(1).getText());
    }
    @Override
    public Type visitExprPostDecField(BagOfGrammarParser.ExprPostDecFieldContext ctx) {
        return checkIncDecField(ctx, ctx.ID(0).getText(), ctx.ID(1).getText());
    }

    /** Shared check for field inc/dec: the resolved field type must be numeric; result type equals it. */
    private Type checkIncDecField(ParserRuleContext ctx, String obj, String field) {
        Type objType = lookup(obj, ctx);
        Type fieldType = resolveField(objType, field, ctx);
        if (!isNumeric(fieldType))
            error(ctx, "Increment/decrement requires numeric type, got " + fieldType);
        return fieldType;
    }

    // --- User input ---

    /** 'declare <type> [, promptExpr]' — if a prompt is given, it must be a String; the expression's static type is the declared type. */
    @Override
    public Type visitExprDeclare(BagOfGrammarParser.ExprDeclareContext ctx) {
        if (ctx.expr() != null) {
            Type promptType = visit(ctx.expr());

            if (promptType != SimpleType.STRING && promptType != SimpleType.ANY
                    && !(promptType instanceof ErrType)) {
                error(ctx, "'declare' prompt must be a String, got " + promptType);
            }
        }
        return visitType(ctx.type()); // returns declared type
    }

    // =====================================================================
    // 15. Type visitors
    // =====================================================================

    /** Resolves a 'type' parse-tree node into the corresponding {@link Type} instance. */
    private Type visitType(BagOfGrammarParser.TypeContext ctx) { return visit(ctx); }

    @Override public Type visitTypeInt(BagOfGrammarParser.TypeIntContext ctx) { return SimpleType.INT; }
    @Override public Type visitTypeFloat(BagOfGrammarParser.TypeFloatContext ctx) { return SimpleType.FLOAT; }
    @Override public Type visitTypeBool(BagOfGrammarParser.TypeBoolContext ctx) { return SimpleType.BOOL; }
    @Override public Type visitTypeString(BagOfGrammarParser.TypeStringContext ctx) { return SimpleType.STRING; }
    @Override public Type visitTypeHP(BagOfGrammarParser.TypeHPContext ctx) { return SimpleType.HP; }
    @Override public Type visitTypeDamage(BagOfGrammarParser.TypeDamageContext ctx) { return SimpleType.DAMAGE; }
    @Override public Type visitTypeLevel(BagOfGrammarParser.TypeLevelContext ctx) { return SimpleType.LEVEL; }
    @Override public Type visitTypeQuestName(BagOfGrammarParser.TypeQuestNameContext ctx) { return SimpleType.QUESTNAME; }
    @Override public Type visitTypeDie(BagOfGrammarParser.TypeDieContext ctx) { return SimpleType.DIE; }
    @Override public Type visitTypeAny(BagOfGrammarParser.TypeAnyContext ctx) { return SimpleType.ANY; }
    @Override public Type visitTypeObject(BagOfGrammarParser.TypeObjectContext ctx) { return new ObjectType(ctx.ID().getText()); }

}