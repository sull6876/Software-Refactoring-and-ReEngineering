// Rental.java
package com.cleancode.martinfowler.videostore;

public class Rental {

    // Refactor reason: immutable value-like object, fields don't change after creation.
    private final Movie movie;
    private final int daysRented;

    public Rental(Movie movie, int daysRented) {
        this.movie = movie;
        this.daysRented = daysRented;
    }

    public Movie getMovie() {
        return movie;
    }

    public int getDaysRented() {
        return daysRented;
    }

    public double getCharge() {
        // Refactor reason: Rental is the right level to answer "what did this rental cost?"
        return movie.getCharge(daysRented);
    }

    public int getFrequentRenterPoints() {
        // Refactor reason: same idea for points, avoids Customer knowing category rules.
        return movie.getFrequentRenterPoints(daysRented);
    }
}
