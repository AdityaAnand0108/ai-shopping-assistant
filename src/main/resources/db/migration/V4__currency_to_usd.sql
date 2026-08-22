-- Localises the store to the US market.
--
-- A new migration rather than an edit to V1: V1 has already run wherever this
-- application has been started, and changing an applied migration breaks
-- Flyway's checksum and refuses to start.
--
-- The column default is the belt to the entity's braces. Nothing relies on it
-- today, because every insert sets the currency explicitly, but leaving 'INR'
-- there would quietly reintroduce rupees for any row written by hand or by a
-- future import.

ALTER TABLE products     ALTER COLUMN currency SET DEFAULT 'USD';
ALTER TABLE orders       ALTER COLUMN currency SET DEFAULT 'USD';
ALTER TABLE order_drafts ALTER COLUMN currency SET DEFAULT 'USD';

-- Any row seeded before the change. A demo database is normally recreated from
-- scratch, but an existing one should not be left with a mix of currencies.
UPDATE products     SET currency = 'USD' WHERE currency = 'INR';
UPDATE orders       SET currency = 'USD' WHERE currency = 'INR';
UPDATE order_drafts SET currency = 'USD' WHERE currency = 'INR';
