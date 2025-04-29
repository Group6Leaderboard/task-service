package com.leaderboard.demo.service;

import com.leaderboard.demo.config.AppConfig;
import com.leaderboard.demo.dto.*;
import com.leaderboard.demo.entity.Task;
import com.leaderboard.demo.exception.ResourceNotFoundException;
import com.leaderboard.demo.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskService {
    @Autowired
    private TaskRepository taskRepository;

    private final RestTemplate restTemplate;

    @Autowired
    public TaskService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public UserDto getUserById(UUID userId) {
        String url = AppConfig.USER_SERVICE_BASE_URL + "/api/users/id/" + userId;
        return restTemplate.getForObject(url, UserDto.class);
    }

    public UserDto getUserByEmail(String email) {
        String url = AppConfig.USER_SERVICE_BASE_URL + "/api/users/email/" + email;
        return restTemplate.getForObject(url, UserDto.class);
    }

    public ProjectDto getProjectById(UUID projectId) {
        String url = AppConfig.PROJECT_SERVICE_BASE_URL + "/api/projects/" + projectId;
        return restTemplate.getForObject(url, ProjectDto.class);
    }

    public CollegeDTO getCollegeById(UUID collegeId) {
        String url = AppConfig.USER_SERVICE_BASE_URL + "/api/colleges/" + collegeId;
        return restTemplate.getForObject(url, CollegeDTO.class);
    }

    @Transactional
    public TaskDTO getTaskById(UUID taskId) {
        Task task = taskRepository.findByIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        return convertToDTO(task);
    }

    public List<TaskDTO> getAllTasks() {
        return taskRepository.findByIsDeletedFalse()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public void deleteTask(UUID taskId) {
        Task task = taskRepository.findByIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        task.setDeleted(true);
        taskRepository.save(task);
    }

    public boolean isProjectExists(UUID projectId) {
        String url = AppConfig.PROJECT_SERVICE_BASE_URL + "/api/projects/internal/exists/" + projectId;

        try {
            System.out.println("Checking project exists at URL: " + url);
            ResponseEntity<Boolean> response = restTemplate.getForEntity(url, Boolean.class);
            System.out.println("Response: " + response.getStatusCode() + ", Body: " + response.getBody());
            return response.getStatusCode().is2xxSuccessful() && Boolean.TRUE.equals(response.getBody());
        } catch (Exception e) {
            System.err.println("Error checking if project exists: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    @Transactional
    public TaskDTO createTask(TaskPostDTO taskDTO, UUID userId) {
        Task task = new Task();
        task.setName(taskDTO.getName());
        task.setDescription(taskDTO.getDescription());
        task.setDueDate(taskDTO.getDueDate());
        task.setStatus("Not Submitted");
        task.setDeleted(false);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        task.setAssignedBy(userId);

        if (taskDTO.getAssignedTo() != null) {
            boolean exists = isProjectExists(taskDTO.getAssignedTo());
            if (!exists) {
                throw new IllegalArgumentException("Assigned project does not exist.");
            }

            task.setAssignedTo(taskDTO.getAssignedTo());
        }

        return convertToDTO(taskRepository.save(task));
    }


    @Transactional
    public TaskDTO updateTask(UUID taskId, TaskPutDTO taskDTO) {
        Task task = taskRepository.findByIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (LocalDateTime.now().isAfter(task.getDueDate()) && task.getStatus().equalsIgnoreCase("Not Submitted")){
            task.setStatus("Overdue");
        }

        if (taskDTO.getScore() != null) {
            if (task.getDueDate() != null && task.getDueDate().isAfter(LocalDateTime.now())) {
                task.setScore(taskDTO.getScore());
                task.setStatus("Completed");
            } else {
                throw new RuntimeException("You can only score tasks after the due date has passed.");
            }
        }
        if(taskDTO.getFile() != null) {
            try {
                if(task.getDueDate().isBefore(LocalDateTime.now())){
                    throw new IllegalArgumentException("Task is overdue.cant submit now");
                }

                byte[] fileBytes = taskDTO.getFile().getBytes();
                task.setFile(fileBytes);
                task.setStatus("To be reviewed");
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file content", e);
            }

        }
        task.setUpdatedAt(LocalDateTime.now());



        return convertToDTO(taskRepository.save(task));
    }
    @Transactional
    public List<TaskDTO> getTasksByProjectId(UUID projectId) {
        return taskRepository.findByAssignedToAndIsDeletedFalse(projectId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    public List<TaskDTO> getTasksAssignedToProjects(List<UUID> projectIds) {
        return taskRepository.findByAssignedToInAndIsDeletedFalse(projectIds)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    public List<TaskDTO> getTasksAssignedBy(UUID mentorId) {
        return taskRepository.findByAssignedByAndIsDeletedFalse(mentorId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
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


    private TaskDTO convertToDTO(Task task) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setName(task.getName());
        dto.setDescription(task.getDescription());
        dto.setDueDate(task.getDueDate());
        dto.setScore(task.getScore());
        dto.setStatus(task.getStatus());
        dto.setDeleted(task.isDeleted());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        dto.setFile(task.getFile());
        dto.setAssignedBy(task.getAssignedBy());
        dto.setAssignedTo(task.getAssignedTo());
        return dto;
    }



?}?