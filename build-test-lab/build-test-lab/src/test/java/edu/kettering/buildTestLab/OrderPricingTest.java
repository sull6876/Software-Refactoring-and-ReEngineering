package edu.kettering.buildTestLab;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderPricingTest {

    private final OrderPricing pricing = new OrderPricing();


    @Test
    void emptyCartReturnsZero() {
        int total = pricing.totalCents(List.of());
        assertEquals(0, total);
    }


    @Test
    void subtotalAtOrAboveFreeShippingGetsFreeShipping() {
        // Subtotal = 5000 exactly
        List<OrderPricing.LineItem> items = List.of(
                new OrderPricing.LineItem("LAPTOP", 1, 5000)
        );

        int total = pricing.totalCents(items);

        assertEquals(5000, total);
    }

    
    // -------------------- Boundary / edge cases --------------------

    

    // -------------------- Validation tests --------------------

    @Test
    void nullItemListThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> pricing.totalCents(null));
    }

    

    
}

