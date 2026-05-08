package com.tzl.llongagent.security.service;

import com.tzl.llongagent.security.dto.*;
import com.tzl.llongagent.security.jwt.JwtProperties;
import com.tzl.llongagent.security.jwt.JwtTokenProvider;
import com.tzl.llongagent.security.token.TokenBlacklistService;
import com.tzl.llongagent.security.user.RedisUserDetailsService;
import com.tzl.llongagent.security.user.UserEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class AuthService {

    private final RedisUserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProperties jwtProperties;

    public AuthService(RedisUserDetailsService userDetailsService,
                       JwtTokenProvider jwtTokenProvider,
                       TokenBlacklistService tokenBlacklistService,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtProperties jwtProperties) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtProperties = jwtProperties;
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        UserEntity user = userDetailsService.findByUsername(request.getUsername());
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRoles());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return new LoginResponse(user.getId(), user.getUsername(),
                accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }

    public RegisterResponse register(RegisterRequest request) {
        if (userDetailsService.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        UserEntity user = userDetailsService.createUser(request.getUsername(), passwordHash);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRoles());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return new RegisterResponse(user.getId(), user.getUsername(),
                accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }

    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        UserEntity user = userDetailsService.findById(userId);
        if (user == null) {
            throw new BadCredentialsException("User not found");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRoles());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return new TokenRefreshResponse(newAccessToken, newRefreshToken,
                jwtProperties.accessTokenExpiration());
    }

    public void logout(String accessToken) {
        if (accessToken.startsWith("Bearer ")) {
            accessToken = accessToken.substring(7);
        }

        String tokenId = jwtTokenProvider.getTokenIdFromToken(accessToken);
        Date expiration = jwtTokenProvider.getExpirationFromToken(accessToken);
        long remainingSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;

        tokenBlacklistService.blacklist(tokenId, remainingSeconds);
    }
}
