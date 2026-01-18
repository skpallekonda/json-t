package io.github.datakore.jsont.stringify;

import java.io.Writer;

public interface DataStringifier {
    <T> void stringify(T object, Writer writer);
}
