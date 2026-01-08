package org.jsont.validation.schema;

import java.util.HashMap;
import java.util.Map;

public class JsonTEngineContext {
    private final Map<String, Object> context;

    public JsonTEngineContext() {
        this.context = new HashMap<>();
    }

    public <T> T get(String key) {
        return (T) context.get(key);
    }

    public <T> void set(String key, T value) {
        context.put(key, value);
    }
}
