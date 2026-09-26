-- V10__create_medical_documents.sql
-- The actual "medical record" document vault (lab reports, imaging, discharge
-- summaries, etc.), distinct from ephemeral chat attachments. Chat upload can
-- optionally also write a row here when the sender flags it as a document.
CREATE TABLE medical_documents (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id               UUID NOT NULL REFERENCES patient_profiles(id),
    uploaded_by_user_id      UUID NOT NULL REFERENCES users(id),
    document_type            VARCHAR(30) NOT NULL
        CHECK (document_type IN ('LAB_REPORT', 'XRAY', 'MRI_CT', 'PRESCRIPTION_SCAN', 'DISCHARGE_SUMMARY', 'OTHER')),
    s3_key                   TEXT NOT NULL,
    original_filename        VARCHAR(255),
    content_type             VARCHAR(100),
    size_bytes               BIGINT,
    related_appointment_id   UUID REFERENCES appointments(id),
    related_consultation_id  UUID REFERENCES consultations(id),
    uploaded_at               TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_medical_documents_patient ON medical_documents(patient_id);
