package it.univr.lang.type;

public class ComType implements Type {
    public static final ComType INSTANCE = new ComType();
    private ComType() { }

    @Override
    public boolean isCompatibleWith(Type type) {
        return type == INSTANCE;
    }

    @Override
    public String getName() { return "Com"; }

    @Override
    public String toString() { return "Com"; }

}