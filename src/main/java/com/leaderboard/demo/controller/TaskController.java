package com.leaderboard.demo.controller;

import com.leaderboard.demo.config.AppConfig;
import com.leaderboard.demo.dto.*;
import com.leaderboard.demo.exception.ResourceNotFoundException;
import com.leaderboard.demo.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    public ResponseEntity<ApiResponse<TaskDTO>> createTask(@RequestBody TaskPostDTO taskPostDTO) {
        try {
            UUID loggedInUserId = getLoggedInUserId();

            TaskDTO taskDTO = taskService.createTask(taskPostDTO, loggedInUserId);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Task created successfully", taskDTO));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, e.getMessage(), null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }


    private UUID getLoggedInUserId() {
        String loggedInUserEmail = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        String url = UriComponentsBuilder.fromHttpUrl(USER_SERVICE_URL)
                .pathSegment(loggedInUserEmail)
                .toUriString();

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> userMap = response.getBody();
                Object idObj = userMap.get("id");

                if (idObj != null) {
                    return UUID.fromString(idObj.toString());
                }
            }

            throw new RuntimeException("Logged in user not found in user-service");
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user details from user-service: " + e.getMessage());
        }
    }



    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<TaskDTO>> updateTask(@PathVariable UUID id,
                                                           @RequestParam(value = "dueDate", required = false) LocalDateTime dueDate,
                                                           @RequestParam(value = "score", required = false) Integer score,
                                                           @RequestParam(value = "file", required = false) MultipartFile file)
    {
        try {
            if (dueDate != null && dueDate.isBefore(LocalDateTime.now())) {
                return ApiResponse.badRequest("Due date cannot be in the past.");
            }
            TaskPutDTO taskPutDTO = new TaskPutDTO();
            taskPutDTO.setDuedate(dueDate);
            taskPutDTO.setScore(score);
            taskPutDTO.setFile(file);
            TaskDTO taskDTO = taskService.updateTask(id, taskPutDTO);
            return ApiResponse.success(taskDTO, "Task updated successfully");
        } catch (RuntimeException e) {
            return ApiResponse.badRequest(e.getMessage());
        }

    }
    @PutMapping("/check-overdue")
    public ResponseEntity<ApiResponse<String>> updateOverdueTasks() {
        try {
            int updatedCount = taskService.markOverdueTasks();
            return ApiResponse.success("Updated " , "Tasks updated successfully");
        } catch (Exception e) {
            return ApiResponse.internalServerError("Error while updating overdue tasks");
        }
    }
    @GetMapping("/{id}")

    public ResponseEntity<ApiResponse<TaskDTO>> getTaskById(@PathVariable UUID id) {
        TaskDTO taskDTO = taskService.getTaskById(id);
        if (taskDTO == null) {
            return ApiResponse.notFound("Task not found");
        }
        return ApiResponse.success(taskDTO, "Task retrieved successfully");
    }


    @GetMapping
    @PreAuthorize("hasRole('MENTOR') or hasRole('STUDENT')")
    @Transactional
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getAllTasks() {
        String loggedInUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        // Call user-service to get user details
        String userUrl = AppConfig.USER_SERVICE_BASE_URL + "/api/users/email/" + loggedInUserEmail;
        Map userMap = restTemplate.getForObject(userUrl, Map.class);
        if (userMap == null || userMap.get("id") == null || userMap.get("role_id") == null) {
            return ApiResponse.notFound("User not found or invalid response from user-service");
        }

        UUID userId = UUID.fromString(userMap.get("id").toString());
        UUID roleId = UUID.fromString(userMap.get("role_id").toString());


        String roleUrl = AppConfig.USER_SERVICE_BASE_URL + "/api/roles/" + roleId;
        Map roleMap = restTemplate.getForObject(roleUrl, Map.class);
        if (roleMap == null || roleMap.get("name") == null) {
            return ApiResponse.badRequest("Role not found for user");
        }

        String roleName = roleMap.get("name").toString().toUpperCase();
        List<TaskDTO> tasks;

        if ("MENTOR".equals(roleName)) {
            tasks = taskService.getTasksAssignedBy(userId);
        } else if ("STUDENT".equals(roleName)) {
            String token = getTokenFromRequest();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            String studentProjectsUrl = AppConfig.PROJECT_SERVICE_BASE_URL + "/api/student-projects/projects/" + userId;
            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                    studentProjectsUrl,
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> responseMap = responseEntity.getBody();
            if (responseMap == null || !responseMap.containsKey("response")) {
                return ApiResponse.success(Collections.emptyList(), "No projects found for student");
            }

// Cast the "response" field to a list
            List<Map<String, Object>> projectList = (List<Map<String, Object>>) responseMap.get("response");

            List<UUID> projectIds = projectList.stream()
                    .map(project -> UUID.fromString(project.get("id").toString()))
                    .collect(Collectors.toList());

            tasks = taskService.getTasksAssignedToProjects(projectIds);
        } else {
            tasks = Collections.emptyList();
        }

        return ApiResponse.success(tasks, "Tasks retrieved successfully");
    }





    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MENTOR') or hasRole('COLLEGE') or hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getTasksByProjectId(@PathVariable UUID projectId) {
        List<TaskDTO> tasks = taskService.getTasksByProjectId(projectId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Tasks fetched successfully", tasks));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletTask(@PathVariable UUID id){
        try{
            taskService.deleteTask(id);
            return ApiResponse.noContent("Task deleted Succesfully");
        }catch (RuntimeException e){
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @Autowired
    private HttpServletRequest request;

    public String getTokenFromRequest() {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7); // Remove "Bearer "
        }
        return null;
    }
}