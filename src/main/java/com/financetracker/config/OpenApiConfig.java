package com.financetracker.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Autonomous Financial Intelligence Platform API",
        version = "1.0.0",
        description = "A secure, multi-user REST API for personal finance management " +
                      "with JWT authentication, analytics, and intelligent alerting.",
        contact = @Contact(name = "GitHub", url = "https://github.com/yourusername/finance-tracker")
    ),
    servers = @Server(url = "http://localhost:8080", description = "Local development")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER,
    description = "Enter your JWT access token"
)
public class OpenApiConfig {}
