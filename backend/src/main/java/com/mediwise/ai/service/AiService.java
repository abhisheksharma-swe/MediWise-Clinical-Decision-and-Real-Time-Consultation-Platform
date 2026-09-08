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
import com.mediwise.doctor.dto.DoctorResponse;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.doctor.service.DoctorSpecification;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI Service — real symptom triage via Google Gemini, cross-referenced
 * against verified doctors actually available on the platform.
 *
 * SAFETY DESIGN:
 * - Never gives a confident diagnosis — always frames output as a suggestion.
 * - Any life-threatening keyword forces urgencyScore=100 + Emergency Medicine
 *   regardless of what the model returns, as a hard safety floor.
 * - Every recommendation ends with a "not a substitute for professional
 *   medical advice" disclaimer, appended server-side (not trusted from the model).
 * - If Gemini is unreachable/misconfigured, falls back to a safe generic
 *   response instead of crashing or leaving the user with nothing.
 * - The model is constrained to only return specialty names that actually
 *   exist on the platform, so doctor-matching never fails due to wording
 *   mismatches (e.g. "Cardiologist" vs "Cardiology").
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

    @Value("${application.ai-service.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${application.ai-service.gemini-model:gemini-3.6-flash}")
    private String geminiModel;

    private static final String MODEL_NAME = "gemini_symptom_triage";
    private static final String MODEL_VERSION = "v2";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(45);
    private static final int MAX_MATCHED_DOCTORS = 5;

    // Must match the specialty values actually seeded/used in the doctors table.
    private static final List<String> PLATFORM_SPECIALTIES = List.of(
            "Cardiology", "General Medicine", "Dermatology",
            "Neurology", "Pediatrics", "Orthopedics", "Emergency Medicine"
    );

    private static final List<String> EMERGENCY_KEYWORDS = List.of(
            "chest pain", "difficulty breathing", "shortness of breath", "severe bleeding",
            "unconscious", "seizure", "stroke", "can't breathe", "suicidal", "severe allergic reaction",
            "anaphylaxis", "heart attack", "loss of consciousness", "choking"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    public AiReportResponse analyzeSymptoms(SymptomLogRequest request, User user) {
        assertSymptomLogAccess(request.getPatientId(), request.getAppointmentId(), user);

        SymptomLog log = new SymptomLog();
        log.setPatientId(request.getPatientId());
        log.setAppointmentId(request.getAppointmentId());
        log.setSymptoms(request.getSymptoms());
        log.setSeverity(request.getSeverity());
        log.setNotes(request.getNotes());
        log.setLoggedAt(Instant.now());
        symptomLogRepository.save(log);

        AiReport report = analyzeWithGemini(request);
        AiReport saved = aiReportRepository.save(report);

        List<DoctorResponse> matchedDoctors = findMatchingDoctors(saved.getSuggestedSpecialty());

        return toResponse(saved, matchedDoctors);
    }

    public List<AiReportResponse> getReportsForPatient(UUID patientId, User user) {
        assertCanViewReports(patientId, user);
        return aiReportRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(r -> toResponse(r, findMatchingDoctors(r.getSuggestedSpecialty())))
                .collect(Collectors.toList());
    }

    public AiReportResponse getLatestReport(UUID patientId, User user) {
        assertCanViewReports(patientId, user);
        return aiReportRepository.findTopByPatientIdOrderByCreatedAtDesc(patientId)
                .map(r -> toResponse(r, findMatchingDoctors(r.getSuggestedSpecialty())))
                .orElse(null);
    }

    // ─── Doctor matching ─────────────────────────────────────────────────────

    private List<DoctorResponse> findMatchingDoctors(String specialty) {
        if (specialty == null || specialty.isBlank()) {
            return List.of();
        }

        Specification<Doctor> spec = Specification
                .where(DoctorSpecification.isVerified())
            .and(DoctorSpecification.isAvailable())
                .and(DoctorSpecification.hasSpecialty(specialty));

        return doctorRepository.findAll(spec)
                .stream()
                .map(DoctorResponse::from)
                .limit(MAX_MATCHED_DOCTORS)
                .toList();
    }

    // ─── Access control helpers (unchanged) ─────────────────────────────────

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

    // ─── Gemini integration ─────────────────────────────────────────────────

    private AiReport analyzeWithGemini(SymptomLogRequest req) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("Gemini API key not configured — returning safe fallback response");
            return applyEmergencyOverride(fallbackReport(req, "AI analysis is currently unavailable."), req);
        }

        try {
            String prompt = buildPrompt(req);
            String rawResponse = callGemini(prompt);
            AiReport report = parseGeminiResponse(rawResponse, req);
            return applyEmergencyOverride(report, req);
        } catch (Exception e) {
            log.error("Gemini symptom analysis failed: {}", e.getMessage(), e);
            return applyEmergencyOverride(fallbackReport(req, "AI analysis failed — please consult a doctor directly."), req);
        }
    }

    private String buildPrompt(SymptomLogRequest req) {
        StringBuilder vitals = new StringBuilder();
        if (req.getHeartRate() != null) vitals.append("Heart rate: ").append(req.getHeartRate()).append(" bpm. ");
        if (req.getSystolicBp() != null && req.getDiastolicBp() != null)
            vitals.append("Blood pressure: ").append(req.getSystolicBp()).append("/").append(req.getDiastolicBp()).append(" mmHg. ");
        if (req.getSpo2() != null) vitals.append("SpO2: ").append(req.getSpo2()).append("%. ");
        if (req.getTemperature() != null) vitals.append("Temperature: ").append(req.getTemperature()).append("°C. ");

        String specialtyList = String.join(", ", PLATFORM_SPECIALTIES);

        return """
                You are a medical triage assistant helping a patient understand which type of \
                doctor to see and how urgent their situation is. You are NOT diagnosing them — \
                you are suggesting a specialty and a general urgency level only.

                Patient-reported symptoms: %s
                Reported severity: %s
                Additional notes: %s
                Vitals: %s

                IMPORTANT: For "suggestedSpecialty", you MUST choose exactly one value from this \
                fixed list — do not invent or vary the wording: %s

                Respond with ONLY a valid JSON object (no markdown, no code fences, no extra text) \
                in exactly this shape:
                {
                  "urgencyScore": <integer 0-100, where 100 is a life-threatening emergency>,
                  "suggestedSpecialty": "<one value exactly as written from the list above>",
                  "confidence": <float 0.0-1.0>,
                  "recommendation": "<2-3 sentences: what this might suggest, whether to see a doctor soon or it's likely minor, and simple safe home-care advice ONLY if the symptoms appear mild. Never state a definitive diagnosis. Always suggest consulting a doctor for confirmation.>",
                  "riskFactors": ["<short phrase>", "<short phrase>"]
                }

                If any symptom could indicate a medical emergency (e.g. chest pain, difficulty \
                breathing, stroke signs, severe bleeding, loss of consciousness), set urgencyScore \
                to 90 or above, suggestedSpecialty to "Emergency Medicine", and the recommendation \
                must tell the patient to seek emergency care immediately rather than home remedies.
                """.formatted(
                String.join(", ", req.getSymptoms()),
                req.getSeverity() != null ? req.getSeverity() : "not specified",
                req.getNotes() != null && !req.getNotes().isBlank() ? req.getNotes() : "none",
                !vitals.isEmpty() ? vitals.toString() : "not provided",
                specialtyList
        );
    }

    private String callGemini(String prompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModel + ":generateContent?key=" + geminiApiKey;

        JSONObject part = new JSONObject().put("text", prompt);
        JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
        JSONObject body = new JSONObject().put("contents", new JSONArray().put(content));

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API returned status " + response.statusCode() + ": " + response.body());
        }

        JSONObject responseJson = new JSONObject(response.body());
        return responseJson
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text");
    }

    private AiReport parseGeminiResponse(String rawText, SymptomLogRequest req) {
        String cleaned = rawText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }

        JSONObject json = new JSONObject(cleaned);

        AiReport report = new AiReport();
        report.setPatientId(req.getPatientId());
        report.setAppointmentId(req.getAppointmentId());
        report.setModelName(MODEL_NAME);
        report.setModelVersion(MODEL_VERSION);
        report.setUrgencyScore(json.optInt("urgencyScore", 20));

        String specialty = json.optString("suggestedSpecialty", "General Medicine");
        // Safety net: if the model ever drifts from the fixed list, snap to the closest known value.
        if (!PLATFORM_SPECIALTIES.contains(specialty)) {
            log.warn("Gemini returned an unrecognized specialty '{}', defaulting to General Medicine", specialty);
            specialty = "General Medicine";
        }
        report.setSuggestedSpecialty(specialty);

        report.setConfidence(json.optDouble("confidence", 0.5));

        String recommendation = json.optString("recommendation", "Please consult a doctor for further evaluation.");
        report.setRecommendation(recommendation + " This is an AI-generated suggestion and is not a substitute for professional medical advice.");

        List<String> riskFactors = new java.util.ArrayList<>();
        JSONArray riskArray = json.optJSONArray("riskFactors");
        if (riskArray != null) {
            for (int i = 0; i < riskArray.length(); i++) {
                riskFactors.add(riskArray.getString(i));
            }
        }
        report.setRiskFactors(riskFactors);
        report.setCreatedAt(Instant.now());
        return report;
    }

    private AiReport applyEmergencyOverride(AiReport report, SymptomLogRequest req) {
        String combinedText = (String.join(" ", req.getSymptoms()) + " " +
                (req.getNotes() != null ? req.getNotes() : "")).toLowerCase();

        boolean isEmergency = EMERGENCY_KEYWORDS.stream().anyMatch(combinedText::contains);

        if (isEmergency) {
            report.setUrgencyScore(100);
            report.setSuggestedSpecialty("Emergency Medicine");
            report.setRecommendation(
                    "Your symptoms may indicate a medical emergency. Please seek emergency care immediately " +
                            "or call emergency services. This is an AI-generated suggestion and is not a substitute " +
                            "for professional medical advice.");
        }
        return report;
    }

    private AiReport fallbackReport(SymptomLogRequest req, String reason) {
        AiReport report = new AiReport();
        report.setPatientId(req.getPatientId());
        report.setAppointmentId(req.getAppointmentId());
        report.setModelName(MODEL_NAME);
        report.setModelVersion(MODEL_VERSION);
        report.setUrgencyScore(50);
        report.setSuggestedSpecialty("General Medicine");
        report.setConfidence(0.0);
        report.setRecommendation(reason + " Please consult a doctor to discuss your symptoms. " +
                "This is an AI-generated suggestion and is not a substitute for professional medical advice.");
        report.setRiskFactors(List.of());
        report.setCreatedAt(Instant.now());
        return report;
    }

    private AiReportResponse toResponse(AiReport r, List<DoctorResponse> matchedDoctors) {
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
                .matchedDoctors(matchedDoctors)
                .createdAt(r.getCreatedAt())
                .build();
    }
}