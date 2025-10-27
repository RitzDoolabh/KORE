-- Flyway migration: initialize configuration tables for KnightKore
CREATE TABLE IF NOT EXISTS module_config (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    instance VARCHAR(100) NOT NULL,
    domain VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    route_mode VARCHAR(32) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_module_config_type_instance ON module_config(type, instance);
CREATE INDEX IF NOT EXISTS idx_module_config_domain ON module_config(domain);

CREATE TABLE IF NOT EXISTS service_config (
    id UUID PRIMARY KEY,
    name VARCHAR(120) UNIQUE NOT NULL,
    max_threads INT NOT NULL,
    dependencies TEXT,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_service_config_enabled ON service_config(enabled);

CREATE TABLE IF NOT EXISTS plugin_config (
    id UUID PRIMARY KEY,
    name VARCHAR(120) UNIQUE NOT NULL,
    enabled BOOLEAN NOT NULL,
    settings TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS flow_config (
    id UUID PRIMARY KEY,
    name VARCHAR(150) UNIQUE NOT NULL,
    definition TEXT,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS global_settings (
    id UUID PRIMARY KEY,
    settings TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);
