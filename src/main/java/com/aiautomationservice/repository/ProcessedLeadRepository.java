package com.aiautomationservice.repository;

import com.aiautomationservice.entity.ProcessedLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedLeadRepository extends JpaRepository<ProcessedLead, Long> {

    boolean existsByPhone(String phone);
}