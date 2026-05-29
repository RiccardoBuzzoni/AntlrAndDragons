package it.univr.lang;

// Imports
import it.univr.lang.type.*;
import it.univr.lang.value.*;
import org.antlr.v4.runtime.*;
import org.apache.commons.text.StringEscapeUtils;
import java.util.*;
import java.util.regex.*;

/**
 * BagOfGrammarIntp – Tree-walking interpreter for BagOfGrammar.
 */

public class BagOfGrammarIntp extends BagOfGrammarBaseVisitor<ExpValue<?>>{

    // Memory
    private Mem mem;

    public BagOfGrammarIntp(){
        this.mem = new Mem();
    }

    public Mem getMem(){return mem;}

    // Typed visitors helpers
    private ExpValue<?> visitExpr(BagOfGrammarParser.ExprContext ctx){return visit(ctx);}
    private BoolValue visitBoolExpr(BagOfGrammarParser.ExprContext ctx){return (BoolValue) visit(ctx);}
    private IntValue visitIntExpr(BagOfGrammarParser.ExprContext ctx){return (IntValue) visit(ctx);}
    private StringValue visitStringExpr(BagOfGrammarParser.ExprContext ctx){return (StringValue) visit(ctx);}

    private double toDouble(ExpValue<?> value){
        if(value instanceof DecValue d)
            return d.toJavaValue();
        return ((IntValue) value).toJavaValue();
    }

    // Control-flow signals
    private static class ReturnException extends RuntimeException {
        final ExpValue<?> value;
        ReturnException(ExpValue<?> value) {
            super(null, null, true, false); // no stack trace
            this.value = value;
        }
    }

    private static class BreakException extends RuntimeException {
        static final BreakException INSTANCE = new BreakException();
        private BreakException() { super(null, null, true, false); }
    }

    private static class ExitException extends RuntimeException {
        static final ExitException INSTANCE = new ExitException();
        private ExitException() { super(null, null, true, false); }
    }

    // Creature descriptor
    private static class CreatureDescriptor{
        final Map<String, ExpType> fields = new LinkedHashMap<>();
        final Map<String, BagOfGrammarParser.CreatureMemberContext> methods = new LinkedHashMap<>();
    }

    private final Map<String, CreatureDescriptor> creatures = new LinkedHashMap<>();
    private final Map<String, BagOfGrammarParser.SpellDeclContext> spells = new LinkedHashMap<>();
    private final Random rng = new Random();

    // Program
    @Override
    public ExpValue<?> visitProgram(BagOfGrammarParser.ProgramContext ctx) {
        if(ctx.creatureSection() != null) registerCreatures(ctx.creatureSection());
        if(ctx.spellbookSection() != null) registerSpells(ctx.spellbookSection());
        return visit(ctx.questBlock());
    }

    private void registerCreatures(BagOfGrammarParser.CreatureSectionContext ctx){
        for(BagOfGrammarParser.CreatureDeclContext cd : ctx.creatureDecl()){
            CreatureDescriptor desc = new CreatureDescriptor();
            for(BagOfGrammarParser.CreatureMemberContext m : cd.creatureMember()){
                if(m instanceof BagOfGrammarParser.ClassFieldContext f)
                    desc.fields.put(f.ID().getText(), resolveType(f.type()));
                else if(m instanceof BagOfGrammarParser.ClassMethodReturnContext mr)
                    desc.methods.put(mr.ID().getText(), m);
                else if(m instanceof BagOfGrammarParser.ClassMethodVoidContext mv)
                    desc.methods.put(mv.ID().getText(), m);
            }
            creatures.put(cd.ID().getText(), desc);
        }
    }

    private void registerSpells(BagOfGrammarParser.SpellbookSectionContext ctx){
        for (BagOfGrammarParser.SpellDeclContext sd : ctx.spellDecl()) {
            String name = (sd instanceof BagOfGrammarParser.FuncDeclReturnContext fr)
                    ? fr.ID().getText() : ((BagOfGrammarParser.FuncDeclVoidContext) sd).ID().getText();
            spells.put(name, sd);
        }
    }

    // Quest block
    @Override
    public ExpValue<?> visitQuestBlock(BagOfGrammarParser.QuestBlockContext ctx) {
        try {
            visit(ctx.block());
        } catch (ExitException e) { // flee calls ExitException
            System.exit(0);
        }
        return null;
    }

    // Block
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

    // Statements
    @Override public ExpValue<?> visitStatVarDecl(BagOfGrammarParser.StatVarDeclContext ctx){
        return visit(ctx.varDecl());
    }
    @Override public ExpValue<?> visitStatAssign(BagOfGrammarParser.StatAssignContext ctx){
        return visit(ctx.assign());
    }
    @Override public ExpValue<?> visitStatIf(BagOfGrammarParser.StatIfContext ctx){
        return visit(ctx.ifStat());
    }
    @Override public ExpValue<?> visitStatWhile(BagOfGrammarParser.StatWhileContext ctx){
        return visit(ctx.untilStat());
    }
    @Override public ExpValue<?> visitStatFor(BagOfGrammarParser.StatForContext ctx){
        return visit(ctx.forStat());
    }
    @Override public ExpValue<?> visitStatSwitch(BagOfGrammarParser.StatSwitchContext ctx){
        return visit(ctx.switchStat());
    }
    @Override public ExpValue<?> visitStatBlock(BagOfGrammarParser.StatBlockContext ctx){
        return visit(ctx.block());
    }
    @Override public ExpValue<?> visitStatExpr(BagOfGrammarParser.StatExprContext ctx) {
        visitExpr(ctx.expr());
        return null;
    }
    @Override
    public ExpValue<?> visitStatPrint(BagOfGrammarParser.StatPrintContext ctx){
        System.out.println(visitExpr(ctx.expr()));
        return null;
    }
    @Override
    public ExpValue<?> visitStatReturn(BagOfGrammarParser.StatReturnContext ctx) {
        throw new ReturnException(visitExpr(ctx.expr()));
    }
    @Override
    public ExpValue<?> visitStatReturnVoid(BagOfGrammarParser.StatReturnVoidContext ctx) {
        throw new ReturnException(null);
    }
    @Override
    public ExpValue<?> visitStatBreak(BagOfGrammarParser.StatBreakContext ctx) {
        throw BreakException.INSTANCE;
    }
    @Override
    public ExpValue<?> visitStatExit(BagOfGrammarParser.StatExitContext ctx) {
        throw ExitException.INSTANCE;
    }
    @Override
    public ExpValue<?> visitStatFuncCall(BagOfGrammarParser.StatFuncCallContext ctx) {
        visit(ctx.spellCall());
        return null;
    }

    // Variable declaration
    @Override
    public ExpValue<?> visitVarDeclInit(BagOfGrammarParser.VarDeclInitContext ctx) {
        mem.declareInit(ctx.ID().getText(), resolveType(ctx.type()), visitExpr(ctx.expr()));
        return null;
    }

    @Override
    public ExpValue<?> visitVarDeclDefault(BagOfGrammarParser.VarDeclDefaultContext ctx) {
        mem.declare(ctx.ID().getText(), resolveType(ctx.type()));
        return null;
    }

    // Assignment
    @Override
    public ExpValue<?> visitAssignSimple(BagOfGrammarParser.AssignSimpleContext ctx){
        mem.setValue(ctx.ID().getText(), visitExpr(ctx.expr()));
        return null;
    }

    @Override
    public ExpValue<?> visitAssignField(BagOfGrammarParser.AssignFieldContext ctx){
        ObjectValue obj = (ObjectValue) mem.getValue(ctx.ID(0).getText());
        obj.setField(ctx.ID(1).getText(), visitExpr(ctx.expr()));
        return null;
    }

    // Compound assignments (advanced feature: Zucchero sintattico)
    @Override public ExpValue<?> visitAssignAdd(BagOfGrammarParser.AssignAddContext ctx){
        return applyCompound(ctx.ID().getText(), visitExpr(ctx.expr()), "+");
    }
    @Override public ExpValue<?> visitAssignSub(BagOfGrammarParser.AssignSubContext ctx){
        return applyCompound(ctx.ID().getText(), visitExpr(ctx.expr()), "-");
    }
    @Override public ExpValue<?> visitAssignMul(BagOfGrammarParser.AssignMulContext ctx){
        return applyCompound(ctx.ID().getText(), visitExpr(ctx.expr()), "*");
    }
    @Override public ExpValue<?> visitAssignDiv(BagOfGrammarParser.AssignDivContext ctx){
        return applyCompound(ctx.ID().getText(), visitExpr(ctx.expr()), "/");
    }
    @Override public ExpValue<?> visitAssignMod(BagOfGrammarParser.AssignModContext ctx){
        return applyCompound(ctx.ID().getText(), visitExpr(ctx.expr()), "%");
    }

    private ExpValue<?> applyCompound(String id, ExpValue<?> rhs, String op){ // rhs -> right hand side
        mem.setValue(id, applyArith(mem.getValue(id), rhs, op));
        return null;
    }

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

    private ExpValue<?> applyCompoundField(String objName, String fieldName, ExpValue<?> rhs, String op) {
        ObjectValue obj = (ObjectValue) mem.getValue(objName);
        ExpValue<?> current = obj.getField(fieldName);
        obj.setField(fieldName, applyArith(current, rhs, op));
        return null;
    }

    // Control flow
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

    // Function/method calls
    @Override
    public ExpValue<?> visitSpellCall(BagOfGrammarParser.SpellCallContext ctx) {
        String name = ctx.ID().getText();
        BagOfGrammarParser.SpellDeclContext decl = spells.get(name);
        if (decl == null)
            throw new RuntimeException("Unknown spell: " + name);

        List<ExpValue<?>> args = evalArgs(ctx.argList());

        if (decl instanceof BagOfGrammarParser.FuncDeclReturnContext fr)
            return callFunction(fr.paramList(), fr.block(), args);
        else {
            BagOfGrammarParser.FuncDeclVoidContext fv = (BagOfGrammarParser.FuncDeclVoidContext) decl;
            callFunction(fv.paramList(), fv.block(), args);
            return null;
        }
    }

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

    private ExpValue<?> callMethod(ObjectValue receiver, BagOfGrammarParser.SpellCallContext callCtx) {
        String className  = receiver.getClassName();
        String methodName = callCtx.ID().getText();
        CreatureDescriptor desc = creatures.get(className);
        if (desc == null || !desc.methods.containsKey(methodName))
            throw new RuntimeException("Unknown method '" + methodName + "' on " + className);

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

    private ExpValue<?> executeBody(BagOfGrammarParser.BlockContext body) {
        for (BagOfGrammarParser.StatContext s : body.stat())
            visit(s);
        return null;
    }

    private void bindParams(BagOfGrammarParser.ParamListContext params, List<ExpValue<?>> args) {
        if (params == null) return;
        List<BagOfGrammarParser.ParamContext> ps = params.param();
        for (int i = 0; i < ps.size(); i++)
            mem.declareInit(ps.get(i).ID().getText(), resolveType(ps.get(i).type()), args.get(i));
    }

    private List<ExpValue<?>> evalArgs(BagOfGrammarParser.ArgListContext ctx) {
        List<ExpValue<?>> result = new ArrayList<>();
        if (ctx != null)
            for (BagOfGrammarParser.ExprContext e : ctx.expr())
                result.add(visitExpr(e));
        return result;
    }

    // Expressions
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
    @Override
    public ExpValue<?> visitExprString(BagOfGrammarParser.ExprStringContext ctx) {
        String raw = ctx.STRING_LIT().getText();
        return new StringValue(StringEscapeUtils.unescapeJava(raw.substring(1, raw.length() - 1)));
    }
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
    @Override
    public ExpValue<?> visitExprDie(BagOfGrammarParser.ExprDieContext ctx) {
        String text = ctx.getText();
        int dIdx = text.indexOf('d');
        int numDice = dIdx > 0 ? Integer.parseInt(text.substring(0, dIdx)) : 1;
        String face = text.substring(dIdx + 1);
        int sides = face.equals("%") ? 100 : Integer.parseInt(face);
        int total = 0;
        for (int i = 0; i < numDice; i++) total += rng.nextInt(sides) + 1;
        return new IntValue(total);
    }
    @Override
    public ExpValue<?> visitExprId(BagOfGrammarParser.ExprIdContext ctx) {
        return mem.getValue(ctx.ID().getText());
    }
    @Override
    public ExpValue<?> visitExprNew(BagOfGrammarParser.ExprNewContext ctx) {
        String className = ctx.ID().getText();
        CreatureDescriptor desc = creatures.get(className);
        if (desc == null) throw new RuntimeException("Unknown creature: " + className);
        ObjectValue obj = new ObjectValue(className);
        for (Map.Entry<String, ExpType> e : desc.fields.entrySet())
            obj.setField(e.getKey(), defaultValue(e.getValue()));
        return obj;
    }
    @Override public ExpValue<?> visitExprFuncCall(BagOfGrammarParser.ExprFuncCallContext ctx) {
        return visit(ctx.spellCall());
    }
    @Override public ExpValue<?> visitExprMethodCall(BagOfGrammarParser.ExprMethodCallContext ctx) {
        return callMethod((ObjectValue) visitExpr(ctx.expr()), ctx.spellCall());
    }
    @Override public ExpValue<?> visitExprFieldAccess(BagOfGrammarParser.ExprFieldAccessContext ctx) {
        return ((ObjectValue) visitExpr(ctx.expr())).getField(ctx.ID().getText());
    }

    // Arithmetic
    @Override
    public ExpValue<?> visitExprAddSub(BagOfGrammarParser.ExprAddSubContext ctx) {
        ExpValue<?> left  = visitExpr(ctx.expr(0));
        ExpValue<?> right = visitExpr(ctx.expr(1));
        if (left instanceof StringValue l && right instanceof StringValue r && ctx.op.getText().equals("+"))
            return new StringValue(l.toJavaValue() + r.toJavaValue());
        return applyArith(left, right, ctx.op.getText());
    }
    @Override
    public ExpValue<?> visitExprMulDivMod(BagOfGrammarParser.ExprMulDivModContext ctx) {
        return applyArith(visitExpr(ctx.expr(0)), visitExpr(ctx.expr(1)), ctx.op.getText());
    }
    @Override
    public ExpValue<?> visitExprNeg(BagOfGrammarParser.ExprNegContext ctx) {
        ExpValue<?> v = visitExpr(ctx.expr());
        return v instanceof IntValue i ? new IntValue(-i.toJavaValue()) : new DecValue(-((DecValue) v).toJavaValue());
    }

    private ExpValue<?> applyArith(ExpValue<?> left, ExpValue<?> right, String op) {
        double l = toDouble(left), r = toDouble(right);
        double result = switch (op) {
            case "+" -> l + r;
            case "-" -> l - r;
            case "*" -> l * r;
            case "/" -> l / r;
            case "%" -> l % r;
            default  -> throw new RuntimeException("Unknown operator: " + op);
        };
        boolean bothInt = (left instanceof IntValue) && (right instanceof IntValue);
        return bothInt ? new IntValue((int) result) : new DecValue(result);
    }

    // Logical
    @Override
    public ExpValue<?> visitExprLogical(BagOfGrammarParser.ExprLogicalContext ctx) {
        boolean left = visitBoolExpr(ctx.expr(0)).toJavaValue();
        if (ctx.op.getText().equals("and") && !left) return new BoolValue(false);
        if (ctx.op.getText().equals("or")  &&  left) return new BoolValue(true);
        return visitExpr(ctx.expr(1));
    }
    @Override
    public ExpValue<?> visitExprNot(BagOfGrammarParser.ExprNotContext ctx) {
        return new BoolValue(!visitBoolExpr(ctx.expr()).toJavaValue());
    }

    // Relational
    @Override
    public ExpValue<?> visitExprRelational(BagOfGrammarParser.ExprRelationalContext ctx) {
        double left  = toDouble(visitExpr(ctx.expr(0)));
        double right = toDouble(visitExpr(ctx.expr(1)));
        return new BoolValue(switch (ctx.op.getText()) {
            case "lt"  -> left <  right;
            case "gt"  -> left >  right;
            case "lte" -> left <= right;
            case "gte" -> left >= right;
            default    -> throw new RuntimeException("Unknown relational op: " + ctx.op.getText());
        });
    }

    // Equality
    @Override
    public ExpValue<?> visitExprEquality(BagOfGrammarParser.ExprEqualityContext ctx) {
        boolean eq = valuesEqual(visitExpr(ctx.expr(0)), visitExpr(ctx.expr(1)));
        return new BoolValue(ctx.op.getText().equals("eq") ? eq : !eq);
    }

    // Ternary
    @Override
    public ExpValue<?> visitExprTernary(BagOfGrammarParser.ExprTernaryContext ctx) {
        return visitBoolExpr(ctx.expr(0)).toJavaValue()
                ? visitExpr(ctx.expr(1)) : visitExpr(ctx.expr(2));
    }

    // Cast
    @Override
    public ExpValue<?> visitExprCast(BagOfGrammarParser.ExprCastContext ctx) {
        return TypeUtils.castValue(visitExpr(ctx.expr()), resolveType(ctx.type()));
    }

    // Inc/dec in expressions
    @Override public ExpValue<?> visitExprPreInc(BagOfGrammarParser.ExprPreIncContext ctx){
        return incDecExpr(ctx.ID().getText(), 1, true);
    }
    @Override public ExpValue<?> visitExprPreDec(BagOfGrammarParser.ExprPreDecContext ctx){
        return incDecExpr(ctx.ID().getText(), -1, true);
    }
    @Override public ExpValue<?> visitExprPostInc(BagOfGrammarParser.ExprPostIncContext ctx){
        return incDecExpr(ctx.ID().getText(), 1, false);
    }
    @Override public ExpValue<?> visitExprPostDec(BagOfGrammarParser.ExprPostDecContext ctx){
        return incDecExpr(ctx.ID().getText(), -1, false);
    }

    private ExpValue<?> incDecExpr(String id, int delta, boolean pre) {
        ExpValue<?> old = mem.getValue(id);
        ExpValue<?> updated = old instanceof IntValue i
                ? new IntValue(i.toJavaValue() + delta)
                : new DecValue(((DecValue) old).toJavaValue() + delta);
        mem.setValue(id, updated);
        return pre ? updated : old;
    }

    // Type resolution
    private ExpType resolveType(BagOfGrammarParser.TypeContext ctx) {
        return TypeUtils.fromString(ctx.getText());
    }

    // Utils
    private BagOfGrammarIntp newBranch() {
        BagOfGrammarIntp branch = new BagOfGrammarIntp(Mem.copyOf(this.mem));
        branch.creatures.putAll(this.creatures);
        branch.spells.putAll(this.spells);
        return branch;
    }

    private BagOfGrammarIntp(Mem mem) {
        this.mem = new Mem(mem);
    }

    private boolean valuesEqual(ExpValue<?> a, ExpValue<?> b) {
        if ((a instanceof IntValue || a instanceof DecValue) &&
                (b instanceof IntValue || b instanceof DecValue))
            return toDouble(a) == toDouble(b);
        if (a instanceof BoolValue ba && b instanceof BoolValue bb) return ba.toJavaValue().equals(bb.toJavaValue());
        if (a instanceof StringValue sa && b instanceof StringValue sb) return sa.toJavaValue().equals(sb.toJavaValue());
        return a == b;
    }

    private ExpValue<?> defaultValue(ExpType type) {
        if (type == SimpleType.INT || type == SimpleType.HP ||
                type == SimpleType.DAMAGE || type == SimpleType.LEVEL) return new IntValue(0);
        if (type == SimpleType.FLOAT) return new DecValue(0.0);
        if (type == SimpleType.BOOL) return new BoolValue(false);
        if (type == SimpleType.STRING || type == SimpleType.QUESTNAME || type == SimpleType.DIE)
            return new StringValue("");
        return null;
    }

    // User input
    @Override
    public ExpValue<?> visitExprDeclare(BagOfGrammarParser.ExprDeclareContext ctx) {
        String prompt = visitStringExpr(ctx.expr()).toJavaValue();
        System.out.println(prompt); // use print not println!
        System.out.flush(); // empties output buffer

        java.util.Scanner scanner = new java.util.Scanner(System.in);
        String input = scanner.nextLine().trim();

        ExpType targetType = resolveType(ctx.type());
        if (targetType == SimpleType.INT || targetType == SimpleType.HP
                || targetType == SimpleType.DAMAGE || targetType == SimpleType.LEVEL) {
            return new IntValue(Integer.parseInt(input));
        } else if (targetType == SimpleType.FLOAT) {
            return new DecValue(Double.parseDouble(input));
        } else if (targetType == SimpleType.BOOL) {
            return new BoolValue(Boolean.parseBoolean(input));
        } else {
            return new StringValue(input); // String, QuestName, Any
        }
    }
}