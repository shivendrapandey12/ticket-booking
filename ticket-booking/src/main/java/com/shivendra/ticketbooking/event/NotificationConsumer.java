package com.shivendra.ticketbooking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Simulates the notification service: consumes booking events and "sends" the confirmation email. */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    private final ObjectMapper mapper;

    public NotificationConsumer(ObjectMapper mapper) { this.mapper = mapper; }

    @KafkaListener(topics = "${app.kafka.booking-topic}")
    public void onBookingConfirmed(String payload) throws Exception {
        BookingConfirmedEvent e = mapper.readValue(payload, BookingConfirmedEvent.class);
        log.info("EMAIL to {} | Hi {}, your booking #{} is confirmed: {} at {} on {}. Seats {} | Total Rs.{}",
                e.userEmail(), e.userName(), e.bookingId(), e.movieTitle(), e.theatreName(),
                e.showTime(), e.seats(), e.amount());
    }
}
