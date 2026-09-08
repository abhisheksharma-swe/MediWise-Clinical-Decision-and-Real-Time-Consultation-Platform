-- V4__add_password_reset_fields.sql
-- Adds fields to support secure forgot/reset password flow

ALTER TABLE users ADD COLUMN reset_token_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN reset_token_expiry TIMESTAMP;