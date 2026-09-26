-- V14__doctor_clinic_info.sql
-- Backs the "doctor discovery by location/mode" requirement, which
-- previously had no field at all to filter or display on.
ALTER TABLE doctors ADD COLUMN IF NOT EXISTS clinic_name VARCHAR(255);
ALTER TABLE doctors ADD COLUMN IF NOT EXISTS clinic_address TEXT;

-- Matches the existing doctor_specialties @ElementCollection convention.
CREATE TABLE doctor_consultation_modes (
    doctor_id UUID NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    mode      VARCHAR(20) NOT NULL CHECK (mode IN ('ONLINE', 'IN_PERSON')),
    PRIMARY KEY (doctor_id, mode)
);

-- Every existing doctor currently only ever supports ONLINE (the only type
-- Android has ever sent) — seed that as their starting mode.
INSERT INTO doctor_consultation_modes (doctor_id, mode)
SELECT id, 'ONLINE' FROM doctors;
