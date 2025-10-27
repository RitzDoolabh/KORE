package org.knightmesh.runtime.router;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.ServiceConfig;
import org.knightmesh.runtime.config.ConfigRepository;
import org.mockito.Mockito;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KubernetesRemoteServiceLocatorTest {

    @Test
    void k8s_discovery_path_uses_DiscoveryClient() {
        // given
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        DefaultServiceInstance cloudInst = new DefaultServiceInstance(
                "spm-1", "REGISTER_USER", "10.0.0.5", 8080, false
        );
        cloudInst.getMetadata().put("zone", "a");
        when(discoveryClient.getInstances("REGISTER_USER")).thenReturn(List.of(cloudInst));

        ConfigRepository repo = mock(ConfigRepository.class);
        MockEnvironment env = new MockEnvironment().withProperty("knightmesh.kubernetes.enabled", "true");

        KubernetesRemoteServiceLocator locator = new KubernetesRemoteServiceLocator(discoveryClient, repo, env);

        // when
        List<ServiceInstance> list = locator.findInstances("REGISTER_USER");

        // then
        assertThat(list).hasSize(1);
        ServiceInstance si = list.get(0);
        assertThat(si.getHost()).isEqualTo("10.0.0.5");
        assertThat(si.getPort()).isEqualTo(8080);
        assertThat(si.getMetadata()).containsEntry("zone", "a");
        assertThat(si.baseUrl()).isEqualTo("http://10.0.0.5:8080");

        verify(discoveryClient, times(1)).getInstances("REGISTER_USER");
        verifyNoInteractions(repo);
    }

    @Test
    void db_fallback_path_reads_ModuleConfig_extraJson() {
        // given: no discovery client (simulating non-K8s dev env)
        DiscoveryClient discoveryClient = null;
        MockEnvironment env = new MockEnvironment(); // no k8s flag

        ConfigRepository repo = mock(ConfigRepository.class);
        ServiceConfig svc = new ServiceConfig();
        svc.setServiceName("USER_AUTH");
        svc.setModuleName("spm");
        when(repo.getService("USER_AUTH")).thenReturn(Optional.of(svc));

        ModuleConfig mod = new ModuleConfig();
        mod.setName("spm");
        mod.setEnabled(true);
        mod.setExtraJson("{\n  \"instances\": [\n    {\n      \"host\": \"127.0.0.1\", \"port\": 9001, \"metadata\": {\"scheme\": \"http\", \"zone\": \"dev\"}\n    },\n    {\n      \"host\": \"127.0.0.2\", \"port\": 9002\n    }\n  ]\n}");
        when(repo.listEnabledModules()).thenReturn(List.of(mod));

        KubernetesRemoteServiceLocator locator = new KubernetesRemoteServiceLocator(discoveryClient, repo, env);

        // when
        List<ServiceInstance> list = locator.findInstances("USER_AUTH");

        // then
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getHost()).isEqualTo("127.0.0.1");
        assertThat(list.get(0).getPort()).isEqualTo(9001);
        assertThat(list.get(0).getMetadata()).containsAllEntriesOf(Map.of("scheme", "http", "zone", "dev"));
        assertThat(list.get(1).getHost()).isEqualTo("127.0.0.2");
        assertThat(list.get(1).getPort()).isEqualTo(9002);

        verify(repo, times(1)).getService("USER_AUTH");
        verify(repo, times(1)).listEnabledModules();
        Mockito.verifyNoMoreInteractions(repo);
    }
}
