package com.leaderboard.demo.service;

import com.leaderboard.demo.config.AppConfig;
import com.leaderboard.demo.entity.Task;
import com.leaderboard.demo.exception.ResourceNotFoundException;
import com.leaderboard.demo.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    private final RestTemplate restTemplate;

    @Autowired
    public TaskService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Task getTaskById(UUID taskId) {
        return taskRepository.findByIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    public List<Task> getAllTasks() {
        return taskRepository.findByIsDeletedFalse();
    }

    @Transactional
    public Task createTask(String name, String description, LocalDateTime dueDate,
                           UUID assignedBy, UUID assignedTo) {

        Task task = new Task();
        task.setName(name);
        task.setDescription(description);
        task.setDueDate(dueDate);
        task.setStatus("Not Submitted");
        task.setDeleted(false);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setAssignedBy(assignedBy);
        task.setAssignedTo(assignedTo);

        return taskRepository.save(task);
    }

    @Transactional
    public Task updateTask(UUID taskId, Integer score, MultipartFile file) {
        Task task = getTaskById(taskId);

        if (score != null) {
            task.setScore(score);
            task.setStatus("Completed");
        }

        if (file != null && !file.isEmpty()) {
            try {
                task.setFile(file.getBytes());
                task.setStatus("To be reviewed");
            } catch (IOException e) {
                throw new RuntimeException("Failed to process file", e);
            }
        }

        task.setUpdatedAt(LocalDateTime.now());
        return taskRepository.save(task);
    }

    @Transactional
    public void deleteTask(UUID taskId) {
        Task task = getTaskById(taskId);
        task.setDeleted(true);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    public List<Task> getTasksByProjectId(UUID projectId) {
        return taskRepository.findByAssignedToAndIsDeletedFalse(projectId);
    }

    public List<Task> getTasksAssignedToProjects(List<UUID> projectIds) {
        return taskRepository.findByAssignedToInAndIsDeletedFalse(projectIds);
    }

    public List<Task> getTasksAssignedBy(UUID mentorId) {
        return taskRepository.findByAssignedByAndIsDeletedFalse(mentorId);
    }

    @Transactional
    public int markOverdueTasks() {
        List<Task> overdueTasks = taskRepository
                .findByStatusAndDueDateBeforeAndIsDeletedFalse("Not Submitted", LocalDateTime.now());

        for (Task task : overdueTasks) {
            task.setStatus("Overdue");
            task.setUpdatedAt(LocalDateTime.now());
        }

        taskRepository.saveAll(overdueTasks);
        return overdueTasks.size();
    }

    // --- External service access (if needed) ---
    public boolean isValidUser(UUID userId) {
        try {
            return restTemplate.getForObject(AppConfig.USER_SERVICE_BASE_URL + "/api/users/validate/" + userId, Boolean.class);
        } catch (Exception e) {
            return false;
        }
    }
}
