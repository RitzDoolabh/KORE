package org.knightmesh.irp;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.RouteMode;
import org.knightmesh.runtime.config.ConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = IrpApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IrpDirectIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfigRepository configRepository;

    @Test
    void direct_mode_invokes_spm_services_and_returns_success() throws Exception {
        // Configure IRP module to DIRECT routing via mocked ConfigRepository
        ModuleConfig mc = new ModuleConfig();
        mc.setRouteMode(RouteMode.DIRECT);
        when(configRepository.getModuleConfig("irp")).thenReturn(Optional.of(mc));

        String body = "{\"username\":\"alice\",\"email\":\"alice@example.org\",\"password\":\"pw\"}";

        mockMvc.perform(post("/irp/REGISTER_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.user", is("alice")))
                .andExpect(jsonPath("$.data.email", is("alice@example.org")))
                .andExpect(jsonPath("$.data.userId", notNullValue()))
                .andExpect(jsonPath("$.data.token", notNullValue()));
    }
}
