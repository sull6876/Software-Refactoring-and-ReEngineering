// Movie.java
package com.cleancode.martinfowler.videostore;

public class Movie {

    // Kept constants for compatibility with existing code/tests.
    public static final int REGULAR = 0;
    public static final int NEW_RELEASE = 1;
    public static final int CHILDRENS = 2;

    // Refactor reason: title doesn't change after construction.
    private final String title;

    // Refactor reason: keep priceCode field because it may be used externally,
    // but delegate actual behavior to Price (polymorphism replaces switch).
    private int priceCode;
    private Price price;

    public Movie(String title, int priceCode) {
        this.title = title;
        setPriceCode(priceCode); // ensures Price is always in sync
    }

    public String getTitle() {
        return title;
    }

    public int getPriceCode() {
        return priceCode;
    }

    public void setPriceCode(int code) {
        // Refactor reason: all mapping from int code -> behavior is centralized here.
        // If a new category is added, change is localized.
        this.priceCode = code;
        this.price = Price.forCode(code);
    }

    public double getCharge(int daysRented) {
        return price.getCharge(daysRented);
    }

    public int getFrequentRenterPoints(int daysRented) {
        return price.getFrequentRenterPoints(daysRented);
    }
}
