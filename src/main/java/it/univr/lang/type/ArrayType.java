package it.univr.lang.type;

public class ArrayType implements ExpType{
    private final ExpType elementType;

    public ArrayType(ExpType elementType){
        this.elementType = elementType;
    }

    public ExpType getElementType(){
        return elementType;
    }

    @Override
    public String getName() {
        return elementType.getName() + "[]";
    }

    @Override
    public boolean canCastDownTo(ExpType other) {
        if(other instanceof ArrayType o){
            return this.elementType.canCastDownTo(o.elementType);
        }
        return false;
    }

    @Override
    public boolean isCompatibleWith(Type other) {
        if(other == SimpleType.ANY){
            return false; // Any cannot be subtype!
        }
        if(this == other){
            return true;
        }

        if(other instanceof ArrayType o){
            // Checks element compatibility
            return this.elementType.isCompatibleWith(o.elementType);
        }
        return false;
    }

    @Override
    public String toString(){
        return getName();
    }
}
