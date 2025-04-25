package com.leaderboard.demo.controller;

import com.leaderboard.demo.config.AppConfig;
import com.leaderboard.demo.entity.Task;
import com.leaderboard.demo.service.TaskService;
import com.leaderboard.demo.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private RestTemplate restTemplate;

    private static final String USER_SERVICE_URL = AppConfig.USER_SERVICE_BASE_URL + "/api/users/email";

    @PostMapping
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<ApiResponse<Task>> createTask(
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam LocalDateTime dueDate,
            @RequestParam UUID assignedTo
    ) {
        try {
            UUID assignedBy = getLoggedInUserId();
            Task task = taskService.createTask(name, description, dueDate, assignedBy, assignedTo);
            return ApiResponse.created(task, "Task created successfully");
        } catch (Exception e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Task>> updateTask(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer score,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) LocalDateTime dueDate
    ) {
        try {
            if (dueDate != null && dueDate.isBefore(LocalDateTime.now())) {
                return ApiResponse.badRequest("Due date cannot be in the past.");
            }

            Task task = taskService.updateTask(id, score, file);
            return ApiResponse.success(task, "Task updated successfully");
        } catch (Exception e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PutMapping("/check-overdue")
    public ResponseEntity<ApiResponse<String>> updateOverdueTasks() {
        try {
            int updatedCount = taskService.markOverdueTasks();
            return ApiResponse.success("Updated " + updatedCount + " tasks", "Tasks updated successfully");
        } catch (Exception e) {
            return ApiResponse.internalServerError("Error while updating overdue tasks");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Task>> getTaskById(@PathVariable UUID id) {
        try {
            Task task = taskService.getTaskById(id);
            return ApiResponse.success(task, "Task retrieved successfully");
        } catch (Exception e) {
            return ApiResponse.notFound("Task not found");
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('MENTOR') or hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<Task>>> getAllTasks() {
        try {
            String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            String userUrl = AppConfig.USER_SERVICE_BASE_URL + "/api/users/email/" + userEmail;
            UserDto userDto = restTemplate.getForObject(userUrl, UserDto.class);

            if (userDto == null) return ApiResponse.notFound("User not found");

            String roleUrl = AppConfig.USER_SERVICE_BASE_URL + "/api/roles/" + userDto.getRole_id();
            Roledto roleDto = restTemplate.getForObject(roleUrl, Roledto.class);

            if (roleDto == null) return ApiResponse.badRequest("Role not found");

            List<Task> tasks;

            if ("MENTOR".equalsIgnoreCase(roleDto.getName())) {
                tasks = taskService.getTasksAssignedBy(userDto.getId());
            } else if ("STUDENT".equalsIgnoreCase(roleDto.getName())) {
                String projectUrl = AppConfig.PROJECT_SERVICE_BASE_URL + "/api/projects/student/" + userDto.getId();
                ProjectDto[] projects = restTemplate.getForObject(projectUrl, ProjectDto[].class);

                if (projects == null || projects.length == 0) {
                    return ApiResponse.success(Collections.emptyList(), "No tasks for student");
                }

                List<UUID> projectIds = Arrays.stream(projects).map(ProjectDto::getId).toList();
                tasks = taskService.getTasksAssignedToProjects(projectIds);
            } else {
                tasks = Collections.emptyList();
            }

            return ApiResponse.success(tasks, "Tasks retrieved successfully");
        } catch (Exception e) {
            return ApiResponse.internalServerError("Error while retrieving tasks");
        }
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MENTOR') or hasRole('COLLEGE') or hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<Task>>> getTasksByProjectId(@PathVariable UUID projectId) {
        List<Task> tasks = taskService.getTasksByProjectId(projectId);
        return ApiResponse.success(tasks, "Tasks fetched successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable UUID id) {
        try {
            taskService.deleteTask(id);
            return ApiResponse.noContent("Task deleted successfully");
        } catch (Exception e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    private UUID getLoggedInUserId() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String url = UriComponentsBuilder.fromHttpUrl(USER_SERVICE_URL)
                .pathSegment(email)
                .toUriString();

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> userMap = response.getBody();

            if (userMap == null || !userMap.containsKey("id")) {
                throw new RuntimeException("User not found");
            }

            return UUID.fromString(userMap.get("id").toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user from user-service: " + e.getMessage());
        }
    }

}
