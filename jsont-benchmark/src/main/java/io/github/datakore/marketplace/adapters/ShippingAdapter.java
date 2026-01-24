package io.github.datakore.marketplace.adapters;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.marketplace.entity.Shipping;
import io.github.datakore.marketplace.entity.ShippingMethod;

import java.time.LocalDate;

public class ShippingAdapter implements SchemaAdapter<Shipping> {
    @Override
    public Class<Shipping> logicalType() {
        return Shipping.class;
    }

    @Override
    public Shipping createTarget() {
        return new Shipping();
    }

    @Override
    public void set(Object target, String fieldName, Object value) {
        Shipping shipping = (Shipping) target;
        switch (fieldName) {
            case "carrier":
                shipping.setCarrier((String) value);
                break;
            case "trackingNumber":
                shipping.setTrackingNumber((String) value);
                break;
            case "estimatedDelivery":
                shipping.setEstimatedDelivery((LocalDate) value);
                break;
            case "actualDelivery":
                shipping.setActualDelivery((LocalDate) value);
                break;
            case "shippingCost":
                shipping.setShippingCost((Double) value);
                break;
            case "shippingMethod":
                if (value instanceof String) {
                    shipping.setShippingMethod(ShippingMethod.valueOf((String) value));
                } else {
                    shipping.setShippingMethod((ShippingMethod) value);
                }
                break;
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        Shipping shipping = (Shipping) target;
        switch (fieldName) {
            case "carrier":
                return shipping.getCarrier();
            case "trackingNumber":
                return shipping.getTrackingNumber();
            case "estimatedDelivery":
                return shipping.getEstimatedDelivery();
            case "actualDelivery":
                return shipping.getActualDelivery();
            case "shippingCost":
                return shipping.getShippingCost();
            case "shippingMethod":
                return shipping.getShippingMethod();
            default:
                return null;
        }
    }
}
