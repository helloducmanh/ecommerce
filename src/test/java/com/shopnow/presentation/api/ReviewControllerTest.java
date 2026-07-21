// src/test/java/com/shopnow/presentation/api/ReviewControllerTest.java
package com.shopnow.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopnow.application.review.ReviewService;
import com.shopnow.domain.model.User;
import com.shopnow.domain.port.UserRepository;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.infrastructure.security.TestSecurityConfig;
import com.shopnow.infrastructure.security.UserPrincipal;
import com.shopnow.presentation.dto.CreateReviewRequest;
import com.shopnow.presentation.dto.ReviewDto;
import com.shopnow.presentation.dto.ReviewPageDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private org.springframework.security.core.Authentication principal() {
        UserPrincipal user = new UserPrincipal(1L, "alice@example.com", "CUSTOMER");
        return new UsernamePasswordAuthenticationToken(user, null, user.authorities());
    }

    @Test
    void shouldListReviewsPublicly() throws Exception {
        when(reviewService.listReviews("iphone-15", 0, 10)).thenReturn(
                new ReviewPageDto(10L, new BigDecimal("4.5"), 2, List.of(), 2L, 1, 0));

        mockMvc.perform(get("/api/v1/products/iphone-15/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(10))
                .andExpect(jsonPath("$.avgRating").value(4.5));
    }

    @Test
    void shouldCreateReviewWhenAuthenticated() throws Exception {
        User user = new User("alice@example.com", "hash", "Alice", "Smith");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(reviewService.createReview(eq(1L), eq("Alice Smith"), eq("iphone-15"), eq(5), eq("Great")))
                .thenReturn(new ReviewDto(1L, 10L, 1L, "Alice Smith", 5, "Great", true, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/products/iphone-15/reviews")
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(5, "Great"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.userName").value("Alice Smith"));
    }

    @Test
    void shouldRejectCreateWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/products/iphone-15/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(5, "Great"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCreateWithInvalidRating() throws Exception {
        mockMvc.perform(post("/api/v1/products/iphone-15/reviews")
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(9, "Great"))))
                .andExpect(status().isBadRequest());
    }
}
