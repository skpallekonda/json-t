package io.github.datakore.jsont.entity;

import io.github.datakore.jsont.JsonTType;

public class User implements JsonTType {
    private int id;
    private String userName;
    private String email;
    private String role;
    private Address address;
    private String[] tags;

    public User(int id, String userName, String role, Address address) {
        this.id = id;
        this.userName = userName;
        this.role = role;
        this.address = address;
    }

    public User() {
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }


}
