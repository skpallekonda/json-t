package io.github.datakore.jsont.entity;

public class Address {
    private String status;
    private String city;
    private String zipCode;

    public Address() {

    }

    public Address(String city, String zipCode, String status) {
        this.city = city;
        this.zipCode = zipCode;
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
