package io.github.datakore.jsont.adapters.marketplace;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.marketplace.Category;
import io.github.datakore.jsont.marketplace.OrderLineItem;

public class OrderLineItemAdapter implements SchemaAdapter<OrderLineItem> {
    @Override
    public Class<OrderLineItem> logicalType() {
        return OrderLineItem.class;
    }

    @Override
    public OrderLineItem createTarget() {
        return new OrderLineItem();
    }

    @Override
    public void set(Object target, String fieldName, Object value) {
        OrderLineItem item = (OrderLineItem) target;
        switch (fieldName) {
            case "lineItemId":
                item.setLineItemId((Long) value);
                break;
            case "sku":
                item.setSku((String) value);
                break;
            case "productName":
                item.setProductName((String) value);
                break;
            case "quantity":
                item.setQuantity((Integer) value);
                break;
            case "unitPrice":
                item.setUnitPrice((Double) value);
                break;
            case "discount":
                item.setDiscount((Double) value);
                break;
            case "tax":
                item.setTax((Double) value);
                break;
            case "totalPrice":
                item.setTotalPrice((Double) value);
                break;
            case "category":
                item.setCategory((Category) value);
                break;
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        OrderLineItem item = (OrderLineItem) target;
        switch (fieldName) {
            case "lineItemId":
                return item.getLineItemId();
            case "sku":
                return item.getSku();
            case "productName":
                return item.getProductName();
            case "quantity":
                return item.getQuantity();
            case "unitPrice":
                return item.getUnitPrice();
            case "discount":
                return item.getDiscount();
            case "tax":
                return item.getTax();
            case "totalPrice":
                return item.getTotalPrice();
            case "category":
                return item.getCategory();
            default:
                return null;
        }
    }
}
