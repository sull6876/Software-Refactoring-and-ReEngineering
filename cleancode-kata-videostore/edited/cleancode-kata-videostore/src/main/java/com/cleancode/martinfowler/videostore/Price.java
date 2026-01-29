// Price.java
package com.cleancode.martinfowler.videostore;

abstract class Price {

    // Refactor reason: pricing and points rules vary by category.
    // Put the variation behind polymorphism so callers don't need switch statements.
    abstract double getCharge(int daysRented);

    int getFrequentRenterPoints(int daysRented) {
        // Default rule: 1 point per rental.
        return 1;
    }

    static Price forCode(int priceCode) {
        // Refactor reason: single mapping point from legacy int codes to behavior.
        switch (priceCode) {
            case Movie.REGULAR:
                return new RegularPrice();
            case Movie.NEW_RELEASE:
                return new NewReleasePrice();
            case Movie.CHILDRENS:
                return new ChildrensPrice();
            default:
                // Keeps behavior explicit if invalid data gets in.
                throw new IllegalArgumentException("Unknown price code: " + priceCode);
        }
    }
}
