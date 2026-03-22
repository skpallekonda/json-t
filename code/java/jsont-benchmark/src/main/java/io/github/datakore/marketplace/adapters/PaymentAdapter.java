package io.github.datakore.marketplace.adapters;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.marketplace.entity.CardDetails;
import io.github.datakore.marketplace.entity.Payment;
import io.github.datakore.marketplace.entity.PaymentMethod;

import java.time.LocalDate;

public class PaymentAdapter implements SchemaAdapter<Payment> {
    @Override
    public Class<Payment> logicalType() {
        return Payment.class;
    }

    @Override
    public Payment createTarget() {
        return new Payment();
    }

    @Override
    public void set(Object target, String fieldName, Object value) {
        Payment payment = (Payment) target;
        switch (fieldName) {
            case "paymentMethod":
                if (value instanceof String) {
                    payment.setPaymentMethod(PaymentMethod.valueOf((String) value));
                } else {
                    payment.setPaymentMethod((PaymentMethod) value);
                }
                break;
            case "transactionId":
                payment.setTransactionId((String) value);
                break;
            case "paymentDate":
                payment.setPaymentDate((LocalDate) value);
                break;
            case "amount":
                payment.setAmount((Double) value);
                break;
            case "currency":
                payment.setCurrency((String) value);
                break;
            case "cardDetails":
                payment.setCardDetails((CardDetails) value);
                break;
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        Payment payment = (Payment) target;
        switch (fieldName) {
            case "paymentMethod":
                return payment.getPaymentMethod();
            case "transactionId":
                return payment.getTransactionId();
            case "paymentDate":
                return payment.getPaymentDate();
            case "amount":
                return payment.getAmount();
            case "currency":
                return payment.getCurrency();
            case "cardDetails":
                return payment.getCardDetails();
            default:
                return null;
        }
    }
}
