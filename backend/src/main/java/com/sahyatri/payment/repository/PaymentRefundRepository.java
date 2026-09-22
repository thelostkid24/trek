package com.sahyatri.payment.repository;

import com.sahyatri.payment.entity.PaymentRefund;
import com.sahyatri.payment.entity.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefund r where r.id = :id")
    Optional<PaymentRefund> findByIdForUpdate(UUID id);

    Optional<PaymentRefund> findByRazorpayRefundId(String razorpayRefundId);

    List<PaymentRefund> findByPaymentIdInOrderByCreatedAt(Collection<UUID> paymentIds);

    @Query("""
            select r.id from PaymentRefund r
            where r.status = :status and r.razorpayRefundId is null and r.createdAt <= :before""")
    List<UUID> findUnsubmittedIds(RefundStatus status, Instant before);
}
