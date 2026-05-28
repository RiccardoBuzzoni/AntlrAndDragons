package it.univr.lang.type;

public enum SimpleType implements ExpType{
    ANY("Any"),
    INT("Int"),
    FLOAT("Float"),
    BOOL("Bool"),
    STRING("String"),
    HP("HP"),
    DAMAGE("Damage"),
    LEVEL("Level"),
    QUESTNAME("QuestName"),
    DIE("Die");

    private final String name;

    SimpleType(String name){
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean canCastDownTo(ExpType other) {
        if(this == ANY){
            return true; // From any you can cast down to anything
        }

        if((this == INT || this == HP || this == DAMAGE || this == LEVEL) &&
                (other == INT || other == HP || other == DAMAGE || other == LEVEL)){
            return true;
        }
        if(this == FLOAT && other == INT){
            return true;
        }
        if(this == STRING && (other == QUESTNAME || other == DIE)){
            return true;
        }
        return false;
    }

    @Override
    public boolean isCompatibleWith(Type other) {
        if(this == ANY && other instanceof ExpType){ // Any -> root
            return true;
        }
        if(this == other){
            return true;
        }
        if(other instanceof SimpleType o){
            if (o == ANY) {
                return true;
            }

            // Subtyping rules for numeric and domain types
            if ((this == HP || this == DAMAGE || this == LEVEL) && o == INT) {
                return true;
            }
            if ((this == QUESTNAME || this == DIE) && o == STRING) {
                return true;
            }
            if (this == INT && o == FLOAT) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString(){
        return name;
    }
}
