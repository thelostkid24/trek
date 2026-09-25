package com.sahyatri.insights.dto;

/** Trekker accounts now, and how many of them agreed to trek offers on each channel. */
public record MarketingReach(long accounts, long email, long whatsapp) {
}
