package com.canteen.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Repository
public class OrderDao {
    private final JdbcTemplate jdbc;

    public OrderDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Places an order as ONE transaction:
     *  1. lock and check stock  2. insert order  3. insert order_items (trigger reduces stock)
     *  4. CALL sp_generate_token  5. insert payment.
     * If anything fails, everything is rolled back.
     */
    @Transactional
    public int placeOrder(int userId, Map<Integer, Integer> cart, String pickup, String payMode, BigDecimal gstRate) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> e : cart.entrySet()) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT name, price, stock, is_available FROM menu_items WHERE item_id = ? FOR UPDATE", e.getKey());
            if (rows.isEmpty()) {
                throw new IllegalStateException("An item in your cart is no longer on the menu.");
            }
            Map<String, Object> item = rows.get(0);
            int stock = ((Number) item.get("stock")).intValue();
            int available = item.get("is_available") instanceof Boolean b ? (b ? 1 : 0) : ((Number) item.get("is_available")).intValue();
            if (available != 1 || stock < e.getValue()) {
                throw new IllegalStateException(item.get("name") + " is not available in that quantity (" + stock + " left).");
            }
            subtotal = subtotal.add(((BigDecimal) item.get("price")).multiply(BigDecimal.valueOf(e.getValue())));
        }
        BigDecimal tax = subtotal.multiply(gstRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);

        KeyHolder keys = new GeneratedKeyHolder();
        final BigDecimal fSub = subtotal;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO orders (user_id, pickup_time, subtotal, tax, total) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, userId);
            ps.setTime(2, Time.valueOf(LocalTime.parse(pickup)));
            ps.setBigDecimal(3, fSub);
            ps.setBigDecimal(4, tax);
            ps.setBigDecimal(5, total);
            return ps;
        }, keys);
        int orderId = keys.getKey().intValue();

        for (Map.Entry<Integer, Integer> e : cart.entrySet()) {
            BigDecimal price = jdbc.queryForObject("SELECT price FROM menu_items WHERE item_id = ?", BigDecimal.class, e.getKey());
            jdbc.update("INSERT INTO order_items (order_id, item_id, quantity, unit_price) VALUES (?, ?, ?, ?)",orderId, e.getKey(), e.getValue(), price);
        }

        jdbc.update("CALL sp_generate_token(?)", orderId);

        String payStatus = payMode.equals("CASH") ? "PENDING" : "PAID";
        jdbc.update("INSERT INTO payments (order_id, amount, payment_mode, status, paid_at) "
                  + "VALUES (?, ?, ?, ?, CASE WHEN ? = 'PAID' THEN NOW() ELSE NULL END)",
            orderId, total, payMode, payStatus, payStatus);
        return orderId;
    }

    /** Changes status. COLLECTED settles cash payments; CANCELLED refunds/voids the payment (trigger restores stock). */
    @Transactional
    public void setStatus(int orderId, String status) {
        int changed = jdbc.update(
            "UPDATE orders SET status = ? WHERE order_id = ? AND status NOT IN ('CANCELLED','COLLECTED')",
            status, orderId);
        if (changed == 0) return;
        if (status.equals("COLLECTED")) {
            jdbc.update("UPDATE payments SET status = 'PAID', paid_at = NOW() WHERE order_id = ? AND status = 'PENDING'", orderId);
        } else if (status.equals("CANCELLED")) {
            jdbc.update("UPDATE payments SET status = IF(status = 'PAID', 'REFUNDED', 'CANCELLED') WHERE order_id = ?", orderId);
        }
    }

    private static final String ORDER_COLS =
        "o.order_id, o.user_id, u.name AS customer, o.status, "
      + "TIME_FORMAT(o.pickup_time, '%H:%i') AS pickup, "
      + "DATE_FORMAT(o.order_time, '%d %b %Y, %h:%i %p') AS placed_at, "
      + "o.subtotal, o.tax, o.total, t.token_code, p.payment_mode, p.status AS pay_status ";
    private static final String ORDER_JOINS =
        "FROM orders o JOIN users u ON u.user_id = o.user_id "
      + "JOIN tokens t ON t.order_id = o.order_id JOIN payments p ON p.order_id = o.order_id ";

    public Map<String, Object> detail(int orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT " + ORDER_COLS + ORDER_JOINS + "WHERE o.order_id = ?", orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> items(int orderId) {
        return jdbc.queryForList(
            "SELECT m.name, m.emoji, oi.quantity, oi.unit_price, oi.quantity * oi.unit_price AS line_total "
          + "FROM order_items oi JOIN menu_items m ON m.item_id = oi.item_id WHERE oi.order_id = ? ORDER BY oi.order_item_id",
            orderId);
    }

    public List<Map<String, Object>> byUser(int userId) {
        return jdbc.queryForList(
            "SELECT " + ORDER_COLS + ORDER_JOINS + "WHERE o.user_id = ? ORDER BY o.order_id DESC", userId);
    }

    /** All orders for admin, active ones first. Items are collected with a correlated subquery. */
    public List<Map<String, Object>> allForAdmin() {
        return jdbc.queryForList(
            "SELECT " + ORDER_COLS + ", (SELECT GROUP_CONCAT(CONCAT(oi.quantity, ' x ', m.name) SEPARATOR ', ') "
          + "FROM order_items oi JOIN menu_items m ON m.item_id = oi.item_id WHERE oi.order_id = o.order_id) AS items "
          + ORDER_JOINS + "ORDER BY (o.status IN ('COLLECTED','CANCELLED')), o.pickup_time, o.order_id DESC LIMIT 100");
    }

    /** Uses the v_kitchen_queue view. */
    public List<Map<String, Object>> kitchenQueue() {
        return jdbc.queryForList("SELECT * FROM v_kitchen_queue ORDER BY pickup, order_id");
    }
}
