-- V7__create_consultations.sql
-- Formalizes what was previously three free-text columns on `appointments`
-- (notes/diagnosis/prescription) into a proper Consultation record, one per
-- completed appointment. The old columns are kept (not dropped) for one
-- release as a read-compatibility shim while Android migrates to the new
-- consultation endpoints.
--
-- `diagnosis`/`prescription` were never added by a tracked migration — they
-- only exist today because `spring.jpa.hibernate.ddl-auto: update` silently
-- created them to match the JPA entity. Adding them here idempotently closes
-- that gap so a clean deploy (ddl-auto off) ends up with the same schema.
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS diagnosis TEXT;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS prescription TEXT;

CREATE TABLE consultations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id   UUID UNIQUE NOT NULL REFERENCES appointments(id),
    patient_id       UUID NOT NULL REFERENCES patient_profiles(id),
    doctor_id        UUID NOT NULL REFERENCES doctors(id),
    chief_complaint  TEXT,
    observations     TEXT,
    assessment       TEXT,
    treatment_plan   TEXT,
    doctor_notes     TEXT,
    status           VARCHAR(30),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- Structured symptom list, child table (matches the existing
-- doctor_specialties @ElementCollection convention rather than a native
-- Postgres array, which nothing else in this schema uses).
CREATE TABLE consultation_symptoms (
    consultation_id UUID NOT NULL REFERENCES consultations(id) ON DELETE CASCADE,
    symptom         VARCHAR(255) NOT NULL,
    sort_order      INT DEFAULT 0
);

CREATE INDEX idx_consultations_patient ON consultations(patient_id);
CREATE INDEX idx_consultations_doctor  ON consultations(doctor_id);
CREATE INDEX idx_consultation_symptoms ON consultation_symptoms(consultation_id);

-- Backfill one consultation row per existing COMPLETED appointment, carrying
-- over the old free-text fields so nothing is lost.
INSERT INTO consultations (appointment_id, patient_id, doctor_id, chief_complaint,
                            observations, assessment, doctor_notes, status, created_at, updated_at)
SELECT a.id, a.patient_id, a.doctor_id, a.chief_complaint,
       NULL, a.diagnosis, a.notes, a.status, a.created_at, a.updated_at
FROM appointments a
WHERE a.status = 'COMPLETED';
