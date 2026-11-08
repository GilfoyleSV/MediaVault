package ru.kuzmin.mediavault.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.kuzmin.mediavault.dto.Credentials;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static ru.kuzmin.mediavault.config.SecurityConfig.JWT_ISSUER;

@Service
public class AccountService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final JdbcClient jdbc;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwt;
    private final String dummyHash;

    public AccountService(JdbcClient jdbc, PasswordEncoder passwords, JwtEncoder jwt) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.jwt = jwt;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public AccountResponse register(Credentials credentials) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.sql("""
                INSERT INTO media_account (id, username, password_hash) VALUES (?, ?, ?)
                ON CONFLICT (username) DO NOTHING
                """)
                .params(id, credentials.username(), passwords.encode(credentials.password()))
                .update();
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        return new AccountResponse(id, credentials.username());
    }

    public TokenResponse issueToken(Credentials credentials) {
        Account account = jdbc.sql("SELECT id, password_hash FROM media_account WHERE username = ?")
                .param(credentials.username())
                .query((rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("password_hash")))
                .optional()
                .orElse(null);
        boolean valid = passwords.matches(credentials.password(), account == null ? dummyHash : account.passwordHash());
        if (!valid || account == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JWT_ISSUER)
                .subject(account.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwt.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", TOKEN_LIFETIME.toSeconds());
    }

    private record Account(UUID id, String passwordHash) {}

    public record AccountResponse(UUID accountId, String username) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
