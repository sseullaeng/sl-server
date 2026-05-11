ALTER TABLE items
    ADD COLUMN deposit_type VARCHAR(16) NULL;

UPDATE items
   SET deposit_type = 'AMOUNT'
 WHERE deposit_type IS NULL;

ALTER TABLE items
    MODIFY COLUMN deposit_type VARCHAR(16) NOT NULL;
