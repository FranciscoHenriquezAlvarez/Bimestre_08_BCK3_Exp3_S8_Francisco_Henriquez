package cl.duoc.bancoxyz.core;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cl.duoc.bancoxyz.core.kafka.AsyncWithdrawalService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(properties = "spring.cloud.config.enabled=false")
@ContextConfiguration(classes = {CoreBankingController.class, CoreSecurityConfig.class})
class CoreSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean BankingService service;
    @MockBean AsyncWithdrawalService withdrawals;
    @MockBean JwtDecoder decoder;

    @Test
    void rejectsDirectBankingAccessWithoutToken() throws Exception {
        mvc.perform(get("/api/core/accounts/104/balance")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void allowsAuthenticatedOAuth2Token() throws Exception {
        accountExists();
        mvc.perform(get("/api/core/accounts/104/balance").with(jwt().jwt(jwt -> jwt.claim("scope", "atm"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(104));
        verify(service).account(104);
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));
        mvc.perform(get("/api/core/accounts/104/balance").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void acceptsBearerTokenPropagatedByBff() throws Exception {
        accountExists();
        when(decoder.decode("propagated-token")).thenReturn(Jwt.withTokenValue("propagated-token")
                .header("alg", "RS256").subject("atm-client").claim("scope", "atm").build());
        mvc.perform(get("/api/core/accounts/104/balance").header(HttpHeaders.AUTHORIZATION, "Bearer propagated-token"))
                .andExpect(status().isOk());
        verify(decoder).decode("propagated-token");
        verify(service).account(104);
    }

    private void accountExists() {
        when(service.account(104)).thenReturn(new BankingService.Account(104, "Cuenta", "SAVINGS",
                new BigDecimal("10000"), new BigDecimal("0.01"), new BigDecimal("10100")));
    }
}
