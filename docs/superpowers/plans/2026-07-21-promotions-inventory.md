# ShopNow Promotions & Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add coupon promotions (admin CRUD, redeemed at checkout) and inventory reservation (reserve+commit on order, restore on cancel, low-stock report) to the existing single-step checkout.

**Architecture:** Layered monolith matching prior phases. A `Promotion` + `CouponRedemption` domain with a `PromotionService` (validate/apply/CRUD), a new `InventoryRepository`/`InventoryService` (the `Inventory` entity exists but has no repository yet), and a rewritten `OrderService.placeOrder(userId, couponCode)` that applies a discount, locks+reserves+commits stock atomically, and records the redemption. Stock oversell and coupon double-use are prevented by pessimistic row locks + DB unique constraints; all mutations are one `@Transactional` boundary.

**Tech Stack:** Java 17, Spring Boot 3.2.1, Spring Data JPA, PostgreSQL 15 + Flyway, JUnit 5 + Mockito + Testcontainers.

## Global Constraints

- Java 17+ required.
- Spring Boot 3.2.1 (already configured in `pom.xml`).
- All tests must pass before commit (`mvn test`). The full suite is currently 105 green — it must stay green at every commit.
- Package structure: `com.shopnow.{layer}.{module}` — promotion code under `com.shopnow.domain.model`/`com.shopnow.domain.port`/`com.shopnow.application.promotion`/`com.shopnow.presentation.admin`; inventory under `com.shopnow.domain.port`/`com.shopnow.application.inventory`.
- REST API versioned `/api/v1/*`.
- New Flyway migrations numbered `V006` (promotions + coupons_used) and `V007` (order_items.variant FK). V001–V005 exist.
- Entities use `Long` IDs (auto-generated). DTOs are Java `record`s.
- `CouponRedemption` stores `userId` as a plain `Long` (no JPA relation to `User`), matching `Order`/`Review`.
- Commit format: `feat: <description>` / `test: <description>` / `refactor: <description>`.
- Reuse `GlobalExceptionHandler` for error envelopes; new exceptions live in `com.shopnow.domain.model`.
- Test profile uses `spring.jpa.hibernate.ddl-auto=create-drop` with Flyway disabled — so entity mappings (not migrations) define the schema in tests.

---

## File Structure

### New files

| Path | Responsibility |
|------|----------------|
| `src/main/java/com/shopnow/domain/model/Promotion.java` | promotion entity (code/type/value/minOrderValue/usageLimit/usageCount/startsAt/endsAt/status) |
| `src/main/java/com/shopnow/domain/model/CouponRedemption.java` | redemption record (promotion, userId, orderId, usedAt); UNIQUE(promotion_id,user_id) |
| `src/main/java/com/shopnow/domain/model/PromotionException.java` | carries a `PromotionException.Code` enum |
| `src/main/java/com/shopnow/domain/model/InsufficientStockException.java` | 409 signal |
| `src/main/java/com/shopnow/domain/port/PromotionRepository.java` | port |
| `src/main/java/com/shopnow/domain/port/CouponRedemptionRepository.java` | port |
| `src/main/java/com/shopnow/domain/port/InventoryRepository.java` | port (incl. `findByVariantIdForUpdate` lock query + `findLowStock`) |
| `src/main/java/com/shopnow/infrastructure/persistence/PromotionJpaRepository.java` | Spring Data JPA |
| `src/main/java/com/shopnow/infrastructure/persistence/PromotionRepositoryImpl.java` | adapter |
| `src/main/java/com/shopnow/infrastructure/persistence/CouponRedemptionJpaRepository.java` | Spring Data JPA |
| `src/main/java/com/shopnow/infrastructure/persistence/CouponRedemptionRepositoryImpl.java` | adapter |
| `src/main/java/com/shopnow/infrastructure/persistence/InventoryJpaRepository.java` | Spring Data JPA (with `@Lock(PESSIMISTIC_WRITE)` query) |
| `src/main/java/com/shopnow/infrastructure/persistence/InventoryRepositoryImpl.java` | adapter |
| `src/main/java/com/shopnow/application/promotion/PromotionService.java` | CRUD + validate/apply (window/usage/one-per-user/min-order/discount math) |
| `src/main/java/com/shopnow/application/inventory/InventoryService.java` | reserve+commit; restore; low-stock |
| `src/main/java/com/shopnow/presentation/admin/AdminPromotionController.java` | CRUD `/api/v1/admin/promotions` |
| `src/main/java/com/shopnow/presentation/admin/AdminInventoryController.java` | `GET /api/v1/admin/inventory/low-stock` |
| `src/main/java/com/shopnow/presentation/dto/CreatePromotionRequest.java` | create/update payload |
| `src/main/java/com/shopnow/presentation/dto/PromotionDto.java` | promotion view |
| `src/main/java/com/shopnow/presentation/dto/LowStockDto.java` | low-stock view |
| `src/main/resources/db/migration/V006__create_promotions.sql` | promotions + coupons_used |
| `src/main/resources/db/migration/V007__order_items_variant_fk.sql` | order_items.variant_id FK |

### Modified files

| Path | Change |
|------|--------|
| `src/main/java/com/shopnow/domain/model/Order.java` | constructor that takes discountAmount; `discountAmount` already exists |
| `src/main/java/com/shopnow/domain/model/OrderItem.java` | `variantId` becomes `@ManyToOne` to `ProductVariant` (real FK) |
| `src/main/java/com/shopnow/presentation/dto/OrderDto.java` | add `discountAmount` field |
| `src/main/java/com/shopnow/presentation/api/OrderController.java` | `placeOrder` gains optional `@RequestParam couponCode` |
| `src/main/java/com/shopnow/application/order/OrderService.java` | rewrite `placeOrder(userId, couponCode)` + `cancelOrder` restore; inject Promotion/Inventory deps |
| `src/main/java/com/shopnow/presentation/api/GlobalExceptionHandler.java` | handlers for `PromotionException` + `InsufficientStockException` |
| `src/test/java/com/shopnow/application/order/OrderServiceTest.java` | rewrite for new placeOrder signature + deps |
| `src/test/java/com/shopnow/presentation/api/OrderControllerTest.java` | update `OrderDto` construction (discountAmount field) + coupon param test |

---

## Task 1: Promotion Domain + Repository + V006 Migration + Admin CRUD + Service

**Files:**
- Create: `src/main/java/com/shopnow/domain/model/Promotion.java`
- Create: `src/main/java/com/shopnow/domain/model/PromotionException.java`
- Create: `src/main/java/com/shopnow/domain/port/PromotionRepository.java`
- Create: `src/main/java/com/shopnow/infrastructure/persistence/PromotionJpaRepository.java`
- Create: `src/main/java/com/shopnow/infrastructure/persistence/PromotionRepositoryImpl.java`
- Create: `src/main/java/com/shopnow/application/promotion/PromotionService.java`
- Create: `src/main/java/com/shopnow/presentation/dto/CreatePromotionRequest.java`
- Create: `src/main/java/com/shopnow/presentation/dto/PromotionDto.java`
- Create: `src/main/java/com/shopnow/presentation/admin/AdminPromotionController.java`
- Create: `src/main/resources/db/migration/V006__create_promotions.sql`
- Modify: `src/main/java/com/shopnow/presentation/api/GlobalExceptionHandler.java`
- Test: `src/test/java/com/shopnow/domain/model/PromotionExceptionTest.java`
- Test: `src/test/java/com/shopnow/application/promotion/PromotionServiceTest.java`
- Test: `src/test/java/com/shopnow/presentation/admin/AdminPromotionControllerTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks (leaf domain + admin CRUD).
- Produces: `Promotion` entity (constructor `Promotion(String code, PromoType type, BigDecimal value, BigDecimal minOrderValue, Integer usageLimit, LocalDateTime startsAt, LocalDateTime endsAt, PromoStatus status)`; getters `getId/getCode/getType/getValue/getMinOrderValue/getUsageLimit/getUsageCount/getStartsAt/getEndsAt/getStatus`; setters `setStatus/setUsageCount`). `Promotion.PromoType { PERCENTAGE, FIXED }`, `Promotion.PromoStatus { ACTIVE, INACTIVE }`. `PromotionRepository` port: `Optional<Promotion> findByCode(String)`, `Optional<Promotion> findById(Long)`, `List<Promotion> findAll()`, `Promotion save(Promotion)`, `void deleteById(Long)`, `boolean existsById(Long)`. `PromotionService`: `PromotionDto create(CreatePromotionRequest)`, `PromotionDto update(Long, CreatePromotionRequest)`, `PromotionDto get(Long)`, `List<PromotionDto> list()`, `void delete(Long)` (409 if redeemed — redemption repo is in Task 4, so this task's delete checks `usageCount == 0`). `PromotionException(Code code)` where `Code { NOT_FOUND, INACTIVE, EXPIRED, USAGE_EXCEEDED, ALREADY_USED, MIN_NOT_MET, INVALID_VALUE }` with `Code getCode()`.

> Note: `AdminPromotionController` delete guards against deleting redeemed promotions. The `CouponRedemptionRepository` does not exist until Task 4, so Task 1's `PromotionService.delete` guards on `usageCount == 0` instead (a promotion with any redemptions has `usageCount > 0`). This is sufficient and keeps Task 1 self-contained.

- [ ] **Step 1: Create the PromotionException**

```java
// src/main/java/com/shopnow/domain/model/PromotionException.java
package com.shopnow.domain.model;

public class PromotionException extends RuntimeException {

    public enum Code {
        NOT_FOUND,        // 404
        INACTIVE,         // 409
        EXPIRED,          // 409
        USAGE_EXCEEDED,   // 409
        ALREADY_USED,     // 409
        MIN_NOT_MET,      // 400
        INVALID_VALUE     // 400
    }

    private final Code code;

    public PromotionException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
```

```java
// src/test/java/com/shopnow/domain/model/PromotionExceptionTest.java
package com.shopnow.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromotionExceptionTest {

    @Test
    void shouldCarryCode() {
        PromotionException ex = new PromotionException(PromotionException.Code.EXPIRED, "expired");
        assertEquals(PromotionException.Code.EXPIRED, ex.getCode());
        assertEquals("expired", ex.getMessage());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=PromotionExceptionTest`
Expected: FAIL — `PromotionException` not found.

- [ ] **Step 3: Create the Promotion entity**

```java
// src/main/java/com/shopnow/domain/model/Promotion.java
package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromoType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "min_order_value", precision = 10, scale = 2)
    private BigDecimal minOrderValue;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromoStatus status = PromoStatus.INACTIVE;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected Promotion() {
    }

    public Promotion(String code, PromoType type, BigDecimal value, BigDecimal minOrderValue,
                     Integer usageLimit, LocalDateTime startsAt, LocalDateTime endsAt, PromoStatus status) {
        this.code = code == null ? null : code.toUpperCase();
        this.type = type;
        this.value = value;
        this.minOrderValue = minOrderValue;
        this.usageLimit = usageLimit;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status == null ? PromoStatus.INACTIVE : status;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public PromoType getType() { return type; }
    public BigDecimal getValue() { return value; }
    public BigDecimal getMinOrderValue() { return minOrderValue; }
    public Integer getUsageLimit() { return usageLimit; }
    public Integer getUsageCount() { return usageCount; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public PromoStatus getStatus() { return status; }

    public void setStatus(PromoStatus status) { this.status = status; }
    public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }

    public enum PromoType { PERCENTAGE, FIXED }
    public enum PromoStatus { ACTIVE, INACTIVE }
}
```

- [ ] **Step 4: Create the V006 migration**

```sql
-- src/main/resources/db/migration/V006__create_promotions.sql
CREATE TABLE promotions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    value DECIMAL(10,2) NOT NULL,
    min_order_value DECIMAL(10,2),
    usage_limit INTEGER,
    usage_count INTEGER NOT NULL DEFAULT 0,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE coupons_used (
    id BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL REFERENCES promotions(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    order_id BIGINT NOT NULL REFERENCES orders(id),
    used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (promotion_id, user_id)
);
```

- [ ] **Step 5: Create the DTOs**

```java
// src/main/java/com/shopnow/presentation/dto/CreatePromotionRequest.java
package com.shopnow.presentation.dto;

import com.shopnow.domain.model.Promotion;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreatePromotionRequest(
        @NotBlank @Size(max = 50) String code,
        @NotNull Promotion.PromoType type,
        @NotNull @DecimalMin("0.01") BigDecimal value,
        @DecimalMin("0") BigDecimal minOrderValue,
        @Min(1) Integer usageLimit,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        Promotion.PromoStatus status
) {
}
```

```java
// src/main/java/com/shopnow/presentation/dto/PromotionDto.java
package com.shopnow.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionDto(
        Long id,
        String code,
        String type,
        BigDecimal value,
        BigDecimal minOrderValue,
        Integer usageLimit,
        Integer usageCount,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String status
) {
}
```

- [ ] **Step 6: Create the repository port**

```java
// src/main/java/com/shopnow/domain/port/PromotionRepository.java
package com.shopnow.domain.port;

import com.shopnow.domain.model.Promotion;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository {
    Promotion save(Promotion promotion);
    Optional<Promotion> findById(Long id);
    Optional<Promotion> findByCode(String code);
    List<Promotion> findAll();
    void deleteById(Long id);
    boolean existsById(Long id);
}
```

- [ ] **Step 7: Create the JPA repo + adapter**

```java
// src/main/java/com/shopnow/infrastructure/persistence/PromotionJpaRepository.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromotionJpaRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCode(String code);
}
```

```java
// src/main/java/com/shopnow/infrastructure/persistence/PromotionRepositoryImpl.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.port.PromotionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PromotionRepositoryImpl implements PromotionRepository {

    private final PromotionJpaRepository jpaRepository;

    public PromotionRepositoryImpl(PromotionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Promotion save(Promotion promotion) {
        return jpaRepository.save(promotion);
    }

    @Override
    public Optional<Promotion> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Promotion> findByCode(String code) {
        return jpaRepository.findByCode(code == null ? null : code.toUpperCase());
    }

    @Override
    public List<Promotion> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }
}
```

- [ ] **Step 8: Create the PromotionService**

```java
// src/main/java/com/shopnow/application/promotion/PromotionService.java
package com.shopnow.application.promotion;

import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.model.PromotionException;
import com.shopnow.domain.port.PromotionRepository;
import com.shopnow.presentation.dto.CreatePromotionRequest;
import com.shopnow.presentation.dto.PromotionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Transactional
    public PromotionDto create(CreatePromotionRequest request) {
        validate(request);
        Promotion promotion = new Promotion(
                request.code(),
                request.type(),
                request.value(),
                request.minOrderValue(),
                request.usageLimit(),
                request.startsAt(),
                request.endsAt(),
                request.status());
        return toDto(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionDto update(Long id, CreatePromotionRequest request) {
        validate(request);
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found"));
        Promotion updated = new Promotion(
                request.code(),
                request.type(),
                request.value(),
                request.minOrderValue(),
                request.usageLimit(),
                request.startsAt(),
                request.endsAt(),
                request.status());
        // preserve id + usageCount
        try {
            var idField = Promotion.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(updated, promotion.getId());
            var usageField = Promotion.class.getDeclaredField("usageCount");
            usageField.setAccessible(true);
            usageField.set(updated, promotion.getUsageCount());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return toDto(promotionRepository.save(updated));
    }

    @Transactional(readOnly = true)
    public PromotionDto get(Long id) {
        return toDto(promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found")));
    }

    @Transactional(readOnly = true)
    public List<PromotionDto> list() {
        return promotionRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found"));
        if (promotion.getUsageCount() != null && promotion.getUsageCount() > 0) {
            throw new PromotionException(PromotionException.Code.USAGE_EXCEEDED,
                    "Cannot delete a promotion that has been redeemed; set status=INACTIVE instead");
        }
        promotionRepository.deleteById(id);
    }

    private void validate(CreatePromotionRequest request) {
        if (request.endsAt().isBefore(request.startsAt())) {
            throw new PromotionException(PromotionException.Code.INVALID_VALUE, "endsAt must be after startsAt");
        }
        if (request.type() == Promotion.PromoType.PERCENTAGE) {
            double v = request.value().doubleValue();
            if (v < 1 || v > 100) {
                throw new PromotionException(PromotionException.Code.INVALID_VALUE,
                        "PERCENTAGE value must be between 1 and 100");
            }
        }
    }

    private PromotionDto toDto(Promotion p) {
        return new PromotionDto(
                p.getId(),
                p.getCode(),
                p.getType().name(),
                p.getValue(),
                p.getMinOrderValue(),
                p.getUsageLimit(),
                p.getUsageCount(),
                p.getStartsAt(),
                p.getEndsAt(),
                p.getStatus().name());
    }
}
```

- [ ] **Step 9: Write the PromotionService test**

```java
// src/test/java/com/shopnow/application/promotion/PromotionServiceTest.java
package com.shopnow.application.promotion;

import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.model.PromotionException;
import com.shopnow.domain.port.PromotionRepository;
import com.shopnow.presentation.dto.CreatePromotionRequest;
import com.shopnow.presentation.dto.PromotionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    private PromotionService promotionService;

    private final LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final LocalDateTime end = LocalDateTime.of(2026, 12, 31, 23, 59);

    @BeforeEach
    void setUp() {
        promotionService = new PromotionService(promotionRepository);
    }

    private CreatePromotionRequest req(Promotion.PromoType type, BigDecimal value) {
        return new CreatePromotionRequest("summer20", type, value, null, 100, start, end, Promotion.PromoStatus.ACTIVE);
    }

    @Test
    void shouldCreatePercentagePromotionAndUppercaseCode() {
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> {
            Promotion p = inv.getArgument(0);
            var f = Promotion.class.getDeclaredField("id"); f.setAccessible(true); f.set(p, 1L);
            return p;
        });

        PromotionDto dto = promotionService.create(req(Promotion.PromoType.PERCENTAGE, new BigDecimal("20")));

        assertEquals("SUMMER20", dto.code());
        assertEquals("PERCENTAGE", dto.type());
        assertEquals("ACTIVE", dto.status());
    }

    @Test
    void shouldRejectPercentageValueOutOfRange() {
        assertThrows(PromotionException.class,
                () -> promotionService.create(req(Promotion.PromoType.PERCENTAGE, new BigDecimal("150"))));
        assertThrows(PromotionException.class,
                () -> promotionService.create(req(Promotion.PromoType.PERCENTAGE, BigDecimal.ZERO)));
    }

    @Test
    void shouldRejectEndsAtBeforeStartsAt() {
        CreatePromotionRequest bad = new CreatePromotionRequest(
                "x", Promotion.PromoType.FIXED, new BigDecimal("5"), null, null, end, start, Promotion.PromoStatus.ACTIVE);
        PromotionException ex = assertThrows(PromotionException.class, () -> promotionService.create(bad));
        assertEquals(PromotionException.Code.INVALID_VALUE, ex.getCode());
    }

    @Test
    void shouldListPromotions() {
        when(promotionRepository.findAll()).thenReturn(List.of());
        assertEquals(0, promotionService.list().size());
    }

    @Test
    void shouldGetById() {
        Promotion p = new Promotion("X", Promotion.PromoType.FIXED, new BigDecimal("5"), null, null, start, end, Promotion.PromoStatus.ACTIVE);
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(p));
        assertEquals("X", promotionService.get(1L).code());
    }

    @Test
    void shouldThrowNotFoundOnMissingGet() {
        when(promotionRepository.findById(9L)).thenReturn(Optional.empty());
        PromotionException ex = assertThrows(PromotionException.class, () -> promotionService.get(9L));
        assertEquals(PromotionException.Code.NOT_FOUND, ex.getCode());
    }

    @Test
    void shouldDeleteUnusedPromotion() {
        Promotion p = new Promotion("X", Promotion.PromoType.FIXED, new BigDecimal("5"), null, null, start, end, Promotion.PromoStatus.ACTIVE);
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(p));
        promotionService.delete(1L);
        verify(promotionRepository).deleteById(1L);
    }

    @Test
    void shouldNotDeleteRedeemedPromotion() {
        Promotion p = new Promotion("X", Promotion.PromoType.FIXED, new BigDecimal("5"), null, null, start, end, Promotion.PromoStatus.ACTIVE);
        p.setUsageCount(3);
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(p));
        PromotionException ex = assertThrows(PromotionException.class, () -> promotionService.delete(1L));
        assertEquals(PromotionException.Code.USAGE_EXCEEDED, ex.getCode());
        verify(promotionRepository, never()).deleteById(any());
    }
}
```

- [ ] **Step 10: Run the service test to verify it passes**

Run: `mvn test -Dtest=PromotionServiceTest`
Expected: PASS (8 tests).

- [ ] **Step 11: Create the AdminPromotionController**

```java
// src/main/java/com/shopnow/presentation/admin/AdminPromotionController.java
package com.shopnow.presentation.admin;

import com.shopnow.application.promotion.PromotionService;
import com.shopnow.presentation.dto.CreatePromotionRequest;
import com.shopnow.presentation.dto.PromotionDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/promotions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromotionController {

    private final PromotionService promotionService;

    public AdminPromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping
    public ResponseEntity<PromotionDto> create(@Valid @RequestBody CreatePromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<PromotionDto>> list() {
        return ResponseEntity.ok(promotionService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromotionDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromotionDto> update(@PathVariable Long id,
                                                @Valid @RequestBody CreatePromotionRequest request) {
        return ResponseEntity.ok(promotionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        promotionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 12: Add exception handlers to GlobalExceptionHandler**

Modify `src/main/java/com/shopnow/presentation/api/GlobalExceptionHandler.java`. Add these imports (with the existing domain-model imports):

```java
import com.shopnow.domain.model.PromotionException;
import com.shopnow.domain.model.InsufficientStockException;
import org.springframework.http.HttpHeaders;
```

Add these handler methods inside the class (after `handleIllegalArgument`):

```java
    @ExceptionHandler(PromotionException.class)
    public ResponseEntity<Object> handlePromotion(PromotionException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INACTIVE, EXPIRED, USAGE_EXCEEDED, ALREADY_USED -> HttpStatus.CONFLICT;
            case MIN_NOT_MET, INVALID_VALUE -> HttpStatus.BAD_REQUEST;
        };
        return envelope(status, ex.getCode().name(), ex.getMessage(), null);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Object> handleInsufficientStock(InsufficientStockException ex) {
        return envelope(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", ex.getMessage(), null);
    }
```

> Note: `InsufficientStockException` is created in Task 4. To keep Task 1 compiling, create a stub now (Task 4 fills its behavior). Create the file:

```java
// src/main/java/com/shopnow/domain/model/InsufficientStockException.java
package com.shopnow.domain.model;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
```

> The `HttpHeaders` import is unused after this edit — remove it if your IDE flags it. Do not leave unused imports.

- [ ] **Step 13: Write the AdminPromotionController test**

```java
// src/test/java/com/shopnow/presentation/admin/AdminPromotionControllerTest.java
package com.shopnow.presentation.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopnow.application.promotion.PromotionService;
import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.model.PromotionException;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.infrastructure.security.TestSecurityConfig;
import com.shopnow.presentation.dto.CreatePromotionRequest;
import com.shopnow.presentation.dto.PromotionDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminPromotionController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class AdminPromotionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PromotionService promotionService;

    @Autowired
    private ObjectMapper objectMapper;

    private CreatePromotionRequest validRequest() {
        return new CreatePromotionRequest(
                "summer20", Promotion.PromoType.PERCENTAGE, new BigDecimal("20"),
                null, 100, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                Promotion.PromoStatus.ACTIVE);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreatePromotion() throws Exception {
        when(promotionService.create(any())).thenReturn(
                new PromotionDto(1L, "SUMMER20", "PERCENTAGE", new BigDecimal("20"),
                        null, 100, 0, null, null, "ACTIVE"));

        mockMvc.perform(post("/api/v1/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUMMER20"));
    }

    @Test
    void shouldRejectCreateWithoutAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldListPromotions() throws Exception {
        when(promotionService.list()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/promotions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectInvalidPercentageValue() throws Exception {
        doThrow(new PromotionException(PromotionException.Code.INVALID_VALUE, "bad"))
                .when(promotionService).create(any());
        mockMvc.perform(post("/api/v1/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_VALUE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404OnMissingPromotion() throws Exception {
        when(promotionService.get(9L)).thenThrow(
                new PromotionException(PromotionException.Code.NOT_FOUND, "missing"));
        mockMvc.perform(get("/api/v1/admin/promotions/9"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 14: Run the controller test**

Run: `mvn test -Dtest=AdminPromotionControllerTest`
Expected: PASS (5 tests).

- [ ] **Step 15: Run the full suite**

Run: `mvn test`
Expected: all pass (previous 105 + PromotionExceptionTest 1 + PromotionServiceTest 8 + AdminPromotionControllerTest 5 = 119). No regressions.

- [ ] **Step 16: Commit**

```bash
git add src/main/java/com/shopnow/domain/model/Promotion.java \
        src/main/java/com/shopnow/domain/model/PromotionException.java \
        src/main/java/com/shopnow/domain/model/InsufficientStockException.java \
        src/main/java/com/shopnow/domain/port/PromotionRepository.java \
        src/main/java/com/shopnow/infrastructure/persistence/PromotionJpaRepository.java \
        src/main/java/com/shopnow/infrastructure/persistence/PromotionRepositoryImpl.java \
        src/main/java/com/shopnow/application/promotion/PromotionService.java \
        src/main/java/com/shopnow/presentation/dto/CreatePromotionRequest.java \
        src/main/java/com/shopnow/presentation/dto/PromotionDto.java \
        src/main/java/com/shopnow/presentation/admin/AdminPromotionController.java \
        src/main/java/com/shopnow/presentation/api/GlobalExceptionHandler.java \
        src/main/resources/db/migration/V006__create_promotions.sql \
        src/test/java/com/shopnow/domain/model/PromotionExceptionTest.java \
        src/test/java/com/shopnow/application/promotion/PromotionServiceTest.java \
        src/test/java/com/shopnow/presentation/admin/AdminPromotionControllerTest.java
git commit -m "feat: add promotion domain, repository, admin CRUD, and V006 migration"
```

---

## Task 2: Inventory Repository + Service

**Files:**
- Create: `src/main/java/com/shopnow/domain/port/InventoryRepository.java`
- Create: `src/main/java/com/shopnow/infrastructure/persistence/InventoryJpaRepository.java`
- Create: `src/main/java/com/shopnow/infrastructure/persistence/InventoryRepositoryImpl.java`
- Create: `src/main/java/com/shopnow/application/inventory/InventoryService.java`
- Create: `src/main/java/com/shopnow/presentation/dto/LowStockDto.java`
- Test: `src/test/java/com/shopnow/application/inventory/InventoryServiceTest.java`

**Interfaces:**
- Consumes: `Inventory` entity (fields `variant: ProductVariant`, `quantity`, `reserved`, `threshold`; methods `reserve(qty)`, `commitReservation(qty)`, `releaseReservation(qty)`, `getAvailable()`). The `Inventory` entity and its DB table already exist.
- Produces: `InventoryRepository` port with `Optional<Inventory> findByVariantId(Long)`, `Optional<Inventory> findByVariantIdForUpdate(Long)`, `List<Inventory> findLowStock()`, `Inventory save(Inventory)`. `InventoryService`: `void commitStock(List<StockRequest> items)` (locks + reserve + commit each; throws `InsufficientStockException` on any shortfall → caller's transaction rolls back), `void restoreStock(List<StockRequest> items)` (locks + quantity += qty), `List<LowStockDto> lowStock()`. `StockRequest` is a nested `record(Long variantId, Integer quantity)` in `InventoryService`.

> **Order matters for locking.** Callers must pass `StockRequest` lists sorted by `variantId` ascending so every transaction locks in the same order (deadlock avoidance). `InventoryService` re-sorts defensively before locking.

- [ ] **Step 1: Create the LowStockDto**

```java
// src/main/java/com/shopnow/presentation/dto/LowStockDto.java
package com.shopnow.presentation.dto;

public record LowStockDto(
        Long variantId,
        String sku,
        String productName,
        Integer quantity,
        Integer reserved,
        Integer available,
        Integer threshold
) {
}
```

- [ ] **Step 2: Create the repository port**

```java
// src/main/java/com/shopnow/domain/port/InventoryRepository.java
package com.shopnow.domain.port;

import com.shopnow.domain.model.Inventory;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {
    Inventory save(Inventory inventory);
    Optional<Inventory> findByVariantId(Long variantId);
    Optional<Inventory> findByVariantIdForUpdate(Long variantId);
    List<Inventory> findLowStock();
}
```

- [ ] **Step 3: Create the JPA repo with the lock query**

```java
// src/main/java/com/shopnow/infrastructure/persistence/InventoryJpaRepository.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByVariant_Id(Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variant.id = :variantId")
    Optional<Inventory> findByVariantIdForUpdate(@Param("variantId") Long variantId);

    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reserved) < i.threshold")
    List<Inventory> findLowStock();
}
```

- [ ] **Step 4: Create the adapter**

```java
// src/main/java/com/shopnow/infrastructure/persistence/InventoryRepositoryImpl.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Inventory;
import com.shopnow.domain.port.InventoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryJpaRepository jpaRepository;

    public InventoryRepositoryImpl(InventoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Inventory save(Inventory inventory) {
        return jpaRepository.save(inventory);
    }

    @Override
    public Optional<Inventory> findByVariantId(Long variantId) {
        return jpaRepository.findByVariant_Id(variantId);
    }

    @Override
    public Optional<Inventory> findByVariantIdForUpdate(Long variantId) {
        return jpaRepository.findByVariantIdForUpdate(variantId);
    }

    @Override
    public List<Inventory> findLowStock() {
        return jpaRepository.findLowStock();
    }
}
```

- [ ] **Step 5: Create the InventoryService**

```java
// src/main/java/com/shopnow/application/inventory/InventoryService.java
package com.shopnow.application.inventory;

import com.shopnow.domain.model.Inventory;
import com.shopnow.domain.model.InsufficientStockException;
import com.shopnow.domain.port.InventoryRepository;
import com.shopnow.presentation.dto.LowStockDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public record StockRequest(Long variantId, Integer quantity) {
    }

    /**
     * Lock, reserve, and commit stock for each item in one transaction. Items are sorted by
     * variantId so all callers lock in the same order (deadlock avoidance). Any shortfall
     * throws InsufficientStockException and the caller's @Transactional rolls back all prior
     * commits in this call.
     */
    @Transactional
    public void commitStock(List<StockRequest> items) {
        List<StockRequest> sorted = items.stream()
                .sorted(Comparator.comparing(StockRequest::variantId))
                .toList();
        for (StockRequest item : sorted) {
            Inventory inventory = inventoryRepository.findByVariantIdForUpdate(item.variantId())
                    .orElseThrow(() -> new InsufficientStockException("No inventory for variant " + item.variantId()));
            inventory.reserve(item.quantity());      // throws if available < qty
            inventory.commitReservation(item.quantity());
            if (inventory.getAvailable() < inventory.getThreshold()) {
                log.warn("Low stock for variant {}: available={}, threshold={}",
                        item.variantId(), inventory.getAvailable(), inventory.getThreshold());
            }
            inventoryRepository.save(inventory);
        }
    }

    @Transactional
    public void restoreStock(List<StockRequest> items) {
        List<StockRequest> sorted = items.stream()
                .sorted(Comparator.comparing(StockRequest::variantId))
                .toList();
        for (StockRequest item : sorted) {
            Inventory inventory = inventoryRepository.findByVariantIdForUpdate(item.variantId())
                    .orElseThrow(() -> new InsufficientStockException("No inventory for variant " + item.variantId()));
            // restore the committed quantity directly
            try {
                var qtyField = Inventory.class.getDeclaredField("quantity");
                qtyField.setAccessible(true);
                qtyField.set(inventory, inventory.getQuantity() + item.quantity());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            inventoryRepository.save(inventory);
        }
    }

    @Transactional(readOnly = true)
    public List<LowStockDto> lowStock() {
        return inventoryRepository.findLowStock().stream()
                .map(i -> new LowStockDto(
                        i.getVariant().getId(),
                        i.getVariant().getSku(),
                        i.getVariant().getProduct().getName(),
                        i.getQuantity(),
                        i.getReserved(),
                        i.getAvailable(),
                        i.getThreshold()))
                .toList();
    }
}
```

- [ ] **Step 6: Write the InventoryService test**

```java
// src/test/java/com/shopnow/application/inventory/InventoryServiceTest.java
package com.shopnow.application.inventory;

import com.shopnow.domain.model.InsufficientStockException;
import com.shopnow.domain.model.Inventory;
import com.shopnow.domain.port.InventoryRepository;
import com.shopnow.presentation.dto.LowStockDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository);
    }

    @Test
    void shouldCommitStockWhenAvailable() {
        Inventory inv = new Inventory(null, 10); // variant null here; service only calls reserve/commit/save
        when(inventoryRepository.findByVariantIdForUpdate(1L)).thenReturn(Optional.of(inv));

        inventoryService.commitStock(List.of(new InventoryService.StockRequest(1L, 3)));

        assertEquals(7, inv.getQuantity());
        verify(inventoryRepository).save(inv);
    }

    @Test
    void shouldThrowWhenInsufficient() {
        Inventory inv = new Inventory(null, 2);
        when(inventoryRepository.findByVariantIdForUpdate(1L)).thenReturn(Optional.of(inv));

        assertThrows(InsufficientStockException.class,
                () -> inventoryService.commitStock(List.of(new InventoryService.StockRequest(1L, 5))));
        // quantity unchanged because reserve() threw before commit
        assertEquals(2, inv.getQuantity());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenNoInventoryForVariant() {
        when(inventoryRepository.findByVariantIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(InsufficientStockException.class,
                () -> inventoryService.commitStock(List.of(new InventoryService.StockRequest(99L, 1))));
    }

    @Test
    void shouldRestoreStock() {
        Inventory inv = new Inventory(null, 5);
        when(inventoryRepository.findByVariantIdForUpdate(1L)).thenReturn(Optional.of(inv));

        inventoryService.restoreStock(List.of(new InventoryService.StockRequest(1L, 3)));

        assertEquals(8, inv.getQuantity());
        verify(inventoryRepository).save(inv);
    }

    @Test
    void lowStockDelegatesToRepository() {
        // lowStock mapping is exercised in the controller/repository test; here just verify delegation wiring
        when(inventoryRepository.findLowStock()).thenReturn(List.of());
        List<LowStockDto> result = inventoryService.lowStock();
        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
```

- [ ] **Step 7: Run the service test to verify it passes**

Run: `mvn test -Dtest=InventoryServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 8: Run the full suite**

Run: `mvn test`
Expected: all pass (previous 119 + 5 = 124).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/shopnow/domain/port/InventoryRepository.java \
        src/main/java/com/shopnow/infrastructure/persistence/InventoryJpaRepository.java \
        src/main/java/com/shopnow/infrastructure/persistence/InventoryRepositoryImpl.java \
        src/main/java/com/shopnow/application/inventory/InventoryService.java \
        src/main/java/com/shopnow/presentation/dto/LowStockDto.java \
        src/test/java/com/shopnow/application/inventory/InventoryServiceTest.java
git commit -m "feat: add inventory repository with row locking and InventoryService"
```

---

## Task 3: OrderItem variant FK + Order/OrderDto discountAmount + V007

**Files:**
- Modify: `src/main/java/com/shopnow/domain/model/OrderItem.java`
- Modify: `src/main/java/com/shopnow/domain/model/Order.java`
- Modify: `src/main/java/com/shopnow/presentation/dto/OrderDto.java`
- Modify: `src/test/java/com/shopnow/presentation/api/OrderControllerTest.java` (fix `OrderDto` construction)
- Modify: `src/test/java/com/shopnow/domain/model/OrderTest.java` (existing — uses old `OrderItem` 6-arg constructor, must update)
- Create: `src/main/resources/db/migration/V007__order_items_variant_fk.sql`
- Test: `src/test/java/com/shopnow/domain/model/OrderItemTest.java`

**Interfaces:**
- Consumes: `ProductVariant` entity.
- Produces: `OrderItem.variant` as a `@ManyToOne` to `ProductVariant` (FK to `product_variants(id)`). `OrderItem` keeps `getVariantId()` returning `variant.getId()` for DTO convenience, and adds `getVariant()`. The `OrderItem` constructor changes: the old `OrderItem(Long productId, Long variantId, String productName, String variantName, Integer quantity, BigDecimal unitPrice)` is replaced by `OrderItem(Long productId, ProductVariant variant, String productName, String variantName, Integer quantity, BigDecimal unitPrice)`. `Order` gains an extra constructor `Order(Long userId, List<OrderItem> items, BigDecimal totalAmount, BigDecimal discountAmount)`; the existing 3-arg constructor delegates with `discountAmount = ZERO`. `OrderDto` gains a `discountAmount` field.

> **This task changes a constructor used by `OrderService` (Task 4) and the existing `OrderServiceTest`.** The existing `OrderServiceTest` still compiles at the end of Task 3 because it uses `Order`'s 3-arg constructor (unchanged) and constructs `OrderItem` indirectly — actually it does NOT construct OrderItem. Review `OrderServiceTest`: it only builds `Cart` + `Order(userId, items, total)`, so it stays green. `OrderControllerTest` constructs `OrderDto(1L, 1L, "PENDING", price, items)` — that 5-arg constructor breaks when `discountAmount` is added. Fix it in Step 5.

- [ ] **Step 1: Modify OrderItem — variant becomes a relation**

Replace the entire contents of `src/main/java/com/shopnow/domain/model/OrderItem.java` with:

```java
// src/main/java/com/shopnow/domain/model/OrderItem.java
package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(length = 255)
    private String variantName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItem() {
    }

    public OrderItem(Long productId, ProductVariant variant, String productName,
                     String variantName, Integer quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.variant = variant;
        this.productName = productName;
        this.variantName = variantName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    void setOrder(Order order) { this.order = order; }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public Long getProductId() { return productId; }
    public ProductVariant getVariant() { return variant; }
    public Long getVariantId() { return variant == null ? null : variant.getId(); }
    public String getProductName() { return productName; }
    public String getVariantName() { return variantName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
```

- [ ] **Step 2: Modify Order — add discount constructor**

In `src/main/java/com/shopnow/domain/model/Order.java`, replace the existing 3-arg constructor block:

```java
    public Order(Long userId, List<OrderItem> items, BigDecimal totalAmount) {
        this.userId = userId;
        this.items.addAll(items);
        this.totalAmount = totalAmount;
        items.forEach(item -> item.setOrder(this));
    }
```

with:

```java
    public Order(Long userId, List<OrderItem> items, BigDecimal totalAmount) {
        this(userId, items, totalAmount, BigDecimal.ZERO);
    }

    public Order(Long userId, List<OrderItem> items, BigDecimal totalAmount, BigDecimal discountAmount) {
        this.userId = userId;
        this.items.addAll(items);
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        items.forEach(item -> item.setOrder(this));
    }
```

Also add a setter for discount (used by OrderService in Task 4):

```java
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
```

- [ ] **Step 3: Modify OrderDto — add discountAmount**

Replace `src/main/java/com/shopnow/presentation/dto/OrderDto.java` with:

```java
// src/main/java/com/shopnow/presentation/dto/OrderDto.java
package com.shopnow.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderDto(
    Long id,
    Long userId,
    String status,
    BigDecimal totalAmount,
    BigDecimal discountAmount,
    List<OrderItemDto> items
) {}
```

- [ ] **Step 4: Create the V007 migration**

```sql
-- src/main/resources/db/migration/V007__order_items_variant_fk.sql
ALTER TABLE order_items
    ADD CONSTRAINT fk_oi_variant
    FOREIGN KEY (variant_id) REFERENCES product_variants(id);
```

- [ ] **Step 5: Fix OrderControllerTest OrderDto construction**

In `src/test/java/com/shopnow/presentation/api/OrderControllerTest.java`, the line:

```java
                .thenReturn(new OrderDto(1L, 1L, "PENDING", new BigDecimal("999.00"), List.of()));
```

becomes:

```java
                .thenReturn(new OrderDto(1L, 1L, "PENDING", new BigDecimal("999.00"), new BigDecimal("0.00"), List.of()));
```

- [ ] **Step 5b: Fix existing OrderTest (uses the old OrderItem constructor)**

The existing `src/test/java/com/shopnow/domain/model/OrderTest.java` constructs `new OrderItem(1L, 100L, ...)` with the old 6-arg `(productId, variantId Long, ...)` signature. After Step 1 that signature is `(Long productId, ProductVariant variant, ...)`. Update `shouldCreateOrder` to build a detached `ProductVariant`. Replace the first test method's body item-construction:

Replace this line in `OrderTest.java`:

```java
        OrderItem item = new OrderItem(1L, 100L, "iPhone 15", "128GB/Black", 1, new BigDecimal("999.00"));
```

with:

```java
        Category category = new Category("Electronics", "electronics");
        Product product = new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00"));
        ProductVariant variant = new ProductVariant(product, "SKU-100", new BigDecimal("999.00"));
        try {
            var f = ProductVariant.class.getDeclaredField("id"); f.setAccessible(true); f.set(variant, 100L);
        } catch (Exception e) { throw new RuntimeException(e); }
        OrderItem item = new OrderItem(1L, variant, "iPhone 15", "128GB/Black", 1, new BigDecimal("999.00"));
```

(The other two tests in `OrderTest` use `new Order(1L, List.of(), BigDecimal.ZERO)` — no OrderItem — so they stay unchanged.)

- [ ] **Step 6: Write the OrderItem test**

```java
// src/test/java/com/shopnow/domain/model/OrderItemTest.java
package com.shopnow.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderItemTest {

    @Test
    void shouldExposeVariantIdFromRelation() {
        Category category = new Category("Electronics", "electronics");
        Product product = new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00"));
        ProductVariant variant = new ProductVariant(product, "SKU-1", new BigDecimal("999.00"));
        // simulate JPA-assigned id
        try {
            var f = ProductVariant.class.getDeclaredField("id"); f.setAccessible(true); f.set(variant, 7L);
        } catch (Exception e) { fail(e); }

        OrderItem item = new OrderItem(1L, variant, "iPhone 15", "128GB", 2, new BigDecimal("999.00"));

        assertEquals(7L, item.getVariantId());
        assertEquals(variant, item.getVariant());
        assertEquals(new BigDecimal("1998.00"), item.getSubtotal());
    }
}
```

- [ ] **Step 7: Run the new + affected tests**

Run: `mvn test -Dtest=OrderItemTest,OrderControllerTest,OrderServiceTest`
Expected: PASS. (`OrderControllerTest` now uses the 6-field `OrderDto`; `OrderServiceTest` uses the 3-arg `Order` constructor, unaffected.)

- [ ] **Step 8: Run the full suite**

Run: `mvn test`
Expected: all pass (previous 124 + OrderItemTest 1 = 125). `OrderServiceTest` still green because it does not construct `OrderItem` and uses the 3-arg `Order` constructor. **However**, `OrderService.placeOrder` currently constructs `new OrderItem(variantId, variantId, ...)` using the OLD constructor signature — this will NOT compile after Step 1. So the full suite will FAIL to compile here.

> **Resolution:** This is expected — Task 4 rewrites `OrderService.placeOrder` to use the new `OrderItem(productId, variant, ...)` constructor. But Task 3 must end green. Therefore, update `OrderService.placeOrder` minimally in Task 3 to compile against the new constructor, deferring the full rewrite to Task 4. Open `src/main/java/com/shopnow/application/order/OrderService.java` and change ONLY the `OrderItem` construction in `placeOrder` from:

```java
            .map(ci -> new OrderItem(
                ci.getVariantId(), ci.getVariantId(),
                "Product-" + ci.getVariantId(), ci.getSku(),
                ci.getQuantity(), ci.getPrice()
            ))
```

to a minimal change that compiles. Since the cart's `variantId` is a `Long` with no `ProductVariant` loaded yet, look the variant up — but `OrderService` does not have a variant repo. **Simplest compile fix for Task 3:** keep the old behavior by changing the constructor to load nothing and pass `null` variant is not allowed (nullable=false). Instead, **revert the constructor-arity change impact** by having `placeOrder` pass a detached `ProductVariant` carrying only the id:

```java
            .map(ci -> {
                ProductVariant v = new ProductVariant(null, ci.getSku(), ci.getPrice());
                try {
                    var f = ProductVariant.class.getDeclaredField("id"); f.setAccessible(true); f.set(v, ci.getVariantId());
                } catch (Exception e) { throw new IllegalStateException(e); }
                return new OrderItem(
                    ci.getVariantId(), v,
                    "Product-" + ci.getVariantId(), ci.getSku(),
                    ci.getQuantity(), ci.getPrice()
                );
            })
```

Add the import `import com.shopnow.domain.model.ProductVariant;` to `OrderService.java`. Add the import `import java.util.List;` if not present. This keeps Task 3 compiling and green; Task 4 replaces this with the real variant lookup + stock reservation.

- [ ] **Step 9: Run the full suite again**

Run: `mvn test`
Expected: all pass (125). `OrderServiceTest.shouldPlaceOrder` mocks `orderRepository.save` and verifies item count + cart delete — still passes.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/shopnow/domain/model/OrderItem.java \
        src/main/java/com/shopnow/domain/model/Order.java \
        src/main/java/com/shopnow/presentation/dto/OrderDto.java \
        src/main/java/com/shopnow/application/order/OrderService.java \
        src/main/resources/db/migration/V007__order_items_variant_fk.sql \
        src/test/java/com/shopnow/domain/model/OrderItemTest.java \
        src/test/java/com/shopnow/presentation/api/OrderControllerTest.java
git commit -m "refactor: make OrderItem.variant a real FK and add discountAmount to Order/OrderDto"
```

(add `src/test/java/com/shopnow/domain/model/OrderTest.java` to the `git add` list above)

---

## Task 4: OrderService.placeOrder rewrite + cancel restore + CouponRedemption

**Files:**
- Create: `src/main/java/com/shopnow/domain/model/CouponRedemption.java`
- Create: `src/main/java/com/shopnow/domain/port/CouponRedemptionRepository.java`
- Create: `src/main/java/com/shopnow/infrastructure/persistence/CouponRedemptionJpaRepository.java`
- Create: `src/main/java/com/shopnow/infrastructure/persistence/CouponRedemptionRepositoryImpl.java`
- Create: `src/main/java/com/shopnow/domain/port/ProductVariantRepository.java`
- Create: `src/main/java/com/shopnow/infrastructure/persistence/ProductVariantJpaRepository.java`
- Create: `src/main/java/com/shopnow/infrastructure/persistence/ProductVariantRepositoryImpl.java`
- Modify: `src/main/java/com/shopnow/application/order/OrderService.java`
- Modify: `src/main/java/com/shopnow/application/promotion/PromotionService.java` (add `validateAndApply` + `recordRedemption` + `reverseRedemption`)
- Modify: `src/main/java/com/shopnow/presentation/api/OrderController.java` (optional couponCode param)
- Rewrite test: `src/test/java/com/shopnow/application/order/OrderServiceTest.java`
- Modify test: `src/test/java/com/shopnow/presentation/api/OrderControllerTest.java` (coupon param test)
- Modify test: `src/test/java/com/shopnow/application/promotion/PromotionServiceTest.java` (add apply/record tests)

**Interfaces:**
- Consumes: `PromotionService`, `InventoryService`, `PromotionRepository`, `CouponRedemptionRepository` (this task), `ProductVariantRepository` (this task), `CartRepository`, `OrderRepository`.
- Produces: `OrderService.placeOrder(Long userId, String couponCode)` and `cancelOrder(Long orderId)` (unchanged signature, now restores stock + reverses coupon). `PromotionService.validateAndApply(String code, Long userId, BigDecimal cartSubtotal)` returns a `DiscountResult(BigDecimal discountAmount, Promotion promotion)` (throws `PromotionException` on any failure). `PromotionService.recordRedemption(Promotion, Long userId, Long orderId)` inserts a `CouponRedemption` and increments usageCount. `PromotionService.reverseRedemption(Long orderId)` deletes a redemption by order and decrements usageCount (no-op if none). `ProductVariantRepository.findById(Long)`.

> **Lock order (deadlock avoidance):** `placeOrder` locks the Promotion row first (for the usage-limit check), then Inventory rows in variantId order. `PromotionService.validateAndApply` must lock the promotion. To keep the lock inside the `placeOrder` transaction, `validateAndApply` is called within `placeOrder`'s `@Transactional` boundary and the `PromotionRepository` exposes a `findByIdForUpdate` — added in this task.

- [ ] **Step 1: Add findByIdForUpdate to PromotionRepository + JPA + adapter**

Add to the `PromotionRepository` port interface (`src/main/java/com/shopnow/domain/port/PromotionRepository.java`):

```java
    Optional<Promotion> findByIdForUpdate(Long id);
```

Add to `PromotionJpaRepository.java`:

```java
    @jakarta.persistence.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Promotion p WHERE p.id = :id")
    Optional<Promotion> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
```

Add to `PromotionRepositoryImpl.java`:

```java
    @Override
    public Optional<Promotion> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id);
    }
```

- [ ] **Step 2: Create the CouponRedemption entity**

```java
// src/main/java/com/shopnow/domain/model/CouponRedemption.java
package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons_used",
        uniqueConstraints = @UniqueConstraint(columnNames = {"promotion_id", "user_id"}))
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt = LocalDateTime.now();

    protected CouponRedemption() {
    }

    public CouponRedemption(Promotion promotion, Long userId, Long orderId) {
        this.promotion = promotion;
        this.userId = userId;
        this.orderId = orderId;
    }

    public Long getId() { return id; }
    public Promotion getPromotion() { return promotion; }
    public Long getUserId() { return userId; }
    public Long getOrderId() { return orderId; }
    public LocalDateTime getUsedAt() { return usedAt; }
}
```

- [ ] **Step 3: Create the CouponRedemption port + JPA + adapter**

```java
// src/main/java/com/shopnow/domain/port/CouponRedemptionRepository.java
package com.shopnow.domain.port;

import com.shopnow.domain.model.CouponRedemption;

import java.util.Optional;

public interface CouponRedemptionRepository {
    CouponRedemption save(CouponRedemption redemption);
    boolean existsByPromotionIdAndUserId(Long promotionId, Long userId);
    Optional<CouponRedemption> findByOrderId(Long orderId);
    void deleteById(Long id);
}
```

```java
// src/main/java/com/shopnow/infrastructure/persistence/CouponRedemptionJpaRepository.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponRedemptionJpaRepository extends JpaRepository<CouponRedemption, Long> {
    boolean existsByPromotionIdAndUserId(Long promotionId, Long userId);
    Optional<CouponRedemption> findByOrderId(Long orderId);
}
```

```java
// src/main/java/com/shopnow/infrastructure/persistence/CouponRedemptionRepositoryImpl.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.CouponRedemption;
import com.shopnow.domain.port.CouponRedemptionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CouponRedemptionRepositoryImpl implements CouponRedemptionRepository {

    private final CouponRedemptionJpaRepository jpaRepository;

    public CouponRedemptionRepositoryImpl(CouponRedemptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CouponRedemption save(CouponRedemption redemption) {
        return jpaRepository.save(redemption);
    }

    @Override
    public boolean existsByPromotionIdAndUserId(Long promotionId, Long userId) {
        return jpaRepository.existsByPromotionIdAndUserId(promotionId, userId);
    }

    @Override
    public Optional<CouponRedemption> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
```

- [ ] **Step 4: Create the ProductVariant port + JPA + adapter**

```java
// src/main/java/com/shopnow/domain/port/ProductVariantRepository.java
package com.shopnow.domain.port;

import com.shopnow.domain.model.ProductVariant;

import java.util.Optional;

public interface ProductVariantRepository {
    Optional<ProductVariant> findById(Long id);
}
```

```java
// src/main/java/com/shopnow/infrastructure/persistence/ProductVariantJpaRepository.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantJpaRepository extends JpaRepository<ProductVariant, Long> {
}
```

```java
// src/main/java/com/shopnow/infrastructure/persistence/ProductVariantRepositoryImpl.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.ProductVariant;
import com.shopnow.domain.port.ProductVariantRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ProductVariantRepositoryImpl implements ProductVariantRepository {

    private final ProductVariantJpaRepository jpaRepository;

    public ProductVariantRepositoryImpl(ProductVariantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ProductVariant> findById(Long id) {
        return jpaRepository.findById(id);
    }
}
```

- [ ] **Step 5: Add validate/apply/record/reverse to PromotionService**

In `src/main/java/com/shopnow/application/promotion/PromotionService.java`, inject `CouponRedemptionRepository` and add the apply/record/reverse methods. Replace the constructor and add the new methods + a `DiscountResult` record. The updated class:

```java
// src/main/java/com/shopnow/application/promotion/PromotionService.java
package com.shopnow.application.promotion;

import com.shopnow.domain.model.CouponRedemption;
import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.model.PromotionException;
import com.shopnow.domain.port.CouponRedemptionRepository;
import com.shopnow.domain.port.PromotionRepository;
import com.shopnow.presentation.dto.CreatePromotionRequest;
import com.shopnow.presentation.dto.PromotionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;

    public PromotionService(PromotionRepository promotionRepository,
                            CouponRedemptionRepository couponRedemptionRepository) {
        this.promotionRepository = promotionRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
    }

    public record DiscountResult(BigDecimal discountAmount, Promotion promotion) {
    }

    /**
     * Validate a coupon for the given user + cart subtotal and return the discount.
     * Locks the promotion row (caller's transaction) for a consistent usage_count read.
     * Does NOT record the redemption or mutate usage_count — call recordRedemption after the order is saved.
     */
    public DiscountResult validateAndApply(String code, Long userId, BigDecimal cartSubtotal) {
        if (code == null || code.isBlank()) {
            return new DiscountResult(BigDecimal.ZERO, null);
        }
        Promotion promotion = promotionRepository.findByCode(code)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found: " + code));
        Promotion locked = promotionRepository.findByIdForUpdate(promotion.getId())
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found: " + code));

        if (locked.getStatus() != Promotion.PromoStatus.ACTIVE) {
            throw new PromotionException(PromotionException.Code.INACTIVE, "Promotion is inactive");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(locked.getStartsAt()) || now.isAfter(locked.getEndsAt())) {
            throw new PromotionException(PromotionException.Code.EXPIRED, "Promotion is not within its active window");
        }
        if (locked.getMinOrderValue() != null && cartSubtotal.compareTo(locked.getMinOrderValue()) < 0) {
            throw new PromotionException(PromotionException.Code.MIN_NOT_MET, "Cart subtotal below minimum order value");
        }
        if (locked.getUsageLimit() != null && locked.getUsageCount() >= locked.getUsageLimit()) {
            throw new PromotionException(PromotionException.Code.USAGE_EXCEEDED, "Promotion usage limit reached");
        }
        if (couponRedemptionRepository.existsByPromotionIdAndUserId(locked.getId(), userId)) {
            throw new PromotionException(PromotionException.Code.ALREADY_USED, "You have already used this promotion");
        }
        return new DiscountResult(computeDiscount(locked, cartSubtotal), locked);
    }

    private BigDecimal computeDiscount(Promotion promotion, BigDecimal subtotal) {
        BigDecimal discount;
        if (promotion.getType() == Promotion.PromoType.PERCENTAGE) {
            discount = subtotal.multiply(promotion.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = promotion.getValue();
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        if (discount.signum() < 0) {
            discount = BigDecimal.ZERO;
        }
        return discount;
    }

    @Transactional
    public void recordRedemption(Promotion promotion, Long userId, Long orderId) {
        couponRedemptionRepository.save(new CouponRedemption(promotion, userId, orderId));
        promotion.setUsageCount((promotion.getUsageCount() == null ? 0 : promotion.getUsageCount()) + 1);
        promotionRepository.save(promotion);
    }

    @Transactional
    public void reverseRedemption(Long orderId) {
        couponRedemptionRepository.findByOrderId(orderId).ifPresent(redemption -> {
            couponRedemptionRepository.deleteById(redemption.getId());
            Promotion promotion = redemption.getPromotion();
            promotion.setUsageCount(Math.max(0, (promotion.getUsageCount() == null ? 0 : promotion.getUsageCount()) - 1));
            promotionRepository.save(promotion);
        });
    }

    // --- admin CRUD methods unchanged from Task 1 ---
    @Transactional
    public PromotionDto create(CreatePromotionRequest request) {
        validate(request);
        Promotion promotion = new Promotion(
                request.code(), request.type(), request.value(), request.minOrderValue(),
                request.usageLimit(), request.startsAt(), request.endsAt(), request.status());
        return toDto(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionDto update(Long id, CreatePromotionRequest request) {
        validate(request);
        Promotion existing = promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found"));
        Promotion updated = new Promotion(
                request.code(), request.type(), request.value(), request.minOrderValue(),
                request.usageLimit(), request.startsAt(), request.endsAt(), request.status());
        try {
            var idField = Promotion.class.getDeclaredField("id"); idField.setAccessible(true); idField.set(updated, existing.getId());
            var usageField = Promotion.class.getDeclaredField("usageCount"); usageField.setAccessible(true); usageField.set(updated, existing.getUsageCount());
        } catch (Exception e) { throw new IllegalStateException(e); }
        return toDto(promotionRepository.save(updated));
    }

    @Transactional(readOnly = true)
    public PromotionDto get(Long id) {
        return toDto(promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found")));
    }

    @Transactional(readOnly = true)
    public List<PromotionDto> list() {
        return promotionRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found"));
        if (promotion.getUsageCount() != null && promotion.getUsageCount() > 0) {
            throw new PromotionException(PromotionException.Code.USAGE_EXCEEDED,
                    "Cannot delete a promotion that has been redeemed; set status=INACTIVE instead");
        }
        promotionRepository.deleteById(id);
    }

    private void validate(CreatePromotionRequest request) {
        if (request.endsAt().isBefore(request.startsAt())) {
            throw new PromotionException(PromotionException.Code.INVALID_VALUE, "endsAt must be after startsAt");
        }
        if (request.type() == Promotion.PromoType.PERCENTAGE) {
            double v = request.value().doubleValue();
            if (v < 1 || v > 100) {
                throw new PromotionException(PromotionException.Code.INVALID_VALUE, "PERCENTAGE value must be between 1 and 100");
            }
        }
    }

    private PromotionDto toDto(Promotion p) {
        return new PromotionDto(p.getId(), p.getCode(), p.getType().name(), p.getValue(),
                p.getMinOrderValue(), p.getUsageLimit(), p.getUsageCount(),
                p.getStartsAt(), p.getEndsAt(), p.getStatus().name());
    }
}
```

- [ ] **Step 6: Rewrite OrderService.placeOrder + cancelOrder**

Replace the entire contents of `src/main/java/com/shopnow/application/order/OrderService.java` with:

```java
// src/main/java/com/shopnow/application/order/OrderService.java
package com.shopnow.application.order;

import com.shopnow.application.inventory.InventoryService;
import com.shopnow.application.promotion.PromotionService;
import com.shopnow.domain.model.Cart;
import com.shopnow.domain.model.Order;
import com.shopnow.domain.model.OrderItem;
import com.shopnow.domain.model.ProductVariant;
import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.port.CartRepository;
import com.shopnow.domain.port.OrderRepository;
import com.shopnow.domain.port.ProductVariantRepository;
import com.shopnow.presentation.dto.OrderDto;
import com.shopnow.presentation.dto.OrderItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryService inventoryService;
    private final PromotionService promotionService;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        ProductVariantRepository productVariantRepository,
                        InventoryService inventoryService,
                        PromotionService promotionService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryService = inventoryService;
        this.promotionService = promotionService;
    }

    @Transactional
    public OrderDto placeOrder(Long userId, String couponCode) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        BigDecimal subtotal = cart.getTotal();

        // 1. Validate + apply coupon (locks promotion row within this tx)
        PromotionService.DiscountResult discount = promotionService.validateAndApply(couponCode, userId, subtotal);

        // 2. Build order items with real variants (looked up for the FK + snapshots)
        List<OrderItem> items = new ArrayList<>();
        List<InventoryService.StockRequest> stockRequests = new ArrayList<>();
        for (var ci : cart.getItems()) {
            ProductVariant variant = productVariantRepository.findById(ci.getVariantId())
                    .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + ci.getVariantId()));
            items.add(new OrderItem(
                    variant.getProduct().getId(), variant,
                    variant.getProduct().getName(), variant.getVariantName(),
                    ci.getQuantity(), ci.getPrice()));
            stockRequests.add(new InventoryService.StockRequest(ci.getVariantId(), ci.getQuantity()));
        }

        // 3. Reserve + commit stock (locks inventory rows in variantId order)
        inventoryService.commitStock(stockRequests);

        // 4. Persist order with discount
        BigDecimal total = subtotal.subtract(discount.discountAmount());
        Order order = new Order(userId, items, total, discount.discountAmount());
        Order saved = orderRepository.save(order);

        // 5. Record coupon redemption (now that the order has an id)
        if (discount.promotion() != null) {
            promotionService.recordRedemption(discount.promotion(), userId, saved.getId());
        }

        // 6. Clear cart after successful commit
        cartRepository.deleteByUserId(userId);

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        return toDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public OrderDto cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        order.cancel();

        // Restore stock for each item
        List<InventoryService.StockRequest> restores = order.getItems().stream()
                .map(i -> new InventoryService.StockRequest(i.getVariantId(), i.getQuantity()))
                .toList();
        inventoryService.restoreStock(restores);

        // Reverse coupon redemption if any
        promotionService.reverseRedemption(orderId);

        Order saved = orderRepository.save(order);
        return toDto(saved);
    }

    private OrderDto toDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(i -> new OrderItemDto(
                        i.getProductId(), i.getVariantId(),
                        i.getProductName(), i.getVariantName(),
                        i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();
        return new OrderDto(order.getId(), order.getUserId(), order.getStatus().name(),
                order.getTotalAmount(), order.getDiscountAmount(), itemDtos);
    }
}
```

- [ ] **Step 7: Add optional couponCode param to OrderController**

In `src/main/java/com/shopnow/presentation/api/OrderController.java`, change the `placeOrder` method signature:

```java
    @PostMapping
    public ResponseEntity<OrderDto> placeOrder(@AuthenticationPrincipal UserPrincipal principal,
                                               @RequestParam(required = false) String couponCode) {
        return ResponseEntity.status(201).body(orderService.placeOrder(principal.userId(), couponCode));
    }
```

- [ ] **Step 8: Rewrite OrderServiceTest**

Replace the entire contents of `src/test/java/com/shopnow/application/order/OrderServiceTest.java` with:

```java
// src/test/java/com/shopnow/application/order/OrderServiceTest.java
package com.shopnow.application.order;

import com.shopnow.application.inventory.InventoryService;
import com.shopnow.application.promotion.PromotionService;
import com.shopnow.domain.model.Cart;
import com.shopnow.domain.model.Category;
import com.shopnow.domain.model.Order;
import com.shopnow.domain.model.Product;
import com.shopnow.domain.model.ProductVariant;
import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.port.CartRepository;
import com.shopnow.domain.port.OrderRepository;
import com.shopnow.domain.port.ProductVariantRepository;
import com.shopnow.presentation.dto.OrderDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private InventoryService inventoryService;
    @Mock private PromotionService promotionService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartRepository, productVariantRepository,
                inventoryService, promotionService);
    }

    private ProductVariant variant(Long id) {
        Category category = new Category("Electronics", "electronics");
        Product product = new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00"));
        ProductVariant v = new ProductVariant(product, "SKU-1", new BigDecimal("999.00"));
        try {
            var f = ProductVariant.class.getDeclaredField("id"); f.setAccessible(true); f.set(v, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return v;
    }

    @Test
    void shouldPlaceOrderWithoutCoupon() {
        Cart cart = new Cart(1L);
        cart.addItem(7L, "SKU-1", new BigDecimal("999.00"), 1);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(promotionService.validateAndApply(null, 1L, new BigDecimal("999.00")))
                .thenReturn(new PromotionService.DiscountResult(BigDecimal.ZERO, null));
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(variant(7L)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            var f = Order.class.getDeclaredField("id"); f.setAccessible(true); f.set(o, 1L);
            return o;
        });

        OrderDto result = orderService.placeOrder(1L, null);

        assertEquals(new BigDecimal("999.00"), result.totalAmount());
        assertEquals(new BigDecimal("0.00"), result.discountAmount());
        verify(inventoryService).commitStock(anyList());
        verify(promotionService, never()).recordRedemption(any(), any(), any());
        verify(cartRepository).deleteByUserId(1L);
    }

    @Test
    void shouldApplyCouponDiscount() {
        Cart cart = new Cart(1L);
        cart.addItem(7L, "SKU-1", new BigDecimal("100.00"), 1);
        Promotion promo = new Promotion("TEN", Promotion.PromoType.FIXED, new BigDecimal("10"),
                null, null, java.time.LocalDateTime.now().minusDays(1),
                java.time.LocalDateTime.now().plusDays(1), Promotion.PromoStatus.ACTIVE);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(promotionService.validateAndApply("TEN", 1L, new BigDecimal("100.00")))
                .thenReturn(new PromotionService.DiscountResult(new BigDecimal("10"), promo));
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(variant(7L)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            var f = Order.class.getDeclaredField("id"); f.setAccessible(true); f.set(o, 1L);
            return o;
        });

        OrderDto result = orderService.placeOrder(1L, "TEN");

        assertEquals(new BigDecimal("90.00"), result.totalAmount());
        assertEquals(new BigDecimal("10"), result.discountAmount());
        verify(promotionService).recordRedemption(promo, 1L, 1L);
    }

    @Test
    void shouldThrowWhenCartEmpty() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(1L, null));
    }

    @Test
    void shouldThrowWhenVariantMissing() {
        Cart cart = new Cart(1L);
        cart.addItem(99L, "SKU-9", new BigDecimal("100.00"), 1);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(promotionService.validateAndApply(null, 1L, new BigDecimal("100.00")))
                .thenReturn(new PromotionService.DiscountResult(BigDecimal.ZERO, null));
        when(productVariantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(1L, null));
        verify(inventoryService, never()).commitStock(anyList());
    }

    @Test
    void shouldCancelOrderAndRestoreStock() {
        Order order = new Order(1L, java.util.List.of(), BigDecimal.ZERO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.cancelOrder(1L);

        assertEquals("CANCELLED", result.status());
        verify(inventoryService).restoreStock(anyList());
        verify(promotionService).reverseRedemption(1L);
    }

    @Test
    void shouldGetOrder() {
        Order order = new Order(1L, java.util.List.of(), new BigDecimal("50.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDto result = orderService.getOrder(1L);
        assertEquals(new BigDecimal("50.00"), result.totalAmount());
    }
}
```

- [ ] **Step 9: Update OrderControllerTest for the coupon param**

In `src/test/java/com/shopnow/presentation/api/OrderControllerTest.java`, add a test verifying the couponCode request param is passed through. Add this method:

```java
    @Test
    void shouldPlaceOrderWithCoupon() throws Exception {
        when(orderService.placeOrder(eq(1L), eq("TEN"))).thenReturn(
                new OrderDto(2L, 1L, "PENDING", new BigDecimal("90.00"), new BigDecimal("10.00"), List.of()));

        mockMvc.perform(post("/api/v1/orders")
                        .param("couponCode", "TEN")
                        .with(authentication(principal())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountAmount").value(10.00));
    }
```

This requires the imports:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
```

(`eq`, `any`, `authentication`, `post` — ensure all are imported; `post` is the new one.)

- [ ] **Step 10: Update PromotionServiceTest for the new constructor**

The `PromotionServiceTest` constructor changed (now takes `CouponRedemptionRepository`). Update the test's `setUp`. In `src/test/java/com/shopnow/application/promotion/PromotionServiceTest.java`:

Add a mock field:

```java
    @Mock
    private com.shopnow.domain.port.CouponRedemptionRepository couponRedemptionRepository;
```

Change the `setUp`:

```java
    @BeforeEach
    void setUp() {
        promotionService = new PromotionService(promotionRepository, couponRedemptionRepository);
    }
```

The existing 8 tests remain valid (they don't call validateAndApply). Add one apply test at the end of the class:

```java
    @Test
    void shouldValidateAndApplyPercentageDiscount() {
        Promotion promo = new Promotion("SUMMER20", Promotion.PromoType.PERCENTAGE, new BigDecimal("20"),
                null, null, start, end, Promotion.PromoStatus.ACTIVE);
        when(promotionRepository.findByCode("SUMMER20")).thenReturn(Optional.of(promo));
        when(promotionRepository.findByIdForUpdate(any())).thenReturn(Optional.of(promo));
        when(couponRedemptionRepository.existsByPromotionIdAndUserId(any(), any())).thenReturn(false);

        PromotionService.DiscountResult result = promotionService.validateAndApply("SUMMER20", 1L, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("20.00"), result.discountAmount());
        assertEquals(promo, result.promotion());
    }

    @Test
    void shouldReturnZeroDiscountWhenNoCode() {
        PromotionService.DiscountResult result = promotionService.validateAndApply(null, 1L, new BigDecimal("100.00"));
        assertEquals(BigDecimal.ZERO, result.discountAmount());
        assertNull(result.promotion());
    }
```

Add imports: `import static org.mockito.ArgumentMatchers.any;` (already present) — `findByIdForUpdate` uses `any()`. Ensure `import java.util.Optional;` and `import java.math.BigDecimal;` are present.

- [ ] **Step 11: Run the affected tests**

Run: `mvn test -Dtest=OrderServiceTest,OrderControllerTest,PromotionServiceTest`
Expected: PASS.

- [ ] **Step 12: Run the full suite**

Run: `mvn test`
Expected: all pass (previous 125 + new tests). Count: OrderServiceTest 6, PromotionServiceTest 10, OrderControllerTest 4 (added coupon test), AdminPromotionControllerTest 5, InventoryServiceTest 5, plus the rest unchanged.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/com/shopnow/domain/model/CouponRedemption.java \
        src/main/java/com/shopnow/domain/port/CouponRedemptionRepository.java \
        src/main/java/com/shopnow/domain/port/PromotionRepository.java \
        src/main/java/com/shopnow/domain/port/ProductVariantRepository.java \
        src/main/java/com/shopnow/infrastructure/persistence/CouponRedemptionJpaRepository.java \
        src/main/java/com/shopnow/infrastructure/persistence/CouponRedemptionRepositoryImpl.java \
        src/main/java/com/shopnow/infrastructure/persistence/PromotionJpaRepository.java \
        src/main/java/com/shopnow/infrastructure/persistence/PromotionRepositoryImpl.java \
        src/main/java/com/shopnow/infrastructure/persistence/ProductVariantJpaRepository.java \
        src/main/java/com/shopnow/infrastructure/persistence/ProductVariantRepositoryImpl.java \
        src/main/java/com/shopnow/application/promotion/PromotionService.java \
        src/main/java/com/shopnow/application/order/OrderService.java \
        src/main/java/com/shopnow/presentation/api/OrderController.java \
        src/test/java/com/shopnow/application/order/OrderServiceTest.java \
        src/test/java/com/shopnow/presentation/api/OrderControllerTest.java \
        src/test/java/com/shopnow/application/promotion/PromotionServiceTest.java
git commit -m "feat: apply coupon and reserve stock in checkout, restore on cancel"
```

---

## Task 5: AdminInventoryController low-stock

**Files:**
- Create: `src/main/java/com/shopnow/presentation/admin/AdminInventoryController.java`
- Test: `src/test/java/com/shopnow/presentation/admin/AdminInventoryControllerTest.java`

**Interfaces:**
- Consumes: `InventoryService.lowStock()` (Task 2).
- Produces: `GET /api/v1/admin/inventory/low-stock` (ADMIN) → `List<LowStockDto>`.

- [ ] **Step 1: Create the controller**

```java
// src/main/java/com/shopnow/presentation/admin/AdminInventoryController.java
package com.shopnow.presentation.admin;

import com.shopnow.application.inventory.InventoryService;
import com.shopnow.presentation.dto.LowStockDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<LowStockDto>> lowStock() {
        return ResponseEntity.ok(inventoryService.lowStock());
    }
}
```

- [ ] **Step 2: Write the controller test**

```java
// src/test/java/com/shopnow/presentation/admin/AdminInventoryControllerTest.java
package com.shopnow.presentation.admin;

import com.shopnow.application.inventory.InventoryService;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.infrastructure.security.TestSecurityConfig;
import com.shopnow.presentation.dto.LowStockDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminInventoryController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class AdminInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnLowStock() throws Exception {
        when(inventoryService.lowStock()).thenReturn(List.of(
                new LowStockDto(7L, "SKU-7", "iPhone 15", 2, 0, 2, 10)));

        mockMvc.perform(get("/api/v1/admin/inventory/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].variantId").value(7))
                .andExpect(jsonPath("$[0].available").value(2))
                .andExpect(jsonPath("$[0].threshold").value(10));
    }

    @Test
    void shouldRejectWithoutAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inventory/low-stock"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 3: Run the test**

Run: `mvn test -Dtest=AdminInventoryControllerTest`
Expected: PASS (2 tests).

- [ ] **Step 4: Run the full suite**

Run: `mvn test`
Expected: all pass (previous + 2).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/shopnow/presentation/admin/AdminInventoryController.java \
        src/test/java/com/shopnow/presentation/admin/AdminInventoryControllerTest.java
git commit -m "feat: add admin low-stock inventory report endpoint"
```

---

## Task 6: Full Suite + Push

**Files:**
- No new files. Final verification.

- [ ] **Step 1: Run the entire test suite**

Run: `mvn clean test`
Expected: ALL tests pass (existing 105 + new ~35 = ~140), 0 failures, 0 errors.

- [ ] **Step 2: Sanity-check compile + context load**

Run: `mvn clean compile`
Expected: BUILD SUCCESS — confirms the full Spring context wires (new repos, services, controllers, SecurityConfig).

- [ ] **Step 3: Push to GitHub**

```bash
git push origin main
```

---

## Summary

After this plan, ShopNow has:

1. PERCENTAGE/FIXED cart-scope coupons with admin CRUD (`/api/v1/admin/promotions`).
2. Coupon redemption at checkout: `POST /api/v1/orders?couponCode=...` applies the discount, records the redemption (one-use-per-user, usage-limit, active-window, min-order enforced).
3. Inventory reserve+commit inside `placeOrder` with pessimistic row locks (sorted by variantId) preventing oversell; restore on cancel.
4. `OrderItem.variantId` as a real FK to `product_variants(id)`; `OrderDto.discountAmount` populated.
5. Admin low-stock report (`/api/v1/admin/inventory/low-stock`).
6. Concurrency: stock oversell prevented by `FOR UPDATE`; coupon double-use by DB `UNIQUE(promotion_id,user_id)` + service check; all mutations in one `@Transactional` boundary.

**Deferred (out of scope):** two-step quote/payment checkout, payment gateway, category/product/variant-scoped promotions, `max_discount` cap, async low-stock notifications, idempotency keys.
