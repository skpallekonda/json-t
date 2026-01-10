package io.github.datakore.jsont;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.entity.Address;
import io.github.datakore.jsont.io.JsonTWriter;

import java.util.List;

public class AddressAdapter implements SchemaAdapter<Address> {
    @Override
    public String toSchemaDef() {
        return "Address: {\n" +
                "            str: street,\n" +
                "            str: city,\n" +
                "            zip: zipCode\n" +
                "        }";
    }

    @Override
    public List<Class<?>> childrenTypes() {
        return List.of();
    }

    @Override
    public Class<Address> logicalType() {
        return Address.class;
    }

    @Override
    public Address createTarget() {
        return new Address();
    }

    @Override
    public void writeObject(Object target, JsonTWriter writer) {

    }

    @Override
    public void set(Object target, String fieldName, Object value) {
        Address address = (Address) target;
        switch (fieldName) {
            case "street":
                address.setStreet((String) value);
                break;
            case "zipCode":
                address.setZipCode((String) value);
                break;
            case "city":
            default:
                address.setCity((String) value);
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        Address address = (Address) target;
        switch (fieldName) {
            case "street":
                return address.getStreet();
            case "zipCode":
                return address.getZipCode();
            case "city":
            default:
                return address.getCity();
        }
    }
}
