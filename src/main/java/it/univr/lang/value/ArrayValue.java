package it.univr.lang.value;

// Imports
import java.util.Arrays;

public class ArrayValue extends ExpValue<ExpValue<?>[]>{
    public ArrayValue(int size){
        // Initialises empty array with specified dimension
        super(new ExpValue<?>[size]);
    }

    public ArrayValue(ExpValue<?>[] elements){
        super(elements);
    }

    public ExpValue<?> get(int index){
        return toJavaValue()[index];
    }

    public void set(int index, ExpValue<?> value){
        toJavaValue()[index] = value;
    }

    public int length(){
        return toJavaValue().length;
    }

    @Override
    public String toString() {
        return Arrays.toString(toJavaValue());
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj){
            return true;
        }

        if(obj == null || getClass() != obj.getClass()){
            return false;
        }

        ArrayValue that = (ArrayValue) obj;

        return Arrays.equals(this.toJavaValue(), that.toJavaValue());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(toJavaValue());
    }
}
