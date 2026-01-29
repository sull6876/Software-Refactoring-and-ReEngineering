// ChildrensPrice.java
package com.cleancode.martinfowler.videostore;

final class ChildrensPrice extends Price {

    @Override
    double getCharge(int daysRented) {
        // Same math as the original CHILDRENS switch case.
        double charge = 1.5;
        if (daysRented > 3) {
            charge += (daysRented - 3) * 1.5;
        }
        return charge;
    }
}
