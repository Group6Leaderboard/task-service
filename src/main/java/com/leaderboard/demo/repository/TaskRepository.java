package com.leaderboard.demo.repository;

import com.leaderboard.demo.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByIsDeletedFalse();
    Optional<Task> findByIdAndIsDeletedFalse(UUID id);

    List<Task> findByAssignedToAndIsDeletedFalse(UUID assignedToId);

    List<Task> findByAssignedToAndAssignedByAndIsDeletedFalse(UUID projectId, UUID mentorId);

    @Query("SELECT COALESCE(SUM(t.score), 0) FROM Task t WHERE t.assignedTo = :projectId AND t.isDeleted = false")
    int sumScoresByProjectId(@Param("projectId") UUID projectId);

    boolean existsByNameAndAssignedToAndIsDeletedFalse(String name, UUID projectId);

    List<Task> findByAssignedByAndIsDeletedFalse(UUID assignedBy);


    int countByAssignedToIn(List<UUID> projectIds);

    int countByAssignedToInAndIsDeletedFalse(List<UUID> projectIds);

    int countByAssignedToAndIsDeletedFalse(UUID projectId);


    List<Task> findByAssignedToInAndIsDeletedFalse(List<UUID> assignedTo);

    List<Task> findByStatusAndDueDateBeforeAndIsDeletedFalse(String status, LocalDateTime dueDate);

}
