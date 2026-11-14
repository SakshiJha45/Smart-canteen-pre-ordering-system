package com.canteen.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Repository
public class MenuDao {
    private final JdbcTemplate jdbc;

    public MenuDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT category_id, name FROM categories ORDER BY category_id");
    }

    /** Items students can see (JOIN with categories). Optional category filter. */
    public List<Map<String, Object>> available(Integer categoryId) {
        String sql = "SELECT m.item_id, m.name, m.description, m.price, m.emoji, m.stock, "
                   + "c.name AS category, c.category_id "
                   + "FROM menu_items m JOIN categories c ON c.category_id = m.category_id "
                   + "WHERE m.is_available = 1 ";
        if (categoryId == null) {
            return jdbc.queryForList(sql + "ORDER BY c.category_id, m.name");
        }
        return jdbc.queryForList(sql + "AND c.category_id = ? ORDER BY m.name", categoryId);
    }

    /** can_order = 1 when the item is switched on and in stock. */
    public Map<String, Object> findById(int itemId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT item_id, name, price, emoji, stock, IF(is_available = 1 AND stock > 0, 1, 0) AS can_order "
          + "FROM menu_items WHERE item_id = ?", itemId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> all() {
        return jdbc.queryForList(
            "SELECT m.item_id, m.name, m.description, m.price, m.emoji, m.stock, "
          + "IF(m.is_available = 1, 1, 0) AS available_flag, c.name AS category "
          + "FROM menu_items m JOIN categories c ON c.category_id = m.category_id "
          + "ORDER BY c.category_id, m.name");
    }

    public void add(String name, String description, BigDecimal price, int categoryId, String emoji, int stock) {
        jdbc.update("INSERT INTO menu_items (name, description, price, category_id, emoji, stock) VALUES (?, ?, ?, ?, ?, ?)",
            name, description, price, categoryId, emoji, stock);
    }

    public void update(int itemId, BigDecimal price, int stock, boolean available) {
        jdbc.update("UPDATE menu_items SET price = ?, stock = ?, is_available = ? WHERE item_id = ?",
            price, stock, available ? 1 : 0, itemId);
    }
}
