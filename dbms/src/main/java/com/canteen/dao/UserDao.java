package com.canteen.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class UserDao {
    private final JdbcTemplate jdbc;

    public UserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> findByEmail(String email) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id, name, email, password_hash, role FROM users WHERE email = ?", email);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void create(String name, String email, String hash, String role) {
        jdbc.update("INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)",
            name, email, hash, role);
    }

    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        return n == null ? 0 : n;
    }
}
