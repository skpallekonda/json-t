package io.github.datakore.marketplace.adapters;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.marketplace.entity.Address;

public class AddressAdapter implements SchemaAdapter<Address> {
    @Override
    public Class<Address> logicalType() {
        return Address.class;
    }

    @Override
    public Address createTarget() {
        return new Address();
    }

    @Override
    public void set(Object target, String fieldName, Object value) {
        Address address = (Address) target;
        switch (fieldName) {
            case "street":
                address.setStreet((String) value);
                break;
            case "city":
                address.setCity((String) value);
                break;
            case "state":
                address.setState((String) value);
                break;
            case "zipCode":
                address.setZipCode((String) value);
                break;
            case "country":
                address.setCountry((String) value);
                break;
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        Address address = (Address) target;
        switch (fieldName) {
            case "street":
                return address.getStreet();
            case "city":
                return address.getCity();
            case "state":
                return address.getState();
            case "zipCode":
                return address.getZipCode();
            case "country":
                return address.getCountry();
            default:
                return null;
        }
    }
}
