package com.ledger.service;

import com.ledger.domain.entity.RefreshToken;
import com.ledger.domain.entity.User;
import com.ledger.domain.repository.RefreshTokenRepository;
import com.ledger.domain.repository.UserRepository;
import com.ledger.dto.auth.AuthDtos.*;
import com.ledger.exception.DuplicateResourceException;
import com.ledger.exception.InvalidTokenException;
import com.ledger.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }

        User user = User.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .fullName(request.fullName())
            .role("ROLE_USER")
            .build();

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        return buildAuthResponse(user);
    }
    
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException if invalid - caught by GlobalExceptionHandler
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new InvalidTokenException("User not found"));

        // Revoke any existing refresh tokens before issuing new ones
        refreshTokenRepository.revokeAllByUserId(user.getId());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
            .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (!storedToken.isValid()) {
            // If token is invalid, revoke all tokens for this user (possible token theft)
            refreshTokenRepository.revokeAllByUserId(storedToken.getUser().getId());
            throw new InvalidTokenException("Refresh token is expired or revoked. Please log in again.");
        }

        // Rotate: revoke old token, issue new pair
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        return buildAuthResponse(storedToken.getUser());
    }

    @Transactional
    public void logout(Long userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked {} refresh tokens for user {}", revoked, userId);
    }
    
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = createRefreshToken(user);

        UserSummary userSummary = new UserSummary(
            user.getId(), user.getEmail(), user.getFullName(), user.getRole()
        );

        return AuthResponse.of(
            accessToken,
            refreshToken,
            jwtService.getAccessTokenExpiration() / 1000,
            userSummary
        );
    }

    private String createRefreshToken(User user) {
        String tokenValue = UUID.randomUUID().toString();

        RefreshToken token = RefreshToken.builder()
            .token(tokenValue)
            .user(user)
            .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
            .build();

        refreshTokenRepository.save(token);
        return tokenValue;
    }
}
