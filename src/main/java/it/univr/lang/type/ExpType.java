package it.univr.lang.type;

public interface ExpType extends Type{
    String getName();
    boolean canCastDownTo(ExpType other);
}
