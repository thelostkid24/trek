package com.sahyatri.account.dto;

/** Either field left out keeps that channel as it is. */
public record MarketingConsentRequest(Boolean email, Boolean whatsapp) {
}
