package com.typeme.models;

import java.time.LocalDateTime;

public class User {
    private int id;
    private String googleId;
    private String email;
    private String name;
    private String avatarUrl;
    private boolean isAdmin;
    private LocalDateTime createdAt;

    public User(int id, String googleId, String email, String name, String avatarUrl, boolean isAdmin, LocalDateTime createdAt) {
        this.id = id;
        this.googleId = googleId;
        this.email = email;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.isAdmin = isAdmin;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getGoogleId() {
        return googleId;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}