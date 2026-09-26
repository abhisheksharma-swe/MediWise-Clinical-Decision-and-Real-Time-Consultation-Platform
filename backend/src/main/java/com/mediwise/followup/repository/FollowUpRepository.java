package com.mediwise.followup.repository;

import com.mediwise.followup.model.FollowUp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FollowUpRepository extends JpaRepository<FollowUp, UUID> {

    List<FollowUp> findByPatientIdOrderByRecommendedDateAsc(UUID patientId);

    List<FollowUp> findByPatientIdAndStatusOrderByRecommendedDateAsc(UUID patientId, FollowUp.Status status);
}
