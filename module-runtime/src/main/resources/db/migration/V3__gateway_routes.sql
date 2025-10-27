-- Flyway V3: gateway routes table
CREATE TABLE IF NOT EXISTS gateway_route (
    id UUID PRIMARY KEY,
    path_pattern VARCHAR(300) NOT NULL,
    uri VARCHAR(500) NOT NULL,
    required_roles VARCHAR(300),
    strip_prefix INT,
    filters_json TEXT,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_gateway_route_enabled ON gateway_route(enabled);
CREATE INDEX IF NOT EXISTS idx_gateway_route_path ON gateway_route(path_pattern);
