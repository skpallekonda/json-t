package io.github.datakore.jsont.entity;

import io.github.datakore.jsont.JsonTType;

public class Address implements JsonTType {
    private String street;
    private String city;
    private String zipCode;

    public Address() {

    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }
}
