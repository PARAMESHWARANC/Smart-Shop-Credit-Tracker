package com.smartshop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public BootstrapAdmin(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                          @Value("${shop.bootstrap.username:admin}") String username,
                          @Value("${shop.bootstrap.password:}") String password) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (users != null && users == 0) {
            if (password.length() < 12) {
                throw new IllegalStateException("Set SHOP_BOOTSTRAP_PASSWORD to at least 12 characters before starting the app.");
            }
            jdbc.update("INSERT INTO users (username, password_hash, role) VALUES (?, ?, 'ADMIN')",
                    username, passwordEncoder.encode(password));
        }
    }
}
