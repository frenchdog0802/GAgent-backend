package com.gagent.repository;

import com.gagent.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByUserIdOrderByCreatedAtAsc(String userId);
    List<Message> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
