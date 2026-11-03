# ShopNow Reviews & Ratings Design

**Date:** 2026-07-20
**Phase:** Post-Auth — Product Reviews & Ratings
**Status:** Draft

## 1. Overview

Adds product reviews and ratings. Authenticated customers who have purchased a product can leave one 1–5 star review; ratings aggregate into the product's denormalized `avgRating`. Public users can browse reviews.

**In scope:**
- Product-level reviews (attach to `Product`, not variants)
- Verified-purchase gating (only buyers with a delivered/confirmed order can review)
- 1–5 integer rating scale
- One review per user per product (uniqueness)
- Recompute-on-write aggregation of `Product.avgRating` + new `reviewCount`
- Public review listing (paginated), authenticated create, owner delete

**Out of scope (deferred):**
- Admin moderation queue / `is_published` flag
- Review editing (PUT)
- "Helpful" upvotes / sorting by helpfulness
- Photo/video reviews
- Seller responses
- Elasticsearch indexing of review content

## 2. Architecture

Follows the existing layered monolith: presentation → application → domain ← infrastructure. The review domain stays decoupled from the order and user modules via ports.

### New Components

| Layer | Class | Responsibility |
|-------|-------|----------------|
| Domain | `Review` entity | id, product (FK), userId (Long), userName (denormalized), rating (1–5), comment, verifiedPurchase, createdAt |
| Domain | `ReviewRepository` port | save, findById, findByProductId(pageable), existsByUserIdAndProductId, deleteById, countAndAvgRatingByProductId |
| Domain | `OrderQueryPort` port | `hasUserPurchasedProduct(userId, productId)` — purchase verification |
| Infrastructure | `ReviewJpaRepository` + `ReviewRepositoryImpl` | JPA + aggregate query for avg_rating |
| Infrastructure | `OrderQueryAdapter` | implements `OrderQueryPort` via existing `OrderRepository` + `OrderItem.productId` |
| Application | `ReviewService` | create (verify purchase + uniqueness), delete, list, recompute avg_rating |
| Presentation | `ReviewController` | GET/POST `/api/v1/products/{slug}/reviews`, DELETE `/api/v1/reviews/{id}` |
| Presentation | `ReviewSecurity` bean | `isOwner(reviewId, authentication)` for delete |

### Security Model

- `GET /api/v1/products/{slug}/reviews` → `permitAll` (already covered by existing GET `/api/v1/products/**` rule — no new rule needed)
- `POST /api/v1/products/{slug}/reviews` → `hasRole('CUSTOMER')`
- `DELETE /api/v1/reviews/{id}` → `hasRole('CUSTOMER')` + `@reviewSecurity.isOwner(#id, authentication)`

### Module Decoupling

- `Review` stores `userId` as a plain `Long` (no JPA relation to `User`), matching the pattern `Order`/`OrderItem` already use (`OrderItem.productId`/`variantId` are Longs). Avoids cross-module lazy-loading coupling.
- Purchase verification goes through `OrderQueryPort`, so the review domain depends on a port, not the order module's internals.

## 3. Data Flow

### Create review

1. `POST /api/v1/products/{slug}/reviews` with `{rating, comment}`, authenticated CUSTOMER
2. `ReviewController` resolves product by slug (`ProductRepository.findBySlug`); `userId` from `@AuthenticationPrincipal`
3. `ReviewService.createReview(userId, userName, productId, rating, comment)`:
   - **Uniqueness:** `existsByUserIdAndProductId(userId, productId)` → if true, 409 `REVIEW_EXISTS`
   - **Purchase verification:** `orderQueryPort.hasUserPurchasedProduct(userId, productId)` → if false, 403 `VERIFIED_PURCHASE_REQUIRED`
   - Create `Review` with `verifiedPurchase = true`
   - Save review → recompute `Product.avgRating`/`reviewCount` via aggregate query → save product
4. Return 201 with `ReviewDto`

### List reviews

1. `GET /api/v1/products/{slug}/reviews?page=0&size=10` — `permitAll`
2. Resolve product by slug → `reviewRepository.findByProductId(productId, pageable)`
3. Return `ReviewPageDto` (reviews + `avgRating` + `reviewCount` + pagination meta)

### Delete review

1. `DELETE /api/v1/reviews/{id}`, authenticated CUSTOMER
2. `@PreAuthorize("@reviewSecurity.isOwner(#id, authentication)")` — loads review, checks `review.userId == principal.userId`
3. `ReviewService.deleteReview(reviewId)`:
   - Load review (capture productId) → delete → recompute `Product.avgRating`/`reviewCount`
4. Return 204

### Verified-purchase check (`OrderQueryAdapter.hasUserPurchasedProduct`)

- Query: does any `Order` for `userId` with status in `{CONFIRMED, SHIPPED, DELIVERED}` contain an `OrderItem` with `productId == target`?
- Reuses existing `OrderRepository` + `OrderItem.productId` (denormalized Long). PENDING/CANCELLED orders do NOT count.

### avg_rating recompute

- Aggregate: `SELECT COUNT(r), COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.product.id = :pid`
- Set `product.avgRating` (1 decimal) + `product.reviewCount`
- Same `@Transactional` boundary as the review write — atomic, no drift

### Error responses (via existing `GlobalExceptionHandler`)

- Duplicate review → 409 `REVIEW_EXISTS`
- No verified purchase → 403 `VERIFIED_PURCHASE_REQUIRED`
- Not your review → 403 (via `@PreAuthorize`)
- Product not found → 404 (existing `IllegalArgumentException` → 404 mapping)

## 4. Data Model

### Review entity

```java
@Entity @Table(name = "reviews",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "user_id", nullable = false)
    private Long userId;                  // FK to users.id, stored as Long (no JPA relation)

    @Column(name = "user_name", nullable = false, length = 201)
    private String userName;              // denormalized at creation to avoid N+1 on list views

    @Column(nullable = false)
    private Integer rating;               // 1–5

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "verified_purchase", nullable = false)
    private Boolean verifiedPurchase = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

### Product change

Add `reviewCount` (`avgRating` already exists from V002):

```java
@Column(nullable = false)
private Integer reviewCount = 0;
```

### Flyway migration (`V005__create_reviews.sql`)

```sql
ALTER TABLE products ADD COLUMN review_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    user_name VARCHAR(201) NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    verified_purchase BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, product_id)
);

CREATE INDEX idx_reviews_product_id ON reviews(product_id);
CREATE INDEX idx_reviews_user_id ON reviews(user_id);
```

### DTOs

```java
public record CreateReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating,
    @Size(max = 2000) String comment
) {}

public record ReviewDto(
    Long id,
    Long productId,
    Long userId,
    String userName,
    Integer rating,
    String comment,
    Boolean verifiedPurchase,
    LocalDateTime createdAt
) {}

public record ReviewPageDto(
    Long productId,
    BigDecimal avgRating,
    Integer reviewCount,
    List<ReviewDto> reviews,
    Long totalElements,
    Integer totalPages,
    Integer page
) {}
```

## 5. Testing Strategy

### Test layers

| Layer | Test | Approach | Count |
|-------|------|----------|-------|
| Domain | `ReviewServiceTest` | Mockito, mock ports | ~7 |
| Controller | `ReviewControllerTest` | `@WebMvcTest` + MockMvc | ~5 |
| Ownership | `ReviewSecurityTest` | Unit, mock repos | ~4 |
| Repository | `ReviewRepositoryTest` | `@DataJpaTest` + Testcontainers Postgres | ~4 |
| Integration | `OrderQueryAdapterTest` | `@DataJpaTest` + Testcontainers — purchase-detection query | ~3 |

**Total:** ~23 new tests (77 existing + 23 new = ~100).

### Key scenarios

**Create:** verified purchaser + first review → 201 + avg recomputed; no purchase → 403; duplicate → 409; rating out of range → 400; bad slug → 404.
**Delete:** owner → 204 + recompute; non-owner → 403; recompute reflects remaining reviews.
**List:** paginated, only that product's reviews; public (no auth) → 200; wrapper avg/count match stored values.
**avg_rating recompute (repo):** {5,4,3} → 4.0/count 3; none → 0.0/count 0; after delete, correct.
**Verified-purchase (`OrderQueryAdapter`):** DELIVERED order w/ product → true; PENDING only → false; CANCELLED → false; different product → false.

### Test infrastructure

- `@WebMvcTest` slice tests `@Import({SecurityConfig.class, TestSecurityConfig.class})` (auth-phase pattern; `TestSecurityConfig` already supplies `JwtService`/`JwtProperties`).
- Controller tests use `@WithMockUser(roles="CUSTOMER")` + `.with(authentication(...))` for the principal, like `CartControllerTest`.
- Testcontainers Postgres for `ReviewRepositoryTest` + `OrderQueryAdapterTest` (existing `@Container`/`@DynamicPropertySource` pattern).

**Verification gate:** full `mvn test` green (existing 77 + new ~23 = ~100).

## 6. Scope & Migration Plan

### Existing files touched

| File | Change |
|------|--------|
| `Product.java` | Add `reviewCount` field + `@Column` + getter |
| `SecurityConfig.java` | Add two `hasRole('CUSTOMER')` matchers (before `anyRequest()`): `POST /api/v1/products/**/reviews` and `DELETE /api/v1/reviews/**`. The GET list stays `permitAll` via the existing `GET /api/v1/products/**` rule (no change needed). |

**SecurityConfig matcher placement (Task 5):** add these two lines in the `authorizeHttpRequests` chain, before `.anyRequest().authenticated()`:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/products/**/reviews").hasRole("CUSTOMER")
.requestMatchers(HttpMethod.DELETE, "/api/v1/reviews/**").hasRole("CUSTOMER")
```

Spring evaluates matchers in declaration order; placing them before the `anyRequest()` catch-all ensures the role restriction wins, while the existing `GET /api/v1/products/**` permitAll (declared earlier) still governs listing. The delete endpoint's `@PreAuthorize("@reviewSecurity.isOwner(#id, authentication)")` adds the ownership check on top of the role.

### Task breakdown

1. **Task 1: Review domain + repo + migration** — `Review` entity, `ReviewRepository` port + JPA impl, `V005` migration, `reviewCount` on `Product`, repository test
2. **Task 2: Order query port + adapter** — `OrderQueryPort` + `OrderQueryAdapter`, integration test
3. **Task 3: ReviewService** — create/delete/list + uniqueness, purchase-gating, avg_rating recompute, service test
4. **Task 4: DTOs + ReviewController** — `CreateReviewRequest`, `ReviewDto`, `ReviewPageDto`, controller, controller test
5. **Task 5: ReviewSecurity + RBAC wiring** — `@reviewSecurity.isOwner`, `SecurityConfig` rules, ownership test
6. **Task 6: Full suite + commit + push** — ensure existing tests stay green, integration verification, push

## 7. Risk Notes

- **N+1 avoided** by denormalizing `userName` onto `Review` at creation (reviews are effectively immutable; names rarely change).
- **Purchase-gating correctness** depends on `OrderItem.productId` being reliably populated — it is (set at order placement). Query checks order status to exclude PENDING/CANCELLED.
- **avg_rating consistency** maintained transactionally per write; no background sync at this scale (~5 reviews/product, 50k products).
- **`GET /products/{slug}/reviews` permitAll** — no auth-principal concern there; only create/delete need the customer principal.
- **One-review constraint** enforced at both the JPA `@UniqueConstraint` (DB level) and service-level check (clear 409 before any write).

## 8. References

- System design spec: `docs/superpowers/specs/2026-07-15-shopnow-design.md` (lines 15, 435–441, ERD lines 157/170)
- Auth spec (security patterns, `UserPrincipal`, `GlobalExceptionHandler`): `docs/superpowers/specs/2026-07-15-auth-design.md`
- Existing pattern references: `OrderItem.java` (Long FK pattern), `OrderSecurity.java` (ownership bean pattern)
