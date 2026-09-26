package com.sahyatri.common.acquisition;

import java.util.Locale;

public enum DeviceType {
    MOBILE, TABLET, DESKTOP;

    /** Lenient: an unknown value is dropped, never an error. */
    static DeviceType parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
