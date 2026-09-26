package com.sahyatri.insights.dto;

import java.util.List;

/**
 * Razorpay orders created in the window.
 *
 * @param successBps paid ÷ settled (paid, failed or expired); null with none settled
 * @param methods    paid orders by method
 * @param failures   failed orders by reason, most common first
 */
public record PaymentSummary(long attempts, long paid, long failed, Integer successBps, List<CountRow> methods,
                             List<CountRow> failures) {
}
