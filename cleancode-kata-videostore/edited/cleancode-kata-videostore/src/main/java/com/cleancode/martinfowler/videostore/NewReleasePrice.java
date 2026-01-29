// NewReleasePrice.java
package com.cleancode.martinfowler.videostore;

final class NewReleasePrice extends Price {

    @Override
    double getCharge(int daysRented) {
        // Same math as the original NEW_RELEASE switch case.
        return daysRented * 3.0;
    }

    @Override
    int getFrequentRenterPoints(int daysRented) {
        // Same rule as original: bonus point for >1 day new release.
        return (daysRented > 1) ? 2 : 1;
    }
}
