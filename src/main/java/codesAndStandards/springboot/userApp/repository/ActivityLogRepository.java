package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.ActivityLog;
import codesAndStandards.springboot.userApp.entity.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // Get all logs ordered by newest first
    @Query("SELECT al FROM ActivityLog al LEFT JOIN FETCH al.user ORDER BY al.timestamp DESC")
    List<ActivityLog> findAllByOrderByTimestampDesc();

    // Get logs by user
    List<ActivityLog> findByUserOrderByTimestampDesc(User user);

    // Count today's logs
    // Count today's logs - FIXED QUERY
    @Query("SELECT COUNT(al) FROM ActivityLog al WHERE al.timestamp >= :startOfDay AND al.timestamp < :endOfDay")
    Long countTodayLogs(LocalDateTime startOfDay, LocalDateTime endOfDay);

    @Modifying
    @Transactional
    @Query("DELETE FROM ActivityLog a WHERE a.timestamp < :cutoffDate")
    int deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT COUNT(a) FROM ActivityLog a WHERE a.action NOT LIKE '%_FAILED'")
    Long countSuccessLogs();

    // Count failed logs (any action ending with '_FAILED')
    @Query("SELECT COUNT(a) FROM ActivityLog a WHERE a.action LIKE '%_FAILED'")
    Long countFailedLogs();

}