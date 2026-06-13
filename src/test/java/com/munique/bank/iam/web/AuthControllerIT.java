// src/test/java/com/munique/bank/iam/web/AuthControllerIT.java
package com.munique.bank.iam.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController — testes de integracao")
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String VALID_PASSWORD = "S3nh@Forte!2026";

    /** Username unico por chamada para nao colidir com o estado compartilhado do H2. */
    private static String uniqueUsername() {
        return "user-" + UUID.randomUUID();
    }

    private String registerBody(String username, String password, String fullName) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        if (username != null) {
            node.put("username", username);
        }
        if (password != null) {
            node.put("password", password);
        }
        if (fullName != null) {
            node.put("fullName", fullName);
        }
        return objectMapper.writeValueAsString(node);
    }

    private String loginBody(String username, String password) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("username", username);
        node.put("password", password);
        return objectMapper.writeValueAsString(node);
    }

    /** Registra um usuario e devolve o accessToken obtido no login. */
    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, VALID_PASSWORD, "Fulano de Tal")))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    // ---------------------------------------------------------------------
    // POST /api/auth/register
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("register: retorna 201 com role CUSTOMER")
    void register_retorna201ComRoleCustomer() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, VALID_PASSWORD, "Maria Silva")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.fullName").value("Maria Silva"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("register: username duplicado retorna 409")
    void register_usernameDuplicadoRetorna409() throws Exception {
        String username = uniqueUsername();
        String body = registerBody(username, VALID_PASSWORD, "Joao Pereira");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("register: password curto retorna 400")
    void register_passwordCurtoRetorna400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueUsername(), "short", "Ana Lima")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("register: campos faltando retorna 400")
    void register_camposFaltandoRetorna400() throws Exception {
        // fullName ausente e password ausente — viola @NotBlank.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueUsername(), null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // ---------------------------------------------------------------------
    // POST /api/auth/login
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("login: credenciais corretas retorna 200 com accessToken nao-vazio e tokenType Bearer")
    void login_credenciaisCorretasRetorna200() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, VALID_PASSWORD, "Carlos Souza")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("login: credenciais erradas retorna 401")
    void login_credenciaisErradasRetorna401() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, VALID_PASSWORD, "Beatriz Rocha")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, "wrong-password-123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login: usuario inexistente retorna 401")
    void login_usuarioInexistenteRetorna401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(uniqueUsername(), VALID_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // GET /api/auth/me
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("me: sem token retorna 401")
    void me_semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("me: com token retorna o usuario autenticado")
    void me_comTokenRetornaUsuario() throws Exception {
        String username = uniqueUsername();
        String token = registerAndLogin(username);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }
}
