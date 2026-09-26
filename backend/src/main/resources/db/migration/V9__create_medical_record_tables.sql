-- V9__create_medical_record_tables.sql
-- Patient-owned medical record: conditions, allergies, medications. Not
-- consultation-scoped — this is the patient's longitudinal record, though
-- an entry may optionally trace back to the consultation that created it.
-- No backfill: this data never existed in any structured form before.
CREATE TABLE patient_conditions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id              UUID NOT NULL REFERENCES patient_profiles(id),
    name                    VARCHAR(255) NOT NULL,
    diagnosed_date          DATE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RESOLVED')),
    notes                   TEXT,
    source_consultation_id  UUID REFERENCES consultations(id),
    self_reported           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE patient_allergies (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id     UUID NOT NULL REFERENCES patient_profiles(id),
    allergen       VARCHAR(255) NOT NULL,
    reaction       TEXT,
    severity       VARCHAR(20) CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE')),
    notes          TEXT,
    self_reported  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ DEFAULT NOW(),
    updated_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE patient_medications (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id                  UUID NOT NULL REFERENCES patient_profiles(id),
    name                        VARCHAR(255) NOT NULL,
    dosage                      VARCHAR(100),
    frequency                   VARCHAR(100),
    start_date                  DATE,
    end_date                    DATE,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    source_prescription_item_id UUID REFERENCES prescription_items(id),
    self_reported                BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_patient_conditions_patient  ON patient_conditions(patient_id);
CREATE INDEX idx_patient_allergies_patient   ON patient_allergies(patient_id);
CREATE INDEX idx_patient_medications_patient ON patient_medications(patient_id);
