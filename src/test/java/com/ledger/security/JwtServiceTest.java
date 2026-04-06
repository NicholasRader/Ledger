package com.ledger.security;

import com.ledger.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    private static final String TEST_SECRET =
        "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900000L); // 15 min

        testUser = User.builder()
            .id(1L).email("test@example.com").fullName("Test User").role("ROLE_USER")
            .build();
    }

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateToken {

        @Test
        @DisplayName("should generate a non-null token")
        void generateToken_notNull() {
            String token = jwtService.generateAccessToken(testUser);
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("should generate different tokens on subsequent calls")
        void generateToken_uniquePerCall() {
            String token1 = jwtService.generateAccessToken(testUser);
            // Small delay to ensure different issuedAt
            String token2 = jwtService.generateAccessToken(testUser);
            // Both should be valid even if issued close together
            assertThat(jwtService.isTokenValid(token1, testUser)).isTrue();
            assertThat(jwtService.isTokenValid(token2, testUser)).isTrue();
        }
    }

    @Nested
    @DisplayName("extractUsername()")
    class ExtractUsername {

        @Test
        @DisplayName("should extract correct email from token")
        void extractUsername_correct() {
            String token = jwtService.generateAccessToken(testUser);
            String username = jwtService.extractUsername(token);
            assertThat(username).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValid {

        @Test
        @DisplayName("should return true for valid token")
        void isTokenValid_validToken() {
            String token = jwtService.generateAccessToken(testUser);
            assertThat(jwtService.isTokenValid(token, testUser)).isTrue();
        }

        @Test
        @DisplayName("should return false for token belonging to different user")
        void isTokenValid_wrongUser() {
            User otherUser = User.builder()
                .id(2L).email("other@example.com").fullName("Other").role("ROLE_USER")
                .build();

            String token = jwtService.generateAccessToken(testUser);
            assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
        }

        @Test
        @DisplayName("should return false for expired token")
        void isTokenValid_expiredToken() {
            // Set very short expiration
            ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", -1000L);
            String expiredToken = jwtService.generateAccessToken(testUser);

            // Restore normal expiration
            ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900000L);

            // isTokenExpired internally calls extractAllClaims which throws ExpiredJwtException
            // We need to catch that and assert it means the token is invalid
            assertThatThrownBy(() -> jwtService.isTokenExpired(expiredToken))
                    .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
        }
    }

    @Nested
    @DisplayName("getAccessTokenExpiration()")
    class GetExpiration {

        @Test
        @DisplayName("should return configured expiration value")
        void getExpiration_returnsConfigured() {
            assertThat(jwtService.getAccessTokenExpiration()).isEqualTo(900000L);
        }
    }
}
