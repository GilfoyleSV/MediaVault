package ru.kuzmin.mediavault.controller;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kuzmin.mediavault.dto.Credentials;
import ru.kuzmin.mediavault.service.AccountService;
import ru.kuzmin.mediavault.service.AccountService.AccountResponse;
import ru.kuzmin.mediavault.service.AccountService.TokenResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody Credentials credentials) {
        return accounts.register(credentials);
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody Credentials credentials) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.issueToken(credentials));
    }
}
