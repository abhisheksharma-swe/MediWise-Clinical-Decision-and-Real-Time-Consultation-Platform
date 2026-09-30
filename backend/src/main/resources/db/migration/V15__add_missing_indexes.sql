-- V15__add_missing_indexes.sql
-- doctor_ratings.doctor_id (V1) and appointment_status_history.appointment_id (added post-V1,
-- see AppointmentStatusHistory entity) are both filtered on directly but were never indexed —
-- every doctor-detail-page view (ReviewService.getForDoctor/recomputeDoctorRating) and any
-- future status-history lookup would do a full table scan as these grow.
CREATE INDEX idx_doctor_ratings_doctor ON doctor_ratings(doctor_id);
CREATE INDEX idx_appointment_status_history_appointment ON appointment_status_history(appointment_id);
