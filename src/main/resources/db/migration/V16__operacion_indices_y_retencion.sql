-- V16__operacion_indices_y_retencion.sql (inicial)
CREATE INDEX IF NOT EXISTS idx_rides_state ON rides (status);
CREATE INDEX IF NOT EXISTS idx_ride_offers_ride ON ride_offers (ride_id);
CREATE INDEX IF NOT EXISTS idx_ledger_driver_currency ON ledger_entries (driver_id, currency_code);
CREATE INDEX IF NOT EXISTS idx_country_rules_code ON country_rules (country_code);
CREATE INDEX IF NOT EXISTS idx_admin_audit_actor ON admin_audit_log (actor_username);
