package it.univr.lang.type;

public class ErrType implements Type{
    public static final ErrType INSTANCE = new ErrType();
    private ErrType(){}

    @Override
    public boolean isCompatibleWith(Type other){
        return false; // error is never compatible!
    }

    @Override
    public String getName() { return "Err"; }

    @Override
    public String toString() { return "Err"; }

}
