package com.sahyatri.booking;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingNotificationTests extends AuthTestSupport {

    @Test
    void confirmationAndCancellationAreEmailedToTheContact() throws Exception {
        String token = bookingTrekker();
        UUID departure = publishedDeparture(today().plusDays(20), 219_900, 6);
        UUID booking = confirmedBooking(token, departure, 2);

        List<Mail> sent = sentMails(booking);
        assertThat(sent).singleElement().satisfies(m -> {
            assertThat(m.subject()).startsWith("Booked: ");
            assertThat(m.text()).contains("Seats: 2", "₹4,398", "/account/bookings/" + booking);
            assertThat(m.to()).isEqualTo(contactEmail(booking));
        });

        authed(post("/api/trekker/bookings/" + booking + "/cancel"), token, null).andExpect(status().isOk());
        assertThat(sentMails(booking)).last().satisfies(m -> {
            assertThat(m.subject()).startsWith("Cancelled: ");
            assertThat(m.text()).contains("A refund of ₹3,958.20");
        });
    }

    @Test
    void aMailFailureNeverUndoesThePayment() throws Exception {
        doThrow(new RuntimeException("SES down")).when(mailTransport).send(anyString(), anyString(), anyString());
        UUID booking = confirmedBooking(bookingTrekker(), publishedDeparture(), 1);
        assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");
    }

    private String contactEmail(UUID booking) {
        return jdbc.queryForObject("SELECT contact_email FROM bookings WHERE id = ?", String.class, booking);
    }

    private List<Mail> sentMails(UUID booking) {
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailTransport, atLeastOnce()).send(to.capture(), subject.capture(), text.capture());
        return IntStream.range(0, to.getAllValues().size())
                .mapToObj(i -> new Mail(to.getAllValues().get(i), subject.getAllValues().get(i), text.getAllValues().get(i)))
                .filter(m -> m.text().contains(booking.toString()))
                .toList();
    }

    private record Mail(String to, String subject, String text) {
    }
}
