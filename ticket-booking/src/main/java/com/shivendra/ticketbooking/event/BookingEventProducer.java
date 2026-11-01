package com.shivendra.ticketbooking.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventProducer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventProducer.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public BookingEventProducer(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                                @Value("${app.kafka.booking-topic}") String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    /** Fire-and-forget: a Kafka outage must never undo an already-committed booking. */
    public void publish(BookingConfirmedEvent event) {
        try {
            kafka.send(topic, String.valueOf(event.bookingId()), mapper.writeValueAsString(event))
                    .whenComplete((res, ex) -> {
                        if (ex != null) log.error("Failed to publish booking {} event", event.bookingId(), ex);
                    });
        } catch (JsonProcessingException e) {
            log.error("Could not serialise booking event {}", event.bookingId(), e);
        } catch (Exception e) {
            log.error("Kafka unavailable, booking {} event not sent", event.bookingId(), e);
        }
    }
}
