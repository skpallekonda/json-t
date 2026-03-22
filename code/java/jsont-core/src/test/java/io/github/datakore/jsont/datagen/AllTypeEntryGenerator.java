package io.github.datakore.jsont.datagen;

import io.github.datakore.jsont.entity.AllTypeEntry;
import io.github.datakore.jsont.entity.AllTypeHolder;
import org.instancio.Instancio;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class AllTypeEntryGenerator implements DataGenerator<AllTypeHolder> {

    private static int POOL_SIZE = 10;
    private static List<AllTypeHolder> POOL = new ArrayList<>(POOL_SIZE);

    public void initialize() {
        System.out.println("Pre-generating 10K orders...");
        for (int i = 0; i < POOL_SIZE; i++) {
            POOL.add(Instancio.of(AllTypeHolder.class).create());
        }
    }

    private SecureRandom random = new SecureRandom();

    @Override
    public AllTypeHolder generate(String schema) {
        if (POOL != null) {
            return POOL.get(random.nextInt(POOL_SIZE));
        } else {
            return Instancio.of(AllTypeHolder.class).create();
        }
    }
}
