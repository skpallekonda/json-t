package io.github.datakore.jsont.stringify;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringifyOptionsTest {

    @Test void compact_notPretty() {
        assertFalse(StringifyOptions.compact().isPretty());
    }

    @Test void compact_defaultIndent() {
        assertEquals(2, StringifyOptions.compact().indent());
    }

    @Test void pretty_isPretty() {
        assertTrue(StringifyOptions.pretty().isPretty());
    }

    @Test void pretty_defaultIndent() {
        assertEquals(2, StringifyOptions.pretty().indent());
    }

    @Test void prettyWithIndent_customIndent() {
        StringifyOptions opts = StringifyOptions.prettyWithIndent(4);
        assertTrue(opts.isPretty());
        assertEquals(4, opts.indent());
    }

    @Test void prettyWithIndent_zeroIsAllowed() {
        StringifyOptions opts = StringifyOptions.prettyWithIndent(0);
        assertTrue(opts.isPretty());
        assertEquals(0, opts.indent());
    }

    @Test void negativeIndent_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> StringifyOptions.prettyWithIndent(-1));
    }

    @Test void toString_compact() {
        assertTrue(StringifyOptions.compact().toString().contains("compact"));
    }

    @Test void toString_pretty() {
        String s = StringifyOptions.pretty().toString();
        assertTrue(s.contains("pretty") || s.contains("indent"));
    }
}
