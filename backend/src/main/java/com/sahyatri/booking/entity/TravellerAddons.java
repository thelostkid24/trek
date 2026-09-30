package com.sahyatri.booking.entity;

/** One traveller's add-ons: our insurance or their own policy ID, bag offloading, transport. */
public record TravellerAddons(boolean insurance, String ownInsuranceId, boolean offloading, boolean transport) {

    public static final TravellerAddons NONE = new TravellerAddons(false, null, false, false);

    /** Insured one way or the other. */
    public boolean covered() {
        return insurance || ownInsuranceId != null;
    }
}
