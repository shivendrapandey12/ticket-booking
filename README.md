# Ticket Booking System (BookMyShow-style)

[![CI](https://github.com/shivendrapandey12/ticket-booking/actions/workflows/ci.yml/badge.svg)](https://github.com/shivendrapandey12/ticket-booking/actions)

Spring Boot backend where many users compete for the same seats without ever double-booking.

**Stack:** Java 21, Spring Boot 3.3, Spring Security + JWT, Spring Data JPA (MySQL), Redis, Kafka, Docker, JUnit 5, Mockito, Testcontainers, GitHub Actions, Swagger/OpenAPI

## Architecture

```
Client ──► JWT filter ─► Rate-limit filter (Redis) ─► Controllers ─► Services
                                                                      │
        ┌─────────────────────────────┬───────────────────────────────┤
        ▼                             ▼                               ▼
 Redis seat locks (SET NX, TTL)   MySQL (JPA, @Version)      Kafka topic: booking-events
 hold seats for 5 minutes         source of truth             └─► NotificationConsumer (email)
```

## Booking flow

1. `POST /api/shows/{id}/hold` — seats are locked in Redis (`SET NX EX 300`) and a `PENDING` booking is created. All-or-nothing: if one seat is taken, the others are released.
2. `POST /api/bookings/{id}/pay` with an `Idempotency-Key` header — checks the lock is still yours, marks seats `BOOKED`, stores the payment, confirms the booking, releases the lock and publishes a Kafka event.
3. Abandon step 2 and the Redis TTL frees the seats by itself; a scheduler marks the booking `EXPIRED`.

## How double booking is prevented (3 layers)

| Layer | Where | What it stops |
|---|---|---|
| Redis `SET NX` + TTL | `SeatLockService` | Two users holding the same seat at the same time |
| Status check in a DB transaction | `BookingService.confirm` | Paying for a seat that is already `BOOKED` |
| JPA optimistic locking (`@Version`) | `ShowSeat` | Two transactions updating the same seat row at once (second one gets HTTP 409) |

Lock release uses a Lua compare-and-delete script, so a user can never release someone else's lock.

**Idempotent payments:** `payments.idempotency_key` is unique. A retried request returns the original result and never charges twice, even when two identical requests race.

## Run it

```bash
docker compose up --build        # app + MySQL + Redis + Kafka
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Seeded admin: `admin@ticket.com` / `Admin@12345` (override with `ADMIN_EMAIL`, `ADMIN_PASSWORD`)
- Set `JWT_SECRET` (32+ chars) in any real deployment.

Run locally without Docker for the app: start MySQL, Redis and Kafka (`docker compose up mysql redis kafka`), then `mvn spring-boot:run`. If you run the app outside Docker, change `KAFKA_ADVERTISED_LISTENERS` in `docker-compose.yml` to `PLAINTEXT://localhost:9092`.

## Try it (curl)

```bash
# 1. admin login
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@ticket.com","password":"Admin@12345"}' | jq -r .accessToken)

# 2. create a movie and a show (5 rows x 10 seats)
curl -X POST localhost:8080/api/admin/movies -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Interstellar","language":"English","genre":"Sci-Fi","durationMinutes":169}'
curl -X POST localhost:8080/api/admin/shows -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"movieId":1,"theatreName":"PVR Orion","screenName":"Screen 1","startTime":"2030-01-01T18:30:00","rows":5,"seatsPerRow":10,"price":250}'

# 3. register as a user, hold seats, pay
USER=$(curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Ravi","email":"ravi@example.com","password":"password123"}' | jq -r .accessToken)
curl -X POST localhost:8080/api/shows/1/hold -H "Authorization: Bearer $USER" -H 'Content-Type: application/json' \
  -d '{"seatNumbers":["A1","A2"]}'
curl -X POST localhost:8080/api/bookings/1/pay -H "Authorization: Bearer $USER" -H 'Idempotency-Key: order-001'
```

## API

| Method | Path | Auth |
|---|---|---|
| POST | `/api/auth/register`, `/login`, `/refresh` | public |
| GET | `/api/movies`, `/api/movies/{id}/shows`, `/api/shows/{id}/seats` | public |
| POST | `/api/admin/movies`, `/api/admin/shows` | ADMIN |
| POST | `/api/shows/{id}/hold` | USER |
| POST | `/api/bookings/{id}/pay` (header `Idempotency-Key`) | USER |
| DELETE | `/api/bookings/{id}` (release a hold) | USER |
| GET | `/api/bookings/me` | USER |

Booking endpoints are rate limited (30 requests/min per client, Redis-backed).

## Tests

```bash
mvn test       # unit tests (no Docker needed)
mvn verify     # + Testcontainers integration tests (needs Docker)
```

`ConcurrentBookingIT` starts real MySQL, Redis and Kafka containers and checks that **50 users racing for one seat produce exactly one winner**, that **a retried payment charges once**, and that a booked seat cannot be held again.

## CI/CD

`.github/workflows/ci.yml` runs `mvn verify` (including Testcontainers) on every push and PR. To deploy, push the Docker image to Render, Railway or AWS and point the `DB_URL`, `REDIS_HOST`, `KAFKA_BOOTSTRAP` and `JWT_SECRET` env vars at managed services.
