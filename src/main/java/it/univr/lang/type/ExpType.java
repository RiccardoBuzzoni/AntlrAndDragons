package it.univr.lang.type;

public interface ExpType extends Type{
    boolean canCastDownTo(ExpType other);
}
