package com.sahyatri.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Charity share (`app.charity`). Part of the price, never added on top; shown on the trek page.
 *
 * @param name the charity, e.g. "Cancer Patients Aid Association"; blank hides the line
 * @param bps  share of each booking, in basis points
 */
@ConfigurationProperties("app.charity")
public record CharityProperties(String name, int bps) {

    public CharityProperties {
        if (bps < 0 || bps > 10_000) {
            throw new IllegalStateException("app.charity.bps must be 0-10000");
        }
        name = name == null || name.isBlank() ? null : name.trim();
    }
}
