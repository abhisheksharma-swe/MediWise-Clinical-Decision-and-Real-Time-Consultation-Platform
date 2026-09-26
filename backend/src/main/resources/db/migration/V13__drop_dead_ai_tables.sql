-- V13__drop_dead_ai_tables.sql
-- Postgres `symptom_logs` and `vital_records` were created in V1 but no JPA
-- entity has ever read or written them — the real, functioning symptom-log
-- implementation lives in the MongoDB `symptom_logs` collection instead.
-- Keeping two same-named-but-disconnected concepts around is confusing, so
-- the dead Postgres tables are dropped rather than left as unused schema.
DROP TABLE IF EXISTS symptom_logs;
DROP TABLE IF EXISTS vital_records;
