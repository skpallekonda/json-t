package io.github.datakore.jsont.adapters;

import io.github.datakore.jsont.entity.Address;
import io.github.datakore.jsont.entity.User;
import io.github.datakore.jsont.util.CollectionUtils;

import java.util.Arrays;

// class name to be dynamic <modelName>Adapter implements SchemaAdapter<modelName>
public class UserAdapter implements SchemaAdapter<User> {
    // this method also uses public Class<modelName>
    @Override
    public Class<User> logicalType() {
        return User.class;
    }

    // This method also uses modelName in place of User
    @Override
    public User createTarget() {
        return new User();
    }


    @Override
    public void set(Object target, String fieldName, Object value) {
        // Using modelName, convert target to model class
        User user = (User) target;
        switch (fieldName) {
            // Here, using fields as a map with its type construct the below cases
            case "id":
                user.setId((Integer) value);
                break;
            case "username":
                user.setUserName((String) value);
                break;
            case "email":
                user.setEmail((String) value);
                break;
            case "address":
                user.setAddress((Address) value);
                break;
            case "role":
                user.setRole((String) value);
                break;
            case "tags":
                user.setTags(CollectionUtils.toArray(value, String.class));
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        User user = (User) target;
        switch (fieldName) {
            case "id":
                return user.getId();
            case "username":
                return user.getUserName();
            case "email":
                return user.getEmail();
            case "address":
                return user.getAddress();
            case "role":
                return user.getRole();
            case "tags":
                return Arrays.asList(user.getTags());
            default:
        }
        return null;
    }
}
