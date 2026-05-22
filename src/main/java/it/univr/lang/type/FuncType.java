package it.univr.lang.type;

import java.util.List;
import java.util.Objects;

public class FuncType implements Type {

    private final List<Type> parameterTypes;
    private final Type returnType;

    public FuncType(List<Type> parameterTypes, Type returnType) {

        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }

    public List<Type> getParameterTypes() {
        return parameterTypes;
    }

    public Type getReturnType() {
        return returnType;
    }

    @Override
    public String getName() {

        StringBuilder sb = new StringBuilder();

        sb.append("spell(");

        for(int i = 0; i < parameterTypes.size(); i++) {

            sb.append(parameterTypes.get(i).getName());

            if(i < parameterTypes.size() - 1)
                sb.append(", ");
        }

        sb.append(") -> ");

                sb.append(returnType.getName());

        return sb.toString();
    }

    @Override
    public boolean isCompatibleWith(Type other) {

        if(!(other instanceof FuncType s))
            return false;

        if(parameterTypes.size()
                != s.parameterTypes.size())
            return false;

        for(int i = 0; i < parameterTypes.size(); i++) {

            if(!parameterTypes.get(i)
                    .equals(s.parameterTypes.get(i))) {

                return false;
            }
        }

        return returnType.equals(s.getReturnType());
    }

    @Override
    public boolean equals(Object o) {

        if(!(o instanceof FuncType s))
            return false;

        return parameterTypes.equals(s.getParameterTypes())
                && returnType.equals(s.getReturnType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterTypes, returnType);
    }
}