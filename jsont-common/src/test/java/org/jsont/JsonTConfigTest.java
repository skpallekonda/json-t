package org.jsont;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

public class JsonTConfigTest {

    @org.junit.jupiter.api.Test
    void testMaxAllowedSchemas() {
        org.junit.jupiter.api.Assertions.assertEquals(100, JsonTConfig.getMaxAllowedSchemas());
    }

    @org.junit.jupiter.api.Test
    void testMaxFieldsPerSchema() {
        org.junit.jupiter.api.Assertions.assertEquals(100, JsonTConfig.getMaxFieldsPerSchema());
    }

    @org.junit.jupiter.api.Test
    void testMaxLengthOfIdentifier() {
        org.junit.jupiter.api.Assertions.assertEquals(128, JsonTConfig.getMaxLengthOfIdentifier());
    }

    @org.junit.jupiter.api.Test
    void testMaxLengthOfString() {
        org.junit.jupiter.api.Assertions.assertEquals(10000, JsonTConfig.getMaxLengthOfString());
    }

    @org.junit.jupiter.api.Test
    void testWhitelistCharacters() {
        String pattern = "^[A-Za-z0-9 !#$%&()*+,./:;=?@_{}~?£¥]+$";
        org.junit.jupiter.api.Assertions.assertEquals(Pattern.compile(pattern).pattern(), JsonTConfig.getWhitelistCharacters().pattern());
    }

    @Test
    void testMaxDepthAllowed() {
        org.junit.jupiter.api.Assertions.assertEquals(20, JsonTConfig.getMaxDepthAllowed());
    }

}
