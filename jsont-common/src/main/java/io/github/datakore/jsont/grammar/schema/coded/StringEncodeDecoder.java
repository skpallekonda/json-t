package io.github.datakore.jsont.grammar.schema.coded;

import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;
import io.github.datakore.jsont.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

public class StringEncodeDecoder implements EncodeDecoder {

    private static final BooleanEncodeDecoder boolDecoder = new BooleanEncodeDecoder();
    private static final NumberEncodeDecoder nbrDecoder = new NumberEncodeDecoder();
    private static final DateEncodeDecoder dateDecoder = new DateEncodeDecoder();

    @Override
    public Object decode(JsonBaseType jsonBaseType, String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        raw = StringUtils.removeQuotes(raw);
        if ("String".equals(jsonBaseType.lexerValueType())) {
            return handlePureStringTypeDecodes(jsonBaseType, raw);
        } else if ("Number".equals(jsonBaseType.lexerValueType())) {
            return nbrDecoder.decode(jsonBaseType, raw);
        } else if ("Date".equals(jsonBaseType.lexerValueType())) {
            return dateDecoder.decode(jsonBaseType, raw);
        } else if ("Boolean".equals(jsonBaseType.lexerValueType())) {
            return boolDecoder.decode(jsonBaseType, raw);
        }
        String message = String.format("Invalid type %s, to decode < %s >", jsonBaseType.identifier(), raw);
        throw new DataException(message);
    }

    private Object handlePureStringTypeDecodes(JsonBaseType jsonBaseType, String raw) {
        switch (jsonBaseType.name()) {
            case "K_STRING":
            case "K_NSTR":
                return raw;
            case "K_URI":
                try {
                    return new URL(raw);
                } catch (MalformedURLException e) {
                    return URI.create(raw);
                }
            case "K_UUID":
                return UUID.fromString(raw);
            default:
                return raw;
        }
    }

    @Override
    public String encode(FieldModel fieldModel, Object object) {
        if (object == null) {
            return "null";
        } else {
            return StringUtils.wrapInQuotes(object.toString());
        }
    }
}
