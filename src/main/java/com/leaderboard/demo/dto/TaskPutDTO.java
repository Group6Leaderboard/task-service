package com.leaderboard.demo.dto;

import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

public class TaskPutDTO {
    private LocalDateTime duedate;
    private Integer score;
    private MultipartFile file;

    public LocalDateTime getDuedate() {
        return duedate;
    }

    public void setDuedate(LocalDateTime duedate) {
        this.duedate = duedate;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
