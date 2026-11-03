# ShopNow Promotions & Inventory Design

**Date:** 2026-07-21
**Phase:** Post-Reviews — Promotions (coupons) & Inventory reservation
**Status:** Draft

## 1. Overview

Adds coupon promotions (admin-managed, redeemed at checkout) and inventory reservation to the existing single-step checkout. A customer passes an optional coupon code on `placeOrder`; the service applies the discount, reserves+commits stock atomically, and records the redemption. Admins get promotion CRUD and a low-stock report.

**In scope:**
- PERCENTAGE and FIXED coupon types, cart-scope only
- Coupon validation: active window, min-order value, usage limit, one-use-per-user
- Discount applied to order total; `Order.discountAmount` populated
- Inventory reserve+commit inside `placeOrder` with row locking; restore on cancel
- Admin promotion CRUD (`/api/v1/admin/promotions`)
- Admin low-stock report (`/api/v1/admin/inventory/low-stock`)
- `OrderItem.variantId` becomes a real FK to `product_variants(id)`

**Out of scope (deferred):**
- Two-step quote/payment checkout, quote TTL, payment gateway (future phase)
- Category/Product/Variant-scoped promotions (cart-scope only this phase)
- `max_discount` percentage cap
- Flash-sale-specific mechanics beyond time windows
- Email/async low-stock notifications (log-only this phase)
- Idempotency keys

## 2. Architecture

Layered monolith, consistent with prior phases. Two sub-domains converge in `OrderService.placeOrder`.

### New Components

| Layer | Class | Responsibility |
|-------|-------|----------------|
| Domain | `Promotion` entity | id, code (unique, uppercased), type (PERCENTAGE/FIXED), value, minOrderValue, usageLimit, usageCount, startsAt, endsAt, status (ACTIVE/INACTIVE), timestamps |
| Domain | `CouponRedemption` entity | promotion, userId (Long), orderId (Long), usedAt; `UNIQUE(promotion_id, user_id)` |
| Domain | `PromotionRepository` port | findByCode, findById, save, findAll, deleteById, existsById |
| Domain | `InventoryRepository` port | findByVariantId, findByVariantIdForUpdate (lock), save, findLowStock |
| Infrastructure | JPA repos + adapters for both ports | |
| Domain | `PromotionException` | carries a specific code (NOT_FOUND / EXPIRED / USAGE_EXCEEDED / ALREADY_USED / MIN_NOT_MET / INACTIVE / INVALID_VALUE) |
| Domain | `InsufficientStockException` | 409 signal |
| Application | `PromotionService` | admin CRUD + validate/apply (window, usage, one-per-user, min-order, discount math) |
| Application | `InventoryService` | reserve+commit for (variantId, qty) set; restore on cancel; low-stock query |
| Presentation | `AdminPromotionController` | CRUD `/api/v1/admin/promotions` (ADMIN) |
| Presentation | `AdminInventoryController` | `GET /api/v1/admin/inventory/low-stock` (ADMIN) |
| Modified | `OrderService.placeOrder(userId, couponCode)` | apply coupon → reserve+commit stock → persist order with discount → record redemption → clear cart |
| Modified | `OrderService.cancelOrder(orderId)` | restore quantity per item; reverse coupon redemption + usageCount |
| Modified | `OrderItem.variantId` | real FK to `product_variants(id)` (was denormalized Long) |
| Modified | `Order` | `discountAmount` now populated (field already exists) |
| Modified | `OrderDto` | add `discountAmount` |
| Modified | `GlobalExceptionHandler` | handlers for `PromotionException` (code-driven status), `InsufficientStockException` (409) |

### Security Model

- `/api/v1/admin/promotions/**` and `/api/v1/admin/inventory/**` → covered by existing `/api/v1/admin/**` → `hasRole("ADMIN")`. No new SecurityConfig rules needed.
- `POST /api/v1/orders?couponCode=...` → existing `/api/v1/orders/**` → `hasRole("CUSTOMER")`. `couponCode` is optional.

### Module Decoupling

- `CouponRedemption` stores `userId` as a plain Long (no JPA relation to `User`), matching the `Order`/`Review` pattern.
- `InventoryService` exposes a port-style API (`InventoryReservation` record) so `OrderService` depends on the service, not the repository.

## 3. Data Flow

### placeOrder(userId, couponCode)

1. Load cart from Redis; empty → `IllegalStateException` (existing behavior).
2. **Coupon validation (if `couponCode` present):** find by code → 404 if missing; checks below.
   - `status == ACTIVE` (else `PromotionException` INACTIVE)
   - `now` within `[startsAt, endsAt]` (else EXPIRED)
   - cart subtotal ≥ `minOrderValue` (else MIN_NOT_MET)
   - `usageCount < usageLimit` (if limit set) (else USAGE_EXCEEDED)
   - not already redeemed by this user (else ALREADY_USED)
   - Compute discount; `discountAmount ∈ [0, subtotal]`.
3. **Stock reservation (per cart item, sorted by variantId ascending):** lock `Inventory` (`FOR UPDATE`), `inventory.reserve(qty)` then `inventory.commitReservation(qty)` immediately. If `available < qty` → `InsufficientStockException` (409) → full rollback.
4. **Persist order:** `totalAmount = subtotal - discountAmount`, `discountAmount` set; `OrderItem`s snapshot variant/product/price; save.
5. **Record redemption (if coupon):** insert `CouponRedemption`; `promotion.usageCount++`.
6. **Low-stock check:** after commit, for any variant with `available < threshold`, log WARN.
7. Clear cart (after DB commit). Return `OrderDto`.

### cancelOrder(orderId)

1. Load order; `order.cancel()` (PENDING only — existing behavior).
2. For each `OrderItem`: lock `Inventory`, `quantity += item.quantity` (restore). Save.
3. If order redeemed a coupon: delete `CouponRedemption`; `promotion.usageCount--`.
4. Return updated order.

### Discount math

- PERCENTAGE: `subtotal × value / 100`; `value` validated 1–100 at admin create.
- FIXED: `min(value, subtotal)` — capped to subtotal, never negative.
- Guardrail: `discountAmount ∈ [0, subtotal]`.

### Error codes

- `PromotionException` → status from carried code: NOT_FOUND (404), INACTIVE/EXPIRED/USAGE_EXCEEDED/ALREADY_USED (409), MIN_NOT_MET/INVALID_VALUE (400).
- `InsufficientStockException` → 409 `INSUFFICIENT_STOCK`.

## 4. Concurrency & Failure Handling

**Stock oversell — pessimistic row lock:**
- `findByVariantIdForUpdate` uses `@Lock(PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`).
- `placeOrder` locks + mutates each item's inventory inside one `@Transactional` method. Concurrent orders block on the row lock; the second sees updated `available` and 409s → rollback.
- Cart items sorted by `variantId` ascending before locking → deterministic lock order → no deadlocks.

**Coupon double-redemption — DB constraint backstop:**
- `UNIQUE(promotion_id, user_id)` on `coupons_used` is the backstop. Service-level check → 409 ALREADY_USED for the normal case. Under a race, `DataIntegrityViolationException` → existing handler → 409 CONFLICT. Both paths 409, never 500.

**Usage-limit race:**
- `placeOrder` `FOR UPDATE`-locks the `Promotion` row before the `usageCount < usageLimit` check, so the count is read consistently. Over-limit → 409, rollback.

**Global lock order (deadlock avoidance):**
- `placeOrder` acquires locks in a fixed order across all transactions: **Promotion row first (if a coupon is applied), then Inventory rows sorted by `variantId` ascending.** Because every concurrent checkout follows this same order, there is no lock-acquisition cycle and no deadlock. (Promotion and Inventory are disjoint row sets, and the within-Inventory order is deterministic via the variantId sort.)

**Atomicity:**
- Stock reserve+commit, coupon redemption, order save, usageCount++ — all in one `@Transactional` boundary. Any exception → full rollback, no partial state.
- Cart cleared only after the DB transaction commits. If commit fails, cart survives for retry.

**Low-stock:** synchronous WARN log per affected variant after a successful commit. No async notification this phase.

## 5. Data Model

### Promotion entity

```java
@Entity @Table(name = "promotions")
public class Promotion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;                  // uppercased on save

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromoType type;               // PERCENTAGE | FIXED

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;             // percent 1-100 (PERCENTAGE) or dollar (FIXED)

    @Column(name = "min_order_value", precision = 10, scale = 2)
    private BigDecimal minOrderValue;     // nullable

    @Column(name = "usage_limit")
    private Integer usageLimit;           // nullable

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromoStatus status = PromoStatus.INACTIVE;

    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    public enum PromoType { PERCENTAGE, FIXED }
    public enum PromoStatus { ACTIVE, INACTIVE }
}
```

### CouponRedemption entity

```java
@Entity @Table(name = "coupons_used",
    uniqueConstraints = @UniqueConstraint(columnNames = {"promotion_id", "user_id"}))
public class CouponRedemption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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
}
```

### Flyway migration V006__create_promotions.sql

```sql
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

### OrderItem.variantId FK — migration V007__order_items_variant_fk.sql

```sql
ALTER TABLE order_items
    ADD CONSTRAINT fk_oi_variant
    FOREIGN KEY (variant_id) REFERENCES product_variants(id);
```

**Known migration risk:** this fails if any existing `order_items` row references a non-existent variant. The current simplified `placeOrder` (and early test data) stored `variantId` from the cart, which may be fabricated. The implementation must confirm the dev DB is clean before applying V007, or use `createdb`-fresh state in tests (`ddl-auto=create-drop` for the test profile means the constraint is created fresh from the entity mapping — so the FK is enforced in tests via the `@JoinColumn`/entity relation regardless of the migration). For non-test environments, V007 runs against real data and requires valid rows.

### DTOs

```java
public record CreatePromotionRequest(
    @NotBlank @Size(max = 50) String code,
    @NotNull Promotion.PromoType type,
    @NotNull @DecimalMin("0.01") BigDecimal value,
    @DecimalMin("0") BigDecimal minOrderValue,        // nullable
    @Min(1) Integer usageLimit,                        // nullable
    @NotNull LocalDateTime startsAt,
    @NotNull LocalDateTime endsAt,
    Promotion.PromoStatus status                       // defaults INACTIVE if null
) {}

public record PromotionDto(
    Long id, String code, String type, BigDecimal value,
    BigDecimal minOrderValue, Integer usageLimit, Integer usageCount,
    LocalDateTime startsAt, LocalDateTime endsAt, String status
) {}

public record LowStockDto(
    Long variantId, String sku, String productName,
    Integer quantity, Integer reserved, Integer available, Integer threshold
) {}

public record OrderItemVariantDto(Long variantId, String sku) {}  // optional, for low-stock joins
```

`OrderDto` gains `discountAmount` (BigDecimal).

## 6. Admin APIs & RBAC

### AdminPromotionController — `/api/v1/admin/promotions`

| Method | Path | Body | Notes |
|--------|------|------|-------|
| POST | `` | `CreatePromotionRequest` | create; code uppercased; PERCENTAGE value 1–100; `endsAt > startsAt`; status default INACTIVE |
| GET | `` | — | list all |
| GET | `/{id}` | — | get one |
| PUT | `/{id}` | `CreatePromotionRequest` | full update |
| DELETE | `/{id}` | — | hard delete; 409 if already redeemed (preserve history) |

Validation (service → 400 INVALID_VALUE): `endsAt > startsAt`; PERCENTAGE `value` ∈ [1,100].

### AdminInventoryController — `/api/v1/admin/inventory/low-stock`

| Method | Path | Returns |
|--------|------|---------|
| GET | `/low-stock` | `List<LowStockDto>` — variants where `available < threshold` |

### RBAC

- `/api/v1/admin/promotions/**`, `/api/v1/admin/inventory/**` → existing `/api/v1/admin/**` → `hasRole("ADMIN")`. No new rules.
- `POST /api/v1/orders` with optional `couponCode` → existing `hasRole("CUSTOMER")`.

## 7. Testing Strategy

### Test layers

| Layer | Test | Approach | Count |
|-------|------|----------|-------|
| Domain | `PromotionServiceTest` | Mockito — validate/apply (window, usage, one-per-user, min-order, discount math) | ~8 |
| Domain | `InventoryServiceTest` | Mockito — reserve+commit success/insufficient, restore, low-stock | ~5 |
| Domain | `OrderServiceTest` (extend) | coupon applied, stock reserved, redemption recorded, cancel restores | ~6 |
| Controller | `AdminPromotionControllerTest` | `@WebMvcTest` — CRUD, role checks | ~5 |
| Controller | `AdminInventoryControllerTest` | `@WebMvcTest` — low-stock list, role check | ~3 |
| Controller | `OrderControllerTest` (extend) | `placeOrder` with/without coupon param | ~2 |
| Repository | `PromotionRepositoryTest` | `@DataJpaTest` + Testcontainers — findByCode, lock, usage_count | ~3 |
| Repository | `InventoryRepositoryTest` | `@DataJpaTest` + Testcontainers — findForUpdate lock, findLowStock | ~3 |

**Total:** ~35 new (105 existing + ~35 = ~140).

### Key scenarios

- **Discount math:** PERCENTAGE 20% on $100 → $20 off; FIXED $50 on $30 → $30 off (capped).
- **Validation:** expired → 409; before startsAt/after endsAt → 409; below min_order → 400; over usage_limit → 409; already used → 409; inactive → 409; PERCENTAGE value 0 or 150 → 400.
- **Stock:** checkout qty 3 when available 2 → 409 INSUFFICIENT_STOCK, nothing committed; partial reservation rolls back.
- **Cancel restore:** cancel qty-2 order → inventory.quantity += 2; redemption deleted; usageCount decremented; user can re-use coupon.
- **Low-stock:** qty=2/threshold=10 → in report; qty=15/threshold=10 → not.
- **OrderItem FK:** checkout referencing non-existent variant → 404.

### Concurrency proof

Pragmatic (true multi-threaded tests are brittle): assert `@Lock(PESSIMISTIC_WRITE)` present on `findByVariantIdForUpdate`; the insufficient-stock rollback test proves over-consumption is rejected; the rollback guarantees no partial commit.

### Existing tests affected

- `OrderServiceTest` / `OrderControllerTest` — `placeOrder` signature changes (coupon param, stock reservation). The plan's Task rewrites these tests with the added Inventory/Promotion mocks.
- No regression to auth/reviews phases.

**Verification gate:** full `mvn test` green (existing 105 + new ~35 = ~140).

## 8. Risk Notes

- **OrderItem.variantId FK migration (V007)** is the one risky migration: it requires existing `order_items` rows to reference valid variants. Tests use `ddl-auto=create-drop` so the entity mapping enforces the FK fresh; non-test environments must have clean data.
- **Stock reservation is single-step** (reserve+commit in placeOrder, no hold window). This matches the current simplified checkout; the future payment phase will introduce the quote-TTL hold window.
- **`CouponRedemption.orderId`** references the order being created — saved within the same transaction as the order, so the FK is always valid at commit.
- **Hard delete of redeemed promotions is blocked** (409) to preserve redemption history; admins must set `status=INACTIVE` to disable instead.
- **Concurrency guarantees** rest on pessimistic locks + DB constraints, proven pragmatically (lock annotation + rollback test) rather than flaky threaded tests.

## 9. References

- System design spec: `docs/superpowers/specs/2026-07-15-shopnow-design.md` (promotions table lines 316–332, coupons_used 334–343, checkout sequence 492–521, low-stock/threshold 301–302)
- Auth spec (`UserPrincipal`, `GlobalExceptionHandler`, RBAC patterns): `docs/superpowers/specs/2026-07-15-auth-design.md`
- Reviews spec (DataIntegrityViolationException → 409 pattern): `docs/superpowers/specs/2026-07-20-reviews-design.md`
- Existing code: `Inventory.java` (reserve/commit/release methods), `OrderService.java` (placeOrder to extend), `OrderItem.java` (variantId FK change)
