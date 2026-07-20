-- src/main/resources/db/migration/V002__create_products_and_variants.sql
CREATE TABLE attributes (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'TEXT'
);

CREATE TABLE attribute_values (
    id BIGSERIAL PRIMARY KEY,
    attribute_id BIGINT NOT NULL REFERENCES attributes(id),
    value VARCHAR(255) NOT NULL,
    swatch_hex VARCHAR(7)
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    base_price DECIMAL(10,2) NOT NULL,
    avg_rating DECIMAL(2,1) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_category ON products(category_id);

CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    sku VARCHAR(64) NOT NULL UNIQUE,
    price DECIMAL(10,2) NOT NULL,
    price_override BOOLEAN NOT NULL DEFAULT FALSE,
    variant_name VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_variants_sku ON product_variants(sku);
CREATE INDEX idx_variants_product ON product_variants(product_id);

CREATE TABLE product_attributes (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    attribute_id BIGINT NOT NULL REFERENCES attributes(id),
    is_variant_axis BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(product_id, attribute_id)
);

CREATE TABLE product_variant_values (
    id BIGSERIAL PRIMARY KEY,
    variant_id BIGINT NOT NULL REFERENCES product_variants(id),
    attribute_value_id BIGINT NOT NULL REFERENCES attribute_values(id),
    UNIQUE(variant_id, attribute_value_id)
);

CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    variant_id BIGINT NOT NULL UNIQUE REFERENCES product_variants(id),
    quantity INTEGER NOT NULL,
    reserved INTEGER NOT NULL DEFAULT 0,
    threshold INTEGER NOT NULL DEFAULT 10
);
