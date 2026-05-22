package it.univr.lang.type;

public final class VoidType implements Type {

    public static final VoidType INSTANCE =
            new VoidType();

    private VoidType() {}

    @Override
    public String getName() {
        return "void";
    }

    @Override
    public boolean isCompatibleWith(Type other) {
        return other instanceof VoidType;
    }

    @Override
    public String toString() {
        return getName();
    }
}