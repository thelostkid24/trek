package com.sahyatri.insights.dto;

import java.time.LocalDate;

/** One day (IST): trekker accounts created and bookings confirmed. */
public record DailyPoint(LocalDate date, long accounts, long confirmed) {
}
