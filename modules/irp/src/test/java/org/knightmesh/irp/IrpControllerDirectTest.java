package org.knightmesh.irp;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.RouteMode;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.runtime.config.ConfigRepository;
import org.knightmesh.runtime.router.ServiceRouter;
import org.knightmesh.plugins.queue.QueuePlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = IrpController.class)
class IrpControllerDirectTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfigRepository configRepository;

    @MockBean
    private ServiceRouter serviceRouter;

    @MockBean
    private QueuePlugin queuePlugin;

    @Test
    void post_direct_invokes_router_and_returns_response() throws Exception {
        // Given: IRP module configured for DIRECT routing
        ModuleConfig mc = new ModuleConfig();
        mc.setRouteMode(RouteMode.DIRECT);
        when(configRepository.getModuleConfig("irp")).thenReturn(Optional.of(mc));

        // And router returns SUCCESS
        ServiceResponse ok = ServiceResponse.success(Map.of("user", "alice"));
        when(serviceRouter.route(any())).thenReturn(ok);

        // When/Then: POST should return 200 with ServiceResponse JSON
        String json = "{\"user\":\"alice\",\"email\":\"a@x.com\"}";
        mockMvc.perform(post("/irp/REGISTER_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.user", is("alice")));

        verify(serviceRouter, times(1)).route(any());
        verify(queuePlugin, never()).enqueue(anyString(), any());
    }
}
