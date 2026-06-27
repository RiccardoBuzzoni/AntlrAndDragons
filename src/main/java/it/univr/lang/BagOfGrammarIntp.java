package it.univr.lang;

// Imports
import it.univr.lang.errors.RuntimeError;
import it.univr.lang.type.*;
import it.univr.lang.value.*;
import org.antlr.v4.runtime.*;
import org.apache.commons.text.StringEscapeUtils;
import java.util.*;
import java.util.regex.*;

/**
 * BagOfGrammarIntp – Tree-walking interpreter for BagOfGrammar.
 *
 * High-level structure of this file:
 *   1. Fields & construction (memory, typed-visitor helpers, control-flow signal exceptions)
 *   2. Creature descriptor & program-level registries
 *   3. Program entry point & section registration
 *   4. Quest block (program entry) & generic block
 *   5. Statements
 *   6. Variable declarations
 *   7. Assignments (simple, field, compound)
 *   8. Control flow (if / until / for / switch) – the branch + merge ('Mem') mechanism
 *   9. Function & method calls
 *  10. Expressions – literals & primary
 *  11. Expressions – arithmetic
 *  12. Expressions – logical, relational, equality, ternary, cast
 *  13. Expressions – increment / decrement (variable and field)
 *  14. Type resolution & misc utilities
 *  15. User input
 */
public class BagOfGrammarIntp extends BagOfGrammarBaseVisitor<ExpValue<?>> {

    // =====================================================================
    // 1. Fields & construction
    // =====================================================================

    /** The interpreter's memory: a stack of scopes mapping variable names to (type, value). */
    private Mem mem;

    /** Creates a top-level interpreter with a fresh, empty memory. */
    public BagOfGrammarIntp() {
        this.mem = new Mem();
    }

    /**
     * Creates a "branch" interpreter that shares no live state with its parent: it gets its
     * own private copy of memory (see {@link Mem#copyOf}). Used by {@link #newBranch()} to
     * implement the branch + merge pattern for conditionals and loop bodies (see section 8).
     */
    private BagOfGrammarIntp(Mem mem) {
        this.mem = new Mem(mem);
    }

    /** Exposes this interpreter's memory (e.g. so a branch's changes can be merged back). */
    public Mem getMem() { return mem; }

    // --- Typed visitor helpers: wrap visit(...) with a cast to the expected runtime value type. ---
    private ExpValue<?> visitExpr(BagOfGrammarParser.ExprContext ctx) { return visit(ctx); }
    private BoolValue visitBoolExpr(BagOfGrammarParser.ExprContext ctx) { return (BoolValue) visit(ctx); }
    private IntValue visitIntExpr(BagOfGrammarParser.ExprContext ctx) { return (IntValue) visit(ctx); }
    private StringValue visitStringExpr(BagOfGrammarParser.ExprContext ctx) { return (StringValue) visit(ctx); }

    /** Widens an Int or Dec runtime value to a Java double, for numeric comparisons/arithmetic. */
    private double toDouble(ExpValue<?> value) {
        if (value instanceof DecValue d)
            return d.toJavaValue();
        return ((IntValue) value).toJavaValue();
    }

    // --- Control-flow signals: unchecked exceptions used to implement return/break/flee. ---
    // All three suppress the stack trace (super(null, null, true, false)) since they are used
    // purely as control-flow signals, not as error conditions.

    /** Thrown by 'return <expr>' to unwind out of the current function/method call, carrying its value. */
    private static class ReturnException extends RuntimeException {
        final ExpValue<?> value;
        ReturnException(ExpValue<?> value) {
            super(null, null, true, false); // no stack trace
            this.value = value;
        }
    }

    /** Thrown by 'break' to unwind out of the innermost loop or switch. */
    private static class BreakException extends RuntimeException {
        static final BreakException INSTANCE = new BreakException();
        private BreakException() { super(null, null, true, false); }
    }

    /** Thrown by 'flee' to unwind all the way out of the quest block and terminate the program. */
    private static class ExitException extends RuntimeException {
        static final ExitException INSTANCE = new ExitException();
        private ExitException() { super(null, null, true, false); }
    }

    // =====================================================================
    // 2. Creature descriptor & program-level registries
    // =====================================================================

    /** Runtime description of a creature (class): its field types and its method bodies. */
    private static class CreatureDescriptor {
        final Map<String, ExpType> fields = new LinkedHashMap<>();
        final Map<String, BagOfGrammarParser.CreatureMemberContext> methods = new LinkedHashMap<>();
    }

    /** All declared creatures, keyed by name. */
    private final Map<String, CreatureDescriptor> creatures = new LinkedHashMap<>();

    /** All declared top-level spells (functions), keyed by name, stored as their declaration node. */
    private final Map<String, BagOfGrammarParser.SpellDeclContext> spells = new LinkedHashMap<>();

    /** Shared RNG used to evaluate 'roll' expressions. */
    private final Random rng = new Random();

    // =====================================================================
    // 3. Program entry point & section registration
    // =====================================================================

    /**
     * Entry point of the interpreter. Unlike the type-checker, this is a single pass:
     * creatures, globals and spells are registered (in that order) before the quest block
     * runs, so the quest block (and any spell/method body) can already reference them.
     * Globals are executed (not just registered) here, since 'world:' variables can have
     * initializers that must run exactly once, before 'main' starts.
     */
    @Override
    public ExpValue<?> visitProgram(BagOfGrammarParser.ProgramContext ctx) {
        if (ctx.creatureSection() != null) registerCreatures(ctx.creatureSection());
        if (ctx.globalSection() != null) registerGlobals(ctx.globalSection());
        if (ctx.spellbookSection() != null) registerSpells(ctx.spellbookSection());
        return visit(ctx.questBlock());
    }

    /** Builds a {@link CreatureDescriptor} for every declared creature: field types and method bodies (not yet executed). */
    private void registerCreatures(BagOfGrammarParser.CreatureSectionContext ctx) {
        for (BagOfGrammarParser.CreatureDeclContext cd : ctx.creatureDecl()) {
            CreatureDescriptor desc = new CreatureDescriptor();
            for (BagOfGrammarParser.CreatureMemberContext m : cd.creatureMember()) {
                if (m instanceof BagOfGrammarParser.ClassFieldContext f)
                    desc.fields.put(f.ID().getText(), resolveType(f.type()));
                else if (m instanceof BagOfGrammarParser.ClassMethodReturnContext mr)
                    desc.methods.put(mr.ID().getText(), m);
                else if (m instanceof BagOfGrammarParser.ClassMethodVoidContext mv)
                    desc.methods.put(mv.ID().getText(), m);
            }
            creatures.put(cd.ID().getText(), desc);
        }
    }

    /** Registers every top-level spell declaration (by name) so it can be called from anywhere in the program. */
    private void registerSpells(BagOfGrammarParser.SpellbookSectionContext ctx) {
        for (BagOfGrammarParser.SpellDeclContext sd : ctx.spellDecl()) {
            String name = (sd instanceof BagOfGrammarParser.FuncDeclReturnContext fr)
                    ? fr.ID().getText() : ((BagOfGrammarParser.FuncDeclVoidContext) sd).ID().getText();
            spells.put(name, sd);
        }
    }

    /** Executes every 'world:' global variable declaration, in source order, in the (already current) global scope. */
    private void registerGlobals(BagOfGrammarParser.GlobalSectionContext ctx) {
        for (BagOfGrammarParser.VarDeclContext vd : ctx.varDecl())
            visit(vd);
    }

    // =====================================================================
    // 4. Quest block (program entry) & generic block
    // =====================================================================

    /** The quest block is the program's "main": runs its block, and turns a 'flee' (ExitException) into a clean process exit. */
    @Override
    public ExpValue<?> visitQuestBlock(BagOfGrammarParser.QuestBlockContext ctx) {
        try {
            visit(ctx.block());
        } catch (ExitException e) { // flee calls ExitException
            System.exit(0);
        }
        return null;
    }

    /** Executes a block: pushes a fresh scope, runs each statement, then pops the scope (even if a control-flow exception escapes). */
    @Override
    public ExpValue<?> visitBlock(BagOfGrammarParser.BlockContext ctx) {
        mem.pushScope();
        try {
            for (BagOfGrammarParser.StatContext s : ctx.stat())
                visit(s);
        } finally {
            mem.popScope(); // performs pop even if exception is thrown
        }
        return null;
    }

    // =====================================================================
    // 5. Statements
    // =====================================================================

    // Simple statements just delegate to the relevant sub-rule's visitor.
    @Override public ExpValue<?> visitStatVarDecl(BagOfGrammarParser.StatVarDeclContext ctx) {
        return visit(ctx.varDecl());
    }
    @Override public ExpValue<?> visitStatAssign(BagOfGrammarParser.StatAssignContext ctx) {
        return visit(ctx.assign());
    }
    @Override public ExpValue<?> visitStatIf(BagOfGrammarParser.StatIfContext ctx) {
        return visit(ctx.ifStat());
    }
    @Override public ExpValue<?> visitStatWhile(BagOfGrammarParser.StatWhileContext ctx) {
        return visit(ctx.untilStat());
    }
    @Override public ExpValue<?> visitStatFor(BagOfGrammarParser.StatForContext ctx) {
        return visit(ctx.forStat());
    }
    @Override public ExpValue<?> visitStatSwitch(BagOfGrammarParser.StatSwitchContext ctx) {
        return visit(ctx.switchStat());
    }
    @Override public ExpValue<?> visitStatBlock(BagOfGrammarParser.StatBlockContext ctx) {
        return visit(ctx.block());
    }
    @Override public ExpValue<?> visitStatExpr(BagOfGrammarParser.StatExprContext ctx) {
        visitExpr(ctx.expr());
        return null;
    }

    /** 'print <expr>' — evaluates the expression and writes its string representation to stdout. */
    @Override
    public ExpValue<?> visitStatPrint(BagOfGrammarParser.StatPrintContext ctx) {
        System.out.println(visitExpr(ctx.expr()));
        return null;
    }

    /** 'return <expr>' — evaluates the expression and unwinds the call via {@link ReturnException}. */
    @Override
    public ExpValue<?> visitStatReturn(BagOfGrammarParser.StatReturnContext ctx) {
        throw new ReturnException(visitExpr(ctx.expr()));
    }

    /** Bare 'return' — unwinds the call via {@link ReturnException} carrying no value. */
    @Override
    public ExpValue<?> visitStatReturnVoid(BagOfGrammarParser.StatReturnVoidContext ctx) {
        throw new ReturnException(null);
    }

    /** 'break' — unwinds to the innermost loop/switch via {@link BreakException}. */
    @Override
    public ExpValue<?> visitStatBreak(BagOfGrammarParser.StatBreakContext ctx) {
        throw BreakException.INSTANCE;
    }

    /** 'flee' — unwinds all the way to the quest block via {@link ExitException}, terminating the program. */
    @Override
    public ExpValue<?> visitStatExit(BagOfGrammarParser.StatExitContext ctx) {
        throw ExitException.INSTANCE;
    }

    /** A spell call used as a statement — evaluated for its side effects, result discarded. */
    @Override
    public ExpValue<?> visitStatFuncCall(BagOfGrammarParser.StatFuncCallContext ctx) {
        visit(ctx.spellCall());
        return null;
    }

    // =====================================================================
    // 6. Variable declarations
    // =====================================================================

    /** 'var x: T = expr' — evaluates expr, casts it to the declared type T, then declares+initializes x in memory. */
    @Override
    public ExpValue<?> visitVarDeclInit(BagOfGrammarParser.VarDeclInitContext ctx) {
        ExpType declaredType = resolveType(ctx.type());
        ExpValue<?> value = TypeUtils.castValue(visitExpr(ctx.expr()), declaredType);
        mem.declareInit(ctx.ID().getText(), declaredType, value);
        return null;
    }

    /** 'var x: T' (no initializer) — declares x with type T, leaving it uninitialised in memory. */
    @Override
    public ExpValue<?> visitVarDeclDefault(BagOfGrammarParser.VarDeclDefaultContext ctx) {
        mem.declare(ctx.ID().getText(), resolveType(ctx.type()));
        return null;
    }

    // =====================================================================
    // 7. Assignments
    // =====================================================================

    /** 'x = expr' — evaluates expr and overwrites x's current value in memory. */
    @Override
    public ExpValue<?> visitAssignSimple(BagOfGrammarParser.AssignSimpleContext ctx) {
        mem.setValue(ctx.ID().getText(), visitExpr(ctx.expr()));
        return null;
    }

    /** 'obj.field = expr' — evaluates expr and overwrites the field on obj (obj must be a creature instance). */
    @Override
    public ExpValue<?> visitAssignField(BagOfGrammarParser.AssignFieldContext ctx) {
        ExpValue<?> val = mem.getValue(ctx.ID(0).getText());
        if (!(val instanceof ObjectValue obj))
            throw new RuntimeError("'" + ctx.ID(0).getText() + "' is not a creature instance", ctx.start.getLine());
        obj.setField(ctx.ID(1).getText(), visitExpr(ctx.expr()));
        return null;
    }

    // Compound assignments on plain variables (+= -= *= /= %=) – advanced feature: syntactic sugar.
    // All five forms evaluate the right-hand side, then delegate the actual arithmetic to applyCompound.
    @Override public ExpValue<?> visitAssignAdd(BagOfGrammarParser.AssignAddContext ctx) {
        return applyCompound(ctx.ID().getText(), visitExpr(ctx.expr()), "+");
    }
    @Override public ExpValue<?> visitAssignSub(BagOfGrammarParser.AssignSubContext ctx) {
        return applyCompound(ctx.ID().getText(), visitExpr(ctx.expr()), "-");
    }
    @Override public ExpValue<?> visitAssignMul(BagOfGrammarParser.AssignMulContext ctx) {
        return applyCompound(ctx.ID().getText(), visitExpr(ctx.expr()), "*");
    }
    @Override public ExpValue<?> visitAssignDiv(BagOfGrammarParser.AssignDivContext ctx) {
        return applyCompound(ctx.ID().getText(), visitExpr(ctx.expr()), "/");
    }
    @Override public ExpValue<?> visitAssignMod(BagOfGrammarParser.AssignModContext ctx) {
        return applyCompound(ctx.ID().getText(), visitExpr(ctx.expr()), "%");
    }

    /** Shared logic for 'x op= rhs' on a plain variable: reads x, applies the operator, writes the result back. */
    private ExpValue<?> applyCompound(String id, ExpValue<?> rhs, String op) { // rhs -> right hand side
        mem.setValue(id, applyArith(mem.getValue(id), rhs, op));
        return null;
    }

    // Compound assignments on object fields (obj.field += expr, etc.) – same idea as above, but on a field.
    @Override
    public ExpValue<?> visitAssignFieldAdd(BagOfGrammarParser.AssignFieldAddContext ctx) {
        return applyCompoundField(ctx.ID(0).getText(), ctx.ID(1).getText(), visitExpr(ctx.expr()), "+");
    }
    @Override
    public ExpValue<?> visitAssignFieldSub(BagOfGrammarParser.AssignFieldSubContext ctx) {
        return applyCompoundField(ctx.ID(0).getText(), ctx.ID(1).getText(), visitExpr(ctx.expr()), "-");
    }
    @Override
    public ExpValue<?> visitAssignFieldMul(BagOfGrammarParser.AssignFieldMulContext ctx) {
        return applyCompoundField(ctx.ID(0).getText(), ctx.ID(1).getText(), visitExpr(ctx.expr()), "*");
    }
    @Override
    public ExpValue<?> visitAssignFieldDiv(BagOfGrammarParser.AssignFieldDivContext ctx) {
        return applyCompoundField(ctx.ID(0).getText(), ctx.ID(1).getText(), visitExpr(ctx.expr()), "/");
    }
    @Override
    public ExpValue<?> visitAssignFieldMod(BagOfGrammarParser.AssignFieldModContext ctx) {
        return applyCompoundField(ctx.ID(0).getText(), ctx.ID(1).getText(), visitExpr(ctx.expr()), "%");
    }

    /** Shared logic for 'obj.field op= rhs': reads the field, applies the operator, writes the result back onto obj. */
    private ExpValue<?> applyCompoundField(String objName, String fieldName, ExpValue<?> rhs, String op) {
        ExpValue<?> val = mem.getValue(objName);
        if (!(val instanceof ObjectValue obj))
            throw new RuntimeError("'" + objName + "' is not a creature instance");
        ExpValue<?> current = obj.getField(fieldName);
        obj.setField(fieldName, applyArith(current, rhs, op));
        return null;
    }

    // =====================================================================
    // 8. Control flow — the branch + merge ('Mem') mechanism
    // =====================================================================
    // Conditionals and loop bodies are executed on a *branch* interpreter (newBranch(), see
    // section 14): a child interpreter with its own private copy of memory. Once the branch
    // finishes, its memory is merged back into the parent's (mem.mergeFrom(...)). This keeps
    // speculative/conditional execution from corrupting the parent's state until it is known
    // which path was actually taken.

    /**
     * 'if <cond> {...} [else if <cond> {...}]* [else {...}]'
     * Runs the matching branch (first true condition, or the trailing else block if none matched)
     * on a child interpreter, then merges its memory back into the parent's.
     * Note: unlike {@link #visitUntilStat} and {@link #visitForStat}, this method does not catch
     * {@link BreakException} — if 'break' is thrown inside the chosen block, it propagates out of
     * this method and the merge below is skipped.
     */
    @Override
    public ExpValue<?> visitIfStat(BagOfGrammarParser.IfStatContext ctx) {
        BagOfGrammarIntp branch = newBranch();
        List<BagOfGrammarParser.ExprContext> exprs = ctx.expr();
        List<BagOfGrammarParser.BlockContext> blocks = ctx.block();
        boolean matched = false;
        for (int i = 0; i < exprs.size(); i++) { // visits all if/else if blocks
            if (visitBoolExpr(exprs.get(i)).toJavaValue()) {
                branch.visit(blocks.get(i));
                matched = true;
                break; // breaks after executing true block
            }
        }
        if (!matched && blocks.size() > exprs.size()) // else
            branch.visit(blocks.get(blocks.size() - 1));
        mem.mergeFrom(branch.getMem());
        return null;
    }

    /**
     * 'until <cond> { ... }' (checked loop, the language's while-equivalent).
     * Recursive formulation: if the condition is already true, the loop is done (until's
     * condition is an exit condition, not an entry one). Otherwise the body runs once on a
     * fresh branch, the branch's memory is merged back, and the method calls itself to
     * re-check the condition. 'break' is caught here so the last iteration's changes are
     * still merged before returning.
     */
    @Override
    public ExpValue<?> visitUntilStat(BagOfGrammarParser.UntilStatContext ctx) {
        if (visitBoolExpr(ctx.expr()).toJavaValue()) // until exits when condition is true
            return null;

        BagOfGrammarIntp branch = newBranch();
        try {
            branch.visit(ctx.block());
            mem.mergeFrom(branch.getMem());
        } catch (BreakException e) {
            mem.mergeFrom(branch.getMem());
            return null;
        }

        return visitUntilStat(ctx);

    }

    /**
     * 'for id from <expr> to <expr> { ... } [else { ... }]'
     * The loop variable lives in its own scope (pushed once, popped at the end) and is updated
     * directly on the parent's memory before each iteration. Each iteration's body runs on a
     * fresh branch so a 'break' can discard only that iteration's in-progress changes before the
     * merge — note the merge still happens for the broken-out iteration via the catch below, only
     * subsequent iterations are skipped. The optional 'else' block runs (on the parent interpreter,
     * not a branch) only if the loop completed without 'break'.
     */
    @Override
    public ExpValue<?> visitForStat(BagOfGrammarParser.ForStatContext ctx) {
        int from = visitIntExpr(ctx.expr(0)).toJavaValue();
        int to = visitIntExpr(ctx.expr(1)).toJavaValue();
        String loopVar = ctx.ID().getText();

        mem.pushScope();
        mem.declareInit(loopVar, SimpleType.INT, new IntValue(from));

        boolean brokeOut = false;
        try {
            for (int i = from; i <= to; i++) {
                mem.setValue(loopVar, new IntValue(i));
                BagOfGrammarIntp branch = newBranch();
                try {
                    branch.visit(ctx.block(0));
                } catch (BreakException e) {
                    mem.mergeFrom(branch.getMem());
                    brokeOut = true;
                    break;
                }
                mem.mergeFrom(branch.getMem());
            }

            if (!brokeOut && ctx.block().size() > 1)
                visit(ctx.block(1)); // performs else block only if it has not broke out of loop

        } finally {
            mem.popScope();
        }
        return null;
    }

    /**
     * 'switch <expr> { case <expr>: stat* ... default: stat* }'
     * Once a case matches, execution falls through every subsequent case (and the default
     * clause) unless a 'break' is hit — there is no per-case branch/merge here, since case
     * bodies run directly on the current interpreter (they share the surrounding scope, like
     * in the type-checker). 'break' is caught around the whole clause loop to stop fallthrough.
     */
    @Override
    public ExpValue<?> visitSwitchStat(BagOfGrammarParser.SwitchStatContext ctx) {
        ExpValue<?> switchVal = visitExpr(ctx.expr());
        boolean matched = false;

        try {
            for (BagOfGrammarParser.CaseClauseContext cc : ctx.caseClause()) {
                if (!matched && valuesEqual(switchVal, visitExpr(cc.expr()))) matched = true;
                if (matched)
                    for (BagOfGrammarParser.StatContext s : cc.stat())
                        visit(s);
            }

            if (!matched && ctx.defaultClause() != null)
                for (BagOfGrammarParser.StatContext s : ctx.defaultClause().stat())
                    visit(s);

        } catch (BreakException e) {
            return null;
        }
        return null;
    }

    // =====================================================================
    // 9. Function & method calls
    // =====================================================================
    // (advanced features: Funzioni / Classi e oggetti)

    /** Resolves a spell call by name, evaluates its arguments, and runs the function (return-value or void form). */
    @Override
    public ExpValue<?> visitSpellCall(BagOfGrammarParser.SpellCallContext ctx) {
        String name = ctx.ID().getText();
        BagOfGrammarParser.SpellDeclContext decl = spells.get(name);
        if (decl == null)
            throw new RuntimeError("Unknown spell: " + name + "'", ctx.start.getLine());

        List<ExpValue<?>> args = evalArgs(ctx.argList());

        if (decl instanceof BagOfGrammarParser.FuncDeclReturnContext fr)
            return callFunction(fr.paramList(), fr.block(), args);
        else {
            BagOfGrammarParser.FuncDeclVoidContext fv = (BagOfGrammarParser.FuncDeclVoidContext) decl;
            callFunction(fv.paramList(), fv.block(), args);
            return null;
        }
    }

    /**
     * Runs a function/method call frame: pushes a fresh scope, binds parameters by value
     * (see {@link #bindParams}), executes the body, and catches {@link ReturnException} to
     * recover the returned value (null for void calls or a bare 'return'). The scope is
     * always popped, even if the body throws.
     */
    private ExpValue<?> callFunction(BagOfGrammarParser.ParamListContext params,
                                     BagOfGrammarParser.BlockContext body, List<ExpValue<?>> args) {
        mem.pushScope();
        bindParams(params, args);
        ExpValue<?> result = null;
        try {
            executeBody(body);
        } catch (ReturnException e) {
            result = e.value;
        } finally {
            mem.popScope();
        }
        return result;
    }

    /**
     * Runs a method call frame on a creature instance: resolves the method on the receiver's
     * creature, binds 'self' to the receiver plus the call's parameters, then executes the
     * method body the same way {@link #callFunction} does (return-value capture, guaranteed pop).
     */
    private ExpValue<?> callMethod(ObjectValue receiver, BagOfGrammarParser.SpellCallContext callCtx) {
        String className  = receiver.getClassName();
        String methodName = callCtx.ID().getText();
        CreatureDescriptor desc = creatures.get(className);
        if (desc == null || !desc.methods.containsKey(methodName))
            throw new RuntimeError("Unknown method '" + methodName + "' on " + className + "'", callCtx.start.getLine());

        BagOfGrammarParser.CreatureMemberContext m = desc.methods.get(methodName);
        List<ExpValue<?>> args = evalArgs(callCtx.argList());

        mem.pushScope();
        mem.declareInit("self", new ObjectType(className), receiver);
        ExpValue<?> result = null;
        try {
            if (m instanceof BagOfGrammarParser.ClassMethodReturnContext mr) {
                bindParams(mr.paramList(), args);
                result = executeBody(mr.block());
            } else {
                BagOfGrammarParser.ClassMethodVoidContext mv = (BagOfGrammarParser.ClassMethodVoidContext) m;
                bindParams(mv.paramList(), args);
                executeBody(mv.block());
            }
        } catch (ReturnException e) {
            result = e.value;
        } finally {
            mem.popScope();
        }
        return result;
    }

    /** Runs every statement of a function/method body directly (no extra scope: the caller already pushed one). */
    private ExpValue<?> executeBody(BagOfGrammarParser.BlockContext body) {
        for (BagOfGrammarParser.StatContext s : body.stat())
            visit(s);
        return null;
    }

    /** Declares and initializes each parameter, in order, with the corresponding already-evaluated argument (pass-by-value). */
    private void bindParams(BagOfGrammarParser.ParamListContext params, List<ExpValue<?>> args) {
        if (params == null) return;
        List<BagOfGrammarParser.ParamContext> ps = params.param();
        for (int i = 0; i < ps.size(); i++)
            mem.declareInit(ps.get(i).ID().getText(), resolveType(ps.get(i).type()), args.get(i));
    }

    /** Evaluates every argument expression, left to right, before the call frame is set up (empty list if no args). */
    private List<ExpValue<?>> evalArgs(BagOfGrammarParser.ArgListContext ctx) {
        List<ExpValue<?>> result = new ArrayList<>();
        if (ctx != null)
            for (BagOfGrammarParser.ExprContext e : ctx.expr())
                result.add(visitExpr(e));
        return result;
    }

    // =====================================================================
    // 10. Expressions — literals & primary
    // =====================================================================

    @Override public ExpValue<?> visitExprParen(BagOfGrammarParser.ExprParenContext ctx) {
        return visitExpr(ctx.expr());
    }

    @Override
    public ExpValue<?> visitExprInt(BagOfGrammarParser.ExprIntContext ctx) {
        return new IntValue(Integer.parseInt(ctx.INT_LIT().getText()));
    }

    @Override
    public ExpValue<?> visitExprFloat(BagOfGrammarParser.ExprFloatContext ctx) {
        return new DecValue(Double.parseDouble(ctx.FLOAT_LIT().getText()));
    }

    @Override
    public ExpValue<?> visitExprBool(BagOfGrammarParser.ExprBoolContext ctx) {
        return new BoolValue(Boolean.parseBoolean(ctx.BOOL_LIT().getText()));
    }

    /** String literal — strips the surrounding quotes and unescapes Java-style escape sequences. */
    @Override
    public ExpValue<?> visitExprString(BagOfGrammarParser.ExprStringContext ctx) {
        String raw = ctx.STRING_LIT().getText();
        return new StringValue(StringEscapeUtils.unescapeJava(raw.substring(1, raw.length() - 1)));
    }

    /**
     * Interpolated string literal (i"... ${expr} ..."). Strips the i"/" delimiters, unescapes
     * basic escape sequences by hand, then finds every ${...} placeholder and evaluates its
     * inner expression text by re-lexing/re-parsing it from scratch as a standalone 'expr' rule
     * (so interpolation reuses the full grammar/expression evaluation, including field access).
     */
    @Override
    public ExpValue<?> visitExprInterpString(BagOfGrammarParser.ExprInterpStringContext ctx) {
        String body = ctx.INTERP_STRING().getText();
        body = body.substring(2, body.length() - 1); // strip i" and "
        body = body.replace("\\n", "\n").replace("\\t", "\t")
                .replace("\\r", "\r").replace("\\\"", "\"")
                .replace("\\\\", "\\");
        StringBuffer sb = new StringBuffer();
        Matcher m = Pattern.compile("\\$\\{([^}]+)}").matcher(body);
        while (m.find()) {
            String exprText = m.group(1).trim();
            BagOfGrammarLexer lexer = new BagOfGrammarLexer(CharStreams.fromString(exprText));
            BagOfGrammarParser parser = new BagOfGrammarParser(new CommonTokenStream(lexer));
            ExpValue<?> val = visitExpr(parser.expr());
            m.appendReplacement(sb, Matcher.quoteReplacement(val.toString()));
        }
        m.appendTail(sb);
        return new StringValue(sb.toString());
    }

    /** Die literal (e.g. d6, d20) used on its own (not rolled) — kept as its raw text, typed as a String/Die value. */
    @Override
    public ExpValue<?> visitExprDie(BagOfGrammarParser.ExprDieContext ctx) { // also manages roll function
        return new StringValue(ctx.getText());
    }

    /**
     * 'roll [n] <die>' — rolls the die's faces 'n' times (default 1) and sums the results.
     * 'd%' (percentile die) is special-cased as n rolls of 1d10 scaled to tens (10..100);
     * any other 'dN' rolls n times in [1, N].
     */
    @Override
    public ExpValue<?> visitExprRoll(BagOfGrammarParser.ExprRollContext ctx) {
        String text = ctx.DIE_LIT().getText(); // ex. "d6", "d20"
        int numDice = ctx.INT_LIT() != null ? Integer.parseInt(ctx.INT_LIT().getText()) : 1;
        int total = 0;
        if (text.substring(1).equals("%")) { // d%
            for (int i = 0; i < numDice; i++) {
                total += (rng.nextInt(10) + 1) * 10; // [10 - 100]
            }
        } else {
            int sides = Integer.parseInt(text.substring(1));
            for (int i = 0; i < numDice; i++) {
                total += rng.nextInt(sides) + 1;
            }
        }
        return new IntValue(total);
    }

    /** Variable reference — fails loudly if the name was never declared, or was declared but never given a value. */
    @Override
    public ExpValue<?> visitExprId(BagOfGrammarParser.ExprIdContext ctx) {
        String id = ctx.ID().getText();
        if (!mem.isDeclared(id))
            throw new RuntimeError("Undefined variable: '" + id + "'", ctx.start.getLine());
        ExpValue<?> val = mem.getValue(id);
        if (val == null)
            throw new RuntimeError("Uninitialised variable: '" + id + "'", ctx.start.getLine());
        return val;
    }

    /** 'new Creature(...)' — allocates a fresh object and initializes every declared field to its type's default value. */
    @Override
    public ExpValue<?> visitExprNew(BagOfGrammarParser.ExprNewContext ctx) {
        String className = ctx.ID().getText();
        CreatureDescriptor desc = creatures.get(className);
        if (desc == null) throw new RuntimeError("Unknown creature: " + className + "'", ctx.start.getLine());
        ObjectValue obj = new ObjectValue(className);
        for (Map.Entry<String, ExpType> e : desc.fields.entrySet())
            obj.setField(e.getKey(), defaultValue(e.getValue()));
        return obj;
    }

    @Override public ExpValue<?> visitExprFuncCall(BagOfGrammarParser.ExprFuncCallContext ctx) {
        return visit(ctx.spellCall());
    }

    /** 'obj.method(...)' used as an expression — the receiver must evaluate to a creature instance. */
    @Override public ExpValue<?> visitExprMethodCall(BagOfGrammarParser.ExprMethodCallContext ctx) {
        ExpValue<?> val = visitExpr(ctx.expr());
        if (!(val instanceof ObjectValue obj))
            throw new RuntimeError("Cannot call a spell on a non-creature value", ctx.start.getLine());
        return callMethod(obj, ctx.spellCall());
    }

    /** 'obj.field' — the receiver must evaluate to a creature instance; returns the field's current value. */
    @Override public ExpValue<?> visitExprFieldAccess(BagOfGrammarParser.ExprFieldAccessContext ctx) {
        ExpValue<?> val = visitExpr(ctx.expr());
        if (!(val instanceof ObjectValue obj))
            throw new RuntimeError("Cannot access field '" + ctx.ID().getText() + "' on a non-creature value (inside string interpolation)", ctx.start.getLine());
        return obj.getField(ctx.ID().getText());
    }

    // =====================================================================
    // 11. Expressions — arithmetic
    // =====================================================================

    /** '+' / '-' — '+' between two Strings concatenates them; otherwise both sides are evaluated and combined via {@link #applyArith}. */
    @Override
    public ExpValue<?> visitExprAddSub(BagOfGrammarParser.ExprAddSubContext ctx) {
        ExpValue<?> left  = visitExpr(ctx.expr(0));
        ExpValue<?> right = visitExpr(ctx.expr(1));
        if (left instanceof StringValue l && right instanceof StringValue r && ctx.op.getText().equals("+"))
            return new StringValue(l.toJavaValue() + r.toJavaValue());
        return applyArith(left, right, ctx.op.getText());
    }

    /** '*' / '/' / '%' — guards against division/modulo by zero before delegating to {@link #applyArith}. */
    @Override
    public ExpValue<?> visitExprMulDivMod(BagOfGrammarParser.ExprMulDivModContext ctx) {
        ExpValue<?> left = visit(ctx.expr(0));
        ExpValue<?> right = visit(ctx.expr(1));
        String op = ctx.op.getText();

        if ("/".equals(op) || "%".equals(op)) {
            Object javaValue = right.toJavaValue();

            if (javaValue instanceof Number && ((Number) javaValue).doubleValue() == 0.0) {
                throw new RuntimeError("Can't divide by 0!", ctx.start.getLine());
            }
        }
        return applyArith(left, right, op);
    }

    /** Unary '-' — negates an Int or Dec value, preserving its runtime type. */
    @Override
    public ExpValue<?> visitExprNeg(BagOfGrammarParser.ExprNegContext ctx) {
        ExpValue<?> v = visitExpr(ctx.expr());
        return v instanceof IntValue i ? new IntValue(-i.toJavaValue()) : new DecValue(-((DecValue) v).toJavaValue());
    }

    /**
     * Shared arithmetic core for + - * / % : if both operands are Int, computes in integer
     * arithmetic (so e.g. division truncates); otherwise widens both sides to double and
     * computes in floating point. Division/modulo-by-zero on the integer path is *not* guarded
     * here (that check lives in {@link #visitExprMulDivMod} for '/' and '%' specifically).
     */
    private ExpValue<?> applyArith(ExpValue<?> left, ExpValue<?> right, String op) {
        boolean bothInt = (left instanceof IntValue) && (right instanceof IntValue);
        if (bothInt) {
            int l = ((IntValue) left).toJavaValue();
            int r = ((IntValue) right).toJavaValue();
            int result = switch (op) {
                case "+" -> l + r;
                case "-" -> l - r;
                case "*" -> l * r;
                case "/" -> l / r;
                case "%" -> l % r;
                default  -> throw new RuntimeError("Unknown operator: '" + op + "'");
            };
            return new IntValue(result);
        }
        double l = toDouble(left), r = toDouble(right);
        double result = switch (op) {
            case "+" -> l + r;
            case "-" -> l - r;
            case "*" -> l * r;
            case "/" -> l / r;
            case "%" -> l % r;
            default  -> throw new RuntimeError("Unknown operator: '" + op + "'");
        };
        return new DecValue(result);
    }

    // =====================================================================
    // 12. Expressions — logical, relational, equality, ternary, cast
    // =====================================================================

    /** 'and' — short-circuits: if the left operand is false, returns false without evaluating the right operand. */
    @Override
    public ExpValue<?> visitExprLogicalAnd(BagOfGrammarParser.ExprLogicalAndContext ctx) {
        boolean left = visitBoolExpr(ctx.expr(0)).toJavaValue(); // if left is false AND is false
        if (!left) return new BoolValue(false);

        return visitExpr(ctx.expr(1));
    }

    /** 'or' — short-circuits: if the left operand is true, returns true without evaluating the right operand. */
    @Override
    public ExpValue<?> visitExprLogicalOr(BagOfGrammarParser.ExprLogicalOrContext ctx) {
        boolean left = visitBoolExpr(ctx.expr(0)).toJavaValue();
        if (left) return new BoolValue(true); // if left is true OR is true

        return visitExpr(ctx.expr(1));
    }

    /** 'not' — logical negation of a Bool operand. */
    @Override
    public ExpValue<?> visitExprNot(BagOfGrammarParser.ExprNotContext ctx) {
        return new BoolValue(!visitBoolExpr(ctx.expr()).toJavaValue());
    }

    /** Relational operators (lt/gt/lte/gte) — both operands are widened to double and compared numerically. */
    @Override
    public ExpValue<?> visitExprRelational(BagOfGrammarParser.ExprRelationalContext ctx) {
        double left  = toDouble(visitExpr(ctx.expr(0)));
        double right = toDouble(visitExpr(ctx.expr(1)));
        return new BoolValue(switch (ctx.op.getText()) {
            case "lt"  -> left <  right;
            case "gt"  -> left >  right;
            case "lte" -> left <= right;
            case "gte" -> left >= right;
            default    -> throw new RuntimeError("Unknown relational op: " + ctx.op.getText() + "'", ctx.start.getLine());
        });
    }

    /** Equality operators (eq/neq) — delegates value comparison to {@link #valuesEqual}, then negates for 'neq'. */
    @Override
    public ExpValue<?> visitExprEquality(BagOfGrammarParser.ExprEqualityContext ctx) {
        boolean eq = valuesEqual(visitExpr(ctx.expr(0)), visitExpr(ctx.expr(1)));
        return new BoolValue(ctx.op.getText().equals("eq") ? eq : !eq);
    }

    /** Ternary 'cond ? then : else' (advanced feature: syntactic sugar) — evaluates only the selected branch. */
    @Override
    public ExpValue<?> visitExprTernary(BagOfGrammarParser.ExprTernaryContext ctx) {
        return visitBoolExpr(ctx.expr(0)).toJavaValue()
                ? visitExpr(ctx.expr(1)) : visitExpr(ctx.expr(2));
    }

    /** Explicit cast 'expr as T' (advanced feature: type conversion) — delegates the actual conversion to {@link TypeUtils#castValue}. */
    @Override
    public ExpValue<?> visitExprCast(BagOfGrammarParser.ExprCastContext ctx) {
        return TypeUtils.castValue(visitExpr(ctx.expr()), resolveType(ctx.type()));
    }

    // =====================================================================
    // 13. Expressions — increment / decrement
    // =====================================================================

    // Pre/post increment/decrement on a plain variable (++x, x++, --x, x--).
    @Override public ExpValue<?> visitExprPreInc(BagOfGrammarParser.ExprPreIncContext ctx) {
        return incDecExpr(ctx.ID().getText(), 1, true);
    }
    @Override public ExpValue<?> visitExprPreDec(BagOfGrammarParser.ExprPreDecContext ctx) {
        return incDecExpr(ctx.ID().getText(), -1, true);
    }
    @Override public ExpValue<?> visitExprPostInc(BagOfGrammarParser.ExprPostIncContext ctx) {
        return incDecExpr(ctx.ID().getText(), 1, false);
    }
    @Override public ExpValue<?> visitExprPostDec(BagOfGrammarParser.ExprPostDecContext ctx) {
        return incDecExpr(ctx.ID().getText(), -1, false);
    }

    /**
     * Shared logic for variable inc/dec: reads the current value, applies the delta (Int or
     * Dec, matching the variable's current runtime type), writes it back, then returns either
     * the updated value (pre-) or the old value (post-), per the usual pre/post semantics.
     */
    private ExpValue<?> incDecExpr(String id, int delta, boolean pre) {
        ExpValue<?> old = mem.getValue(id);
        ExpValue<?> updated = old instanceof IntValue i
                ? new IntValue(i.toJavaValue() + delta)
                : new DecValue(((DecValue) old).toJavaValue() + delta);
        mem.setValue(id, updated);
        return pre ? updated : old;
    }

    // Pre/post increment/decrement on an object field (++obj.field, obj.field++, --obj.field, obj.field--).
    @Override public ExpValue<?> visitExprPreIncField(BagOfGrammarParser.ExprPreIncFieldContext ctx) {
        return incDecField(ctx.ID(0).getText(), ctx.ID(1).getText(), 1, true);
    }
    @Override public ExpValue<?> visitExprPreDecField(BagOfGrammarParser.ExprPreDecFieldContext ctx) {
        return incDecField(ctx.ID(0).getText(), ctx.ID(1).getText(), -1, true);
    }
    @Override public ExpValue<?> visitExprPostIncField(BagOfGrammarParser.ExprPostIncFieldContext ctx) {
        return incDecField(ctx.ID(0).getText(), ctx.ID(1).getText(), 1, false);
    }
    @Override public ExpValue<?> visitExprPostDecField(BagOfGrammarParser.ExprPostDecFieldContext ctx) {
        return incDecField(ctx.ID(0).getText(), ctx.ID(1).getText(), -1, false);
    }

    /** Shared logic for field inc/dec: same update/return scheme as {@link #incDecExpr}, but reading/writing through the object's field. */
    private ExpValue<?> incDecField(String objName, String fieldName, int delta, boolean pre) {
        ExpValue<?> val = mem.getValue(objName);
        if (!(val instanceof ObjectValue obj))
            throw new RuntimeError("'" + objName + "' is not a creature instance");
        ExpValue<?> old = obj.getField(fieldName);
        ExpValue<?> updated = old instanceof IntValue i
                ? new IntValue(i.toJavaValue() + delta)
                : new DecValue(((DecValue) old).toJavaValue() + delta);
        obj.setField(fieldName, updated);
        return pre ? updated : old;
    }

    // =====================================================================
    // 14. Type resolution & misc utilities
    // =====================================================================

    /** Resolves a 'type' parse-tree node into its runtime {@link ExpType}, by re-parsing its source text. */
    private ExpType resolveType(BagOfGrammarParser.TypeContext ctx) {
        return TypeUtils.fromString(ctx.getText());
    }

    /**
     * Creates a child interpreter for the branch + merge pattern (see section 8): it gets its
     * own private copy of the current memory ({@link Mem#copyOf}), while sharing this
     * interpreter's creature and spell registries (by reference — they are read-only after
     * program registration, so sharing them is safe).
     */
    private BagOfGrammarIntp newBranch() {
        BagOfGrammarIntp branch = new BagOfGrammarIntp(Mem.copyOf(this.mem));
        branch.creatures.putAll(this.creatures);
        branch.spells.putAll(this.spells);
        return branch;
    }

    /**
     * Runtime equality used by '==' / '!=' and by 'switch'/'case' matching: numeric values
     * (Int/Dec) compare by numeric value (so 1 == 1.0), Bool/String compare by their Java value,
     * and anything else (e.g. ObjectValue) falls back to reference equality.
     */
    private boolean valuesEqual(ExpValue<?> a, ExpValue<?> b) {
        if ((a instanceof IntValue || a instanceof DecValue) &&
                (b instanceof IntValue || b instanceof DecValue))
            return toDouble(a) == toDouble(b);
        if (a instanceof BoolValue ba && b instanceof BoolValue bb) return ba.toJavaValue().equals(bb.toJavaValue());
        if (a instanceof StringValue sa && b instanceof StringValue sb) return sa.toJavaValue().equals(sb.toJavaValue());
        return a == b;
    }

    /** The language's default value for a given type, used to initialize creature fields on 'new'. */
    private ExpValue<?> defaultValue(ExpType type) {
        if (type == SimpleType.INT || type == SimpleType.HP ||
                type == SimpleType.DAMAGE || type == SimpleType.LEVEL) return new IntValue(0);
        if (type == SimpleType.FLOAT) return new DecValue(0.0);
        if (type == SimpleType.BOOL) return new BoolValue(false);
        if (type == SimpleType.STRING || type == SimpleType.QUESTNAME || type == SimpleType.DIE)
            return new StringValue("");
        return null;
    }

    // =====================================================================
    // 15. User input
    // =====================================================================

    /**
     * 'declare <type> [, promptExpr]' — prints the optional prompt (no trailing newline, then
     * flushes, so the prompt appears right before the user types), reads one line from stdin,
     * and parses it according to the target type:
     *   - Int/HP/Damage/Level -> Integer.parseInt (RuntimeError on failure)
     *   - Float -> Double.parseDouble (RuntimeError on failure)
     *   - Bool -> Boolean.parseBoolean (never throws; anything not "true" parses as false)
     *   - Any -> tries Int, then Float, then Bool, falling back to String
     *   - anything else (String/QuestName/Die) -> taken as-is, as a String
     */
    @Override
    public ExpValue<?> visitExprDeclare(BagOfGrammarParser.ExprDeclareContext ctx) {
        String prompt = "";

        if (ctx.expr() != null)
            prompt = visitStringExpr(ctx.expr()).toJavaValue();

        System.out.print(prompt); // use print not println!
        System.out.flush(); // empties output buffer

        java.util.Scanner scanner = new java.util.Scanner(System.in);
        String input = scanner.nextLine().trim();

        ExpType targetType = resolveType(ctx.type());
        if (targetType == SimpleType.INT || targetType == SimpleType.HP
                || targetType == SimpleType.DAMAGE || targetType == SimpleType.LEVEL) {
            try {
                return new IntValue(Integer.parseInt(input));
            } catch (NumberFormatException e) {
                throw new RuntimeError("Expected an integer value, got: '" + input + "'", ctx.start.getLine());
            }
        } else if (targetType == SimpleType.FLOAT) {
            try {
                return new DecValue(Double.parseDouble(input));
            } catch (NumberFormatException e) {
                throw new RuntimeError("Expected a decimal value, got: '" + input + "'", ctx.start.getLine());
            }
        } else if (targetType == SimpleType.BOOL) {
            return new BoolValue(Boolean.parseBoolean(input));
        } else if (targetType == SimpleType.ANY) {
            // Try to parse as the most specific type possible
            try {
                return new IntValue(Integer.parseInt(input));
            } catch (NumberFormatException ignored) {}
            try {
                return new DecValue(Double.parseDouble(input));
            } catch (NumberFormatException ignored) {}
            if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("false"))
                return new BoolValue(Boolean.parseBoolean(input));
            return new StringValue(input); // fallback
        } else {
            return new StringValue(input); // String, QuestName, Die
        }
    }
}