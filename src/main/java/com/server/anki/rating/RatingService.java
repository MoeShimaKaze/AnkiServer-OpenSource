package com.server.anki.rating;

import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.repository.PurchaseRequestRepository;
import com.server.anki.shopping.repository.ShoppingOrderRepository;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RatingService {

    private static final Logger logger = LoggerFactory.getLogger(RatingService.class);

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private MailOrderRepository mailOrderRepository;

    @Autowired
    private ShoppingOrderRepository shoppingOrderRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 创建评价 - 统一入口
     * 根据订单类型调用不同的评价创建方法
     */
    @Transactional
    public Rating createRating(Long raterId, UUID orderNumber, String comment, int score,
                               RatingType ratingType, OrderType orderType) {
        logger.info("创建评价, 用户ID: {}, 订单号: {}, 订单类型: {}, 评价类型: {}",
                raterId, orderNumber, orderType, ratingType);

        return switch (orderType) {
            case MAIL_ORDER -> createMailOrderRating(raterId, orderNumber, comment, score, ratingType);
            case SHOPPING_ORDER -> createShoppingOrderRating(raterId, orderNumber, comment, score, ratingType);
            case PURCHASE_REQUEST -> createPurchaseRequestRating(raterId, orderNumber, comment, score, ratingType);
        };
    }


    /**
     * 获取用户发出的评价（分页）
     */
    public Page<Rating> getRaterRatings(Long userId, Pageable pageable) {
        logger.debug("获取用户{}发出的评价, 页码: {}, 每页大小: {}",
                userId, pageable.getPageNumber(), pageable.getPageSize());
        return ratingRepository.findByRaterId(userId, pageable);
    }

    /**
     * 创建快递代拿订单评价
     */
    @Transactional
    public Rating createMailOrderRating(Long raterId, UUID orderNumber, String comment, int score, RatingType ratingType) {
        logger.info("创建快递代拿订单评价, 用户ID: {}, 订单号: {}", raterId, orderNumber);

        User rater = getUserById(raterId);
        MailOrder order = getMailOrderByNumber(orderNumber);

        // 验证订单状态
        if (order.getOrderStatus() != OrderStatus.COMPLETED) {
            throw new IllegalStateException("只有已完成的订单才能评价");
        }

        // 验证评价类型和评价者身份
        validateMailOrderRating(order, rater, ratingType);

        // 创建评价
        Rating rating = new Rating();
        rating.setRater(rater);
        rating.setComment(comment);
        rating.setScore(validateScore(score));
        rating.setRatingDate(LocalDateTime.now());
        rating.setRatingType(ratingType);
        rating.setOrderInfo(orderNumber, OrderType.MAIL_ORDER);

        // 设置被评价者
        setRatedUserForMailOrder(rating, order, ratingType);

        return ratingRepository.save(rating);
    }

    /**
     * 创建商家订单评价
     */
    @Transactional
    public Rating createShoppingOrderRating(Long raterId, UUID orderNumber, String comment, int score, RatingType ratingType) {
        logger.info("创建商家订单评价, 用户ID: {}, 订单号: {}", raterId, orderNumber);

        User rater = getUserById(raterId);
        ShoppingOrder order = getShoppingOrderByNumber(orderNumber);

        // 验证订单状态
        if (order.getOrderStatus() != com.server.anki.mailorder.enums.OrderStatus.COMPLETED) {
            throw new IllegalStateException("只有已完成的订单才能评价");
        }

        // 验证评价类型和评价者身份
        validateShoppingOrderRating(order, rater, ratingType);

        // 创建评价
        Rating rating = new Rating();
        rating.setRater(rater);
        rating.setComment(comment);
        rating.setScore(validateScore(score));
        rating.setRatingDate(LocalDateTime.now());
        rating.setRatingType(ratingType);
        rating.setOrderInfo(orderNumber, OrderType.SHOPPING_ORDER);

        // 设置被评价者
        setRatedUserForShoppingOrder(rating, order, ratingType);

        return ratingRepository.save(rating);
    }

    /**
     * 创建商品代购订单评价
     */
    @Transactional
    public Rating createPurchaseRequestRating(Long raterId, UUID requestNumber, String comment, int score, RatingType ratingType) {
        logger.info("创建商品代购订单评价, 用户ID: {}, 订单号: {}", raterId, requestNumber);

        User rater = getUserById(raterId);
        PurchaseRequest request = getPurchaseRequestByNumber(requestNumber);

        // 验证订单状态
        if (request.getStatus() != com.server.anki.mailorder.enums.OrderStatus.COMPLETED) {
            throw new IllegalStateException("只有已完成的订单才能评价");
        }

        // 验证评价类型和评价者身份
        validatePurchaseRequestRating(request, rater, ratingType);

        // 创建评价
        Rating rating = new Rating();
        rating.setRater(rater);
        rating.setComment(comment);
        rating.setScore(validateScore(score));
        rating.setRatingDate(LocalDateTime.now());
        rating.setRatingType(ratingType);
        rating.setOrderInfo(requestNumber, OrderType.PURCHASE_REQUEST);

        // 设置被评价者
        setRatedUserForPurchaseRequest(rating, request, ratingType);

        return ratingRepository.save(rating);
    }

    /**
     * 兼容旧版API，自动检测订单类型
     */
    @Transactional
    public Rating createRating(Long raterId, UUID orderNumber, String comment, int score, RatingType ratingType) {
        // 尝试检测订单类型
        if (mailOrderRepository.findByOrderNumber(orderNumber).isPresent()) {
            return createRating(raterId, orderNumber, comment, score, ratingType, OrderType.MAIL_ORDER);
        } else if (shoppingOrderRepository.findByOrderNumber(orderNumber).isPresent()) {
            return createRating(raterId, orderNumber, comment, score, ratingType, OrderType.SHOPPING_ORDER);
        } else if (purchaseRequestRepository.findByRequestNumber(orderNumber).isPresent()) {
            return createRating(raterId, orderNumber, comment, score, ratingType, OrderType.PURCHASE_REQUEST);
        } else {
            throw new IllegalArgumentException("未找到订单: " + orderNumber);
        }
    }

    /**
     * 获取指定订单的所有评价
     */
    public List<Rating> getOrderRatings(UUID orderNumber, OrderType orderType) {
        return ratingRepository.findByOrderNumberAndOrderType(orderNumber.toString(), orderType);
    }

    /**
     * 兼容旧版API，自动检测订单类型
     */
    public List<Rating> getOrderRatings(UUID orderNumber) {
        // 尝试检测订单类型
        if (mailOrderRepository.findByOrderNumber(orderNumber).isPresent()) {
            return getOrderRatings(orderNumber, OrderType.MAIL_ORDER);
        } else if (shoppingOrderRepository.findByOrderNumber(orderNumber).isPresent()) {
            return getOrderRatings(orderNumber, OrderType.SHOPPING_ORDER);
        } else if (purchaseRequestRepository.findByRequestNumber(orderNumber).isPresent()) {
            return getOrderRatings(orderNumber, OrderType.PURCHASE_REQUEST);
        } else {
            throw new IllegalArgumentException("未找到订单: " + orderNumber);
        }
    }

    /**
     * 获取用户收到的评价（分页）
     */
    public Page<Rating> getRatedUserRatings(Long userId, Pageable pageable) {
        logger.debug("获取用户{}收到的评价, 页码: {}, 每页大小: {}",
                userId, pageable.getPageNumber(), pageable.getPageSize());
        return ratingRepository.findByRatedUserId(userId, pageable);
    }

    /**
     * 删除评价
     */
    @Transactional
    public void deleteRating(Long ratingId) {
        logger.info("删除评价: {}", ratingId);
        ratingRepository.deleteById(ratingId);
    }

    /**
     * 删除订单相关的所有评价
     */
    @Transactional
    public void deleteOrderRatings(UUID orderNumber, OrderType orderType) {
        logger.info("删除订单相关评价: {}, 类型: {}", orderNumber, orderType);
        ratingRepository.deleteByOrderNumberAndOrderType(orderNumber.toString(), orderType);
    }

    // ===================== 辅助方法 =====================

    /**
     * 验证评分范围 (1-5分)
     */
    private int validateScore(int score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("评分范围必须在1-5分之间");
        }
        return score;
    }

    /**
     * 根据ID获取用户
     */
    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
    }

    /**
     * 根据订单号获取快递代拿订单
     */
    private MailOrder getMailOrderByNumber(UUID orderNumber) {
        return mailOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("快递代拿订单不存在: " + orderNumber));
    }

    /**
     * 根据订单号获取商家订单
     */
    private ShoppingOrder getShoppingOrderByNumber(UUID orderNumber) {
        return shoppingOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("商家订单不存在: " + orderNumber));
    }

    /**
     * 根据订单号获取商品代购订单
     */
    private PurchaseRequest getPurchaseRequestByNumber(UUID requestNumber) {
        return purchaseRequestRepository.findByRequestNumber(requestNumber)
                .orElseThrow(() -> new IllegalArgumentException("商品代购订单不存在: " + requestNumber));
    }

    /**
     * 验证快递代拿订单评价
     */
    private void validateMailOrderRating(MailOrder order, User rater, RatingType ratingType) {
        switch (ratingType) {
            case SENDER_TO_DELIVERER:
                if (!order.getUser().getId().equals(rater.getId())) {
                    throw new IllegalStateException("您不是该订单的寄件人，无法评价快递员");
                }
                if (order.getDeliveryService() == DeliveryService.EXPRESS) {
                    throw new IllegalStateException("快递服务订单不能评价配送员");
                }
                if (order.getAssignedUser() == null) {
                    throw new IllegalStateException("该订单没有分配配送员");
                }
                break;
            case SENDER_TO_PLATFORM:
                if (!order.getUser().getId().equals(rater.getId())) {
                    throw new IllegalStateException("您不是该订单的寄件人，无法评价平台");
                }
                if (order.getDeliveryService() != DeliveryService.EXPRESS) {
                    throw new IllegalStateException("只有快递服务订单才能评价平台");
                }
                break;
            case DELIVERER_TO_SENDER:
                if (order.getAssignedUser() == null || !order.getAssignedUser().getId().equals(rater.getId())) {
                    throw new IllegalStateException("您不是该订单的配送员，无法评价寄件人");
                }
                if (order.getDeliveryService() == DeliveryService.EXPRESS) {
                    throw new IllegalStateException("快递服务订单不能使用此评价类型");
                }
                break;
            default:
                throw new IllegalArgumentException("无效的评价类型: " + ratingType);
        }
    }

    /**
     * 验证商家订单评价
     */
    private void validateShoppingOrderRating(ShoppingOrder order, User rater, RatingType ratingType) {
        switch (ratingType) {
            case CUSTOMER_TO_MERCHANT:
                if (!order.getUser().getId().equals(rater.getId())) {
                    throw new IllegalStateException("您不是该订单的顾客，无法评价商家");
                }
                break;
            case MERCHANT_TO_CUSTOMER:
                if (!order.getStore().getMerchant().getId().equals(rater.getId())) {
                    throw new IllegalStateException("您不是该订单的商家，无法评价顾客");
                }
                break;
            case CUSTOMER_TO_DELIVERER:
                if (!order.getUser().getId().equals(rater.getId())) {
                    throw new IllegalStateException("您不是该订单的顾客，无法评价配送员");
                }
                if (order.getAssignedUser() == null) {
                    throw new IllegalStateException("该订单没有分配配送员");
                }
                break;
            case DELIVERER_TO_CUSTOMER:
                if (order.getAssignedUser() == null || !order.getAssignedUser().getId().equals(rater.getId())) {
                    throw new IllegalStateException("您不是该订单的配送员，无法评价顾客");
                }
                break;
            default:
                throw new IllegalArgumentException("无效的评价类型: " + ratingType);
        }
    }

    /**
     * 验证商品代购订单评价
     */
    private void validatePurchaseRequestRating(PurchaseRequest request, User rater, RatingType ratingType) {
        switch (ratingType) {
            case REQUESTER_TO_FULFILLER:
                if (!request.getUser().getId().equals(rater.getId())) {
                    throw new IllegalStateException("您不是该订单的需求方，无法评价代购方");
                }
                if (request.getAssignedUser() == null) {
                    throw new IllegalStateException("该订单没有分配代购人");
                }
                break;
            case FULFILLER_TO_REQUESTER:
                if (request.getAssignedUser() == null || !request.getAssignedUser().getId().equals(rater.getId())) {
                    throw new IllegalStateException("您不是该订单的代购方，无法评价需求方");
                }
                break;
            default:
                throw new IllegalArgumentException("无效的评价类型: " + ratingType);
        }
    }

    /**
     * 设置快递代拿订单的被评价者
     */
    private void setRatedUserForMailOrder(Rating rating, MailOrder order, RatingType ratingType) {
        switch (ratingType) {
            case SENDER_TO_DELIVERER:
                rating.setRatedUser(order.getAssignedUser());
                break;
            case SENDER_TO_PLATFORM:
                // 平台评价不设置被评价用户
                break;
            case DELIVERER_TO_SENDER:
                rating.setRatedUser(order.getUser());
                break;
        }
    }

    /**
     * 设置商家订单的被评价者
     */
    private void setRatedUserForShoppingOrder(Rating rating, ShoppingOrder order, RatingType ratingType) {
        switch (ratingType) {
            case CUSTOMER_TO_MERCHANT:
                rating.setRatedUser(order.getStore().getMerchant());
                break;
            case MERCHANT_TO_CUSTOMER, DELIVERER_TO_CUSTOMER:
                rating.setRatedUser(order.getUser());
                break;
            case CUSTOMER_TO_DELIVERER:
                rating.setRatedUser(order.getAssignedUser());
                break;
        }
    }

    /**
     * 设置商品代购订单的被评价者
     */
    private void setRatedUserForPurchaseRequest(Rating rating, PurchaseRequest request, RatingType ratingType) {
        switch (ratingType) {
            case REQUESTER_TO_FULFILLER:
                rating.setRatedUser(request.getAssignedUser());
                break;
            case FULFILLER_TO_REQUESTER:
                rating.setRatedUser(request.getUser());
                break;
        }
    }
}