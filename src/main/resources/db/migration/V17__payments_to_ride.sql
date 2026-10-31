-- Port de payments a movilidad (T8): Ride en lugar de Reservation.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS ride_id BIGINT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS creator_id BIGINT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS currency_code VARCHAR(8);
