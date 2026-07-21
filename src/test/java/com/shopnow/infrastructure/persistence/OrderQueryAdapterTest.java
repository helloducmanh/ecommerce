package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Category;
import com.shopnow.domain.model.Order;
import com.shopnow.domain.model.OrderItem;
import com.shopnow.domain.model.Product;
import com.shopnow.domain.model.ProductVariant;
import com.shopnow.domain.port.OrderQueryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderQueryAdapter.class)
class OrderQueryAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestEntityManager em;

    @Autowired
    private OrderQueryPort orderQueryPort;

    private Long productId(int n) {
        Category category = em.persistFlushFind(new Category("Electronics", "electronics-" + n));
        Product p = em.persistFlushFind(new Product("Phone " + n, "phone-" + n, category, new BigDecimal("99.00")));
        return p.getId();
    }

    private ProductVariant variantFor(Product product, int n) {
        return em.persistFlushFind(new ProductVariant(product, "SKU-" + n, new BigDecimal("99.00")));
    }

    private void placeOrder(Long userId, Long productId, String productName, Order.OrderStatus status) {
        Product product = em.find(Product.class, productId);
        ProductVariant variant = variantFor(product, Math.toIntExact(productId));
        OrderItem item = new OrderItem(productId, variant, productName, null, 1, new BigDecimal("99.00"));
        Order order = new Order(userId, List.of(item), new BigDecimal("99.00"));
        // Force the status past the default PENDING via reflection-free field update is not possible;
        // use the entity's status field through a small helper instead.
        setOrderStatus(order, status);
        em.persistFlushFind(order);
    }

    private void setOrderStatus(Order order, Order.OrderStatus status) {
        try {
            var f = Order.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(order, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldReturnTrueForDeliveredOrderContainingProduct() {
        Long product = productId(1);
        placeOrder(7L, product, "Phone 1", Order.OrderStatus.DELIVERED);
        assertTrue(orderQueryPort.hasUserPurchasedProduct(7L, product));
    }

    @Test
    void shouldReturnFalseForPendingOrder() {
        Long product = productId(2);
        placeOrder(7L, product, "Phone 2", Order.OrderStatus.PENDING);
        assertFalse(orderQueryPort.hasUserPurchasedProduct(7L, product));
    }

    @Test
    void shouldReturnFalseForCancelledOrder() {
        Long product = productId(3);
        placeOrder(7L, product, "Phone 3", Order.OrderStatus.CANCELLED);
        assertFalse(orderQueryPort.hasUserPurchasedProduct(7L, product));
    }

    @Test
    void shouldReturnFalseWhenUserBoughtDifferentProduct() {
        Long product = productId(4);
        Long other = productId(5);
        placeOrder(7L, other, "Phone 5", Order.OrderStatus.DELIVERED);
        assertFalse(orderQueryPort.hasUserPurchasedProduct(7L, product));
    }

    @Test
    void shouldReturnFalseWhenNoOrdersAtAll() {
        Long product = productId(6);
        assertFalse(orderQueryPort.hasUserPurchasedProduct(999L, product));
    }
}
