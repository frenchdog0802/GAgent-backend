package com.gagent.repository;

import com.gagent.entity.CiWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CiWorkflowRepository extends JpaRepository<CiWorkflow, Long> {
    List<CiWorkflow> findByUserIdOrderByCreatedAtDesc(Integer userId);

    Optional<CiWorkflow> findByWebhookEventId(Long webhookEventId);
}
