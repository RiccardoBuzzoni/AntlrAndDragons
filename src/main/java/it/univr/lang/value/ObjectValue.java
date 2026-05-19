package it.univr.lang.value;

// Imports
import java.util.HashMap;
import java.util.Map;

public class ObjectValue extends ExpValue<Map<String, ExpValue<?>>> {
    private final String className;

    public ObjectValue(String className){
        // Memory of the object maps field name and its value
        super(new HashMap<>());
        this.className = className;
    }

    public String getClassName(){
        return className;
    }

    public ExpValue<?> getField(String fieldName){
        return toJavaValue().get(fieldName);
    }

    public void setField(String fieldName, ExpValue<?> value){
        toJavaValue().put(fieldName, value);
    }

    @Override
    public String toString() {
        return "Creature " + className + " " + toJavaValue().toString();
    }

    // To objects are equal at runtime for identity of allocation or for field state

    @Override
    public boolean equals(Object obj) {
        if(this == obj){
            return true;
        }

        if(obj == null || getClass() != obj.getClass()){
            return false;
        }

        ObjectValue that = (ObjectValue) obj;

        return className.equals(that.className) && toJavaValue().equals(toJavaValue());
    }

    @Override
    public int hashCode() {
        int result = toJavaValue().hashCode();
        result = 31 * result * className.hashCode();
        return result;
    }
}
