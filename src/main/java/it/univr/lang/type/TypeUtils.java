package it.univr.lang.type;

// Imports
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
            return SimpleType.STRING;
        }
        // ArrayValue and ObjectValue are managed in value/ package
        return null;
    }

    // Type conversion (explicit downcast)
    public static ExpValue<?> castValue(ExpValue<?> value, ExpType targetType){
        ExpType valueType = fromValue(value);

        if(valueType == SimpleType.INT && targetType == SimpleType.FLOAT){
            IntValue intValue = (IntValue) value;
            return new DecValue((double) intValue.toJavaValue());
        }

        if (valueType == SimpleType.FLOAT && targetType == SimpleType.INT) {
            DecValue decValue = (DecValue) value;
            return new IntValue((int) decValue.toJavaValue().doubleValue());
        }

        if(valueType == SimpleType.INT &&
                (targetType == SimpleType.INT || targetType == SimpleType.HP || targetType == SimpleType.DAMAGE ||
                        targetType == SimpleType.LEVEL)){
            return value; // no need to cast value to Java value
        }

        if (valueType == SimpleType.STRING &&
                (targetType == SimpleType.STRING || targetType == SimpleType.QUESTNAME ||
                        targetType == SimpleType.DIE)) {
            return value;
        }

        return value; // default
    }
}
