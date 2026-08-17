package com.diet.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 单个会话的滚动摘要及其压缩进度。 */
@Data
public class SessionMemoryRow {
    private String sessionId;
    private String summary;
    private Long summarizedMessageId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
