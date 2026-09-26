package com.mediwise.review.repository;

import com.mediwise.review.model.DoctorRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DoctorRatingRepository extends JpaRepository<DoctorRating, UUID> {

    boolean existsByAppointmentId(UUID appointmentId);

    Page<DoctorRating> findByDoctorIdOrderByCreatedAtDesc(UUID doctorId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM DoctorRating r WHERE r.doctorId = :doctorId")
    Double averageRatingForDoctor(@Param("doctorId") UUID doctorId);

    long countByDoctorId(UUID doctorId);
}
