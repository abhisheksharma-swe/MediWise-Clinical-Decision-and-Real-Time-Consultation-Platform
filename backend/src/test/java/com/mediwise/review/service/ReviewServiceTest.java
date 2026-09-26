package com.mediwise.review.service;

import com.mediwise.appointment.model.Appointment;
import com.mediwise.appointment.repository.AppointmentRepository;
import com.mediwise.auth.model.User;
import com.mediwise.common.exception.BusinessException;
import com.mediwise.common.exception.ResourceNotFoundException;
import com.mediwise.doctor.model.Doctor;
import com.mediwise.doctor.repository.DoctorRepository;
import com.mediwise.profile.model.PatientProfile;
import com.mediwise.profile.repository.PatientProfileRepository;
import com.mediwise.review.dto.ReviewResponse;
import com.mediwise.review.dto.SubmitReviewRequest;
import com.mediwise.review.model.DoctorRating;
import com.mediwise.review.repository.DoctorRatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService")
class ReviewServiceTest {

    @Mock private DoctorRatingRepository ratingRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private PatientProfileRepository patientProfileRepository;

    @InjectMocks
    private ReviewService reviewService;

    private UUID appointmentId;
    private UUID patientId;
    private UUID doctorId;
    private User patientUser;
    private PatientProfile patientProfile;

    @BeforeEach
    void setUp() {
        appointmentId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        patientUser = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
        patientProfile = PatientProfile.builder().id(patientId).userId(patientUser.getId()).fullName("Jane Doe").build();
    }

    private SubmitReviewRequest reviewRequest(int rating) {
        SubmitReviewRequest request = new SubmitReviewRequest();
        request.setRating(rating);
        request.setReviewText("Great doctor");
        return request;
    }

    private Appointment completedAppointment() {
        return Appointment.builder().id(appointmentId).patientId(patientId).doctorId(doctorId)
                .status(Appointment.AppointmentStatus.COMPLETED).build();
    }

    @Test
    @DisplayName("submit: a valid review is saved and the doctor's rating is recomputed")
    void submit_success_recomputesDoctorRating() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(completedAppointment()));
        when(patientProfileRepository.findByUserId(patientUser.getId())).thenReturn(Optional.of(patientProfile));
        when(ratingRepository.existsByAppointmentId(appointmentId)).thenReturn(false);
        when(ratingRepository.save(any(DoctorRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ratingRepository.averageRatingForDoctor(doctorId)).thenReturn(4.5);
        when(ratingRepository.countByDoctorId(doctorId)).thenReturn(2L);
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(Doctor.builder().id(doctorId).build()));

        ReviewResponse response = reviewService.submit(appointmentId, patientUser, reviewRequest(5));

        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getPatientName()).isEqualTo("Jane Doe");

        ArgumentCaptor<Doctor> doctorCaptor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository).save(doctorCaptor.capture());
        assertThat(doctorCaptor.getValue().getAvgRating()).isEqualByComparingTo(BigDecimal.valueOf(4.50));
        assertThat(doctorCaptor.getValue().getTotalReviews()).isEqualTo(2);
    }

    @Test
    @DisplayName("submit: rejected when the caller has no patient profile")
    void submit_noPatientProfile_forbidden() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(completedAppointment()));
        when(patientProfileRepository.findByUserId(patientUser.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.submit(appointmentId, patientUser, reviewRequest(5)))
                .isInstanceOf(BusinessException.class);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit: rejected when the appointment belongs to a different patient")
    void submit_notOwnAppointment_forbidden() {
        Appointment appt = Appointment.builder().id(appointmentId).patientId(UUID.randomUUID()).doctorId(doctorId)
                .status(Appointment.AppointmentStatus.COMPLETED).build();
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        when(patientProfileRepository.findByUserId(patientUser.getId())).thenReturn(Optional.of(patientProfile));

        assertThatThrownBy(() -> reviewService.submit(appointmentId, patientUser, reviewRequest(5)))
                .isInstanceOf(BusinessException.class);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit: rejected when the appointment is not yet COMPLETED")
    void submit_notCompleted_rejected() {
        Appointment appt = Appointment.builder().id(appointmentId).patientId(patientId).doctorId(doctorId)
                .status(Appointment.AppointmentStatus.CONFIRMED).build();
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appt));
        when(patientProfileRepository.findByUserId(patientUser.getId())).thenReturn(Optional.of(patientProfile));

        assertThatThrownBy(() -> reviewService.submit(appointmentId, patientUser, reviewRequest(5)))
                .isInstanceOf(BusinessException.class);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit: rejected when the appointment already has a review")
    void submit_alreadyReviewed_rejected() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(completedAppointment()));
        when(patientProfileRepository.findByUserId(patientUser.getId())).thenReturn(Optional.of(patientProfile));
        when(ratingRepository.existsByAppointmentId(appointmentId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.submit(appointmentId, patientUser, reviewRequest(5)))
                .isInstanceOf(BusinessException.class);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit: an unknown appointment throws ResourceNotFoundException")
    void submit_missingAppointment_throws() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.submit(appointmentId, patientUser, reviewRequest(5)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getForDoctor: returns a page of reviews with the reviewer's name resolved")
    void getForDoctor_returnsPage() {
        DoctorRating rating = DoctorRating.builder().id(UUID.randomUUID()).appointmentId(appointmentId)
                .doctorId(doctorId).patientId(patientId).rating((short) 4).build();
        when(ratingRepository.findByDoctorIdOrderByCreatedAtDesc(eq(doctorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rating)));
        when(patientProfileRepository.findById(patientId)).thenReturn(Optional.of(patientProfile));

        Page<ReviewResponse> page = reviewService.getForDoctor(doctorId, 0, 20);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getPatientName()).isEqualTo("Jane Doe");
    }
}
