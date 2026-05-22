package it.univr.lang.type;

public interface Type {

    String getName();

    /**
     * Checks if value of type 'other' can be assigned/used
     * where 'this' is requested.
     */
    boolean isCompatibleWith(Type other);
}
