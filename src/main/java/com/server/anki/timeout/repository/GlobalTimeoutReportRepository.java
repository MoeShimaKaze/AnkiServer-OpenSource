package com.server.anki.timeout.repository;

import com.server.anki.timeout.model.GlobalTimeoutReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 全局超时报告仓库
 * 提供全局超时报告的数据访问功能
 */
@Repository
public interface GlobalTimeoutReportRepository extends JpaRepository<GlobalTimeoutReport, UUID> {
    /**
     * 根据时间区间查找报告
     * @param start 开始时间
     * @param end 结束时间
     * @return 符合条件的报告列表
     */
    List<GlobalTimeoutReport> findByGeneratedTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 获取最近的N条报告
     * @return 最近的报告列表
     */
    List<GlobalTimeoutReport> findTop30ByOrderByGeneratedTimeDesc();

    /**
     * 删除指定时间之前的报告
     * @param threshold 时间阈值
     */
    @Modifying
    @Query("DELETE FROM GlobalTimeoutReport t WHERE t.generatedTime < ?1")
    void deleteByGeneratedTimeBefore(LocalDateTime threshold);
}