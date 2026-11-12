package com.example.swapi.adapter.in.web;

import com.example.swapi.adapter.in.web.dto.RegisterRequest;
import com.example.swapi.adapter.in.web.dto.RegisterResponse;
import com.example.swapi.application.port.in.RegisterUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Account creation, only under the {@code postgres} profile so the core API needs no database. */
@Profile("postgres")
@Tag(name = "Registration", description = "Account creation (requires the postgres profile)")
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
class RegistrationController {

    private final RegisterUser registration;

    RegistrationController(RegisterUser registration) {
        this.registration = registration;
    }

    @Operation(
            summary = "Create an account",
            description = "Returns 201 and the username. Deliberately does not return a token: "
                    + "the client logs in next, so there is exactly one path that issues them.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400",
                    description = "Username or password breaks the rules",
                    content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "409", description = "Username already taken",
                    content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/register")
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return new RegisterResponse(registration.register(request.username(), request.password()));
    }
}
