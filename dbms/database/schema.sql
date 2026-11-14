-- =====================================================================
--  Smart Canteen Pre-Ordering System  |  MySQL 8.0+
--  Run this whole script once in MySQL Workbench (lightning bolt button)
-- =====================================================================
DROP DATABASE IF EXISTS canteen_db;
CREATE DATABASE canteen_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE canteen_db;

-- ---------------------------------------------------------------------
-- TABLES (3NF)
-- ---------------------------------------------------------------------
CREATE TABLE users (
  user_id       INT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(80)  NOT NULL,
  email         VARCHAR(120) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role          ENUM('STUDENT','ADMIN') NOT NULL DEFAULT 'STUDENT',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
  category_id INT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE menu_items (
  item_id      INT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(80)  NOT NULL,
  description  VARCHAR(160) NOT NULL DEFAULT '',
  price        DECIMAL(8,2) NOT NULL CHECK (price > 0),
  category_id  INT NOT NULL,
  emoji        VARCHAR(16)  NOT NULL DEFAULT '🍽️',
  stock        INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
  is_available TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_item_category FOREIGN KEY (category_id) REFERENCES categories(category_id)
);

CREATE TABLE orders (
  order_id    INT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT NOT NULL,
  order_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  pickup_time TIME NOT NULL,
  status      ENUM('PLACED','PREPARING','READY','COLLECTED','CANCELLED') NOT NULL DEFAULT 'PLACED',
  subtotal    DECIMAL(10,2) NOT NULL,
  tax         DECIMAL(10,2) NOT NULL,
  total       DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- resolves the many-to-many between orders and menu_items
CREATE TABLE order_items (
  order_item_id INT AUTO_INCREMENT PRIMARY KEY,
  order_id      INT NOT NULL,
  item_id       INT NOT NULL,
  quantity      INT NOT NULL CHECK (quantity > 0),
  unit_price    DECIMAL(8,2) NOT NULL,
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
  CONSTRAINT fk_oi_item  FOREIGN KEY (item_id)  REFERENCES menu_items(item_id)
);

-- last token number issued on each day (token numbers restart daily)
CREATE TABLE token_counter (
  counter_date DATE PRIMARY KEY,
  last_no      INT NOT NULL
);

CREATE TABLE tokens (
  token_id   INT AUTO_INCREMENT PRIMARY KEY,
  order_id   INT NOT NULL UNIQUE,
  token_date DATE NOT NULL,
  token_no   INT  NOT NULL,
  token_code VARCHAR(10) NOT NULL,
  UNIQUE KEY uq_token_day (token_date, token_no),
  CONSTRAINT fk_token_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE
);

CREATE TABLE payments (
  payment_id   INT AUTO_INCREMENT PRIMARY KEY,
  order_id     INT NOT NULL UNIQUE,
  amount       DECIMAL(10,2) NOT NULL,
  payment_mode ENUM('CASH','UPI','CARD') NOT NULL,
  status       ENUM('PAID','PENDING','REFUNDED','CANCELLED') NOT NULL,
  paid_at      DATETIME NULL,
  CONSTRAINT fk_pay_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- TRIGGERS
-- ---------------------------------------------------------------------
DELIMITER $$

-- 1) When an item is added to an order, reduce its stock automatically.
--    If stock would go below 0, the CHECK constraint fails and the whole order rolls back.
CREATE TRIGGER trg_reduce_stock
AFTER INSERT ON order_items FOR EACH ROW
BEGIN
  UPDATE menu_items SET stock = stock - NEW.quantity WHERE item_id = NEW.item_id;
END$$

-- 2) When an order is cancelled, put the stock back.
CREATE TRIGGER trg_restore_stock
AFTER UPDATE ON orders FOR EACH ROW
BEGIN
  IF NEW.status = 'CANCELLED' AND OLD.status <> 'CANCELLED' THEN
    UPDATE menu_items m
    JOIN order_items oi ON oi.item_id = m.item_id
    SET m.stock = m.stock + oi.quantity
    WHERE oi.order_id = NEW.order_id;
  END IF;
END$$

-- ---------------------------------------------------------------------
-- STORED PROCEDURE: issues the next token of the day (T-001, T-002 ...)
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_generate_token(IN p_order_id INT)
BEGIN
  DECLARE v_no INT;
  INSERT INTO token_counter (counter_date, last_no) VALUES (CURDATE(), 1)
    ON DUPLICATE KEY UPDATE last_no = last_no + 1;
  SELECT last_no INTO v_no FROM token_counter WHERE counter_date = CURDATE();
  INSERT INTO tokens (order_id, token_date, token_no, token_code)
    VALUES (p_order_id, CURDATE(), v_no, CONCAT('T-', LPAD(v_no, 3, '0')));
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- VIEWS
-- ---------------------------------------------------------------------
CREATE VIEW v_kitchen_queue AS
SELECT o.order_id, t.token_code, u.name AS customer,
       TIME_FORMAT(o.pickup_time, '%H:%i') AS pickup, o.status,
       GROUP_CONCAT(CONCAT(oi.quantity, ' x ', m.name) SEPARATOR ', ') AS items
FROM orders o
JOIN users u        ON u.user_id   = o.user_id
JOIN tokens t       ON t.order_id  = o.order_id
JOIN order_items oi ON oi.order_id = o.order_id
JOIN menu_items m   ON m.item_id   = oi.item_id
WHERE o.status IN ('PLACED','PREPARING','READY')
GROUP BY o.order_id, t.token_code, u.name, o.pickup_time, o.status;

CREATE VIEW v_daily_sales AS
SELECT DATE(order_time) AS sale_date, COUNT(*) AS total_orders,
       SUM(subtotal) AS subtotal, SUM(tax) AS tax, SUM(total) AS revenue
FROM orders
WHERE status <> 'CANCELLED'
GROUP BY DATE(order_time);

-- ---------------------------------------------------------------------
-- SAMPLE DATA  (users are created by the app on first start, with hashed passwords)
-- ---------------------------------------------------------------------
INSERT INTO categories (name) VALUES ('Breakfast'),('Meals'),('Snacks'),('Drinks'),('Desserts');

INSERT INTO menu_items (name, description, price, category_id, emoji, stock) VALUES
('Poha',            'Flattened rice with peanuts and lemon',          30, 1, '🍲', 40),
('Aloo Paratha',    'Two parathas with curd and pickle',              40, 1, '🫓', 35),
('Idli Sambar',     'Three idlis with sambar and chutney',            35, 1, '🥣', 40),
('Masala Dosa',     'Crisp dosa with potato filling',                 55, 1, '🌯', 30),
('Veg Thali',       'Dal, sabzi, rice, 3 rotis, salad',               90, 2, '🍱', 50),
('Rajma Chawal',    'Rajma curry with steamed rice',                  70, 2, '🍛', 40),
('Chole Bhature',   'Two bhature with spicy chole',                   65, 2, '🥙', 35),
('Veg Biryani',     'Aromatic rice with vegetables and raita',        80, 2, '🍚', 30),
('Paneer Wrap',     'Paneer tikka rolled in a soft roti',             60, 2, '🌯', 30),
('Samosa',          'Crispy samosa with chutney',                     15, 3, '🥟', 80),
('Veg Sandwich',    'Grilled sandwich with veggies and cheese',       40, 3, '🥪', 40),
('Veg Burger',      'Crispy patty burger with sauces',                50, 3, '🍔', 30),
('French Fries',    'Salted fries with ketchup',                      45, 3, '🍟', 50),
('Veg Momos',       'Steamed momos with red chutney (6 pcs)',         50, 3, '🥟', 40),
('Masala Chai',     'Hot ginger-cardamom tea',                        12, 4, '☕', 100),
('Cold Coffee',     'Chilled coffee with ice cream',                  40, 4, '🥤', 40),
('Fresh Lime Soda', 'Sweet or salted',                                25, 4, '🍋', 50),
('Mango Shake',     'Thick shake with real mango pulp',               45, 4, '🥭', 30),
('Gulab Jamun',     'Two warm gulab jamuns',                          25, 5, '🍮', 50),
('Ice Cream Cup',   'Vanilla or chocolate scoop',                     30, 5, '🍨', 40);
