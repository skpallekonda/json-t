package io.github.datakore.marketplace.adapters;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.marketplace.entity.CardDetails;

import java.time.LocalDate;

public class CardDetailsAdapter implements SchemaAdapter<CardDetails> {
    @Override
    public Class<CardDetails> logicalType() {
        return CardDetails.class;
    }

    @Override
    public CardDetails createTarget() {
        return new CardDetails();
    }

    @Override
    public void set(Object target, String fieldName, Object value) {
        CardDetails cardDetails = (CardDetails) target;
        switch (fieldName) {
            case "last4Digits":
                cardDetails.setLast4Digits((String) value);
                break;
            case "cardType":
                cardDetails.setCardType((String) value);
                break;
            case "expiryDate":
                cardDetails.setExpiryDate((LocalDate) value);
                break;
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        CardDetails cardDetails = (CardDetails) target;
        switch (fieldName) {
            case "last4Digits":
                return cardDetails.getLast4Digits();
            case "cardType":
                return cardDetails.getCardType();
            case "expiryDate":
                return cardDetails.getExpiryDate();
            default:
                return null;
        }
    }
}
