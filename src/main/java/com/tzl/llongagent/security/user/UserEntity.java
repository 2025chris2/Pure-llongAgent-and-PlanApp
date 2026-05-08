package com.tzl.llongagent.security.user;

import java.util.List;

public class UserEntity {

    private String id;
    private String username;
    private String passwordHash;
    private List<String> roles;
    private long createdAt;

    public UserEntity() {
    }

    public UserEntity(String id, String username, String passwordHash, List<String> roles, long createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
