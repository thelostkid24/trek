package com.sahyatri.common.acquisition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Sent with every sign-up, sign-in and booking (§7.15). Only a new account keeps the first touch, heard-from and
 * consent; a booking keeps the last touch. All optional.
 *
 * @param deviceType MOBILE, TABLET or DESKTOP; anything else is ignored
 */
public record AcquisitionRequest(
        @Valid TouchRequest firstTouch,
        @Valid TouchRequest lastTouch,
        String deviceType,
        HeardFrom heardFrom,
        @Size(max = 200) String heardFromNote,
        Boolean marketingEmail,
        Boolean marketingWhatsapp) {

    /** The first touch, else the last (browser storage may have lost the first). */
    public TouchRequest firstOrLast() {
        return firstTouch != null ? firstTouch : lastTouch;
    }

    /** The last touch, else the first. */
    public TouchRequest lastOrFirst() {
        return lastTouch != null ? lastTouch : firstTouch;
    }

    public DeviceType device() {
        return DeviceType.parse(deviceType);
    }
}
