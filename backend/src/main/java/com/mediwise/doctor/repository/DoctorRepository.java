package com.mediwise.doctor.repository;

import com.mediwise.doctor.model.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, UUID>,
        JpaSpecificationExecutor<Doctor> {

    Optional<Doctor> findByUserId(UUID userId);

    Page<Doctor> findByAvailableTrueAndVerifiedTrue(Pageable pageable);
    @Query("SELECT d FROM Doctor d WHERE d.verified = true AND (" +
            "LOWER(d.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(d.specialty) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Doctor> search(@Param("search") String search, Pageable pageable);

    boolean existsByUserId(UUID userId);

    long countByVerifiedFalse();
}