package io.github.datakore.jsont.adapters;

import io.github.datakore.jsont.entity.*;

public class AllTypeHolderAdapter implements SchemaAdapter<AllTypeHolder> {
    @Override
    public Class<AllTypeHolder> logicalType() {
        return AllTypeHolder.class;
    }

    @Override
    public AllTypeHolder createTarget() {
        return new AllTypeHolder();
    }

    @Override
    public void set(Object target, String fieldName, Object valuee) {
        AllTypeHolder holder = (AllTypeHolder) target;
        switch (fieldName) {
            case "t_nbr":
                holder.setNumber((NumberEntry) valuee);
                break;
            case "t_date":
                holder.setDate((DateEntry) valuee);
                break;
            case "t_str":
                holder.setStr((StringEntry) valuee);
                break;
            case "t_array":
                holder.setArray((ArrayEntry) valuee);
                break;
            default:
                //holder.setOthers((AllTypeEntry) valuee);
        }
    }


    @Override
    public Object get(Object target, String fieldName) {
        AllTypeHolder holder = (AllTypeHolder) target;
        switch (fieldName) {
            case "t_nbr":
                return holder.getNumber();
            case "t_date":
                return holder.getDate();
            case "t_str":
                return holder.getStr();
            case "t_array":
                return holder.getArray();
            default:
                return null;
        }
    }
}
