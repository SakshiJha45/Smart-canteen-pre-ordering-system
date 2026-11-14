package com.canteen.model;

import java.io.Serializable;

/** The logged-in user, kept in the HTTP session. */
public class SessionUser implements Serializable {
    private final int userId;
    private final String name;
    private final String role;

    public SessionUser(int userId, String name, String role) {
        this.userId = userId;
        this.name = name;
        this.role = role;
    }
    public int getUserId() { return userId; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
