package io.github.datakore.jsont.adapters.marketplace;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.marketplace.Address;
import io.github.datakore.jsont.marketplace.Customer;

public class CustomerAdapter implements SchemaAdapter<Customer> {
    @Override
    public Class<Customer> logicalType() {
        return Customer.class;
    }

    @Override
    public Customer createTarget() {
        return new Customer();
    }

    @Override
    public void set(Object target, String fieldName, Object value) {
        Customer customer = (Customer) target;
        switch (fieldName) {
            case "customerId":
                customer.setCustomerId((Long) value);
                break;
            case "firstName":
                customer.setFirstName((String) value);
                break;
            case "lastName":
                customer.setLastName((String) value);
                break;
            case "email":
                customer.setEmail((String) value);
                break;
            case "phoneNumber":
                customer.setPhoneNumber((String) value);
                break;
            case "billingAddress":
                customer.setBillingAddress((Address) value);
                break;
            case "shippingAddress":
                customer.setShippingAddress((Address) value);
                break;
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        Customer customer = (Customer) target;
        switch (fieldName) {
            case "customerId":
                return customer.getCustomerId();
            case "firstName":
                return customer.getFirstName();
            case "lastName":
                return customer.getLastName();
            case "email":
                return customer.getEmail();
            case "phoneNumber":
                return customer.getPhoneNumber();
            case "billingAddress":
                return customer.getBillingAddress();
            case "shippingAddress":
                return customer.getShippingAddress();
            default:
                return null;
        }
    }
}
