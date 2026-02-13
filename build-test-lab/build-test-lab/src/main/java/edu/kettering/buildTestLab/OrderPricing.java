package edu.kettering.buildTestLab;

import java.util.List;
import java.util.Objects;

public class OrderPricing {

    // -------------------- Configuration constants --------------------

    public static final int FREE_SHIPPING_THRESHOLD_CENTS = 5000;
    public static final int SHIPPING_CENTS = 799;

    public static final int BULK_QTY_THRESHOLD = 10;
    public static final int BULK_BONUS_CENTS = 300;

    // -------------------- Data model --------------------

    /**
     * A single line item in an order.
     * Validation is done at construction time.
     */
    public record LineItem(String sku, int quantity, int unitPriceCents) {
        public LineItem {
            if (sku == null || sku.isBlank()) throw new IllegalArgumentException("bad sku");
            if (quantity <= 0) throw new IllegalArgumentException("bad qty");
            if (unitPriceCents < 0) throw new IllegalArgumentException("bad price");
        }

        public int lineTotalCents() {
            return quantity * unitPriceCents;
        }
    }

    // -------------------- Public API --------------------

    /**
     * Computes the final total price in cents.
     * Rules:
     *  - subtotal = sum(quantity * unitPriceCents)
     *  - shipping is free if subtotal >= FREE_SHIPPING_THRESHOLD_CENTS else SHIPPING_CENTS
     *  - if total quantity >= BULK_QTY_THRESHOLD, subtract BULK_BONUS_CENTS
     *  - total is never negative
     */
    public int totalCents(List<LineItem> items) {
        validateItemsList(items);
        if (items.isEmpty()) return 0;

        int subtotal = subtotalCents(items);
        int shipping = shippingCents(subtotal);
        int bulkBonus = bulkBonusCents(totalQuantity(items));

        return clampNonNegative(subtotal + shipping - bulkBonus);
    }

    // -------------------- Helpers --------------------

    private static void validateItemsList(List<LineItem> items) {
        if (items == null) throw new IllegalArgumentException("items is null");
        for (LineItem it : items) {
            Objects.requireNonNull(it, "null item");
        }
    }

    private static int subtotalCents(List<LineItem> items) {
        int subtotal = 0;
        for (LineItem it : items) subtotal += it.lineTotalCents();
        return subtotal;
    }

    private static int totalQuantity(List<LineItem> items) {
        int qty = 0;
        for (LineItem it : items) qty += it.quantity();
        return qty;
    }

    private static int shippingCents(int subtotalCents) {
        return (subtotalCents >= FREE_SHIPPING_THRESHOLD_CENTS) ? 0 : SHIPPING_CENTS;
    }

    private static int bulkBonusCents(int totalQty) {
        return (totalQty >= BULK_QTY_THRESHOLD) ? BULK_BONUS_CENTS : 0;
    }

    private static int clampNonNegative(int cents) {
        return Math.max(0, cents);
    }

    // -------------------- Demo main (NOT for testing) --------------------

    public static void main(String[] args) {
        OrderPricing pricing = new OrderPricing();

        // Cart: 2 textbooks at $30.00 and 1 notebook at $5.00
        // Subtotal = 6500 cents -> free shipping, not bulk
        List<LineItem> cart = List.of(
                new LineItem("TEXTBOOK", 2, 3000),
                new LineItem("NOTEBOOK", 1, 500)
        );

        int total = pricing.totalCents(cart);
        System.out.println("Total (cents): " + total);
        System.out.println("Total (dollars): $" + (total / 100.0));

        // Bulk example: 10 pens at $2.00 each
        // Subtotal = 2000 -> shipping 799, bulk bonus 300
        List<LineItem> bulkCart = List.of(
                new LineItem("PEN", 10, 200)
        );

        int bulkTotal = pricing.totalCents(bulkCart);
        System.out.println("\nBulk total (cents): " + bulkTotal);
        System.out.println("Bulk total (dollars): $" + (bulkTotal / 100.0));
    }
}
