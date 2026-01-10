package io.github.datakore.jsont;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.entity.Address;
import io.github.datakore.jsont.entity.User;
import io.github.datakore.jsont.io.JsonTWriter;

import java.util.List;

// class name to be dynamic <modelName>Adapter implements SchemaAdapter<modelName>
public class UserAdapter implements SchemaAdapter<User> {
    @Override
    public String toSchemaDef() {
        return "        User: {\n" +
                "            int: id,\n" +
                "            str: username,\n" +
                "            str: email?,\n" +
                "            <Address>[]: address,\n" +
                "            str[]: tags?,\n" +
                "            <Role>: role?\n" +
                "        }";
    }

    @Override
    public List<Class<?>> childrenTypes() {
        return List.of(new Class[]{Address.class});
    }

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
    public void writeObject(Object target, JsonTWriter writer) {

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
            case "zipCode":
                user.getAddress().setZipCode((String) value);
                break;
            case "tags":
                if (value instanceof List) {
                    List<String> tags = List.class.cast(value);
                    String[] tagsArr = tags.toArray(new String[]{});
                    user.setTags(tagsArr);
                }
            default:
                ;
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
            case "zipCode":
                return user.getTags();
            case "tags":
                return user.getTags();
            default:
                ;
        }
        return null;
    }
}
