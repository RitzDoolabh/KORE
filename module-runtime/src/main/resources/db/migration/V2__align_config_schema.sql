-- Flyway migration V2: align schema with updated entities and requirements

-- Module configuration: add new columns if missing
ALTER TABLE IF EXISTS module_config
    ADD COLUMN IF NOT EXISTS name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS queue_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS services TEXT,
    ADD COLUMN IF NOT EXISTS extra_json TEXT;

-- Ensure name is unique if present
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE schemaname = current_schema() AND indexname = 'uq_module_config_name'
    ) THEN
        CREATE UNIQUE INDEX uq_module_config_name ON module_config(name);
    END IF;
END$$;

-- Helpful index for enabled modules
CREATE INDEX IF NOT EXISTS idx_module_config_enabled ON module_config(enabled);

-- Service configuration: add new columns if missing
ALTER TABLE IF EXISTS service_config
    ADD COLUMN IF NOT EXISTS service_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS module_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS config_json TEXT;

-- Relax legacy NOT NULL constraint on old 'name' column to allow newer schema usage
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'service_config' AND column_name = 'name'
    ) THEN
        EXECUTE 'ALTER TABLE service_config ALTER COLUMN name DROP NOT NULL';
    END IF;
END$$;

-- Ensure service_name is unique if present
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE schemaname = current_schema() AND indexname = 'uq_service_config_service_name'
    ) THEN
        CREATE UNIQUE INDEX uq_service_config_service_name ON service_config(service_name);
    END IF;
END$$;

-- Helpful index for remote lookups
CREATE INDEX IF NOT EXISTS idx_service_config_module_enabled ON service_config(module_name, enabled);
