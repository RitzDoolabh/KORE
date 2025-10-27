package org.knightmesh.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import java.util.List;
import java.util.Map;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(classes = GatewayApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                // Point /irp/** route at WireMock
                "gateway.irp.uri=http://localhost:${wiremock.port}",
                // Keep DB auto-config off for this test
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
class GatewaySecurityIntegrationTest {

    private static WireMockServer wireMock;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startWireMock() {
        int port = 0; // dynamic
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        System.setProperty("wiremock.port", String.valueOf(wireMock.port()));
        WireMock.configureFor("localhost", wireMock.port());

        // Stub downstream IRP endpoint; verify Authorization header is forwarded
        wireMock.stubFor(post(urlPathMatching("/REGISTER_USER"))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"SUCCESS\",\"data\":{\"ok\":true}}")));

        // Catch-all to return 404 for unexpected calls
        wireMock.stubFor(any(urlMatching("/.*")).atPriority(10)
                .willReturn(aResponse().withStatus(404)));
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
        System.clearProperty("wiremock.port");
    }

    @Test
    void unauthorized_without_token_gets_401() {
        webTestClient.post()
                .uri("/irp/REGISTER_USER")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"user\":\"alice\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void authorized_user_token_can_access_irp_route_and_jwt_is_forwarded() {
        // Configure mock JWT with USER role in realm_access.roles
        var jwtMutator = mockJwt().jwt(jwt -> {
            jwt.claim("realm_access", Map.of("roles", List.of("USER")));
            jwt.claim("sub", "devuser");
            jwt.claim("iss", "http://localhost/realms/knightmesh");
        });

        webTestClient.mutateWith(jwtMutator)
                .post().uri("/irp/REGISTER_USER")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"user\":\"alice\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.data.ok").isEqualTo(true);

        // Verify WireMock saw the call with Authorization header (done via stub requirement as well)
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/REGISTER_USER"))
                .withHeader("Authorization", matching("Bearer .+")));
    }
}
