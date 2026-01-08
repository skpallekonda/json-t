package org.jsont.adapters;

import org.jsont.JsonTType;

import java.util.Map;

public interface JsonTMap<K extends String, V> extends Map<K, V>, JsonTType {
}
