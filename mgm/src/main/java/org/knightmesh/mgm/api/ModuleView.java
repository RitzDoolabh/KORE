package org.knightmesh.mgm.api;

import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.ModuleType;
import org.knightmesh.core.config.RouteMode;

public class ModuleView {
    public String name;
    public ModuleType type;
    public String instance;
    public String domain;
    public boolean enabled;
    public RouteMode routeMode;
    public String queueName;
    public String services;

    public static ModuleView from(ModuleConfig mc) {
        ModuleView v = new ModuleView();
        v.name = mc.getName();
        v.type = mc.getType();
        v.instance = mc.getInstance();
        v.domain = mc.getDomain();
        v.enabled = mc.isEnabled();
        v.routeMode = mc.getRouteMode();
        v.queueName = mc.getQueueName();
        v.services = mc.getServices();
        return v;
    }
}
