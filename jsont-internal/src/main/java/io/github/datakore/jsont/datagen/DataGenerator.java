package io.github.datakore.jsont.datagen;

public interface DataGenerator<T> {
    T generate(String schema);
}
