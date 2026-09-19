package com.sahyatri.catalog.service;

import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.catalog.entity.DepartureStatus;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.common.audit.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Daily housekeeping. Law 9: a published departure that reaches its start date with no seats sold expires;
 * that is not a cancellation. A departure past its end date completes.
 */
@Component
public class DepartureLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(DepartureLifecycleJob.class);

    private final DepartureRepository departures;
    private final CatalogService catalog;
    private final AuditLog audit;
    private final TransactionTemplate tx;

    public DepartureLifecycleJob(DepartureRepository departures, CatalogService catalog, AuditLog audit,
                                 TransactionTemplate tx) {
        this.departures = departures;
        this.catalog = catalog;
        this.audit = audit;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Kolkata")
    public void run() {
        LocalDate today = catalog.today();
        for (UUID id : departures.findUnsoldStartedIds(DepartureStatus.PUBLISHED, today)) {
            transition(id, d -> d.getSeatsTaken() == 0 && !d.getStartDate().isAfter(today),
                    Departure::expire, "DEPARTURE_EXPIRED");
        }
        for (UUID id : departures.findEndedIds(DepartureStatus.PUBLISHED, today)) {
            transition(id, d -> d.getEndDate().isBefore(today), Departure::complete, "DEPARTURE_COMPLETED");
        }
    }

    /** One transaction per departure; the condition is re-checked under the row lock. */
    private void transition(UUID id, Predicate<Departure> stillApplies, Consumer<Departure> change, String action) {
        tx.executeWithoutResult(status -> departures.findByIdForUpdate(id)
                .filter(d -> d.getStatus() == DepartureStatus.PUBLISHED && stillApplies.test(d))
                .ifPresent(d -> {
                    change.accept(d);
                    departures.saveAndFlush(d);
                    audit.record(null, action, DepartureAdminService.ENTITY, id, Map.of());
                    log.info("{} {}", action, id);
                }));
    }
}
