// src/test/java/com/munique/bank/account/web/AccountControllerIT.java
package com.munique.bank.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Teste de integracao end-to-end do fluxo de contas via HTTP real (MockMvc),
 * exercitando seguranca (JWT), autorizacao (anti-IDOR + roles), regras de
 * negocio (tarifa, saldo, bloqueio) e atomicidade (transferencia).
 *
 * Robustez a estado compartilhado do H2: usernames sao unicos por UUID e todas
 * as assercoes usam ids/numbers capturados na propria execucao (nunca contagens
 * globais).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerIT {

    private static final String ADMIN_USERNAME = "admin";

    @Value("${app.seed.admin.password}")
    private String adminPassword;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------------------------------------------------------------------
    // Fluxo feliz: abertura, deposito, saque com tarifa e extrato.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Abre conta CHECKING com deposito inicial e movimenta com tarifa de saque")
    void deveAbrirContaDepositarSacarComTarifaEGerarExtrato() throws Exception {
        String token = registerAndLogin();

        JsonNode account = openAccount(token, "CHECKING", "Titular Feliz", "500.00");
        long accountId = account.get("id").asLong();

        assertThat(account.get("type").asText()).isEqualTo("CHECKING");
        assertThat(account.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(new BigDecimal(account.get("balance").asText())).isEqualByComparingTo("500.00");

        // Deposito: saldo sobe para 650.00.
        JsonNode afterDeposit = movement(token, "deposit", accountId, "150.00");
        assertThat(new BigDecimal(afterDeposit.get("balance").asText())).isEqualByComparingTo("650.00");

        // Saque de 100 numa CHECKING: cai 101 (valor + tarifa de 1.00) -> 549.00.
        JsonNode afterWithdraw = movement(token, "withdraw", accountId, "100.00");
        assertThat(new BigDecimal(afterWithdraw.get("balance").asText())).isEqualByComparingTo("549.00");

        // Extrato deve conter DEPOSIT, WITHDRAWAL e FEE.
        Set<String> types = statementTypes(token, accountId);
        assertThat(types).contains("DEPOSIT", "WITHDRAWAL", "FEE");
    }

    // ---------------------------------------------------------------------
    // Transferencia atomica entre duas contas de usuarios distintos.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Transfere entre contas de usuarios distintos ajustando saldos e registrando TRANSFER_OUT/TRANSFER_IN")
    void deveTransferirEntreContasEAjustarSaldosEExtratos() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();

        JsonNode source = openAccount(tokenA, "CHECKING", "Origem", "1000.00");
        JsonNode destination = openAccount(tokenB, "CHECKING", "Destino", "200.00");

        long sourceId = source.get("id").asLong();
        long destinationId = destination.get("id").asLong();
        String destinationNumber = destination.get("number").asText();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("destinationAccountNumber", destinationNumber);
        body.put("amount", "300.00");

        MvcResult result = mockMvc.perform(post("/api/accounts/{id}/transfer", sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sourceAfter = readBody(result);
        assertThat(new BigDecimal(sourceAfter.get("balance").asText())).isEqualByComparingTo("700.00");

        // Origem perde 300; destino ganha 300 (consultado pelo proprio dono B).
        JsonNode destinationAfter = getAccount(tokenB, destinationId);
        assertThat(new BigDecimal(destinationAfter.get("balance").asText())).isEqualByComparingTo("500.00");

        assertThat(statementTypes(tokenA, sourceId)).contains("TRANSFER_OUT");
        assertThat(statementTypes(tokenB, destinationId)).contains("TRANSFER_IN");
    }

    @Test
    @DisplayName("Transferencia com saldo insuficiente retorna 409 e faz rollback total: nenhum saldo muda")
    void deveFazerRollbackTotalQuandoTransferenciaFalhaPorSaldoInsuficiente() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();

        JsonNode source = openAccount(tokenA, "CHECKING", "Origem Atomica", "100.00");
        JsonNode destination = openAccount(tokenB, "CHECKING", "Destino Atomico", "200.00");

        long sourceId = source.get("id").asLong();
        long destinationId = destination.get("id").asLong();
        String destinationNumber = destination.get("number").asText();

        // Tenta transferir mais do que a origem possui -> saldo insuficiente -> 409.
        ObjectNode body = objectMapper.createObjectNode();
        body.put("destinationAccountNumber", destinationNumber);
        body.put("amount", "500.00");

        mockMvc.perform(post("/api/accounts/{id}/transfer", sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());

        // Rollback total: nenhum centavo se moveu, sem dinheiro "sumido".
        JsonNode sourceAfter = getAccount(tokenA, sourceId);
        assertThat(new BigDecimal(sourceAfter.get("balance").asText())).isEqualByComparingTo("100.00");

        JsonNode destinationAfter = getAccount(tokenB, destinationId);
        assertThat(new BigDecimal(destinationAfter.get("balance").asText())).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Transferencia bem-sucedida grava TRANSFER_OUT na origem e TRANSFER_IN no destino com amount e balanceAfter coerentes")
    void deveRegistrarLancamentosCoerentesNaTransferenciaBemSucedida() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();

        JsonNode source = openAccount(tokenA, "CHECKING", "Origem Lancamento", "1000.00");
        JsonNode destination = openAccount(tokenB, "CHECKING", "Destino Lancamento", "200.00");

        long sourceId = source.get("id").asLong();
        long destinationId = destination.get("id").asLong();
        String destinationNumber = destination.get("number").asText();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("destinationAccountNumber", destinationNumber);
        body.put("amount", "300.00");

        mockMvc.perform(post("/api/accounts/{id}/transfer", sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // Origem: existe um TRANSFER_OUT de 300.00 deixando saldo 700.00 (1000 - 300).
        mockMvc.perform(get("/api/accounts/{id}/statement", sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type == 'TRANSFER_OUT' "
                        + "&& @.amount == 300.00 && @.balanceAfter == 700.00)]").exists());

        // Destino: existe um TRANSFER_IN de 300.00 deixando saldo 500.00 (200 + 300).
        mockMvc.perform(get("/api/accounts/{id}/statement", destinationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type == 'TRANSFER_IN' "
                        + "&& @.amount == 300.00 && @.balanceAfter == 500.00)]").exists());
    }

    // ---------------------------------------------------------------------
    // Anti-IDOR: usuario B nao pode ler nem movimentar a conta de A.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Anti-IDOR: usuario sem propriedade recebe 403 ao consultar e ao sacar conta alheia")
    void deveBloquearAcessoIdorEntreUsuarios() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();

        JsonNode accountOfA = openAccount(tokenA, "CHECKING", "Dono A", "300.00");
        long accountId = accountOfA.get("id").asLong();

        mockMvc.perform(get("/api/accounts/{id}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isForbidden());

        ObjectNode amount = objectMapper.createObjectNode();
        amount.put("amount", "10.00");
        mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(amount)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // Autenticacao e autorizacao por role.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Requisicao sem token e rejeitada com 401")
    void deveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CUSTOMER recebe 403 ao tentar bloquear conta; ADMIN bloqueia e saque na conta bloqueada da 409")
    void deveRestringirBloqueioAoAdminEImpedirSaqueEmContaBloqueada() throws Exception {
        String customerToken = registerAndLogin();
        String adminToken = login(ADMIN_USERNAME, adminPassword);

        JsonNode account = openAccount(customerToken, "CHECKING", "Cliente Bloqueavel", "500.00");
        long accountId = account.get("id").asLong();

        // CUSTOMER nao tem ROLE_ADMIN -> 403 (negado pelo @PreAuthorize).
        mockMvc.perform(post("/api/accounts/{id}/block", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());

        // ADMIN bloqueia com sucesso -> status BLOCKED.
        mockMvc.perform(post("/api/accounts/{id}/block", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        // Saque do proprio dono numa conta bloqueada viola regra de negocio -> 409.
        ObjectNode amount = objectMapper.createObjectNode();
        amount.put("amount", "10.00");
        mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(amount)))
                .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------------
    // Regras de saldo e validacao de entrada.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Saque maior que o saldo retorna 409 (saldo insuficiente)")
    void deveRejeitarSaqueComSaldoInsuficiente() throws Exception {
        String token = registerAndLogin();
        JsonNode account = openAccount(token, "CHECKING", "Sem Saldo", "50.00");
        long accountId = account.get("id").asLong();

        ObjectNode amount = objectMapper.createObjectNode();
        amount.put("amount", "1000.00");
        mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(amount)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Valor negativo no saque e rejeitado na validacao com 400")
    void deveRejeitarValorNegativoComBadRequest() throws Exception {
        String token = registerAndLogin();
        JsonNode account = openAccount(token, "CHECKING", "Valor Invalido", "100.00");
        long accountId = account.get("id").asLong();

        ObjectNode amount = objectMapper.createObjectNode();
        amount.put("amount", "-10.00");
        mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(amount)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Registra um novo CUSTOMER com username unico (UUID) e devolve o token. */
    private String registerAndLogin() throws Exception {
        String username = "user-" + UUID.randomUUID();
        String password = "Str0ng!Pass2026";

        ObjectNode register = objectMapper.createObjectNode();
        register.put("username", username);
        register.put("password", password);
        register.put("fullName", "Integration Test User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        return login(username, password);
    }

    /** Autentica e extrai o accessToken do JSON de resposta. */
    private String login(String username, String password) throws Exception {
        ObjectNode loginBody = objectMapper.createObjectNode();
        loginBody.put("username", username);
        loginBody.put("password", password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andReturn();

        return readBody(result).get("accessToken").asText();
    }

    private JsonNode openAccount(String token, String type, String ownerName, String initialDeposit)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", type);
        body.put("ownerName", ownerName);
        body.put("initialDeposit", initialDeposit);

        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return readBody(result);
    }

    private JsonNode getAccount(String token, long accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/accounts/{id}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return readBody(result);
    }

    /** Executa deposit/withdraw de {@code amount} e devolve a conta resultante. */
    private JsonNode movement(String token, String operation, long accountId, String amount) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("amount", amount);

        MvcResult result = mockMvc.perform(post("/api/accounts/{id}/" + operation, accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        return readBody(result);
    }

    /** Coleta os tipos de lancamento presentes no extrato (campo content[].type). */
    private Set<String> statementTypes(String token, long accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/accounts/{id}/statement", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = readBody(result).get("content");
        Set<String> types = new HashSet<>();
        List<String> seen = new ArrayList<>();
        if (content != null) {
            for (JsonNode entry : content) {
                String type = entry.get("type").asText();
                types.add(type);
                seen.add(type);
            }
        }
        assertThat(seen).isNotEmpty();
        return types;
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
