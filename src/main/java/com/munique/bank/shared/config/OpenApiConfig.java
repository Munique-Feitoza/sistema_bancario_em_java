// src/main/java/com/munique/bank/shared/config/OpenApiConfig.java
package com.munique.bank.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados e esquema de autenticacao Bearer (JWT) para o Swagger UI. */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI digitalBankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Digital Bank API")
                        .version("1.0.0")
                        .description("API bancaria enterprise (DDD + Spring Boot): contas, "
                                + "transacoes atomicas, controle de concorrencia e seguranca JWT.")
                        .contact(new Contact().name("Munique Feitoza"))
                        .license(new License().name("GPL-2.0")
                                .url("https://www.gnu.org/licenses/old-licenses/gpl-2.0.html")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Cole o token retornado por POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
