package it.univr.lang.value;

public class NumValue<T extends Number> extends ExpValue<T> {
    public NumValue(T value) {
        super(value);
    }
}
