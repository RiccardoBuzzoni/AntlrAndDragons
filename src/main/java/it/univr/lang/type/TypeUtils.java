package it.univr.lang.type;

// Imports
import it.univr.lang.errors.RuntimeError;
import it.univr.lang.value.*;

public class TypeUtils {

    public static ExpType fromString(String str){
        return switch (str) {
            case "Any"       -> SimpleType.ANY;
            case "Int"       -> SimpleType.INT;
            case "Float"     -> SimpleType.FLOAT;
            case "Bool"      -> SimpleType.BOOL;
            case "String"    -> SimpleType.STRING;
            case "HP"        -> SimpleType.HP;
            case "Damage"    -> SimpleType.DAMAGE;
            case "Level"     -> SimpleType.LEVEL;
            case "QuestName" -> SimpleType.QUESTNAME;
            case "Die"       -> SimpleType.DIE;
            default          -> new ObjectType(str); // If not basic type -> Crature (Class)
        };
    }

    // This method will be used by the interpreter at runtime to understand return type.
    public static ExpType fromValue(ExpValue<?> value){
        if(value instanceof IntValue){
            return SimpleType.INT;
        }
        if(value instanceof DecValue){
            return SimpleType.FLOAT;
        }
        if(value instanceof BoolValue){
            return SimpleType.BOOL;
        }
        if(value instanceof StringValue){
            String s = ((StringValue) value).toJavaValue();
            if (s.matches("\\d*d(\\d+|%)")) { // to allow roll to be called without Die variable declaration
                return SimpleType.DIE;
            }
            return SimpleType.STRING;
        }
        if (value instanceof ObjectValue obj) {
            return new ObjectType(obj.getClassName());
        }
        return null;
    }

    // Type conversion (explicit downcast)
    public static ExpValue<?> castValue(ExpValue<?> value, ExpType targetType){
        ExpType valueType = fromValue(value);

        if (targetType == SimpleType.ANY) return value;

        if (valueType instanceof ObjectType && targetType instanceof ObjectType) {
            if (((ObjectType) valueType).getName().equals(((ObjectType) targetType).getName()))
                return value;
            throw new RuntimeError("Runtime cast error: cannot cast " + valueType + " to " + targetType);
        }

        if (valueType == SimpleType.INT && targetType == SimpleType.FLOAT) {
            IntValue intValue = (IntValue) value;
            return new DecValue((double) intValue.toJavaValue());
        }

        if (valueType == SimpleType.FLOAT && targetType == SimpleType.INT) {
            DecValue decValue = (DecValue) value;
            return new IntValue((int) decValue.toJavaValue().doubleValue());
        }

        if (valueType == SimpleType.INT &&
                (targetType == SimpleType.INT || targetType == SimpleType.HP ||
                        targetType == SimpleType.DAMAGE || targetType == SimpleType.LEVEL)) {
            return value;
        }

        if ((valueType == SimpleType.STRING || valueType == SimpleType.DIE || valueType == SimpleType.QUESTNAME) &&
                (targetType == SimpleType.STRING || targetType == SimpleType.QUESTNAME ||
                        targetType == SimpleType.DIE)) {
            return value;
        }

        // Widening
        if ((valueType == SimpleType.HP || valueType == SimpleType.DAMAGE || valueType == SimpleType.LEVEL)
                && targetType == SimpleType.INT) {
            return value;
        }

        if (valueType.equals(targetType)) return value;

        throw new RuntimeError("Runtime cast error: cannot cast " + valueType + " to " + targetType);
    }
}
