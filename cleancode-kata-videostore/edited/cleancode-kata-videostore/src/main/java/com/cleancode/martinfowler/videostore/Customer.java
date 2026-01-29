// Customer.java
package com.cleancode.martinfowler.videostore;

import java.util.ArrayList;
import java.util.List;

public class Customer {

    // Refactor reason: name and rentals list reference never change after construction.
    private final String name;
    private final List<Rental> rentals = new ArrayList<>();

    public Customer(String name) {
        this.name = name;
    }

    public void addRental(Rental rental) {
        rentals.add(rental);
    }

    public String getName() {
        return name;
    }

    public String statement() {
        // Refactor reason: Customer should mainly orchestrate and format,
        // not own pricing rules. Pricing and points now come from Rental/Movie/Price.
        double totalCharge = 0.0;
        int totalPoints = 0;

        StringBuilder result = new StringBuilder();
        result.append("Rental Record for ").append(getName()).append("\n");

        for (Rental rental : rentals) {
            double charge = rental.getCharge();
            int points = rental.getFrequentRenterPoints();

            totalCharge += charge;
            totalPoints += points;

            result.append("\t")
                  .append(rental.getMovie().getTitle())
                  .append("\t")
                  .append(charge)
                  .append("\n");
        }

        result.append("You owed ").append(totalCharge).append("\n");
        result.append("You earned ").append(totalPoints).append(" frequent renter points\n");

        return result.toString();
    }
}
