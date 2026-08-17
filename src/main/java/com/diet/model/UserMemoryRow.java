package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户跨会话长期记忆行。 */
@Data
public class UserMemoryRow {
    private Long id;
    private Long userId;
    private String memoryType;
    private String memoryKey;
    private String memoryValue;
    private String sourceSessionId;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
