// src/main/java/com/munique/bank/iam/web/AuthController.java
package com.munique.bank.iam.web;

import com.munique.bank.iam.application.AuthService;
import com.munique.bank.iam.application.AuthenticationResult;
import com.munique.bank.iam.application.LoginAttemptService;
import com.munique.bank.iam.domain.AuthenticatedUser;
import com.munique.bank.iam.domain.User;
import com.munique.bank.iam.web.dto.AuthResponse;
import com.munique.bank.iam.web.dto.LoginRequest;
import com.munique.bank.iam.web.dto.RegisterRequest;
import com.munique.bank.iam.web.dto.UserResponse;
import com.munique.bank.shared.exception.AuthenticationFailedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Cadastro, login e identidade do usuario")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptService loginAttempts;

    public AuthController(AuthService authService, LoginAttemptService loginAttempts) {
        this.authService = authService;
        this.loginAttempts = loginAttempts;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements // endpoint publico (sem JWT)
    @Operation(summary = "Cadastra um novo cliente (role CUSTOMER)")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.username(), request.password(), request.fullName());
        return UserMapper.toResponse(user);
    }

    @PostMapping("/login")
    @SecurityRequirements // endpoint publico (sem JWT)
    @Operation(summary = "Autentica e retorna um token JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String throttleKey = request.username().toLowerCase() + "|" + clientIp(http);
        loginAttempts.assertNotBlocked(throttleKey); // 429 se exceder o limite de tentativas
        try {
            AuthenticationResult result = authService.authenticate(request.username(), request.password());
            loginAttempts.onSuccess(throttleKey);
            return AuthResponse.bearer(
                    result.token().value(),
                    result.token().expiresInSeconds(),
                    result.user().getUsername(),
                    result.user().getRole().name());
        } catch (AuthenticationFailedException ex) {
            loginAttempts.onFailure(throttleKey);
            throw ex;
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim(); // primeiro IP da cadeia de proxies
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/me")
    @Operation(summary = "Retorna os dados do usuario autenticado")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserMapper.toResponse(authService.getById(principal.id()));
    }
}
