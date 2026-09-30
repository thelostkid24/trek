package com.sahyatri.booking.entity;

/**
 * How many seats take each add-on, at the trek's prices when the booking was held. A price is null when the trek
 * doesn't offer that add-on (its count is then 0).
 */
public record AddonChoice(int insuranceSeats, Long insurancePricePaise, int offloadingSeats, Long offloadingPricePaise,
                          int transportSeats, Long transportPricePaise) {

    public static final AddonChoice NONE = new AddonChoice(0, null, 0, null, 0, null);

    public long totalPaise() {
        return insuranceSeats * orZero(insurancePricePaise) + offloadingSeats * orZero(offloadingPricePaise)
                + transportSeats * orZero(transportPricePaise);
    }

    private static long orZero(Long paise) {
        return paise == null ? 0 : paise;
    }
}
