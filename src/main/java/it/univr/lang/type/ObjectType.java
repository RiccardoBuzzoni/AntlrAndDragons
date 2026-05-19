package it.univr.lang.type;

public class ObjectType implements ExpType{
    private final String className;

    public ObjectType(String className){
        this.className = className;
    }

    @Override
    public String getName() {
        return className;
    }

    @Override
    public boolean canCastDownTo(ExpType other) {
        return false; // No class hierarchy
    }

    @Override
    public boolean isCompatibleWith(Type other) {
        if(this == other){
            return true;
        }
        if(other instanceof ObjectType o){
            // Can only be compatible if two objects are instances of the same class.
            return this.className.equals(o.className);
        }
        return false;
    }

    @Override
    public String toString(){
        return "Creature " + className;
    }
}
