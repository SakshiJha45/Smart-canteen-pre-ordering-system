package com.canteen.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ReportDao {
    private final JdbcTemplate jdbc;

    public ReportDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> stats() {
        return jdbc.queryForMap(
            "SELECT (SELECT COUNT(*) FROM orders WHERE DATE(order_time) = CURDATE() AND status <> 'CANCELLED') AS orders_today, "
          + "(SELECT COALESCE(SUM(total), 0) FROM orders WHERE DATE(order_time) = CURDATE() AND status <> 'CANCELLED') AS revenue_today, "
          + "(SELECT COUNT(*) FROM orders WHERE status IN ('PLACED','PREPARING','READY')) AS active_orders, "
          + "(SELECT COUNT(*) FROM menu_items WHERE stock <= 5) AS low_stock");
    }

    /** Uses the v_daily_sales view. */
    public List<Map<String, Object>> dailySales() {
        return jdbc.queryForList(
            "SELECT DATE_FORMAT(v.sale_date, '%d %b %Y') AS sale_day, v.total_orders, v.subtotal, v.tax, v.revenue "
          + "FROM v_daily_sales v ORDER BY v.sale_date DESC");
    }

    /** JOIN + GROUP BY: best sellers. */
    public List<Map<String, Object>> topItems() {
        return jdbc.queryForList(
            "SELECT m.name, m.emoji, SUM(oi.quantity) AS qty_sold, SUM(oi.quantity * oi.unit_price) AS amount "
          + "FROM order_items oi JOIN menu_items m ON m.item_id = oi.item_id JOIN orders o ON o.order_id = oi.order_id "
          + "WHERE o.status <> 'CANCELLED' GROUP BY m.item_id, m.name, m.emoji ORDER BY qty_sold DESC LIMIT 5");
    }
}
