package com.server.anki.mailorder.repository;

import com.server.anki.mailorder.entity.AbandonedOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AbandonedOrderRepository extends JpaRepository<AbandonedOrder, Long> {
    List<AbandonedOrder> findByOrderNumber(UUID orderNumber);
    List<AbandonedOrder> findByRaterId(Long raterId);
    List<AbandonedOrder> findByRatedUserId(Long ratedUserId);
}