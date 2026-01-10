package io.github.datakore.jsont.codec;

class ZipSupport {
    static Object handleZip(String raw, int pre, int post) {
        int v1;
        int v2;
        int hyphenPos = raw.indexOf("-");
        if (hyphenPos > 0) {
            v1 = Integer.parseInt(raw.substring(0, hyphenPos));
            v2 = Integer.parseInt(raw.substring(hyphenPos + 1));
        } else {
            v1 = Integer.parseInt(raw);
            v2 = 0;
        }
        String format = pre == 5 ? "%05d" : "%06d";
        return v2 == 0 ? String.format(format, v1) : String.format(format.concat("-%04d"), v1, v2);
    }
}
