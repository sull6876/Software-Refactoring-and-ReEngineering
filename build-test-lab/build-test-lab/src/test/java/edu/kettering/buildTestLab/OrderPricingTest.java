package edu.kettering.buildTestLab;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

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

    @Test
    void subtotalJustBelowFreeShippingPaysShipping() {
        // Subtotal = 4999 -> shipping applies (799)
        List<OrderPricing.LineItem> items = List.of(
                new OrderPricing.LineItem("MOUSE", 1, 4999)
        );

        int total = pricing.totalCents(items);

        assertEquals(4999 + OrderPricing.SHIPPING_CENTS, total);
    }

    @Test
    void bulkBonusAppliedWhenTotalQuantityAtThreshold() {
        // Qty = 10 (threshold) -> bulk bonus applies (300)
        // Subtotal = 10 * 200 = 2000 -> shipping applies (799)
        // Total = 2000 + 799 - 300 = 2499
        List<OrderPricing.LineItem> items = List.of(
                new OrderPricing.LineItem("PEN", 10, 200)
        );

        int total = pricing.totalCents(items);

        assertEquals(2499, total);
    }

    @Test
    void nullItemInsideListThrowsNullPointerException() {
        List<OrderPricing.LineItem> items = List.of(
                new OrderPricing.LineItem("BOOK", 1, 1000),
                null
        );

        assertThrows(NullPointerException.class, () -> pricing.totalCents(items));
    }

    /**
     * INTENTIONALLY FAILING TEST:
     * Reveals integer overflow in subtotal calculation (quantity * unitPriceCents uses int).
     * Correct subtotal is 2,500,000,000 cents, which does not fit in a signed 32-bit int.
     */
    @Test
    void largeOrderShouldNotOverflowSubtotal_intentionallyFailing() {
        List<OrderPricing.LineItem> items = List.of(
                new OrderPricing.LineItem("BULK_ITEM", 50_000, 50_000) // 2,500,000,000 cents subtotal
        );

        long total = pricing.totalCents(items);

        // Shipping should be free (subtotal >= 5000), bulk bonus applies (qty >= 10)
        long expected = 2_500_000_000L - OrderPricing.BULK_BONUS_CENTS;

        assertEquals(expected, total);
    }

    // -------------------- Validation tests --------------------

    @Test
    void nullItemListThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> pricing.totalCents(null));
    }
}
