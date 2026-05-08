package com.tzl.llongagent.security.dto;

public class RegisterResponse {

    private String userId;
    private String username;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;

    public RegisterResponse(String userId, String username, String accessToken,
                            String refreshToken, long expiresIn) {
        this.userId = userId;
        this.username = username;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public long getExpiresIn() { return expiresIn; }
}
