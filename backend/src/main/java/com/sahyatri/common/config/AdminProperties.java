package com.sahyatri.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** @param emails accounts promoted to ADMIN at startup (`ADMIN_EMAILS`, comma-separated) */
@ConfigurationProperties("app.admin")
public record AdminProperties(List<String> emails) {

    public AdminProperties {
        emails = emails == null ? List.of()
                : emails.stream().map(String::trim).filter(e -> !e.isEmpty()).map(String::toLowerCase).toList();
    }
}
