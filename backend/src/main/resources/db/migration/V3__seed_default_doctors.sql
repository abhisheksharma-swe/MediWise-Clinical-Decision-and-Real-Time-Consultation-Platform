-- V3__seed_default_doctors.sql
-- Seed default specialized doctors, user accounts, weekly schedules, and time slots

-- 1. Insert Admin & Doctor User Accounts (Admin password: Admin@12345, Doctor password: Doctor@12345)
-- Bcrypt hash (strength 12) for 'Admin@12345': $2a$12$casd0AhSDQnW.otJNb0zYuRGJygVF.UPEgABj5.DlXilpw.td.dLm
-- Bcrypt hash (strength 12) for 'Doctor@12345': $2a$12$by5Q4/1p9vVhRCfnFJ/KTeDwiB.FXRoeBIZpGwu3GB.P8ytp02Uke
INSERT INTO users (id, firebase_uid, email, phone, role, full_name, is_active, password_hash, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'firebase_admin_01', 'admin@mediwise.com', '+919876543200', 'ADMIN', 'System Administrator', true, '$2a$12$casd0AhSDQnW.otJNb0zYuRGJygVF.UPEgABj5.DlXilpw.td.dLm', NOW(), NOW()),
    ('11111111-1111-1111-1111-111111111101', 'firebase_doc_101', 'dr.sarah@mediwise.com', '+919876543201', 'DOCTOR', 'Dr. Sarah Johnson', true, '$2a$12$by5Q4/1p9vVhRCfnFJ/KTeDwiB.FXRoeBIZpGwu3GB.P8ytp02Uke', NOW(), NOW()),
    ('11111111-1111-1111-1111-111111111102', 'firebase_doc_102', 'dr.rajesh@mediwise.com', '+919876543202', 'DOCTOR', 'Dr. Rajesh Sharma', true, '$2a$12$by5Q4/1p9vVhRCfnFJ/KTeDwiB.FXRoeBIZpGwu3GB.P8ytp02Uke', NOW(), NOW()),
    ('11111111-1111-1111-1111-111111111103', 'firebase_doc_103', 'dr.elena@mediwise.com', '+919876543203', 'DOCTOR', 'Dr. Elena Rostova', true, '$2a$12$by5Q4/1p9vVhRCfnFJ/KTeDwiB.FXRoeBIZpGwu3GB.P8ytp02Uke', NOW(), NOW()),
    ('11111111-1111-1111-1111-111111111104', 'firebase_doc_104', 'dr.marcus@mediwise.com', '+919876543204', 'DOCTOR', 'Dr. Marcus Chen', true, '$2a$12$by5Q4/1p9vVhRCfnFJ/KTeDwiB.FXRoeBIZpGwu3GB.P8ytp02Uke', NOW(), NOW()),
    ('11111111-1111-1111-1111-111111111105', 'firebase_doc_105', 'dr.ananya@mediwise.com', '+919876543205', 'DOCTOR', 'Dr. Ananya Iyer', true, '$2a$12$by5Q4/1p9vVhRCfnFJ/KTeDwiB.FXRoeBIZpGwu3GB.P8ytp02Uke', NOW(), NOW()),
    ('11111111-1111-1111-1111-111111111106', 'firebase_doc_106', 'dr.david@mediwise.com', '+919876543206', 'DOCTOR', 'Dr. David Miller', true, '$2a$12$by5Q4/1p9vVhRCfnFJ/KTeDwiB.FXRoeBIZpGwu3GB.P8ytp02Uke', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- 2. Insert Doctor Profiles
INSERT INTO doctors (id, user_id, full_name, bio, specialty, license_number, experience_yrs, consultation_fee, avg_rating, total_reviews, is_available, verified, created_at, updated_at)
VALUES
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101', 'Sarah Johnson', 'Senior Interventional Cardiologist with 14+ years of clinical excellence in cardiovascular disease, hypertension management, and echocardiography. Certified by the American Board of Internal Medicine.', 'Cardiology', 'MED-LIC-CARD-001', 14, 800.00, 4.90, 128, true, true, NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111102', 'Rajesh Sharma', 'Lead General Physician & Diabetologist focusing on comprehensive preventive medicine, chronic lifestyle disease reversal, metabolic health, and acute adult care.', 'General Medicine', 'MED-LIC-GEN-002', 18, 500.00, 4.85, 215, true, true, NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111103', 'Elena Rostova', 'Specialist Dermatologist and Trichologist specializing in clinical dermatology, autoimmune skin conditions, acne management, and cosmetic dermatology procedures.', 'Dermatology', 'MED-LIC-DERM-003', 11, 750.00, 4.92, 94, true, true, NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111104', 'Marcus Chen', 'Consultant Neurologist with extensive expertise in stroke management, chronic migraine, epilepsy, and neurodegenerative disorders. Former Fellow at Johns Hopkins Medicine.', 'Neurology', 'MED-LIC-NEUR-004', 16, 1200.00, 4.95, 142, true, true, NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111105', 'Ananya Iyer', 'Dedicated Pediatrician and Child Healthcare Specialist passionate about neonatal development, immunization schedules, childhood infectious diseases, and pediatric nutrition.', 'Pediatrics', 'MED-LIC-PED-005', 9, 600.00, 4.88, 167, true, true, NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222206', '11111111-1111-1111-1111-111111111106', 'David Miller', 'Senior Orthopedic Surgeon specializing in sports injuries, joint replacement, arthroscopic surgery, and musculoskeletal trauma rehabilitation.', 'Orthopedics', 'MED-LIC-ORTH-006', 15, 900.00, 4.80, 110, true, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 3. Insert Doctor Specialties (Multi-specialty associations)
INSERT INTO doctor_specialties (doctor_id, specialty)
VALUES
    ('22222222-2222-2222-2222-222222222201', 'Cardiology'),
    ('22222222-2222-2222-2222-222222222201', 'Echocardiography'),
    ('22222222-2222-2222-2222-222222222202', 'General Medicine'),
    ('22222222-2222-2222-2222-222222222202', 'Diabetology'),
    ('22222222-2222-2222-2222-222222222203', 'Dermatology'),
    ('22222222-2222-2222-2222-222222222203', 'Cosmetology'),
    ('22222222-2222-2222-2222-222222222204', 'Neurology'),
    ('22222222-2222-2222-2222-222222222205', 'Pediatrics'),
    ('22222222-2222-2222-2222-222222222206', 'Orthopedics')
ON CONFLICT (doctor_id, specialty) DO NOTHING;

-- 4. Insert Default Weekly Schedules (Monday to Saturday, 09:00 - 17:00)
INSERT INTO schedules (id, doctor_id, day_of_week, start_time, end_time, slot_duration_mins)
SELECT
    gen_random_uuid(),
    d.id,
    day_num,
    '09:00:00'::TIME,
    '17:00:00'::TIME,
    30
FROM doctors d
CROSS JOIN (VALUES (1), (2), (3), (4), (5), (6)) AS days(day_num);

-- 5. Pre-seed Time Slots for Current and Next 7 Days
INSERT INTO time_slots (id, doctor_id, slot_date, start_time, end_time, status)
SELECT
    gen_random_uuid(),
    d.id,
    (CURRENT_DATE + days_offset * INTERVAL '1 day')::DATE,
    slot_time::TIME,
    (slot_time::TIME + INTERVAL '30 minutes')::TIME,
    'AVAILABLE'
FROM doctors d
CROSS JOIN (VALUES (0), (1), (2), (3), (4), (5), (6), (7)) AS offsets(days_offset)
CROSS JOIN (VALUES
    ('09:00:00'), ('09:30:00'), ('10:00:00'), ('10:30:00'),
    ('11:00:00'), ('11:30:00'), ('14:00:00'), ('14:30:00'),
    ('15:00:00'), ('15:30:00'), ('16:00:00'), ('16:30:00')
) AS times(slot_time)
ON CONFLICT (doctor_id, slot_date, start_time) DO NOTHING;
