package com.server.anki.shopping.service;

import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.MerchantUserRole;
import com.server.anki.shopping.repository.ProductRepository;
import com.server.anki.shopping.repository.ShoppingOrderRepository;
import com.server.anki.shopping.repository.StoreRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DashboardService {
    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

    @Autowired
    private ShoppingOrderRepository orderRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private StoreService storeService;

    /**
     * 获取管理员统计数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAdminStatistics() {
        logger.info("开始获取管理员统计数据");
        Map<String, Object> result = new HashMap<>();

        try {
            // 统计基本数据
            long totalOrders = orderRepository.count();
            logger.debug("获取全平台订单总数: {}", totalOrders);

            List<ShoppingOrder> completedOrders = orderRepository.findByOrderStatus(OrderStatus.COMPLETED);
            logger.debug("获取已完成订单数: {}", completedOrders.size());

            BigDecimal totalSales = calculateTotalSales(completedOrders);
            logger.debug("计算总销售额: {}", totalSales);

            long totalStores = storeRepository.count();
            long totalProducts = productRepository.count();
            logger.debug("获取店铺总数: {}, 商品总数: {}", totalStores, totalProducts);

            // 今日数据
            LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
            List<ShoppingOrder> todayOrders = orderRepository.findByCreatedAtAfter(startOfDay);
            long todayOrderCount = todayOrders.size();
            BigDecimal todaySales = calculateTotalSales(
                    todayOrders.stream()
                            .filter(order -> order.getOrderStatus() == OrderStatus.COMPLETED)
                            .collect(Collectors.toList())
            );
            logger.debug("今日订单数: {}, 今日销售额: {}", todayOrderCount, todaySales);

            // 近七天数据
            logger.debug("开始计算近七天数据");
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            List<ShoppingOrder> recentOrders = orderRepository.findByCreatedAtAfter(sevenDaysAgo);
            Map<String, Object> weeklyStats = calculateWeeklyStats(recentOrders);

            // 热门店铺/商品
            logger.debug("开始获取热门店铺和商品");
            List<Map<String, Object>> topStores = getTopStores();
            List<Map<String, Object>> topProducts = getTopProducts();

            // 添加所有统计数据
            result.put("totalOrders", totalOrders);
            result.put("totalSales", totalSales);
            result.put("totalStores", totalStores);
            result.put("totalProducts", totalProducts);
            result.put("todayOrderCount", todayOrderCount);
            result.put("todaySales", todaySales);
            result.put("weeklyStats", weeklyStats);
            result.put("topStores", topStores);
            result.put("topProducts", topProducts);

            logger.info("管理员统计数据获取完成");
            return result;
        } catch (Exception e) {
            logger.error("获取管理员统计数据失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 获取商家统计数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMerchantStatistics(Long merchantId) {
        logger.info("开始获取商家(ID:{})统计数据", merchantId);
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取商家的所有店铺
            List<Store> merchantStores = storeService.getMerchantStores(merchantId);
            logger.debug("商家(ID:{})拥有{}个店铺", merchantId, merchantStores.size());

            if (merchantStores.isEmpty()) {
                logger.info("商家(ID:{})没有店铺", merchantId);
                result.put("hasStores", false);
                return result;
            }

            result.put("hasStores", true);
            result.put("storeCount", merchantStores.size());

            // 获取店铺ID列表
            List<Long> storeIds = merchantStores.stream().map(Store::getId).collect(Collectors.toList());
            logger.debug("商家(ID:{})的店铺ID列表: {}", merchantId, storeIds);

            // 获取所有订单
            List<ShoppingOrder> allOrders = new ArrayList<>();
            for (Long storeId : storeIds) {
                List<ShoppingOrder> storeOrders = orderRepository.findByStoreId(storeId);
                allOrders.addAll(storeOrders);
                logger.debug("店铺(ID:{})有{}个订单", storeId, storeOrders.size());
            }

            logger.debug("商家(ID:{})总共有{}个订单", merchantId, allOrders.size());

            // 基本统计
            long totalOrders = allOrders.size();
            long pendingOrders = allOrders.stream()
                    .filter(order -> order.getOrderStatus() == OrderStatus.MERCHANT_PENDING)
                    .count();
            logger.debug("商家(ID:{})有{}个待确认订单", merchantId, pendingOrders);

            List<ShoppingOrder> completedOrders = allOrders.stream()
                    .filter(order -> order.getOrderStatus() == OrderStatus.COMPLETED)
                    .collect(Collectors.toList());

            BigDecimal totalSales = calculateTotalSales(completedOrders);
            logger.debug("商家(ID:{})的总销售额: {}", merchantId, totalSales);

            // 今日数据
            LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
            List<ShoppingOrder> todayOrders = allOrders.stream()
                    .filter(order -> order.getCreatedAt().isAfter(startOfDay))
                    .toList();

            long todayOrderCount = todayOrders.size();
            BigDecimal todaySales = calculateTotalSales(
                    todayOrders.stream()
                            .filter(order -> order.getOrderStatus() == OrderStatus.COMPLETED)
                            .collect(Collectors.toList())
            );
            logger.debug("商家(ID:{})今日订单数: {}, 今日销售额: {}", merchantId, todayOrderCount, todaySales);

            // 统计每个店铺的基本数据
            logger.debug("开始获取商家(ID:{})的每个店铺统计数据", merchantId);
            List<Map<String, Object>> storesData = new ArrayList<>();
            for (Store store : merchantStores) {
                Map<String, Object> storeData = getBasicStoreStatistics(store);
                storesData.add(storeData);
            }

            // 汇总所有统计结果
            result.put("totalOrders", totalOrders);
            result.put("pendingOrders", pendingOrders);
            result.put("totalSales", totalSales);
            result.put("todayOrderCount", todayOrderCount);
            result.put("todaySales", todaySales);
            result.put("stores", storesData);

            logger.info("商家(ID:{})统计数据获取完成", merchantId);
            return result;
        } catch (Exception e) {
            logger.error("获取商家(ID:{})统计数据失败: {}", merchantId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 获取店铺统计数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStoreStatistics(Long storeId) {
        logger.info("开始获取店铺(ID:{})统计数据", storeId);
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取店铺基础信息
            Store store = storeRepository.findById(storeId)
                    .orElseThrow(() -> {
                        logger.error("店铺(ID:{})不存在", storeId);
                        return new RuntimeException("店铺不存在");
                    });
            logger.debug("成功获取店铺(ID:{})信息: {}", storeId, store.getStoreName());

            // 获取店铺详细统计数据
            logger.debug("开始获取店铺(ID:{})基础统计数据", storeId);
            Map<String, Object> basicStats = getBasicStoreStatistics(store);
            result.putAll(basicStats);

            // 获取店铺商品数据
            Pageable unpaged = Pageable.unpaged(); // 使用无分页参数获取所有结果
            Page<Product> productsPage = productRepository.findByStore(store, unpaged);
            List<Product> products = productsPage.getContent(); // 从分页结果中提取内容
            result.put("productCount", products.size());
            logger.debug("店铺(ID:{})有{}个商品", storeId, products.size());

            // 近七天订单数据
            logger.debug("开始获取店铺(ID:{})近七天订单数据", storeId);
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            // 添加Pageable参数
            List<ShoppingOrder> recentOrders = orderRepository.findByStoreAndCreatedAtAfter(store, sevenDaysAgo, unpaged).getContent();
            Map<String, Object> weeklyStats = calculateWeeklyStats(recentOrders);
            result.put("weeklyStats", weeklyStats);

            // 热门商品
            logger.debug("开始获取店铺(ID:{})热门商品", storeId);
            List<Map<String, Object>> topProducts = getStoreTopProducts(store);
            result.put("topProducts", topProducts);

            logger.info("店铺(ID:{})统计数据获取完成", storeId);
            return result;
        } catch (Exception e) {
            logger.error("获取店铺(ID:{})统计数据失败: {}", storeId, e.getMessage(), e);
            throw e;
        }
    }


    /**
     * 获取店铺基础统计数据
     */
    private Map<String, Object> getBasicStoreStatistics(Store store) {
        logger.debug("开始获取店铺(ID:{})基础统计数据", store.getId());
        Map<String, Object> storeData = new HashMap<>();

        // 店铺基本信息
        storeData.put("storeId", store.getId());
        storeData.put("storeName", store.getStoreName());
        storeData.put("status", store.getStatus());

        // 使用无分页参数获取所有订单
        Pageable unpaged = Pageable.unpaged();

        // 订单统计
        List<ShoppingOrder> storeOrders = orderRepository.findByStore(store, unpaged).getContent();
        logger.debug("店铺(ID:{})有{}个订单", store.getId(), storeOrders.size());

        long totalOrders = storeOrders.size();
        long pendingOrderCount = storeOrders.stream()
                .filter(order -> order.getOrderStatus() == OrderStatus.MERCHANT_PENDING)
                .count();
        logger.debug("店铺(ID:{})有{}个待确认订单", store.getId(), pendingOrderCount);

        List<ShoppingOrder> completedOrders = storeOrders.stream()
                .filter(order -> order.getOrderStatus() == OrderStatus.COMPLETED)
                .collect(Collectors.toList());

        BigDecimal totalSales = calculateTotalSales(completedOrders);
        logger.debug("店铺(ID:{})的总销售额: {}", store.getId(), totalSales);

        // 添加店铺统计数据
        storeData.put("totalOrders", totalOrders);
        storeData.put("pendingOrders", pendingOrderCount);
        storeData.put("totalSales", totalSales);
        storeData.put("rating", store.getMerchantInfo().getRating());

        // 今日数据
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        List<ShoppingOrder> todayOrders = storeOrders.stream()
                .filter(order -> order.getCreatedAt().isAfter(startOfDay))
                .toList();

        long todayOrderCount = todayOrders.size();

        BigDecimal todaySales = calculateTotalSales(
                todayOrders.stream()
                        .filter(order -> order.getOrderStatus() == OrderStatus.COMPLETED)
                        .collect(Collectors.toList())
        );
        logger.debug("店铺(ID:{})今日订单数: {}, 今日销售额: {}", store.getId(), todayOrderCount, todaySales);

        storeData.put("todayOrderCount", todayOrderCount);
        storeData.put("todaySales", todaySales);

        logger.debug("店铺(ID:{})基础统计数据获取完成", store.getId());
        return storeData;
    }

    /**
     * 计算总销售额
     */
    private BigDecimal calculateTotalSales(List<ShoppingOrder> orders) {
        BigDecimal total = orders.stream()
                .map(ShoppingOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        logger.trace("计算订单总销售额: {} (共{}个订单)", total, orders.size());
        return total;
    }

    /**
     * 计算周统计数据
     */
    private Map<String, Object> calculateWeeklyStats(List<ShoppingOrder> orders) {
        logger.debug("开始计算周统计数据 (共{}个订单)", orders.size());
        Map<String, Object> weeklyStats = new HashMap<>();

        // 按日期分组
        Map<String, List<ShoppingOrder>> ordersByDay = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();

        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = now.minusDays(i).truncatedTo(ChronoUnit.DAYS);
            String dateString = date.toLocalDate().toString();
            ordersByDay.put(dateString, new ArrayList<>());
        }

        // 填充数据
        for (ShoppingOrder order : orders) {
            String orderDate = order.getCreatedAt().toLocalDate().toString();
            if (ordersByDay.containsKey(orderDate)) {
                ordersByDay.get(orderDate).add(order);
            }
        }

        // 计算每天的订单数和销售额
        List<String> dates = new ArrayList<>();
        List<Long> orderCounts = new ArrayList<>();
        List<BigDecimal> salesAmounts = new ArrayList<>();

        for (Map.Entry<String, List<ShoppingOrder>> entry : ordersByDay.entrySet()) {
            dates.add(entry.getKey());
            orderCounts.add((long) entry.getValue().size());

            BigDecimal daySales = entry.getValue().stream()
                    .filter(order -> order.getOrderStatus() == OrderStatus.COMPLETED)
                    .map(ShoppingOrder::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            salesAmounts.add(daySales);
            logger.trace("日期: {}, 订单数: {}, 销售额: {}", entry.getKey(), entry.getValue().size(), daySales);
        }

        weeklyStats.put("dates", dates);
        weeklyStats.put("orderCounts", orderCounts);
        weeklyStats.put("salesAmounts", salesAmounts);

        logger.debug("周统计数据计算完成");
        return weeklyStats;
    }

    /**
     * 获取热门店铺
     */
    private List<Map<String, Object>> getTopStores() {
        logger.debug("开始获取热门店铺 (TOP{})", 5);
        List<Store> allStores = storeRepository.findAll();
        Pageable unpaged = Pageable.unpaged();

        // 计算每个店铺的订单数和销售额
        Map<Store, Long> storeOrderCounts = new HashMap<>();
        Map<Store, BigDecimal> storeSalesAmounts = new HashMap<>();

        for (Store store : allStores) {
            List<ShoppingOrder> storeOrders = orderRepository.findByStore(store, unpaged).getContent();
            storeOrderCounts.put(store, (long) storeOrders.size());

            BigDecimal sales = storeOrders.stream()
                    .filter(order -> order.getOrderStatus() == OrderStatus.COMPLETED)
                    .map(ShoppingOrder::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            storeSalesAmounts.put(store, sales);
            logger.trace("店铺(ID:{}) {}: 订单数: {}, 销售额: {}",
                    store.getId(), store.getStoreName(), storeOrders.size(), sales);
        }

        // 按销售额排序
        List<Store> topStoresBySales = allStores.stream()
                .sorted((s1, s2) -> storeSalesAmounts.get(s2).compareTo(storeSalesAmounts.get(s1)))
                .limit(5)
                .toList();

        // 构建返回结果
        List<Map<String, Object>> result = new ArrayList<>();

        for (Store store : topStoresBySales) {
            Map<String, Object> storeData = new HashMap<>();
            storeData.put("storeId", store.getId());
            storeData.put("storeName", store.getStoreName());
            storeData.put("merchantName", store.getMerchant().getUsername());
            storeData.put("orderCount", storeOrderCounts.get(store));
            storeData.put("salesAmount", storeSalesAmounts.get(store));
            storeData.put("rating", store.getMerchantInfo().getRating());

            result.add(storeData);
        }

        logger.debug("热门店铺获取完成，共{}家", result.size());
        return result;
    }

    /**
     * 获取热门商品
     */
    private List<Map<String, Object>> getTopProducts() {
        logger.debug("开始获取热门商品 (TOP{})", 5);
        Pageable unpaged = Pageable.unpaged(); // 使用无分页参数获取所有结果
        Page<Product> productPage = productRepository.findAll(unpaged);
        List<Product> allProducts = productPage.getContent(); // 从分页结果中提取内容

        // 计算每个商品的销售情况
        Map<Product, Long> productOrderCounts = new HashMap<>();
        Map<Product, BigDecimal> productSalesAmounts = new HashMap<>();

        for (Product product : allProducts) {
            Page<ShoppingOrder> productOrdersPage = orderRepository.findByProduct(product, unpaged);
            List<ShoppingOrder> productOrders = productOrdersPage.getContent();
            productOrderCounts.put(product, (long) productOrders.size());

            BigDecimal sales = productOrders.stream()
                    .filter(order -> order.getOrderStatus() == OrderStatus.COMPLETED)
                    .map(order -> order.getProductPrice().multiply(new BigDecimal(order.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            productSalesAmounts.put(product, sales);
            logger.trace("商品(ID:{}) {}: 订单数: {}, 销售额: {}",
                    product.getId(), product.getName(), productOrders.size(), sales);
        }

        // 按销售额排序
        List<Product> topProductsBySales = allProducts.stream()
                .sorted((p1, p2) -> productSalesAmounts.get(p2).compareTo(productSalesAmounts.get(p1)))
                .limit(5)
                .toList();

        // 构建返回结果
        List<Map<String, Object>> result = new ArrayList<>();

        for (Product product : topProductsBySales) {
            Map<String, Object> productData = new HashMap<>();
            productData.put("productId", product.getId());
            productData.put("productName", product.getName());
            productData.put("storeName", product.getStore().getStoreName());
            productData.put("price", product.getPrice());
            productData.put("orderCount", productOrderCounts.get(product));
            productData.put("salesAmount", productSalesAmounts.get(product));

            result.add(productData);
        }

        logger.debug("热门商品获取完成，共{}个", result.size());
        return result;
    }

    /**
     * 获取店铺热门商品
     */
    private List<Map<String, Object>> getStoreTopProducts(Store store) {
        logger.debug("开始获取店铺(ID:{})热门商品 (TOP{})", store.getId(), 5);
        Pageable unpaged = Pageable.unpaged(); // 使用无分页参数获取所有结果
        Page<Product> storeProductsPage = productRepository.findByStore(store, unpaged);
        List<Product> storeProducts = storeProductsPage.getContent(); // 从分页结果中提取内容

        // 计算每个商品的销售情况
        Map<Product, Long> productOrderCounts = new HashMap<>();
        Map<Product, BigDecimal> productSalesAmounts = new HashMap<>();

        for (Product product : storeProducts) {
            Page<ShoppingOrder> productOrdersPage = orderRepository.findByProduct(product, unpaged);
            List<ShoppingOrder> productOrders = productOrdersPage.getContent();
            productOrderCounts.put(product, (long) productOrders.size());

            BigDecimal sales = productOrders.stream()
                    .filter(order -> order.getOrderStatus() == OrderStatus.COMPLETED)
                    .map(order -> order.getProductPrice().multiply(new BigDecimal(order.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            productSalesAmounts.put(product, sales);
            logger.trace("店铺(ID:{})商品(ID:{}) {}: 订单数: {}, 销售额: {}",
                    store.getId(), product.getId(), product.getName(), productOrders.size(), sales);
        }

        // 按销售额排序
        List<Product> topProducts = storeProducts.stream()
                .sorted((p1, p2) -> productSalesAmounts.get(p2).compareTo(productSalesAmounts.get(p1)))
                .limit(5)
                .toList();

        // 构建返回结果
        List<Map<String, Object>> result = new ArrayList<>();

        for (Product product : topProducts) {
            Map<String, Object> productData = new HashMap<>();
            productData.put("productId", product.getId());
            productData.put("productName", product.getName());
            productData.put("price", product.getPrice());
            productData.put("orderCount", productOrderCounts.get(product));
            productData.put("salesAmount", productSalesAmounts.get(product));

            result.add(productData);
        }

        logger.debug("店铺(ID:{})热门商品获取完成，共{}个", store.getId(), result.size());
        return result;
    }

    /**
     * 检查用户是否有权限访问店铺数据
     */
    public boolean hasStoreAccess(Long userId, Long storeId) {
        logger.debug("检查用户(ID:{})是否有权限访问店铺(ID:{})", userId, storeId);
        try {
            Store store = storeRepository.findById(storeId)
                    .orElseThrow(() -> {
                        logger.error("店铺(ID:{})不存在", storeId);
                        return new RuntimeException("店铺不存在");
                    });

            if (store.getMerchant().getId().equals(userId)) {
                logger.debug("用户(ID:{})是店铺(ID:{})的拥有者，访问权限校验通过", userId, storeId);
                return true;
            }

            // 检查用户是否是店铺所属商家的员工
            boolean hasRole = merchantService.isUserHasRequiredRole(
                    userId,
                    store.getMerchantInfo().getMerchantUid(),
                    MerchantUserRole.VIEWER
            );

            if (hasRole) {
                logger.debug("用户(ID:{})是店铺(ID:{})的员工，访问权限校验通过", userId, storeId);
            } else {
                logger.debug("用户(ID:{})无权访问店铺(ID:{})", userId, storeId);
            }

            return hasRole;
        } catch (Exception e) {
            logger.error("检查用户(ID:{})访问店铺(ID:{})权限时出错: {}", userId, storeId, e.getMessage(), e);
            return false;
        }
    }
}