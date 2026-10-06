package cl.duoc.bancoxyz.authorization;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.oauth2.clients.web-secret=test-web-secret",
        "app.oauth2.clients.mobile-secret=test-mobile-secret",
        "app.oauth2.clients.atm-secret=test-atm-secret"})
@AutoConfigureMockMvc
class AuthorizationServerTest {
    @Autowired MockMvc mvc;
    @Autowired RegisteredClientRepository clients;
    @Autowired JwtDecoder decoder;
    @Autowired ObjectMapper json;

    @Test
    void contextLoads() {
        assertNotNull(mvc);
        assertNotNull(decoder);
    }

    @Test
    void registersChannelClientsWithExclusiveScopes() {
        for (String channel : Set.of("web", "mobile", "atm")) {
            RegisteredClient client = clients.findByClientId(channel + "-client");
            assertNotNull(client);
            assertEquals(Set.of(channel), client.getScopes());
            assertEquals(Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS), client.getAuthorizationGrantTypes());
            assertEquals(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC), client.getClientAuthenticationMethods());
            assertFalse(client.getClientSecret().contains("test-"));
        }
    }

    @Test
    void clientCredentialsIssuesSignedJwtAccessToken() throws Exception {
        String response = mvc.perform(post("/oauth2/token").with(httpBasic("web-client", "test-web-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("scope", "web"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.scope").value("web")).andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("access_token").asText();
        Jwt jwt = decoder.decode(token);
        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals("http://localhost:9000", jwt.getIssuer().toString());
        assertEquals("web-client", jwt.getSubject());
        assertEquals(Set.of("web"), Set.copyOf(jwt.getClaimAsStringList("scope")));
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));
    }

    @Test
    void publishedJwkValidatesAccessTokenForResourceServer() throws Exception {
        NimbusJwtDecoder resourceDecoder = publicKeyDecoder();
        resourceDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("http://localhost:9000"));
        Jwt token = resourceDecoder.decode(webAccessToken());
        assertEquals("web-client", token.getSubject());
        assertEquals(Set.of("web"), Set.copyOf(token.getClaimAsStringList("scope")));
    }

    @Test
    void resourceServerRejectsWrongIssuerAndTamperedSignature() throws Exception {
        String token = webAccessToken();
        NimbusJwtDecoder resourceDecoder = publicKeyDecoder();
        resourceDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("http://different-issuer"));
        assertThrows(JwtException.class, () -> resourceDecoder.decode(token));
        resourceDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("http://localhost:9000"));
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalid";
        assertThrows(JwtException.class, () -> resourceDecoder.decode(tampered));
    }

    private NimbusJwtDecoder publicKeyDecoder() throws Exception {
        String response = mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        RSAKey key = RSAKey.parse(json.readTree(response).get("keys").get(0).toString());
        return NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
    }

    private String webAccessToken() throws Exception {
        String response = mvc.perform(post("/oauth2/token").with(httpBasic("web-client", "test-web-secret"))
                .param("grant_type", "client_credentials").param("scope", "web"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("access_token").asText();
    }

    @Test
    void tokenEndpointRejectsInvalidClientSecret() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("web-client", "wrong-secret"))
                .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenEndpointRejectsScopeNotRegisteredForClient() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("web-client", "test-web-secret"))
                .param("grant_type", "client_credentials").param("scope", "atm"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_scope"));
    }

    @Test
    void jwksEndpointExposesOnlyPublicRsaKey() throws Exception {
        String response = mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode key = json.readTree(response).get("keys").get(0);
        assertEquals("RSA", key.get("kty").asText());
        assertNotNull(key.get("n"));
        assertFalse(key.has("d"));
        assertFalse(key.has("p"));
        assertFalse(key.has("q"));
    }
}
