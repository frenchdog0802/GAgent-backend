package com.gagent.repository;

import com.gagent.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByUserIdOrderByTimestampDesc(String userId);

    @Query("SELECT a FROM ActivityLog a WHERE a.userId = :userId AND a.timestamp >= :start AND a.timestamp < :end ORDER BY a.timestamp ASC")
    List<ActivityLog> findByUserIdAndTimestampRange(
            @Param("userId") String userId,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
