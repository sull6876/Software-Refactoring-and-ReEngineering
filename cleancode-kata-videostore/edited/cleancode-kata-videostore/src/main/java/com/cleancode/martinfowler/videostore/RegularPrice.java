// RegularPrice.java
package com.cleancode.martinfowler.videostore;

final class RegularPrice extends Price {

    @Override
    double getCharge(int daysRented) {
        // Same math as the original REGULAR switch case.
        double charge = 2.0;
        if (daysRented > 2) {
            charge += (daysRented - 2) * 1.5;
        }
        return charge;
    }
}
