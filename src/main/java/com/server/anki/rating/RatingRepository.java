package com.server.anki.rating;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    // 按评价对象查询
    Page<Rating> findByRatedUserId(Long ratedUserId, Pageable pageable);

    // 按订单号和类型查询
    List<Rating> findByOrderNumberAndOrderType(String orderNumber, OrderType orderType);

    // 按订单类型查询
    List<Rating> findByOrderType(OrderType orderType);

    // 按评价类型查询
    List<Rating> findByRatingType(RatingType ratingType);

    // 按评价者查询
    List<Rating> findByRaterId(Long raterId);

    // 复合查询
    @Query("SELECT r FROM Rating r WHERE r.orderNumber = :orderNumber AND r.orderType = :orderType AND r.ratingType = :ratingType")
    List<Rating> findByOrderNumberAndOrderTypeAndRatingType(
            @Param("orderNumber") String orderNumber,
            @Param("orderType") OrderType orderType,
            @Param("ratingType") RatingType ratingType);
    // 按评价者查询（分页）
    Page<Rating> findByRaterId(Long raterId, Pageable pageable);
    // 删除特定订单的所有评价
    void deleteByOrderNumberAndOrderType(String orderNumber, OrderType orderType);
}