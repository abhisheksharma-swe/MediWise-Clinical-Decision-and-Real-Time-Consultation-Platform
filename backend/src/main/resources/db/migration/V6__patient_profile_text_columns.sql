-- V6__patient_profile_text_columns.sql
-- patient_profiles.address / emergency_contact were declared as jsonb but the
-- application only ever writes and reads them as plain free-text strings
-- (e.g. "123 Healthcare Ave, Mumbai"). A bare string is not valid JSON, so
-- Postgres rejected every save with an invalid-input-syntax error, making
-- "Edit Profile" fail whenever either field was filled in. Converting the
-- columns to plain text aligns storage with actual usage.
--
-- A defensive unwrap function is used (instead of a bare `::jsonb` cast in
-- USING) so that any pre-existing row holding a value which is NOT valid
-- JSON does not abort this migration — it is carried over as-is instead.
CREATE OR REPLACE FUNCTION _v6_unwrap_json_text(val TEXT) RETURNS TEXT AS $$
BEGIN
    IF val IS NULL THEN
        RETURN NULL;
    END IF;
    IF jsonb_typeof(val::jsonb) = 'string' THEN
        RETURN val::jsonb #>> '{}';
    END IF;
    RETURN val;
EXCEPTION WHEN OTHERS THEN
    RETURN val;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

ALTER TABLE patient_profiles
    ALTER COLUMN address TYPE TEXT USING _v6_unwrap_json_text(address::text);

ALTER TABLE patient_profiles
    ALTER COLUMN emergency_contact TYPE TEXT USING _v6_unwrap_json_text(emergency_contact::text);

DROP FUNCTION _v6_unwrap_json_text(TEXT);
