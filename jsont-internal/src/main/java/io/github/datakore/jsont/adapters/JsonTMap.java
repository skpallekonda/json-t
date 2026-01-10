package io.github.datakore.jsont.adapters;

import io.github.datakore.jsont.JsonTType;

import java.util.Map;

public interface JsonTMap<K extends String, V> extends Map<K, V>, JsonTType {
}
