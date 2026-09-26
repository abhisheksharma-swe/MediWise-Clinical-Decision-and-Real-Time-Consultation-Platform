-- V8__create_prescriptions.sql
-- Structured prescriptions, replacing the old single free-text
-- `appointments.prescription` column for anything recorded going forward.
CREATE TABLE prescriptions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id  UUID NOT NULL REFERENCES consultations(id),
    patient_id       UUID NOT NULL REFERENCES patient_profiles(id),
    doctor_id        UUID NOT NULL REFERENCES doctors(id),
    notes            TEXT,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE prescription_items (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id    UUID NOT NULL REFERENCES prescriptions(id) ON DELETE CASCADE,
    medicine_name      VARCHAR(255) NOT NULL,
    dosage             VARCHAR(100),
    frequency          VARCHAR(100),
    duration           VARCHAR(100),
    instructions       TEXT,
    before_after_food  VARCHAR(20) CHECK (before_after_food IN ('BEFORE_FOOD', 'AFTER_FOOD', 'NOT_APPLICABLE')),
    sort_order         INT DEFAULT 0
);

CREATE INDEX idx_prescriptions_consultation ON prescriptions(consultation_id);
CREATE INDEX idx_prescriptions_patient      ON prescriptions(patient_id);
CREATE INDEX idx_prescription_items_rx      ON prescription_items(prescription_id);

-- Backfill: one Prescription with a single free-text PrescriptionItem per
-- existing completed appointment that had a non-blank prescription string.
-- The old value is preserved as-is in medicine_name; dosage/frequency/etc.
-- are left null since that structure never existed in the source data.
INSERT INTO prescriptions (consultation_id, patient_id, doctor_id, created_at)
SELECT c.id, c.patient_id, c.doctor_id, c.created_at
FROM consultations c
JOIN appointments a ON a.id = c.appointment_id
WHERE a.prescription IS NOT NULL AND btrim(a.prescription) <> '';

INSERT INTO prescription_items (prescription_id, medicine_name, sort_order)
SELECT p.id, btrim(a.prescription), 0
FROM prescriptions p
JOIN consultations c ON c.id = p.consultation_id
JOIN appointments a ON a.id = c.appointment_id
WHERE a.prescription IS NOT NULL AND btrim(a.prescription) <> '';
