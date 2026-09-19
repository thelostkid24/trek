package com.sahyatri.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment URLs, file storage and mail settings (`app.*`). Auth and CORS have their own records. */
@ConfigurationProperties("app")
public record AppProperties(
        String publicBaseUrl,
        String frontendBaseUrl,
        Storage storage,
        Mail mail) {

    /** @param localDir root directory for uploaded files (local disk in V1) */
    public record Storage(String localDir) {
    }

    public record Mail(String from) {
    }
}
