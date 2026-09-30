package com.mediwise.prescription.repository;

import com.mediwise.prescription.model.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, UUID> {

    List<PrescriptionItem> findByPrescriptionIdOrderBySortOrderAsc(UUID prescriptionId);

    List<PrescriptionItem> findByPrescriptionIdInOrderBySortOrderAsc(List<UUID> prescriptionIds);
}
