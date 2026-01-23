package io.github.datakore.jsont.datagen;

import io.github.datakore.jsont.marketplace.*;
import io.github.datakore.jsont.util.CollectionUtils;
import org.instancio.Instancio;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.instancio.Select.field;

public class OrderDataGenerator implements DataGenerator<Order> {

    private static final int POOL_SIZE = 10_000;
    private static final List<Order> ORDER_POOL = new ArrayList<>(POOL_SIZE);

    public void initialize() {
        System.out.println("Pre-generating 10K orders...");
        Instant start = Instant.now();

        for (int i = 0; i < POOL_SIZE; i++) {
            Order order = getOrder((long) i);
            ORDER_POOL.add(order);
        }
    }

    private static Order getOrder(long i) {
        Order order = Instancio.of(Order.class)
                .set(field(Order::getOrderId), i)
                .generate(field(Order::getOrderNumber), gen ->
                        gen.text().pattern("ORD-#d#d#d#d-#d#d#d#d"))
                .generate(field(Order::getOrderStatus), gen -> gen.enumOf(OrderStatus.class))
                .generate(field(Order::getLineItems), gen ->
                        gen.collection().maxSize(10)) // 2-10 line items
                .generate(field(Customer::getEmail), gen ->
                        gen.text().pattern("#a#a#a#a#a#a#a@example.com"))
                .generate(field(Address::getZipCode), gen ->
                        gen.text().pattern("#d#d#d#d#d"))
                .set(field(Payment::getCurrency), "USD")
                .create();
        return order;
    }

    private final SecureRandom random = new SecureRandom();

    @Override
    public Order generate(String schema) {
        if (CollectionUtils.isEmpty(ORDER_POOL)) {
            return getOrder(10);
        }
        return ORDER_POOL.get(random.nextInt(POOL_SIZE));
    }
}
