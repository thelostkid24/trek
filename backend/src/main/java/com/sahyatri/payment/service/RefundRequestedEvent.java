package com.sahyatri.payment.service;

import java.util.UUID;

record RefundRequestedEvent(UUID refundId) {
}
