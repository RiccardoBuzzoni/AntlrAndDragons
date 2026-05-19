package it.univr.lang.value;

import it.univr.lang.type.ExpType;

public class ExpValue<T> extends Value {
    private final T value;

    protected ExpValue(T value){
        this.value = value;
    }

    public T toJavaValue(){
        return value;
    }

    @Override
    public String toString(){
        return value.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj){
            return true;
        }

        if(obj == null || getClass() != obj.getClass()){
            return false;
        }

        ExpValue<?> expValue = (ExpValue<?>) obj;
        return value.equals(expValue.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
