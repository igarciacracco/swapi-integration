package com.example.swapi.adapter.in.web;

import com.example.swapi.adapter.in.web.dto.LoginRequest;
import com.example.swapi.adapter.in.web.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Issues tokens. The password check is delegated to the {@code AuthenticationManager}. */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;

    AuthController(AuthenticationManager authenticationManager, TokenService tokens) {
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
    }

    @Operation(
            summary = "Exchange credentials for a JWT",
            description = "Returns a bearer token valid for one hour, carrying the "
                    + "catalog:read scope. There are no refresh tokens: when it expires, "
                    + "log in again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A signed access token"),
            @ApiResponse(responseCode = "400", description = "Missing username or password",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "401",
                    description = "Invalid credentials. The response is identical whether the "
                            + "username or the password was wrong.",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    // Public endpoint: opts out of the global bearer requirement.
    @SecurityRequirements
    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        log.info("Issued a token for '{}'", authentication.getName());

        TokenService.IssuedToken token = tokens.issueFor(authentication.getName());
        return LoginResponse.bearer(token.value(), token.expiresInSeconds());
    }
}
