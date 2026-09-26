-- V11__create_follow_ups.sql
CREATE TABLE follow_ups (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id        UUID NOT NULL REFERENCES consultations(id),
    patient_id             UUID NOT NULL REFERENCES patient_profiles(id),
    doctor_id              UUID NOT NULL REFERENCES doctors(id),
    recommended_date       DATE,
    reason                 TEXT,
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SCHEDULED', 'COMPLETED', 'DISMISSED')),
    linked_appointment_id  UUID REFERENCES appointments(id),
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_follow_ups_patient ON follow_ups(patient_id, status);
CREATE INDEX idx_follow_ups_doctor  ON follow_ups(doctor_id);
