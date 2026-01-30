package io.github.datakore.jsont.grammar.schema.coded;

import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;
import io.github.datakore.jsont.grammar.types.ScalarType;
import io.github.datakore.jsont.util.StringUtils;

import java.util.Base64;

public class BinaryEncodeDecoder implements EncodeDecoder {

    @Override
    public Object decode(JsonBaseType jsonBaseType, String raw) {
        if (raw == null) {
            return null;
        }
        raw = StringUtils.removeQuotes(raw);
        String lexerType = jsonBaseType.lexerValueType();
        switch (lexerType) {
            case "Binary":
                // Note: K_BIN was used for base64, K_HEX for hex string.
                // The logic here seems to imply K_BIN is also hex if it starts with "0x"
                // This might be a bug in the original logic, but I'm just updating the enum name.
                if (raw.startsWith("0x") && JsonBaseType.BIN.equals(jsonBaseType)) {
                    return Base64.getDecoder().decode(raw.substring(2));
                } else if (JsonBaseType.HEX.equals(jsonBaseType)) {
                    return hexToBytes(raw);
                }
            default:
                String message = String.format("Invalid type %s, to decode < %s >", jsonBaseType.identifier(), raw);
                throw new DataException(message);
        }
    }

    private byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            int digit1 = Character.digit(s.charAt(i), 16);
            int digit2 = Character.digit(s.charAt(i + 1), 16);

            // Character.digit returns -1 if char is not a valid hex digit
            if (digit1 == -1 || digit2 == -1) {
                throw new IllegalArgumentException("Non-hex character detected");
            }

            data[i / 2] = (byte) ((digit1 << 4) + digit2);
        }
        return data;
    }

    @Override
    public String encode(FieldModel fieldModel, Object object) {
        if (object == null) {
            return "null";
        }
        JsonBaseType jsonBaseType = ((ScalarType) fieldModel.getFieldType()).elementType();
        if (JsonBaseType.BIN.equals(jsonBaseType)) {
            return Base64.getEncoder().encodeToString((byte[]) object);
        } else if (JsonBaseType.HEX.equals(jsonBaseType)) {
            return bytesToHex((byte[]) object);
        }
        String message = String.format("Invalid type %s, to encode < %s >", jsonBaseType.identifier(), object);
        throw new DataException(message);
    }

    private String bytesToHex(byte[] data) {
        StringBuilder hex = new StringBuilder(data.length * 2);
        for (byte b : data) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }
}
