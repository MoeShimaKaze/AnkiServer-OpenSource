package com.server.anki.marketing.repository;

import com.server.anki.fee.model.FeeType;
import com.server.anki.marketing.entity.SpecialTimeRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecialTimeRangeRepository extends JpaRepository<SpecialTimeRange, Long> {

    // 查找所有启用的特殊时段
    List<SpecialTimeRange> findByActiveTrue();

    @Query("select s from SpecialTimeRange s where s.active = true and :hour >= s.startHour and :hour < s.endHour and s.feeType = :feeType")
    List<SpecialTimeRange> findActiveByHourAndFeeType(@Param("hour") int hour, @Param("feeType") FeeType feeType);


    // 查找指定小时内的所有活动特殊时段
    @Query("SELECT str FROM SpecialTimeRange str WHERE str.active = true " +
            "AND :hour >= str.startHour AND :hour < str.endHour")
    List<SpecialTimeRange> findActiveByHour(int hour);

    // 检查时间段是否存在重叠
    @Query("SELECT CASE WHEN COUNT(str) > 0 THEN true ELSE false END FROM SpecialTimeRange str " +
            "WHERE str.active = true " +
            "AND ((str.startHour <= :startHour AND str.endHour > :startHour) " +
            "OR (str.startHour < :endHour AND str.endHour >= :endHour) " +
            "OR (:startHour <= str.startHour AND :endHour >= str.endHour))")
    boolean hasOverlappingTimeRange(int startHour, int endHour);
}
