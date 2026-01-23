package io.github.datakore.jsont.adapters.marketplace;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.marketplace.*;
import io.github.datakore.jsont.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class OrderAdapter implements SchemaAdapter<Order> {
    @Override
    public Class<Order> logicalType() {
        return Order.class;
    }

    @Override
    public Order createTarget() {
        return new Order();
    }

    @Override
    public void set(Object target, String fieldName, Object value) {
        Order order = (Order) target;
        switch (fieldName) {
            case "orderId":
                order.setOrderId((Long) value);
                break;
            case "orderNumber":
                order.setOrderNumber((String) value);
                break;
            case "orderDate":
                order.setOrderDate((LocalDate) value);
                break;
            case "orderStatus":
                if (value instanceof String) {
                    order.setOrderStatus(OrderStatus.valueOf((String) value));
                } else {
                    order.setOrderStatus((OrderStatus) value);
                }
                break;
            case "customer":
                order.setCustomer((Customer) value);
                break;
            case "lineItems":
                order.setLineItems(CollectionUtils.toList(value, OrderLineItem.class));
                break;
            case "payment":
                order.setPayment((Payment) value);
                break;
            case "shipping":
                order.setShipping((Shipping) value);
                break;
            case "subtotal":
                order.setSubtotal((Double) value);
                break;
            case "totalTax":
                order.setTotalTax((Double) value);
                break;
            case "totalDiscount":
                order.setTotalDiscount((Double) value);
                break;
            case "shippingCost":
                order.setShippingCost((Double) value);
                break;
            case "grandTotal":
                order.setGrandTotal((Double) value);
                break;
            case "createdAt":
                order.setCreatedAt((LocalDate) value);
                break;
            case "updatedAt":
                order.setUpdatedAt((LocalDate) value);
                break;
            case "createdBy":
                order.setCreatedBy((String) value);
                break;
            case "lastModifiedBy":
                order.setLastModifiedBy((String) value);
                break;
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        Order order = (Order) target;
        switch (fieldName) {
            case "orderId":
                return order.getOrderId();
            case "orderNumber":
                return order.getOrderNumber();
            case "orderDate":
                return order.getOrderDate();
            case "orderStatus":
                return order.getOrderStatus();
            case "customer":
                return order.getCustomer();
            case "lineItems":
                return order.getLineItems();
            case "payment":
                return order.getPayment();
            case "shipping":
                return order.getShipping();
            case "subtotal":
                return order.getSubtotal();
            case "totalTax":
                return order.getTotalTax();
            case "totalDiscount":
                return order.getTotalDiscount();
            case "shippingCost":
                return order.getShippingCost();
            case "grandTotal":
                return order.getGrandTotal();
            case "createdAt":
                return order.getCreatedAt();
            case "updatedAt":
                return order.getUpdatedAt();
            case "createdBy":
                return order.getCreatedBy();
            case "lastModifiedBy":
                return order.getLastModifiedBy();
            default:
                return null;
        }
    }
}
