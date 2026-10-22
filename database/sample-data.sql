-- Optional development-only sample customers and transactions.
-- The backend creates the initial admin account from SHOP_BOOTSTRAP_USERNAME /
-- SHOP_BOOTSTRAP_PASSWORD when users is empty; never put real credentials here.
USE smart_shop_db;

INSERT INTO customers (full_name, phone, address)
SELECT 'Ravi Kumar', '9876543210', 'Chennai'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE phone = '9876543210');

SET @ravi_id = (SELECT id FROM customers WHERE phone = '9876543210' LIMIT 1);

INSERT INTO credit_entries (customer_id, description, amount, transaction_date, notes)
SELECT @ravi_id, 'Grocery purchase', 2000.00, CURRENT_DATE, 'Development sample'
WHERE @ravi_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM credit_entries WHERE customer_id = @ravi_id AND notes = 'Development sample');

INSERT INTO payments (customer_id, amount, transaction_date, payment_method, notes, request_key)
SELECT @ravi_id, 800.00, CURRENT_DATE, 'CASH', 'Development sample', 'sample-ravi-payment-1'
WHERE @ravi_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM payments WHERE request_key = 'sample-ravi-payment-1');
