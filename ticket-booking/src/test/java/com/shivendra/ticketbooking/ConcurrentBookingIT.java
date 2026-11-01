package com.shivendra.ticketbooking;

import com.shivendra.ticketbooking.dto.Dtos.*;
import com.shivendra.ticketbooking.entity.BookingStatus;
import com.shivendra.ticketbooking.exception.ApiException;
import com.shivendra.ticketbooking.repository.PaymentRepository;
import com.shivendra.ticketbooking.security.AuthUser;
import com.shivendra.ticketbooking.security.JwtService;
import com.shivendra.ticketbooking.service.AuthService;
import com.shivendra.ticketbooking.service.BookingService;
import com.shivendra.ticketbooking.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against real MySQL, Redis and Kafka containers. Needs Docker. Executed by: mvn verify */
@SpringBootTest
@Testcontainers
class ConcurrentBookingIT {

    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired AuthService auth;
    @Autowired CatalogService catalog;
    @Autowired BookingService bookings;
    @Autowired PaymentRepository payments;
    @Autowired JwtService jwt;

    private AuthUser newUser() {
        String email = UUID.randomUUID() + "@test.com";
        String token = auth.register(new RegisterRequest("Tester", email, "password123")).accessToken();
        return jwt.parse(token).orElseThrow();
    }

    private Long newShow(int seatsPerRow) {
        var movie = catalog.createMovie(new MovieRequest("Test Movie", "EN", "Action", 120));
        return catalog.createShow(new ShowRequest(movie.getId(), "PVR", "Screen 1",
                LocalDateTime.now().plusDays(1), 1, seatsPerRow, new BigDecimal("250"))).id();
    }

    @Test
    void fiftyUsersRacingForOneSeat_onlyOneWins() throws Exception {
        Long showId = newShow(5);
        int users = 50;
        List<AuthUser> people = new java.util.ArrayList<>();
        for (int i = 0; i < users; i++) people.add(newUser());

        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();

        for (AuthUser u : people) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    bookings.hold(u, showId, new HoldRequest(List.of("A1")));
                    success.incrementAndGet();
                } catch (ApiException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, success.get(), "exactly one user may hold the seat");
        assertEquals(users - 1, rejected.get());
    }

    @Test
    void payingTwiceWithSameIdempotencyKey_chargesOnce() {
        Long showId = newShow(5);
        AuthUser user = newUser();
        BookingResponse held = bookings.hold(user, showId, new HoldRequest(List.of("A2")));
        long before = payments.count();

        BookingResponse first = bookings.pay(user, held.id(), "key-" + held.id());
        BookingResponse retry = bookings.pay(user, held.id(), "key-" + held.id());

        assertEquals(BookingStatus.CONFIRMED, first.status());
        assertEquals(first.id(), retry.id());
        assertEquals(before + 1, payments.count(), "retry must not create a second payment");
    }

    @Test
    void bookedSeatCannotBeHeldAgain() {
        Long showId = newShow(5);
        AuthUser a = newUser();
        AuthUser b = newUser();
        BookingResponse held = bookings.hold(a, showId, new HoldRequest(List.of("A3")));
        bookings.pay(a, held.id(), "key-" + held.id());

        assertThrows(ApiException.class, () -> bookings.hold(b, showId, new HoldRequest(List.of("A3"))));
    }
}
