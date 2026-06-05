package com.gagent.repository;

import com.gagent.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    List<WebhookEvent> findByUserIdOrderByReceivedAtDesc(String userId);
    long countByUserIdAndStatus(String userId, String status);
}
