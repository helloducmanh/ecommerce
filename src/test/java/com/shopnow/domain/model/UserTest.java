package com.shopnow.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void shouldCreateUserWithDefaults() {
        User user = new User("alice@example.com", "hashed-secret", "Alice", "Smith");

        assertEquals("alice@example.com", user.getEmail());
        assertEquals("hashed-secret", user.getPasswordHash());
        assertEquals("Alice", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals(UserRole.CUSTOMER, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }
}
