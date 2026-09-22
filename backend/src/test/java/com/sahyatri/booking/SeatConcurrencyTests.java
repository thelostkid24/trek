package com.sahyatri.booking;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Law 2 under contention: parallel holds never oversell a departure. */
class SeatConcurrencyTests extends AuthTestSupport {

    @Test
    void parallelHoldsNeverExceedTheBatch() throws Exception {
        UUID departure = publishedDeparture(today().plusDays(30), 150_000, 6);
        int trekkers = 10;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < trekkers; i++) {
            tokens.add(bookingTrekker());
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(trekkers)) {
            for (int i = 0; i < trekkers; i++) {
                String token = tokens.get(i);
                int seats = i % 2 == 0 ? 1 : 2;
                results.add(pool.submit(() -> {
                    start.await();
                    return requestHold(token, departure, seats).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            int created = 0;
            int rejected = 0;
            for (Future<Integer> result : results) {
                int status = result.get();
                if (status == 201) created++;
                else if (status == 409) rejected++;
            }
            assertThat(created + rejected).isEqualTo(trekkers);
            assertThat(created).isPositive();
        }

        Integer heldSeats = jdbc.queryForObject(
                "SELECT coalesce(sum(seats), 0) FROM bookings WHERE departure_id = ? AND status = 'HELD'",
                Integer.class, departure);
        assertThat(heldSeats).isLessThanOrEqualTo(6).isEqualTo(seatsTaken(departure));
        // 10 trekkers want 15 seats: the batch fills (every remaining request needs more seats than are left).
        assertThat(seatsTaken(departure)).isGreaterThanOrEqualTo(5);
    }
}
