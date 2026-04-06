package com.ledger.service;

import com.ledger.domain.entity.User;
import com.ledger.domain.repository.RefreshTokenRepository;
import com.ledger.domain.repository.UserRepository;
import com.ledger.dto.auth.AuthDtos.*;
import com.ledger.exception.DuplicateResourceException;
import com.ledger.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604800000L);
    }

    @Test
    @DisplayName("register() - should create user and return tokens when email is unique")
    void register_success() {
        // Arrange
        RegisterRequest request = new RegisterRequest("John Doe", "john@example.com", "password123");

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        AuthResponse response = authService.register(request);

        // Assert
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotNull();
        assertThat(response.user().email()).isEqualTo("john@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register() - should throw DuplicateResourceException when email already exists")
    void register_duplicateEmail_throwsException() {
        RegisterRequest request = new RegisterRequest("Jane", "jane@example.com", "password123");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login() - should revoke existing tokens and issue new pair")
    void login_revokesExistingTokens() {
        LoginRequest request = new LoginRequest("john@example.com", "password123");
        User user = User.builder().id(1L).email("john@example.com").fullName("John").role("ROLE_USER").build();

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(refreshTokenRepository.revokeAllByUserId(user.getId())).thenReturn(1);
        when(jwtService.generateAccessToken(any())).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    }
}
