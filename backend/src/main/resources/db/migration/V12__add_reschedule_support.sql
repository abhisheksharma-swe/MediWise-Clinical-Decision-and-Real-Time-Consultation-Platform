-- V12__add_reschedule_support.sql
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_status_check;
ALTER TABLE appointments ADD CONSTRAINT appointments_status_check
    CHECK (status IN ('PENDING','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW','RESCHEDULED'));

ALTER TABLE appointments ADD COLUMN IF NOT EXISTS original_slot_id UUID REFERENCES time_slots(id);
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS rescheduled_from_appointment_id UUID REFERENCES appointments(id);
