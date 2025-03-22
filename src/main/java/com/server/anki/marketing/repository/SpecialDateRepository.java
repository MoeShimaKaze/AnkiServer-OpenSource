package com.server.anki.marketing.repository;

import com.server.anki.fee.model.FeeType;
import com.server.anki.marketing.SpecialDateType;
import com.server.anki.marketing.entity.SpecialDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpecialDateRepository extends JpaRepository<SpecialDate, Long> {

    // 查找指定日期的所有有效特殊日期
    @Query("SELECT sd FROM SpecialDate sd WHERE sd.date = :date AND sd.active = true")
    List<SpecialDate> findActiveByDate(LocalDate date);

    // 查找日期范围内的所有特殊日期
    @Query("SELECT sd FROM SpecialDate sd WHERE sd.date BETWEEN :startDate AND :endDate")
    List<SpecialDate> findByDateRange(LocalDate startDate, LocalDate endDate);

    List<SpecialDate> findActiveAndRateEnabledByDateAndFeeType(LocalDate date, FeeType feeType);

    // 查找特定类型的特殊日期
    List<SpecialDate> findByTypeAndActiveTrue(SpecialDateType type);

    // 检查日期是否已存在
    boolean existsByDate(LocalDate date);

    // 查找指定日期的所有启用了费率的特殊日期，按优先级排序
    @Query("SELECT sd FROM SpecialDate sd " +
            "WHERE sd.date = :date " +
            "AND sd.active = true " +
            "AND sd.rateEnabled = true " +
            "ORDER BY sd.priority DESC")
    List<SpecialDate> findActiveAndRateEnabledByDate(LocalDate date);

    // 批量更新特殊日期的费率启用状态
    @Modifying
    @Query("UPDATE SpecialDate sd SET sd.rateEnabled = :enabled " +
            "WHERE sd.id IN :ids")
    int updateRateEnabledStatus(@Param("ids") List<Long> ids,
                                @Param("enabled") boolean enabled);

    Optional<SpecialDate> findByDate(LocalDate date);
    List<SpecialDate> findByDateBetweenAndType(LocalDate startDate, LocalDate endDate, SpecialDateType type);
    boolean existsByDateBetweenAndType(LocalDate startDate, LocalDate endDate, SpecialDateType type);
    // 新增：根据日期和费用类型查找特殊日期
    Optional<SpecialDate> findByDateAndFeeType(LocalDate date, FeeType feeType);


    // 查找指定日期的所有启用了费率的特殊日期，包括通用类型和特定类型，按优先级排序
    @Query("SELECT sd FROM SpecialDate sd " +
            "WHERE sd.date = :date " +
            "AND sd.active = true " +
            "AND sd.rateEnabled = true " +
            "AND (sd.feeType = :feeType OR sd.feeType = com.server.anki.fee.model.FeeType.ALL_ORDERS) " +
            "ORDER BY sd.priority DESC")
    List<SpecialDate> findActiveAndRateEnabledByDateAndFeeTypeIncludingAll(
            @Param("date") LocalDate date,
            @Param("feeType") FeeType feeType);

}
