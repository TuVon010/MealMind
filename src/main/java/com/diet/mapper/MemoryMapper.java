package com.diet.mapper;

import com.diet.model.SessionMemoryRow;
import com.diet.model.SessionMessageRow;
import com.diet.model.UserMemoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 分层会话记忆的 MyBatis Mapper。 */
@Mapper
public interface MemoryMapper {

    SessionMemoryRow findSessionMemory(
            @Param("sessionId") String sessionId,
            @Param("userId") Long userId
    );

    Long findRecentCutoffMessageId(
            @Param("sessionId") String sessionId,
            @Param("userId") Long userId,
            @Param("recentLimit") int recentLimit
    );

    List<SessionMessageRow> listMessagesForSummary(
            @Param("sessionId") String sessionId,
            @Param("userId") Long userId,
            @Param("afterMessageId") long afterMessageId,
            @Param("beforeMessageId") long beforeMessageId,
            @Param("limit") int limit
    );

    int upsertSessionMemory(SessionMemoryRow row);

    int upsertUserMemory(UserMemoryRow row);

    List<UserMemoryRow> listActiveUserMemories(
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    int deactivateUserMemory(
            @Param("memoryId") Long memoryId,
            @Param("userId") Long userId
    );
}
