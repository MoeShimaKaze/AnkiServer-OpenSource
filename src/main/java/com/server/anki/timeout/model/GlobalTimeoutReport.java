package com.server.anki.timeout.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 全局超时报告实体
 * 存储所有类型订单的超时统计报告
 */
@Entity
@Table(name = "global_timeout_report")
public class GlobalTimeoutReport {
    private static final Logger logger = LoggerFactory.getLogger(GlobalTimeoutReport.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Setter
    @Getter
    @Id
    private UUID reportId;

    @Getter
    @Setter
    @Column(name = "generated_time", nullable = false)
    private LocalDateTime generatedTime;

    @Setter
    @Getter
    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Setter
    @Getter
    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "system_stats", columnDefinition = "json")
    private String systemStats;

    @Column(name = "top_timeout_users", columnDefinition = "json")
    private String topTimeoutUsers;

    @Getter
    @ElementCollection
    @CollectionTable(
            name = "global_timeout_report_recommendations",
            joinColumns = @JoinColumn(name = "report_id"),
            foreignKey = @ForeignKey(name = "fk_global_timeout_report_recommendations")
    )
    @Column(name = "recommendation")
    private List<String> recommendations = new ArrayList<>();

    // 默认构造函数
    public GlobalTimeoutReport() {
        this.reportId = UUID.randomUUID();
        this.generatedTime = LocalDateTime.now();
    }

    // 带参数的构造函数
    public GlobalTimeoutReport(LocalDateTime startTime, LocalDateTime endTime) {
        this();
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public SystemTimeoutStatistics getSystemStats() {
        try {
            return systemStats != null ?
                    objectMapper.readValue(systemStats, SystemTimeoutStatistics.class) : null;
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing system stats: {}", e.getMessage());
            return null;
        }
    }

    public void setSystemStats(SystemTimeoutStatistics stats) {
        try {
            this.systemStats = stats != null ? objectMapper.writeValueAsString(stats) : null;
        } catch (JsonProcessingException e) {
            logger.error("Error serializing system stats: {}", e.getMessage());
            this.systemStats = null;
        }
    }

    public List<UserTimeoutStatistics> getTopTimeoutUsers() {
        try {
            return topTimeoutUsers != null ?
                    objectMapper.readValue(topTimeoutUsers,
                            new TypeReference<>() {
                            }) :
                    new ArrayList<>();
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing top timeout users: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public void setTopTimeoutUsers(List<UserTimeoutStatistics> users) {
        try {
            this.topTimeoutUsers = users != null ? objectMapper.writeValueAsString(users) : null;
        } catch (JsonProcessingException e) {
            logger.error("Error serializing top timeout users: {}", e.getMessage());
            this.topTimeoutUsers = null;
        }
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations != null ? recommendations : new ArrayList<>();
    }

    // 工具方法
    public void addRecommendation(String recommendation) {
        if (recommendation != null && !recommendation.trim().isEmpty()) {
            this.recommendations.add(recommendation);
        }
    }

    public void addRecommendations(List<String> newRecommendations) {
        if (newRecommendations != null) {
            newRecommendations.stream()
                    .filter(r -> r != null && !r.trim().isEmpty())
                    .forEach(this.recommendations::add);
        }
    }

    public void clearRecommendations() {
        this.recommendations.clear();
    }

    // Object 方法重写
    @Override
    public String toString() {
        return "GlobalTimeoutReport{" +
                "reportId=" + reportId +
                ", generatedTime=" + generatedTime +
                ", period=" + startTime + " to " + endTime +
                ", recommendationsCount=" + recommendations.size() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GlobalTimeoutReport that)) return false;
        return reportId != null && reportId.equals(that.reportId);
    }

    @Override
    public int hashCode() {
        return reportId != null ? reportId.hashCode() : 0;
    }

    // 构建器
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private SystemTimeoutStatistics systemStats;
        private List<UserTimeoutStatistics> topTimeoutUsers = new ArrayList<>();
        private List<String> recommendations = new ArrayList<>();

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder systemStats(SystemTimeoutStatistics stats) {
            this.systemStats = stats;
            return this;
        }

        public Builder topTimeoutUsers(List<UserTimeoutStatistics> users) {
            this.topTimeoutUsers = users;
            return this;
        }

        public Builder recommendations(List<String> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public Builder addRecommendation(String recommendation) {
            if (recommendation != null && !recommendation.trim().isEmpty()) {
                this.recommendations.add(recommendation);
            }
            return this;
        }

        public GlobalTimeoutReport build() {
            GlobalTimeoutReport report = new GlobalTimeoutReport(startTime, endTime);
            report.setSystemStats(systemStats);
            report.setTopTimeoutUsers(topTimeoutUsers);
            report.setRecommendations(recommendations);
            return report;
        }
    }
}