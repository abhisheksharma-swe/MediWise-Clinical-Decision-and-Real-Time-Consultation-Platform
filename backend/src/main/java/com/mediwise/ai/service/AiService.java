package com.mediwise.ai.service;

import com.mediwise.ai.dto.AiReportResponse;
import com.mediwise.ai.dto.SymptomLogRequest;
import com.mediwise.ai.model.AiReport;
import com.mediwise.ai.model.SymptomLog;
import com.mediwise.ai.repository.AiReportRepository;
import com.mediwise.ai.repository.SymptomLogRepository;
import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.UnauthorizedException;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI Service — currently returns stub responses.
 * When the Python ML FastAPI service is deployed, replace stubAnalyze()
 * with a WebClient call to the ML service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final AiReportRepository aiReportRepository;
    private final SymptomLogRepository symptomLogRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;

    public AiReportResponse analyzeSymptoms(SymptomLogRequest request, User user) {
        assertSymptomLogAccess(request.getPatientId(), request.getAppointmentId(), user);

        // 1. Persist the symptom log
        SymptomLog log = new SymptomLog();
        log.setPatientId(request.getPatientId());
        log.setAppointmentId(request.getAppointmentId());
        log.setSymptoms(request.getSymptoms());
        log.setSeverity(request.getSeverity());
        log.setNotes(request.getNotes());
        log.setLoggedAt(Instant.now());
        symptomLogRepository.save(log);

        // 2. Generate AI analysis (stub — swap with WebClient to Python service)
        AiReport report = stubAnalyze(request);
        AiReport saved = aiReportRepository.save(report);

        return toResponse(saved);
    }

    public List<AiReportResponse> getReportsForPatient(UUID patientId, User user) {
        assertCanViewReports(patientId, user);
        return aiReportRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public AiReportResponse getLatestReport(UUID patientId, User user) {
        assertCanViewReports(patientId, user);
        return aiReportRepository.findTopByPatientIdOrderByCreatedAtDesc(patientId)
                .map(this::toResponse)
                .orElse(null);
    }

    // ─── Access control helpers ─────────────────────────────────────────────

    private void assertSymptomLogAccess(UUID patientId, UUID appointmentId, User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return;
        }

        if (appointmentId != null) {
            Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
            if (appointment != null && appointment.getPatientId().equals(patientId)) {
                if (user.getRole() == User.Role.DOCTOR) {
                    UUID doctorId = doctorRepository.findByUserId(user.getId())
                            .map(Doctor::getId).orElse(null);
                    if (doctorId != null && appointment.getDoctorId().equals(doctorId)) {
                        return;
                    }
                } else {
                    UUID ownPatientId = patientProfileRepository.findByUserId(user.getId())
                            .map(PatientProfile::getId).orElse(null);
                    if (patientId.equals(ownPatientId)) {
                        return;
                    }
                }
            }
        } else if (user.getRole() == User.Role.PATIENT) {
            UUID ownPatientId = patientProfileRepository.findByUserId(user.getId())
                    .map(PatientProfile::getId).orElse(null);
            if (patientId.equals(ownPatientId)) {
                return;
            }
        }

        throw new UnauthorizedException("You do not have permission to log symptoms for this patient.");
    }

    private void assertCanViewReports(UUID patientId, User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return;
        }

        if (user.getRole() == User.Role.PATIENT) {
            UUID ownPatientId = patientProfileRepository.findByUserId(user.getId())
                    .map(PatientProfile::getId).orElse(null);
            if (patientId.equals(ownPatientId)) {
                return;
            }
        } else if (user.getRole() == User.Role.DOCTOR) {
            UUID doctorId = doctorRepository.findByUserId(user.getId())
                    .map(Doctor::getId).orElse(null);
            if (doctorId != null && appointmentRepository.existsByDoctorIdAndPatientId(doctorId, patientId)) {
                return;
            }
        }

        throw new UnauthorizedException("You do not have permission to view reports for this patient.");
    }

    // ─── Stub implementation ─────────────────────────────────────────────────
    // Replace with: webClient.post().uri(mlServiceUrl + "/predict/symptom-triage")...

    private AiReport stubAnalyze(SymptomLogRequest req) {
        int urgency = switch (req.getSeverity() != null ? req.getSeverity().toUpperCase() : "LOW") {
            case "CRITICAL" -> 90;
            case "HIGH"     -> 70;
            case "MODERATE" -> 45;
            default         -> 20;
        };

        String specialty = urgency > 65 ? "Emergency Medicine" :
                urgency > 40 ? "General Medicine" : "General Practice";

        AiReport report = new AiReport();
        report.setPatientId(req.getPatientId());
        report.setAppointmentId(req.getAppointmentId());
        report.setModelName("symptom_triage_v1_stub");
        report.setModelVersion("1.0.0-stub");
        report.setUrgencyScore(urgency);
        report.setSuggestedSpecialty(specialty);
        report.setConfidence(0.72);
        report.setRecommendation("Based on reported symptoms, we recommend consulting a " + specialty + " at the earliest.");
        report.setRiskFactors(req.getSymptoms() != null && !req.getSymptoms().isEmpty()
                ? req.getSymptoms().subList(0, Math.min(3, req.getSymptoms().size()))
                : List.of());
        report.setCreatedAt(Instant.now());
        return report;
    }

    private AiReportResponse toResponse(AiReport r) {
        return AiReportResponse.builder()
                .id(r.getId())
                .patientId(r.getPatientId())
                .appointmentId(r.getAppointmentId())
                .modelName(r.getModelName())
                .modelVersion(r.getModelVersion())
                .urgencyScore(r.getUrgencyScore())
                .suggestedSpecialty(r.getSuggestedSpecialty())
                .confidence(r.getConfidence())
                .recommendation(r.getRecommendation())
                .riskFactors(r.getRiskFactors())
                .createdAt(r.getCreatedAt())
                .build();
    }
}