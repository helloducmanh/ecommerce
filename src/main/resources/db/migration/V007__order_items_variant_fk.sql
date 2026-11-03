-- src/main/resources/db/migration/V007__order_items_variant_fk.sql
ALTER TABLE order_items
    ADD CONSTRAINT fk_oi_variant
    FOREIGN KEY (variant_id) REFERENCES product_variants(id);
