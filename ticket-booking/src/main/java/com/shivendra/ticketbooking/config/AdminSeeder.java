package com.shivendra.ticketbooking.config;

import com.shivendra.ticketbooking.entity.Role;
import com.shivendra.ticketbooking.entity.User;
import com.shivendra.ticketbooking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates a first admin account so you can call the /api/admin endpoints. Change the password via env vars. */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository users, PasswordEncoder encoder,
                       @Value("${app.admin.email:admin@ticket.com}") String email,
                       @Value("${app.admin.password:Admin@12345}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (users.existsByEmail(email)) return;
        User admin = new User();
        admin.setName("Admin");
        admin.setEmail(email);
        admin.setPassword(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.info("Seeded admin user {}", email);
    }
}
