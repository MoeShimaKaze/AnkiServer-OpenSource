package com.server.anki.rating;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "rater_id")
    @JsonBackReference
    private User rater;

    @ManyToOne
    @JoinColumn(name = "rated_user_id")
    @JsonBackReference
    private User ratedUser;

    // 订单类型
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    // 订单唯一标识 - 使用String类型存储UUID
    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "rating_date", nullable = false)
    private LocalDateTime ratingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_type", nullable = false)
    private RatingType ratingType;

    // 设置订单号和类型的便捷方法
    public void setOrderInfo(UUID orderNumber, OrderType orderType) {
        this.orderNumber = orderNumber.toString();
        this.orderType = orderType;
    }

    // 获取UUID形式的订单号
    public UUID getOrderNumberAsUUID() {
        return UUID.fromString(this.orderNumber);
    }
}