package com.example.swapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata, plus the {@code bearerAuth} scheme behind Swagger UI's Authorize button. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI swapiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Star Wars API")
                        .version("1.0.0")
                        .description("""
                                A stateless, JWT-protected REST facade over swapi.tech for \
                                People, Films, Starships and Vehicles.

                                **Getting started**: call `POST /api/v1/auth/login` with the \
                                demo credentials, copy the `accessToken`, then click \
                                **Authorize** above and paste it. Every catalog endpoint \
                                requires the `catalog:read` scope.

                                **List endpoints** accept `page`, `size`, and optional `id` \
                                and `name` filters. A filter that matches nothing returns an \
                                empty page with status 200; only the `/{id}` detail endpoints \
                                return 404.

                                **Errors** are RFC 9457 `application/problem+json`. A 502 means \
                                SWAPI failed, a 504 means it was too slow to answer.""")
                        .contact(new Contact().name("nacho"))
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("A token from POST /api/v1/auth/login. "
                                        + "Valid for one hour.")))
                // Required globally; public endpoints opt out with @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
