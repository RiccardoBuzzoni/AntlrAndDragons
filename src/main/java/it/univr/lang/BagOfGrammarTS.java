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
 */

public class BagOfGrammarTS extends BagOfGrammarBaseVisitor<Type>{

    // Environment
    private static class Scope {
        final Map<String, Type> vars = new LinkedHashMap<>();
    }

    private final Deque<Scope> scopes = new ArrayDeque<>();
    private final Map<String, Map<String, Type>> creatureFields = new LinkedHashMap<>();
    private final Map<String, Map<String, Type>> creatureMethods = new LinkedHashMap<>();
    private final Map<String, FuncType> spells = new LinkedHashMap<>();
    private Type currentReturnType = null; // expected return type
    private int loopDepth = 0; // depth of loop/switch nesting (for 'break')
    private int errorCount = 0;

    // Error reporting
    private void error(org.antlr.v4.runtime.ParserRuleContext ctx, String msg) {
        int line = ctx.getStart().getLine();
        int col  = ctx.getStart().getCharPositionInLine();
        System.err.printf("[TypeCheck] line %d:%d – %s%n", line, col, msg);
        errorCount++;
    }

    public int getErrorCount() { return errorCount; }

    // Scoping helpers
    private void pushScope() { scopes.push(new Scope()); }
    private void popScope()  { scopes.pop(); }

    private void declare(String name, Type type) {
        assert scopes.peek() != null;
        scopes.peek().vars.put(name, type);
    }

    private Type lookup(String name, ParserRuleContext ctx) {
        for (Scope s : scopes)
            if (s.vars.containsKey(name)) {
                return s.vars.get(name);
            }
        error(ctx, "Undeclared variable: " + name);
        return ErrType.INSTANCE;
    }

    // Type compatibility
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

    private boolean isNumeric(Type t) {
        if(t instanceof ErrType || t == SimpleType.ANY) return true;
        return t == SimpleType.INT || t == SimpleType.FLOAT ||
                t == SimpleType.HP || t == SimpleType.DAMAGE || t == SimpleType.LEVEL;
    }

    private boolean isBool(Type t) {
        return t instanceof ErrType || t == SimpleType.BOOL || t == SimpleType.ANY;
    }

    private Type numericJoin(Type a, Type b) {
        if (a instanceof ErrType || b instanceof ErrType) return ErrType.INSTANCE;
        if (a.equals(b)) return a;
        if (a == SimpleType.FLOAT || b == SimpleType.FLOAT) return SimpleType.FLOAT;
        return SimpleType.INT;
    }

    // Program
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

    private List<Type> collectParamTypes(BagOfGrammarParser.ParamListContext ctx) {
        List<Type> result = new ArrayList<>();
        if (ctx != null)
            for (BagOfGrammarParser.ParamContext p : ctx.param())
                result.add(visitType(p.type()));
        return result;
    }

    // Creatures (classes)
    @Override
    public Type visitCreatureSection(BagOfGrammarParser.CreatureSectionContext ctx) {
        for (BagOfGrammarParser.CreatureDeclContext cd : ctx.creatureDecl())
            visit(cd);
        return null;
    }

    @Override
    public Type visitCreatureDecl(BagOfGrammarParser.CreatureDeclContext ctx) {
        for (BagOfGrammarParser.CreatureMemberContext m : ctx.creatureMember())
            visitCreatureMember(m);
        return null;
    }

    private void visitCreatureMember(BagOfGrammarParser.CreatureMemberContext m) {
        if (m instanceof BagOfGrammarParser.ClassMethodReturnContext) {
            BagOfGrammarParser.ClassMethodReturnContext mr = (BagOfGrammarParser.ClassMethodReturnContext) m;
            checkFunctionBody(mr.paramList(), mr.block(), visitType(mr.type()), mr);
        } else if (m instanceof BagOfGrammarParser.ClassMethodVoidContext) {
            BagOfGrammarParser.ClassMethodVoidContext mv = (BagOfGrammarParser.ClassMethodVoidContext) m;
            checkFunctionBody(mv.paramList(), mv.block(), VoidType.INSTANCE, mv);
        }
    }

    @Override public Type visitClassField(BagOfGrammarParser.ClassFieldContext ctx) { return null; }
    @Override public Type visitClassMethodReturn(BagOfGrammarParser.ClassMethodReturnContext ctx) { return null; }
    @Override public Type visitClassMethodVoid(BagOfGrammarParser.ClassMethodVoidContext ctx) { return null; }

    // Spellbook (functions)
    @Override
    public Type visitSpellbookSection(BagOfGrammarParser.SpellbookSectionContext ctx) {
        for (BagOfGrammarParser.SpellDeclContext sd : ctx.spellDecl())
            visit(sd);
        return null;
    }

    @Override
    public Type visitFuncDeclReturn(BagOfGrammarParser.FuncDeclReturnContext ctx) {
        checkFunctionBody(ctx.paramList(), ctx.block(), visitType(ctx.type()), ctx);
        return null;
    }

    @Override
    public Type visitFuncDeclVoid(BagOfGrammarParser.FuncDeclVoidContext ctx) {
        checkFunctionBody(ctx.paramList(), ctx.block(), VoidType.INSTANCE, ctx);
        return null;
    }

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

    // Quest block (main)
    @Override
    public Type visitQuestBlock(BagOfGrammarParser.QuestBlockContext ctx) {
        visit(ctx.block());
        return null;
    }

    // Block
    @Override
    public Type visitBlock(BagOfGrammarParser.BlockContext ctx) {
        pushScope();
        for (BagOfGrammarParser.StatContext s : ctx.stat())
            visit(s);
        popScope();
        return null;
    }

    // Statements
    @Override public Type visitStatVarDecl(BagOfGrammarParser.StatVarDeclContext ctx) { visit(ctx.varDecl()); return null; }
    @Override public Type visitStatAssign(BagOfGrammarParser.StatAssignContext ctx) { visit(ctx.assign()); return null; }
    @Override public Type visitStatIf(BagOfGrammarParser.StatIfContext ctx) { visit(ctx.ifStat()); return null; }
    @Override public Type visitStatWhile(BagOfGrammarParser.StatWhileContext ctx) { visit(ctx.untilStat()); return null; }
    @Override public Type visitStatFor(BagOfGrammarParser.StatForContext ctx) { visit(ctx.forStat()); return null; }
    @Override public Type visitStatSwitch(BagOfGrammarParser.StatSwitchContext ctx) { visit(ctx.switchStat()); return null; }
    @Override public Type visitStatBlock(BagOfGrammarParser.StatBlockContext ctx) { visit(ctx.block()); return null; }
    @Override public Type visitStatExpr(BagOfGrammarParser.StatExprContext ctx) {visit(ctx.expr());return null;}

    @Override
    public Type visitStatPrint(BagOfGrammarParser.StatPrintContext ctx) {
        visit(ctx.expr()); // any type is printable
        return null;
    }

    @Override
    public Type visitStatReturn(BagOfGrammarParser.StatReturnContext ctx) {
        Type exprType = visit(ctx.expr());
        if (currentReturnType == null)
            error(ctx, "'return' used outside of a function.");
        else if (!isAssignable(exprType, currentReturnType))
            error(ctx, "Return type mismatch: expected " + currentReturnType + ", got " + exprType);
        return null;
    }

    @Override
    public Type visitStatReturnVoid(BagOfGrammarParser.StatReturnVoidContext ctx) {
        if (currentReturnType == null)
            error(ctx, "'return' used outside of a function.");
        else if (!currentReturnType.equals(VoidType.INSTANCE))
            error(ctx, "Void return in non-void function (expected " + currentReturnType + ").");
        return null;
    }

    @Override
    public Type visitStatBreak(BagOfGrammarParser.StatBreakContext ctx) {
        if (loopDepth == 0)
            error(ctx, "'break' used outside of a loop or switch.");
        return null;
    }

    @Override
    public Type visitStatExit(BagOfGrammarParser.StatExitContext ctx) {
        return null; // 'flee' is always valid
    }

    @Override
    public Type visitStatFuncCall(BagOfGrammarParser.StatFuncCallContext ctx) {
        visit(ctx.spellCall());
        return null;
    }

    // Variable declaration
    @Override
    public Type visitVarDeclInit(BagOfGrammarParser.VarDeclInitContext ctx) {
        Type declared = visitType(ctx.type());
        Type actual   = visit(ctx.expr());
        if (!isAssignable(actual, declared))
            error(ctx, "Cannot assign " + actual + " to variable of type " + declared);
        declare(ctx.ID().getText(), declared);
        return null;
    }

    @Override
    public Type visitVarDeclDefault(BagOfGrammarParser.VarDeclDefaultContext ctx) {
        declare(ctx.ID().getText(), visitType(ctx.type()));
        return null;
    }

    // Assignments
    @Override
    public Type visitAssignSimple(BagOfGrammarParser.AssignSimpleContext ctx) {
        Type varType  = lookup(ctx.ID().getText(), ctx);
        Type exprType = visit(ctx.expr());
        if (!isAssignable(exprType, varType))
            error(ctx, "Cannot assign " + exprType + " to '" + ctx.ID().getText() + "' (type " + varType + ")");
        return null;
    }

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

    // Compound assignments (+= -= *= /= %=) – advanced feature: Zucchero sintattico
    @Override public Type visitAssignAdd(BagOfGrammarParser.AssignAddContext ctx) { return checkCompound(ctx, ctx.ID().getText(), ctx.expr()); }
    @Override public Type visitAssignSub(BagOfGrammarParser.AssignSubContext ctx) { return checkCompound(ctx, ctx.ID().getText(), ctx.expr()); }
    @Override public Type visitAssignMul(BagOfGrammarParser.AssignMulContext ctx) { return checkCompound(ctx, ctx.ID().getText(), ctx.expr()); }
    @Override public Type visitAssignDiv(BagOfGrammarParser.AssignDivContext ctx) { return checkCompound(ctx, ctx.ID().getText(), ctx.expr()); }
    @Override public Type visitAssignMod(BagOfGrammarParser.AssignModContext ctx) { return checkCompound(ctx, ctx.ID().getText(), ctx.expr()); }

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

    // Control flow
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

    // Functions (advanced feature: Funzioni) and method calls (advanced feature: Classi e oggetti)

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

    // Expressions
    @Override public Type visitExprParen(BagOfGrammarParser.ExprParenContext ctx) { return visit(ctx.expr()); }
    @Override public Type visitExprInt(BagOfGrammarParser.ExprIntContext ctx) { return SimpleType.INT; }
    @Override public Type visitExprFloat(BagOfGrammarParser.ExprFloatContext ctx) { return SimpleType.FLOAT; }
    @Override public Type visitExprBool(BagOfGrammarParser.ExprBoolContext ctx) { return SimpleType.BOOL; }
    @Override public Type visitExprString(BagOfGrammarParser.ExprStringContext ctx) { return SimpleType.STRING; }
    @Override public Type visitExprInterpString(BagOfGrammarParser.ExprInterpStringContext ctx) { return SimpleType.STRING; }

    @Override
    public Type visitExprDie(BagOfGrammarParser.ExprDieContext ctx) {
        return SimpleType.DIE;
    }

    @Override
    public Type visitExprRoll(BagOfGrammarParser.ExprRollContext ctx) {
        return SimpleType.INT;
    }

    @Override
    public Type visitExprId(BagOfGrammarParser.ExprIdContext ctx) {
        return lookup(ctx.ID().getText(), ctx);
    }

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

    @Override
    public Type visitExprFieldAccess(BagOfGrammarParser.ExprFieldAccessContext ctx) {
        Type objType = visit(ctx.expr());
        return resolveField(objType, ctx.ID().getText(), ctx);
    }

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

    // Arithmetic
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

    @Override
    public Type visitExprNeg(BagOfGrammarParser.ExprNegContext ctx) {
        Type t = visit(ctx.expr());
        if (!isNumeric(t))
            error(ctx, "Unary '-' requires numeric operand, got " + t);
        return t;
    }

    // Logical
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

    @Override
    public Type visitExprNot(BagOfGrammarParser.ExprNotContext ctx) {
        Type t = visit(ctx.expr());
        if (!isBool(t))
            error(ctx, "'not' requires Bool operand, got " + t);
        return SimpleType.BOOL;
    }

    // Relational
    @Override
    public Type visitExprRelational(BagOfGrammarParser.ExprRelationalContext ctx) {
        Type left  = visit(ctx.expr(0));
        Type right = visit(ctx.expr(1));
        if (!isNumeric(left) || !isNumeric(right))
            error(ctx, "Relational operator '" + ctx.op.getText() +
                    "' requires numeric operands, got " + left + " and " + right);
        return SimpleType.BOOL;
    }

    // Equality
    @Override
    public Type visitExprEquality(BagOfGrammarParser.ExprEqualityContext ctx) {
        Type left  = visit(ctx.expr(0));
        Type right = visit(ctx.expr(1));
        if (!isAssignable(left, right) && !isAssignable(right, left))
            error(ctx, "Equality operator '" + ctx.op.getText() +
                    "' applied to incompatible types: " + left + " and " + right);
        return SimpleType.BOOL;
    }

    // Ternary (advanced feature: Zucchero sintattico)
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

    // Cast (advanced feature: Conversione di tipo)
    @Override
    public Type visitExprCast(BagOfGrammarParser.ExprCastContext ctx) {
        Type targetType = visitType(ctx.type());
        Type exprType = visit(ctx.expr());

        if (!(exprType instanceof ErrType) && !(targetType instanceof ErrType)
                && !isCastable(exprType, targetType))
            error(ctx, "Invalid cast from " + exprType + " to " + targetType);

        return targetType;
    }

    // Increment decrement inside expressions
    @Override public Type visitExprPreInc(BagOfGrammarParser.ExprPreIncContext ctx) { return checkIncDecExpr(ctx, ctx.ID().getText()); }
    @Override public Type visitExprPreDec(BagOfGrammarParser.ExprPreDecContext ctx) { return checkIncDecExpr(ctx, ctx.ID().getText()); }
    @Override public Type visitExprPostInc(BagOfGrammarParser.ExprPostIncContext ctx) { return checkIncDecExpr(ctx, ctx.ID().getText()); }
    @Override public Type visitExprPostDec(BagOfGrammarParser.ExprPostDecContext ctx) { return checkIncDecExpr(ctx, ctx.ID().getText()); }

    private Type checkIncDecExpr(ParserRuleContext ctx, String varName) {
        Type t = lookup(varName, ctx);
        if (!isNumeric(t))
            error(ctx, "Increment/decrement requires numeric type, got " + t);
        return t;
    }

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

    private Type checkIncDecField(ParserRuleContext ctx, String obj, String field) {
        Type objType = lookup(obj, ctx);
        Type fieldType = resolveField(objType, field, ctx);
        if (!isNumeric(fieldType))
            error(ctx, "Increment/decrement requires numeric type, got " + fieldType);
        return fieldType;
    }

    // User input
    @Override
    public Type visitExprDeclare(BagOfGrammarParser.ExprDeclareContext ctx) {
        if(ctx.expr() != null) {
            Type promptType = visit(ctx.expr());

            if (promptType != SimpleType.STRING && promptType != SimpleType.ANY
                    && !(promptType instanceof ErrType)) {
                error(ctx, "'declare' prompt must be a String, got " + promptType);
            }
        }
        return visitType(ctx.type()); // returns declared type
    }

    // Type visitors
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
