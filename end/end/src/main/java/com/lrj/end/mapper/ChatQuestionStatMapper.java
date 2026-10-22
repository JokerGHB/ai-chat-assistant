package com.lrj.end.mapper;

import com.lrj.end.dto.TopQuestionView;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 高频提问统计（预聚合表）Mapper。
 * 口径：每落库一条 user 消息就问一次加一（upsert），看板"全部"查询直接按累计次数倒序取 Top N，
 * 不再对 chat_message 做 GROUP BY 全表聚合。7d/30d 时间窗口因计数器无时间维度，仍走实时查询。
 */
@Mapper
public interface ChatQuestionStatMapper {

    /** 问一次加一：指纹已存在则 ask_count+1 并刷新样例文本/时间，不存在则插入新行（并发安全，不会丢计数） */
    @Insert("""
            INSERT INTO chat_question_stat (question_md5, question, user_id, ask_count, last_asked_at)
            VALUES (#{md5}, #{sample}, 0, 1, #{askedAt})
            ON DUPLICATE KEY UPDATE
                ask_count = ask_count + 1,
                question = VALUES(question),
                last_asked_at = VALUES(last_asked_at)
            """)
    int upsertGlobal(@Param("md5") String md5,
                     @Param("sample") String sample,
                     @Param("askedAt") LocalDateTime askedAt);

    /** 全局高频提问 Top N：预聚合表直接倒序，无需 GROUP BY（并列时新问的排前面） */
    @Select("""
            SELECT question AS content, ask_count AS cnt
            FROM chat_question_stat
            WHERE user_id = 0
            ORDER BY ask_count DESC, last_asked_at DESC
            LIMIT #{limit}
            """)
    List<TopQuestionView> findTopGlobal(@Param("limit") int limit);
}
