package io.github.datakore.jsont.util;

public class StepCounter {
    private final String name;
    private final Long counter;

    public StepCounter(String name, Long counter) {
        this.name = name;
        this.counter = counter;
    }

    public String getName() {
        return name;
    }

    public Long getCounter() {
        return counter;
    }
}
