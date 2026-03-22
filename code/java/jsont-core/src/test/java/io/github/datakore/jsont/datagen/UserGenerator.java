package io.github.datakore.jsont.datagen;

import io.github.datakore.jsont.entity.Address;
import io.github.datakore.jsont.entity.User;
import io.github.datakore.jsont.util.CollectionUtils;
import org.instancio.Instancio;
import org.instancio.Select;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class UserGenerator implements DataGenerator<User> {
    private static final int POOL_SIZE = 10;
    private static final List<User> USER_POOL = new ArrayList<>(POOL_SIZE);
    private final SecureRandom random = new SecureRandom();

    public void initialize() {
        for (int i = 0; i < POOL_SIZE; i++) {
            USER_POOL.add(createUser(i + 1));
        }
    }

    private User createUser(int index) {
        return Instancio.of(User.class)
                .set(Select.field(User::getId), index)
                .generate(Select.field(User::getId), gen -> gen.id().usa().ssn())
                .generate(Select.field(User::getUserName), gen -> gen.string().minLength(5).maxLength(10))
                .generate(Select.field(User::getEmail), gen -> gen.text().pattern("#a#a#a#a#a#a#a@example.com"))
                .generate(Select.field(User::getRole), gen -> gen.oneOf("ADMIN", "USER"))
                .generate(Select.field(Address::getStatus), gen -> gen.oneOf("ACTIVE", "INACTIVE", "SUSPENDED"))
                .generate(Select.field(Address::getZipCode), gen -> gen.string().digits().length(5))
                .create();
    }

    @Override
    public User generate(String schema) {
        if (CollectionUtils.isEmpty(USER_POOL)) {
            return createUser(random.nextInt(POOL_SIZE + 1, POOL_SIZE * 2));
        }
        return USER_POOL.get(random.nextInt(POOL_SIZE));
    }
}
