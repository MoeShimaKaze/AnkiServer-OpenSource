package com.server.anki.utils;

import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.shopping.entity.*;
import com.server.anki.shopping.enums.*;
import com.server.anki.shopping.repository.*;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import com.server.anki.user.enums.UserIdentity;
import com.server.anki.user.enums.UserVerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 超时统计系统测试数据生成器
 * 用于生成测试数据以验证超时统计模块的功能
 * 修改版：增加了更多的非超时订单和更丰富的订单状态
 */
@SuppressWarnings("ALL")
@Component
public class TimeoutStatisticsDataGenerator {
    private static final Logger logger = LoggerFactory.getLogger(TimeoutStatisticsDataGenerator.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MailOrderRepository mailOrderRepository;

    @Autowired
    private ShoppingOrderRepository shoppingOrderRepository;

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    private final Random random = new Random();

    // 用于生成地址的城市和详细地址
    private final List<String> cities = Arrays.asList("北京市", "上海市", "广州市", "深圳市", "杭州市", "成都市", "武汉市", "西安市");
    private final List<String> districts = Arrays.asList("海淀区", "朝阳区", "浦东新区", "天河区", "南山区", "西湖区", "武侯区", "洪山区", "雁塔区");
    private final List<String> streets = Arrays.asList("中关村大街", "望京街道", "张江高科", "天河路", "科技园路", "文三路", "锦江大道", "珞喻路", "高新路");
    private final List<String> details = Arrays.asList("101号", "202号", "303号", "404号", "505号", "606号", "707号", "808号", "909号");

    // 用于生成商品名称的前缀和后缀
    private final List<String> productPrefixes = Arrays.asList("精品", "新款", "热销", "限量版", "超值", "特惠", "经典", "高端", "入门级");
    private final List<String> productSuffixes = Arrays.asList("手机", "电脑", "平板", "相机", "耳机", "手表", "鞋子", "衣服", "背包", "书籍", "零食", "饮料");

    // 用于生成订单名称
    private final List<String> orderNamePrefixes = Arrays.asList("顺丰快递", "圆通快递", "申通快递", "韵达快递", "中通快递", "百世快递", "京东快递", "邮政快递");

    // 用于生成代购订单标题
    private final List<String> purchaseTitles = Arrays.asList(
            "帮我购买一台笔记本电脑", "需要购买新款手机", "帮忙买一些日用品", "代购零食饮料", "代买教材课本",
            "帮忙购买化妆品", "代买药品", "购买运动器材", "代购生日礼物", "帮忙买衣服"
    );

    // 用于生成取消订单的原因
    private final List<String> cancelReasons = Arrays.asList(
            "临时有事无法等待", "找到其他配送方式", "自己去取了", "商品已售罄", "价格变动不想购买",
            "买错了商品", "商家服务态度不好", "重复下单", "配送时间太长", "临时不需要了"
    );

    /**
     * 生成测试数据的入口方法
     */
    @Transactional
    public void generateTestData() {
        logger.info("开始生成超时统计系统测试数据...");

        try {
            // 1. 生成用户数据
            List<User> normalUsers = generateNormalUsers(30);
            List<User> deliveryUsers = generateDeliveryUsers(20);
            List<User> allUsers = new ArrayList<>(normalUsers);
            allUsers.addAll(deliveryUsers);
            logger.info("已生成 {} 个普通用户和 {} 个配送用户", normalUsers.size(), deliveryUsers.size());

            // 2. 生成商家数据
            List<MerchantInfo> merchants = generateMerchants(15, normalUsers);
            logger.info("已生成 {} 个商家信息", merchants.size());

            // 3. 生成店铺数据
            List<Store> stores = generateStores(20, merchants);
            logger.info("已生成 {} 个店铺", stores.size());

            // 4. 生成商品数据
            List<Product> products = generateProducts(100, stores);
            logger.info("已生成 {} 个商品", products.size());

            // 5. 生成MailOrder数据（快递代拿订单）
            List<MailOrder> mailOrders = generateMailOrders(300, normalUsers, deliveryUsers);
            logger.info("已生成 {} 个快递代拿订单", mailOrders.size());

            // 6. 生成ShoppingOrder数据（商家订单）
            List<ShoppingOrder> shoppingOrders = generateShoppingOrders(350, normalUsers, deliveryUsers, stores, products);
            logger.info("已生成 {} 个商家订单", shoppingOrders.size());

            // 7. 生成PurchaseRequest数据（代购订单）
            List<PurchaseRequest> purchaseRequests = generatePurchaseRequests(300, normalUsers, deliveryUsers);
            logger.info("已生成 {} 个代购订单", purchaseRequests.size());

            logger.info("测试数据生成完成，共生成 {} 条订单数据", mailOrders.size() + shoppingOrders.size() + purchaseRequests.size());
        } catch (Exception e) {
            logger.error("生成测试数据时发生错误", e);
            throw new RuntimeException("生成测试数据失败", e);
        }
    }

    /**
     * 生成普通用户
     */
    private List<User> generateNormalUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setUsername("user" + (i + 1));
            user.setEmail("user" + (i + 1) + "@example.com");
            user.setEncryptedPassword("password123");
            user.setRegistrationDate(LocalDate.now().minusDays(random.nextInt(365)));
            user.setUserGroup("user");
            user.setBirthday(LocalDate.now().minusYears(18 + random.nextInt(30)));
            user.setGender(random.nextBoolean() ? "男" : "女");
            user.setUserIdentity(UserIdentity.VERIFIED_USER);
            user.setUserVerificationStatus(UserVerificationStatus.VERIFIED);
            user.setCanLogin(true);
            user.setSystemAccount(false);

            // 设置个人信息（JSON字段）
            Map<String, Object> personalInfo = new HashMap<>();
            personalInfo.put("realName", "真实姓名" + (i + 1));
            personalInfo.put("idNumber", "1234567890" + String.format("%02d", i + 1));
            personalInfo.put("phone", "1380000" + String.format("%04d", i + 1));
            personalInfo.put("address", generateRandomAddress());
            user.setPersonalInfo(personalInfo);

            users.add(userRepository.save(user));
        }
        return users;
    }

    /**
     * 生成配送用户
     */
    private List<User> generateDeliveryUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setUsername("delivery" + (i + 1));
            user.setEmail("delivery" + (i + 1) + "@example.com");
            user.setEncryptedPassword("password123");
            user.setRegistrationDate(LocalDate.now().minusDays(random.nextInt(365)));
            user.setUserGroup("delivery");
            user.setBirthday(LocalDate.now().minusYears(18 + random.nextInt(30)));
            user.setGender(random.nextBoolean() ? "男" : "女");
            user.setUserIdentity(UserIdentity.VERIFIED_USER);
            user.setUserVerificationStatus(UserVerificationStatus.VERIFIED);
            user.setCanLogin(true);
            user.setSystemAccount(false);

            // 设置个人信息（JSON字段）
            Map<String, Object> personalInfo = new HashMap<>();
            personalInfo.put("realName", "配送员" + (i + 1));
            personalInfo.put("idNumber", "9876543210" + String.format("%02d", i + 1));
            personalInfo.put("phone", "1390000" + String.format("%04d", i + 1));
            personalInfo.put("address", generateRandomAddress());
            user.setPersonalInfo(personalInfo);

            users.add(userRepository.save(user));
        }
        return users;
    }

    /**
     * 生成商家信息
     */
    private List<MerchantInfo> generateMerchants(int count, List<User> users) {
        List<MerchantInfo> merchants = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int userIndex = random.nextInt(users.size());
            User merchantUser = users.get(userIndex);

            MerchantInfo merchantInfo = new MerchantInfo();
            merchantInfo.setMerchantUid("M" + System.currentTimeMillis() + String.format("%04d", i));
            merchantInfo.setPrimaryUser(merchantUser);
            merchantInfo.setBusinessLicense("BL" + (1000000 + i));
            merchantInfo.setLicenseImage("license_image_" + i + ".jpg");
            merchantInfo.setContactName(merchantUser.getUsername());
            merchantInfo.setContactPhone("1350000" + String.format("%04d", i + 1));
            merchantInfo.setBusinessAddress(generateRandomAddress());
            merchantInfo.setMerchantLevel(getRandomMerchantLevel());
            merchantInfo.setTotalSales(random.nextInt(1000));
            merchantInfo.setRating(3.5 + random.nextDouble() * 1.5); // 3.5 - 5.0
            merchantInfo.setVerificationStatus("APPROVED");

            // 创建商家-用户映射
            MerchantUserMapping mapping = new MerchantUserMapping();
            mapping.setMerchantInfo(merchantInfo);
            mapping.setUser(merchantUser);
            mapping.setRole(MerchantUserRole.OWNER);
            mapping.setInvitationAccepted(true);

            // 添加映射
            List<MerchantUserMapping> mappings = new ArrayList<>();
            mappings.add(mapping);
            merchantInfo.setUserMappings(mappings);

            merchants.add(merchantRepository.save(merchantInfo));
        }
        return merchants;
    }

    /**
     * 生成店铺数据
     */
    private List<Store> generateStores(int count, List<MerchantInfo> merchants) {
        List<Store> stores = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MerchantInfo merchant = merchants.get(i % merchants.size());

            Store store = new Store();
            store.setStoreName("店铺" + (i + 1));
            store.setDescription("这是一家提供各种商品的店铺，店铺编号：" + (i + 1));
            store.setMerchant(merchant.getPrimaryUser());
            store.setMerchantInfo(merchant);
            store.setStatus(StoreStatus.ACTIVE);
            store.setContactPhone("1370000" + String.format("%04d", i + 1));
            store.setBusinessHours("09:00-22:00");

            // 生成随机位置
            String location = generateRandomAddress();
            store.setLocation(location);
            double[] coordinates = generateRandomCoordinates();
            store.setLatitude(coordinates[0]);
            store.setLongitude(coordinates[1]);

            stores.add(storeRepository.save(store));
        }
        return stores;
    }

    /**
     * 生成商品数据
     */
    private List<Product> generateProducts(int count, List<Store> stores) {
        List<Product> products = new ArrayList<>();

        // 生成唯一标识符，使用时间戳来确保唯一性
        long timestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            try {
                Store store = stores.get(i % stores.size());

                Product product = new Product();
                product.setStore(store);
                product.setName(generateRandomProductName());
                product.setDescription("这是一个高质量的商品，编号：" + (i + 1));

                // 设置价格
                double basePrice = 10 + random.nextInt(990); // 10-999元
                product.setPrice(BigDecimal.valueOf(basePrice).setScale(2, RoundingMode.HALF_UP));
                product.setMarketPrice(BigDecimal.valueOf(basePrice * 1.2).setScale(2, RoundingMode.HALF_UP));
                product.setCostPrice(BigDecimal.valueOf(basePrice * 0.7).setScale(2, RoundingMode.HALF_UP));

                // 设置库存和其他信息
                product.setStock(10 + random.nextInt(91)); // 10-100
                product.setWeight(0.1 + random.nextDouble() * 9.9); // 0.1-10.0kg

                // 设置尺寸
                if (random.nextBoolean()) {
                    product.setLength(5.0 + random.nextDouble() * 45.0); // 5-50cm
                    product.setWidth(5.0 + random.nextDouble() * 45.0);  // 5-50cm
                    product.setHeight(2.0 + random.nextDouble() * 28.0); // 2-30cm
                }

                product.setStatus(ProductStatus.ON_SALE);
                product.setCategory(getRandomProductCategory());
                product.setImageUrl("product_image_" + (i + 1) + ".jpg");

                // 修改SKU生成逻辑，使用时间戳和随机数确保唯一性
                String uniqueSku = "SKU" + timestamp + "_" + i + "_" + random.nextInt(1000);
                product.setSkuCode(uniqueSku);

                // 同样修改条形码生成逻辑
                product.setBarcode("BAR" + timestamp + "_" + (1000000 + i));

                // 设置特殊属性
                product.setIsLargeItem(random.nextInt(10) == 0); // 10%的概率是大件
                product.setNeedsPackaging(random.nextBoolean());
                product.setIsFragile(random.nextInt(5) == 0); // 20%的概率是易碎品

                // 销售统计
                product.setSalesCount(random.nextInt(1000));
                product.setViewCount(product.getSalesCount() * (2 + random.nextInt(8))); // 浏览量是销量的2-10倍
                product.setRating(3.0 + random.nextDouble() * 2.0); // 3.0-5.0

                products.add(productRepository.save(product));
                logger.debug("成功创建商品: {}, SKU: {}", product.getName(), product.getSkuCode());

            } catch (Exception e) {
                // 添加单个商品失败时的异常处理，避免整批数据生成失败
                logger.warn("创建第{}个商品时出现异常: {}", i, e.getMessage());
                // 继续循环创建下一个商品
            }
        }
        return products;
    }

    /**
     * 生成快递代拿订单(MailOrder)
     * 修改版：增加了更多的订单状态和非超时订单
     */
    private List<MailOrder> generateMailOrders(int count, List<User> customers, List<User> deliveryUsers) {
        List<MailOrder> mailOrders = new ArrayList<>();

        // 创建统计分布：
        // 60%已完成订单
        // 10%已取消订单
        // 30%活跃订单（其中20%是超时订单）
        int completedCount = (int)(count * 0.6);
        int cancelledCount = (int)(count * 0.1);
        int activeCount = count - completedCount - cancelledCount;
        int timeoutCount = (int)(activeCount * 0.2); // 活跃订单中20%是超时订单
        int normalActiveCount = activeCount - timeoutCount;

        // 1. 生成已完成订单
        for (int i = 0; i < completedCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = getRandomUser(deliveryUsers);

            MailOrder order = new MailOrder();
            order.setOrderNumber(UUID.randomUUID());
            order.setUser(customer);
            order.setName(generateRandomOrderName());

            // 根据索引设置不同的创建时间，使订单分布在过去的0-60天
            LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(60));
            order.setCreatedAt(createdAt);

            // 设置地址信息
            double[] pickupCoordinates = generateRandomCoordinates();
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setPickupAddress(generateRandomAddress());
            order.setPickupLatitude(pickupCoordinates[0]);
            order.setPickupLongitude(pickupCoordinates[1]);
            order.setDeliveryAddress(generateRandomAddress());
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置收件信息
            order.setPickupCode(generateRandomCode());
            order.setContactInfo(customer.getUsername() + ": " + "1380000" + String.format("%04d", i + 1));

            // 设置物流信息
            order.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            order.setLargeItem(random.nextInt(10) == 0); // 10%的概率是大件

            // 设置配送服务和距离
            order.setDeliveryService(random.nextBoolean() ? DeliveryService.STANDARD : DeliveryService.EXPRESS);
            order.setDeliveryDistance(1.0 + random.nextDouble() * 9.0); // 1-10km

            // 设置配送员和状态
            order.setAssignedUser(deliveryUser);
            order.setOrderStatus(OrderStatus.COMPLETED);

            // 设置各种时间
            LocalDateTime deliveryTime = createdAt.plusMinutes(30 + random.nextInt(90)); // 30-120分钟后送达
            order.setDeliveryTime(deliveryTime);
            order.setDeliveredDate(deliveryTime.plusMinutes(random.nextInt(30))); // 0-30分钟后完成
            order.setCompletionDate(order.getDeliveredDate().plusMinutes(random.nextInt(60))); // 0-60分钟后确认完成

            // 设置费用信息
            order.setFee(10.0 + random.nextDouble() * 20.0); // 10-30元
            order.setUserIncome(order.getFee() * 0.7); // 配送员获得70%
            order.setPlatformIncome(order.getFee() * 0.3); // 平台获得30%

            // 设置超时状态
            order.setTimeoutStatus(TimeoutStatus.NORMAL);
            order.setTimeoutWarningSent(false);
            order.setTimeoutCount(0);

            mailOrders.add(mailOrderRepository.save(order));
        }

        // 2. 生成取消的订单
        for (int i = 0; i < cancelledCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = random.nextBoolean() ? getRandomUser(deliveryUsers) : null;

            MailOrder order = new MailOrder();
            order.setOrderNumber(UUID.randomUUID());
            order.setUser(customer);
            order.setName(generateRandomOrderName());

            // 设置创建时间在过去1-30天
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1 + random.nextInt(30));
            order.setCreatedAt(createdAt);

            // 设置地址信息
            double[] pickupCoordinates = generateRandomCoordinates();
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setPickupAddress(generateRandomAddress());
            order.setPickupLatitude(pickupCoordinates[0]);
            order.setPickupLongitude(pickupCoordinates[1]);
            order.setDeliveryAddress(generateRandomAddress());
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置收件信息
            order.setPickupCode(generateRandomCode());
            order.setContactInfo(customer.getUsername() + ": " + "1380000" + String.format("%04d", completedCount + i + 1));

            // 设置物流信息
            order.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            order.setLargeItem(random.nextInt(10) == 0); // 10%的概率是大件

            // 设置配送服务和距离
            order.setDeliveryService(random.nextBoolean() ? DeliveryService.STANDARD : DeliveryService.EXPRESS);
            order.setDeliveryDistance(1.0 + random.nextDouble() * 9.0); // 1-10km

            // 如果随机分配了配送员，则设置
            order.setAssignedUser(deliveryUser);

            // 设置状态为已取消
            order.setOrderStatus(OrderStatus.CANCELLED);

            // 设置取消时间（创建后的0-24小时内）
            LocalDateTime cancelTime = createdAt.plusHours(random.nextInt(24));

            // 设置锁定原因为取消原因
            order.setLockReason(getRandomCancelReason());

            // 设置费用信息 - 取消订单没有收益
            order.setFee(10.0 + random.nextDouble() * 20.0); // 10-30元
            order.setUserIncome(0.0);
            order.setPlatformIncome(0.0);

            // 设置超时状态
            order.setTimeoutStatus(TimeoutStatus.NORMAL);
            order.setTimeoutWarningSent(false);
            order.setTimeoutCount(0);

            mailOrders.add(mailOrderRepository.save(order));
        }

        // 3. 生成正常活跃订单（非超时）
        for (int i = 0; i < normalActiveCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = random.nextInt(10) < 7 ? getRandomUser(deliveryUsers) : null; // 70%的概率已分配配送员

            MailOrder order = new MailOrder();
            order.setOrderNumber(UUID.randomUUID());
            order.setUser(customer);
            order.setName(generateRandomOrderName());

            // 设置较最近的创建时间
            LocalDateTime createdAt = LocalDateTime.now().minusHours(random.nextInt(48)); // 过去48小时内
            order.setCreatedAt(createdAt);

            // 设置地址信息
            double[] pickupCoordinates = generateRandomCoordinates();
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setPickupAddress(generateRandomAddress());
            order.setPickupLatitude(pickupCoordinates[0]);
            order.setPickupLongitude(pickupCoordinates[1]);
            order.setDeliveryAddress(generateRandomAddress());
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置收件信息
            order.setPickupCode(generateRandomCode());
            order.setContactInfo(customer.getUsername() + ": " + "1380000" + String.format("%04d", completedCount + cancelledCount + i + 1));

            // 设置物流信息
            order.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            order.setLargeItem(random.nextInt(10) == 0); // 10%的概率是大件

            // 设置配送服务和距离
            order.setDeliveryService(random.nextBoolean() ? DeliveryService.STANDARD : DeliveryService.EXPRESS);
            order.setDeliveryDistance(1.0 + random.nextDouble() * 9.0); // 1-10km

            // 设置配送员
            order.setAssignedUser(deliveryUser);

            // 随机设置不同的活跃状态
            OrderStatus status;
            if (deliveryUser == null) {
                // 未分配配送员时，状态只能是PENDING
                status = OrderStatus.PENDING;
            } else {
                // 已分配配送员，随机设置状态
                OrderStatus[] activeStatuses = {OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED};
                status = activeStatuses[random.nextInt(activeStatuses.length)];
            }
            order.setOrderStatus(status);

            // 根据状态设置相应的时间
            LocalDateTime deliveryTime = createdAt.plusMinutes(30 + random.nextInt(90)); // 30-120分钟后送达
            order.setDeliveryTime(deliveryTime);

            if (status == OrderStatus.IN_TRANSIT || status == OrderStatus.DELIVERED) {
                // 设置取件时间但不设置送达时间（IN_TRANSIT）
            }

            if (status == OrderStatus.DELIVERED) {
                // 设置送达时间
                order.setDeliveredDate(deliveryTime.minusMinutes(random.nextInt(30))); // 预计时间前0-30分钟送达
            }

            // 设置费用信息
            order.setFee(10.0 + random.nextDouble() * 20.0); // 10-30元
            if (deliveryUser != null) {
                order.setUserIncome(order.getFee() * 0.7); // 配送员获得70%
                order.setPlatformIncome(order.getFee() * 0.3); // 平台获得30%
            } else {
                order.setUserIncome(0.0);
                order.setPlatformIncome(0.0);
            }

            // 设置超时状态
            order.setTimeoutStatus(TimeoutStatus.NORMAL);
            order.setTimeoutWarningSent(false);
            order.setTimeoutCount(0);

            mailOrders.add(mailOrderRepository.save(order));
        }

        // 4. 生成超时订单
        for (int i = 0; i < timeoutCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = getRandomUser(deliveryUsers);

            MailOrder order = new MailOrder();
            order.setOrderNumber(UUID.randomUUID());
            order.setUser(customer);
            order.setName(generateRandomOrderName());

            // 设置较早的创建时间，确保超时
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1 + random.nextInt(3)); // 1-3天前
            order.setCreatedAt(createdAt);

            // 设置地址信息
            double[] pickupCoordinates = generateRandomCoordinates();
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setPickupAddress(generateRandomAddress());
            order.setPickupLatitude(pickupCoordinates[0]);
            order.setPickupLongitude(pickupCoordinates[1]);
            order.setDeliveryAddress(generateRandomAddress());
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置收件信息
            order.setPickupCode(generateRandomCode());
            order.setContactInfo(customer.getUsername() + ": " + "1380000" + String.format("%04d", completedCount + cancelledCount + normalActiveCount + i + 1));

            // 设置物流信息
            order.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            order.setLargeItem(random.nextInt(10) == 0); // 10%的概率是大件

            // 设置配送服务和距离
            order.setDeliveryService(random.nextBoolean() ? DeliveryService.STANDARD : DeliveryService.EXPRESS);
            order.setDeliveryDistance(1.0 + random.nextDouble() * 9.0); // 1-10km

            // 设置配送员
            order.setAssignedUser(deliveryUser);

            // 设置状态和时间
            if (i % 2 == 0) {
                // 设置为平台介入状态
                order.setOrderStatus(OrderStatus.PLATFORM_INTERVENTION);
                order.setInterventionTime(LocalDateTime.now().minusHours(random.nextInt(24)));

                // 设置较早的送达时间，确保超时
                LocalDateTime deliveryTime = createdAt.plusMinutes(30);
                order.setDeliveryTime(deliveryTime);
            } else {
                // 设置为其他活跃状态但已超时
                OrderStatus[] activeStatuses = {OrderStatus.PENDING, OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT};
                order.setOrderStatus(activeStatuses[random.nextInt(activeStatuses.length)]);

                // 设置很早的送达时间，确保超时
                LocalDateTime deliveryTime = createdAt.plusMinutes(30);
                order.setDeliveryTime(deliveryTime);
            }

            // 设置费用信息
            order.setFee(10.0 + random.nextDouble() * 20.0); // 10-30元
            order.setUserIncome(order.getFee() * 0.7); // 配送员获得70%
            order.setPlatformIncome(order.getFee() * 0.3); // 平台获得30%

            // 设置超时状态
            TimeoutStatus[] timeoutStatuses = {
                    TimeoutStatus.PICKUP_TIMEOUT, TimeoutStatus.DELIVERY_TIMEOUT,
                    TimeoutStatus.PICKUP_TIMEOUT_WARNING, TimeoutStatus.DELIVERY_TIMEOUT_WARNING
            };
            order.setTimeoutStatus(timeoutStatuses[random.nextInt(timeoutStatuses.length)]);
            order.setTimeoutWarningSent(true);
            order.setTimeoutCount(1 + random.nextInt(3)); // 1-3次超时

            mailOrders.add(mailOrderRepository.save(order));
        }

        return mailOrders;
    }

    /**
     * 生成商家订单(ShoppingOrder)
     * 修改版：增加了更多的订单状态和非超时订单
     */
    private List<ShoppingOrder> generateShoppingOrders(int count, List<User> customers, List<User> deliveryUsers, List<Store> stores, List<Product> products) {
        List<ShoppingOrder> shoppingOrders = new ArrayList<>();

        // 创建统计分布：
        // 55%已完成订单
        // 15%已取消订单
        // 10%支付待处理订单
        // 20%活跃订单（其中25%是超时订单）
        int completedCount = (int)(count * 0.55);
        int cancelledCount = (int)(count * 0.15);
        int paymentPendingCount = (int)(count * 0.10);
        int activeCount = count - completedCount - cancelledCount - paymentPendingCount;
        int timeoutCount = (int)(activeCount * 0.25); // 活跃订单中25%是超时订单
        int normalActiveCount = activeCount - timeoutCount;

        // 1. 生成已完成订单
        for (int i = 0; i < completedCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = getRandomUser(deliveryUsers);
            Store store = getRandomItem(stores);
            Product product = getRandomProductFromStore(products, store);

            ShoppingOrder order = new ShoppingOrder();
            order.setOrderNumber(UUID.randomUUID());
            order.setUser(customer);
            order.setStore(store);
            order.setProduct(product);

            // 设置购买信息
            int quantity = 1 + random.nextInt(5); // 1-5件
            order.setQuantity(quantity);
            order.setProductPrice(product.getPrice());

            // 根据索引设置不同的创建时间，使订单分布在过去的0-90天
            LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(90));
            order.setCreatedAt(createdAt);

            // 设置收件信息
            order.setRecipientName(customer.getUsername());
            order.setRecipientPhone("1380000" + String.format("%04d", i + 1));

            // 设置配送地址
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setDeliveryAddress(generateRandomAddress());
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送类型和距离
            order.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);
            order.setDeliveryDistance(0.5 + random.nextDouble() * 5.5); // 0.5-6km

            // 设置配送员和状态
            order.setAssignedUser(deliveryUser);
            order.setOrderStatus(OrderStatus.COMPLETED);

            // 设置各种时间
            LocalDateTime paymentTime = createdAt.plusMinutes(random.nextInt(30)); // 0-30分钟后支付
            order.setPaymentTime(paymentTime);

            LocalDateTime expectedDeliveryTime = paymentTime.plusMinutes(30 + random.nextInt(90)); // 30-120分钟后送达
            order.setExpectedDeliveryTime(expectedDeliveryTime);

            LocalDateTime deliveredTime = expectedDeliveryTime.plusMinutes(random.nextInt(60) - 30); // 提前或延后30分钟
            order.setDeliveredTime(deliveredTime);

            // 设置费用信息
            BigDecimal productTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            BigDecimal deliveryFee = BigDecimal.valueOf(5 + random.nextInt(10)).setScale(2, RoundingMode.HALF_UP); // 5-15元
            BigDecimal serviceFee = deliveryFee.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP); // 10%服务费
            BigDecimal platformFee = deliveryFee.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP); // 5%平台费
            BigDecimal totalAmount = productTotal.add(deliveryFee).add(serviceFee);
            BigDecimal merchantIncome = productTotal.subtract(platformFee);

            order.setDeliveryFee(deliveryFee);
            order.setServiceFee(serviceFee);
            order.setPlatformFee(platformFee);
            order.setTotalAmount(totalAmount);
            order.setMerchantIncome(merchantIncome);

            // 设置超时状态
            order.setTimeoutStatus(TimeoutStatus.NORMAL);
            order.setTimeoutWarningSent(false);
            order.setTimeoutCount(0);

            // 设置备注
            if (random.nextBoolean()) {
                order.setRemark("请尽快配送，谢谢！");
            }

            shoppingOrders.add(shoppingOrderRepository.save(order));
        }

        // 2. 生成已取消订单
        for (int i = 0; i < cancelledCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = random.nextBoolean() ? getRandomUser(deliveryUsers) : null;
            Store store = getRandomItem(stores);
            Product product = getRandomProductFromStore(products, store);

            ShoppingOrder order = new ShoppingOrder();
            order.setOrderNumber(UUID.randomUUID());
            order.setUser(customer);
            order.setStore(store);
            order.setProduct(product);

            // 设置购买信息
            int quantity = 1 + random.nextInt(5); // 1-5件
            order.setQuantity(quantity);
            order.setProductPrice(product.getPrice());

            // 设置创建时间在过去1-30天
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1 + random.nextInt(30));
            order.setCreatedAt(createdAt);

            // 设置收件信息
            order.setRecipientName(customer.getUsername());
            order.setRecipientPhone("1380000" + String.format("%04d", completedCount + i + 1));

            // 设置配送地址
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setDeliveryAddress(generateRandomAddress());
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送类型和距离
            order.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);
            order.setDeliveryDistance(0.5 + random.nextDouble() * 5.5); // 0.5-6km

            // 设置状态为已取消
            order.setOrderStatus(OrderStatus.CANCELLED);

            // 随机设置支付时间（50%概率未支付取消，50%概率已支付取消）
            if (random.nextBoolean()) {
                LocalDateTime paymentTime = createdAt.plusMinutes(random.nextInt(30)); // 0-30分钟后支付
                order.setPaymentTime(paymentTime);

                // 已支付的设置退款状态
                order.setRefundStatus("REFUNDED");
                order.setRefundReason(getRandomCancelReason());
                order.setRefundTime(createdAt.plusHours(random.nextInt(24) + 1)); // 1-24小时后退款

                // 计算退款金额
                BigDecimal productTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
                order.setRefundAmount(productTotal); // 全额退款商品金额
            } else {
                // 未支付取消不设置退款信息
                order.setRefundStatus("NOT_REQUIRED");
                order.setRefundReason(getRandomCancelReason());
            }

            // 费用信息
            BigDecimal productTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            BigDecimal deliveryFee = BigDecimal.valueOf(5 + random.nextInt(10)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal serviceFee = deliveryFee.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal platformFee = deliveryFee.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalAmount = productTotal.add(deliveryFee).add(serviceFee);

            order.setDeliveryFee(deliveryFee);
            order.setServiceFee(serviceFee);
            order.setPlatformFee(platformFee);
            order.setTotalAmount(totalAmount);
            order.setMerchantIncome(BigDecimal.ZERO); // 已取消订单商家无收入

            // 设置超时状态
            order.setTimeoutStatus(TimeoutStatus.NORMAL);
            order.setTimeoutWarningSent(false);
            order.setTimeoutCount(0);

            shoppingOrders.add(shoppingOrderRepository.save(order));
        }

        // 3. 生成支付待处理订单
        for (int i = 0; i < paymentPendingCount; i++) {
            User customer = getRandomUser(customers);
            Store store = getRandomItem(stores);
            Product product = getRandomProductFromStore(products, store);

            ShoppingOrder order = new ShoppingOrder();
            order.setOrderNumber(UUID.randomUUID());
            order.setUser(customer);
            order.setStore(store);
            order.setProduct(product);

            // 设置购买信息
            int quantity = 1 + random.nextInt(5); // 1-5件
            order.setQuantity(quantity);
            order.setProductPrice(product.getPrice());

            // 设置最近的创建时间
            LocalDateTime createdAt = LocalDateTime.now().minusHours(random.nextInt(48)); // 过去48小时内
            order.setCreatedAt(createdAt);

            // 设置收件信息
            order.setRecipientName(customer.getUsername());
            order.setRecipientPhone("1380000" + String.format("%04d", completedCount + cancelledCount + i + 1));

            // 设置配送地址
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setDeliveryAddress(generateRandomAddress());
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送类型和距离
            order.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);
            order.setDeliveryDistance(0.5 + random.nextDouble() * 5.5); // 0.5-6km

            // 设置状态为支付待处理
            order.setOrderStatus(OrderStatus.PAYMENT_PENDING);

            // 费用信息
            BigDecimal productTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            BigDecimal deliveryFee = BigDecimal.valueOf(5 + random.nextInt(10)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal serviceFee = deliveryFee.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal platformFee = deliveryFee.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalAmount = productTotal.add(deliveryFee).add(serviceFee);
            BigDecimal merchantIncome = productTotal.subtract(platformFee);

            order.setDeliveryFee(deliveryFee);
            order.setServiceFee(serviceFee);
            order.setPlatformFee(platformFee);
            order.setTotalAmount(totalAmount);
            order.setMerchantIncome(merchantIncome);

            // 设置超时状态
            order.setTimeoutStatus(TimeoutStatus.NORMAL);
            order.setTimeoutWarningSent(false);
            order.setTimeoutCount(0);

            // 设置备注
            if (random.nextInt(3) == 0) { // 33%概率有备注
                order.setRemark("等待支付中，请尽快支付");
            }

            shoppingOrders.add(shoppingOrderRepository.save(order));
        }

        // 4. 生成活跃订单（非超时）
        for (int i = 0; i < normalActiveCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = random.nextInt(10) < 8 ? getRandomUser(deliveryUsers) : null; // 80%的概率已分配配送员
            Store store = getRandomItem(stores);
            Product product = getRandomProductFromStore(products, store);

            ShoppingOrder order = new ShoppingOrder();
            order.setOrderNumber(UUID.randomUUID());
            order.setUser(customer);
            order.setStore(store);
            order.setProduct(product);

            // 设置购买信息
            int quantity = 1 + random.nextInt(5); // 1-5件
            order.setQuantity(quantity);
            order.setProductPrice(product.getPrice());

            // 设置较最近的创建时间
            LocalDateTime createdAt = LocalDateTime.now().minusHours(random.nextInt(72)); // 过去72小时内
            order.setCreatedAt(createdAt);

            // 设置收件信息
            order.setRecipientName(customer.getUsername());
            order.setRecipientPhone("1380000" + String.format("%04d", completedCount + cancelledCount + paymentPendingCount + i + 1));

            // 设置配送地址
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setDeliveryAddress(generateRandomAddress());
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送类型和距离
            order.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);
            order.setDeliveryDistance(0.5 + random.nextDouble() * 5.5); // 0.5-6km

            // 随机设置不同的活跃状态
            OrderStatus status;
            if (deliveryUser == null) {
                // 未分配配送员时，状态有限
                OrderStatus[] possibleStatuses = {OrderStatus.PENDING, OrderStatus.MERCHANT_PENDING};
                status = possibleStatuses[random.nextInt(possibleStatuses.length)];
            } else {
                // 已分配配送员，随机设置状态
                OrderStatus[] possibleStatuses = {
                        OrderStatus.MERCHANT_PENDING, OrderStatus.PENDING,
                        OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED
                };
                status = possibleStatuses[random.nextInt(possibleStatuses.length)];
            }
            order.setOrderStatus(status);
            order.setAssignedUser(deliveryUser);

            // 根据状态设置相应的时间
            LocalDateTime paymentTime = createdAt.plusMinutes(random.nextInt(30)); // 0-30分钟后支付
            order.setPaymentTime(paymentTime);

            if (status == OrderStatus.ASSIGNED || status == OrderStatus.IN_TRANSIT || status == OrderStatus.DELIVERED) {
                LocalDateTime expectedDeliveryTime = paymentTime.plusMinutes(30 + random.nextInt(90)); // 30-120分钟后送达
                order.setExpectedDeliveryTime(expectedDeliveryTime);
            }

            if (status == OrderStatus.DELIVERED) {
                LocalDateTime deliveredTime = order.getExpectedDeliveryTime().plusMinutes(random.nextInt(30)); // 0-30分钟后送达
                order.setDeliveredTime(deliveredTime);
            }

            // 设置费用信息
            BigDecimal productTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            BigDecimal deliveryFee = BigDecimal.valueOf(5 + random.nextInt(10)).setScale(2, RoundingMode.HALF_UP); // 5-15元
            BigDecimal serviceFee = deliveryFee.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP); // 10%服务费
            BigDecimal platformFee = deliveryFee.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP); // 5%平台费
            BigDecimal totalAmount = productTotal.add(deliveryFee).add(serviceFee);
            BigDecimal merchantIncome = productTotal.subtract(platformFee);

            order.setDeliveryFee(deliveryFee);
            order.setServiceFee(serviceFee);
            order.setPlatformFee(platformFee);
            order.setTotalAmount(totalAmount);
            order.setMerchantIncome(merchantIncome);

            // 设置超时状态
            order.setTimeoutStatus(TimeoutStatus.NORMAL);
            order.setTimeoutWarningSent(false);
            order.setTimeoutCount(0);

            // 设置备注
            if (random.nextBoolean()) {
                order.setRemark("请在门口放置，谢谢！");
            }

            shoppingOrders.add(shoppingOrderRepository.save(order));
        }

        // 5. 生成超时订单
        for (int i = 0; i < timeoutCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = getRandomUser(deliveryUsers);
            Store store = getRandomItem(stores);
            Product product = getRandomProductFromStore(products, store);

            ShoppingOrder order = new ShoppingOrder();
            order.setOrderNumber(UUID.randomUUID());
            order.setUser(customer);
            order.setStore(store);
            order.setProduct(product);

            // 设置购买信息
            int quantity = 1 + random.nextInt(5); // 1-5件
            order.setQuantity(quantity);
            order.setProductPrice(product.getPrice());

            // 设置较早的创建时间，确保超时
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1 + random.nextInt(5)); // 1-5天前
            order.setCreatedAt(createdAt);

            // 设置收件信息
            order.setRecipientName(customer.getUsername());
            order.setRecipientPhone("1380000" + String.format("%04d", completedCount + cancelledCount + paymentPendingCount + normalActiveCount + i + 1));

            // 设置配送地址
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setDeliveryAddress(generateRandomAddress());
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送类型和距离
            order.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);
            order.setDeliveryDistance(0.5 + random.nextDouble() * 5.5); // 0.5-6km

            // 设置配送员
            order.setAssignedUser(deliveryUser);

            // 设置状态和时间
            if (i % 3 == 0) {
                // 设置为平台介入状态
                order.setOrderStatus(OrderStatus.PLATFORM_INTERVENTION);
                order.setInterventionTime(LocalDateTime.now().minusHours(random.nextInt(48)));
            } else if (i % 3 == 1) {
                // 设置为IN_TRANSIT但已超时
                order.setOrderStatus(OrderStatus.IN_TRANSIT);
            } else {
                // 设置为ASSIGNED但已超时
                order.setOrderStatus(OrderStatus.ASSIGNED);
            }

            // 设置支付时间
            LocalDateTime paymentTime = createdAt.plusMinutes(random.nextInt(30)); // 0-30分钟后支付
            order.setPaymentTime(paymentTime);

            // 设置很早的预计送达时间，确保超时
            LocalDateTime expectedDeliveryTime = paymentTime.plusMinutes(30);
            order.setExpectedDeliveryTime(expectedDeliveryTime);

            // 设置费用信息
            BigDecimal productTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            BigDecimal deliveryFee = BigDecimal.valueOf(5 + random.nextInt(10)).setScale(2, RoundingMode.HALF_UP); // 5-15元
            BigDecimal serviceFee = deliveryFee.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP); // 10%服务费
            BigDecimal platformFee = deliveryFee.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP); // 5%平台费
            BigDecimal totalAmount = productTotal.add(deliveryFee).add(serviceFee);
            BigDecimal merchantIncome = productTotal.subtract(platformFee);

            order.setDeliveryFee(deliveryFee);
            order.setServiceFee(serviceFee);
            order.setPlatformFee(platformFee);
            order.setTotalAmount(totalAmount);
            order.setMerchantIncome(merchantIncome);

            // 设置超时状态
            TimeoutStatus[] timeoutStatuses = {
                    TimeoutStatus.PICKUP_TIMEOUT, TimeoutStatus.DELIVERY_TIMEOUT,
                    TimeoutStatus.PICKUP_TIMEOUT_WARNING, TimeoutStatus.DELIVERY_TIMEOUT_WARNING
            };
            order.setTimeoutStatus(timeoutStatuses[random.nextInt(timeoutStatuses.length)]);
            order.setTimeoutWarningSent(true);
            order.setTimeoutCount(1 + random.nextInt(3)); // 1-3次超时

            shoppingOrders.add(shoppingOrderRepository.save(order));
        }

        return shoppingOrders;
    }

    /**
     * 生成代购订单(PurchaseRequest)
     * 修改版：增加了更多的订单状态和非超时订单
     */
    private List<PurchaseRequest> generatePurchaseRequests(int count, List<User> customers, List<User> deliveryUsers) {
        List<PurchaseRequest> purchaseRequests = new ArrayList<>();

        // 创建统计分布：
        // 50%已完成订单
        // 15%已取消订单
        // 15%支付待处理订单
        // 20%活跃订单（其中25%是超时订单）
        int completedCount = (int)(count * 0.5);
        int cancelledCount = (int)(count * 0.15);
        int paymentPendingCount = (int)(count * 0.15);
        int activeCount = count - completedCount - cancelledCount - paymentPendingCount;
        int timeoutCount = (int)(activeCount * 0.25); // 活跃订单中25%是超时订单
        int normalActiveCount = activeCount - timeoutCount;

        // 1. 生成已完成订单
        for (int i = 0; i < completedCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = getRandomUser(deliveryUsers);

            PurchaseRequest request = new PurchaseRequest();
            request.setRequestNumber(UUID.randomUUID());
            request.setUser(customer);

            // 设置基本信息
            request.setTitle(getRandomItem(purchaseTitles));
            request.setDescription("详细需求描述：请帮我购买这个商品，编号：" + (i + 1));
            request.setCategory(getRandomProductCategory());

            // 设置价格信息
            BigDecimal expectedPrice = BigDecimal.valueOf(50 + random.nextInt(950)).setScale(2, RoundingMode.HALF_UP); // 50-1000元
            request.setExpectedPrice(expectedPrice);

            // 设置图片URL
            if (random.nextBoolean()) {
                request.setImageUrl("purchase_image_" + (i + 1) + ".jpg");
            }

            // 根据索引设置不同的创建时间，使订单分布在过去的0-90天
            LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(90));
            request.setCreatedAt(createdAt);

            // 设置截止时间
            LocalDateTime deadline = createdAt.plusDays(1 + random.nextInt(6)); // 1-7天后
            request.setDeadline(deadline);

            // 设置配送类型
            request.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);

            // 设置采购地址和配送地址
            double[] purchaseCoordinates = generateRandomCoordinates();
            double[] deliveryCoordinates = generateRandomCoordinates();

            request.setPurchaseAddress(generateRandomAddress());
            request.setPurchaseLatitude(purchaseCoordinates[0]);
            request.setPurchaseLongitude(purchaseCoordinates[1]);

            request.setDeliveryAddress(generateRandomAddress());
            request.setDeliveryLatitude(deliveryCoordinates[0]);
            request.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送距离
            request.setDeliveryDistance(0.5 + random.nextDouble() * 9.5); // 0.5-10km

            // 设置收件人信息
            request.setRecipientName(customer.getUsername());
            request.setRecipientPhone("1380000" + String.format("%04d", i + 1));

            // 设置状态和配送员
            request.setStatus(OrderStatus.COMPLETED);
            request.setAssignedUser(deliveryUser);

            // 设置时间信息
            LocalDateTime paymentTime = createdAt.plusMinutes(random.nextInt(60)); // 0-60分钟后支付
            request.setPaymentTime(paymentTime);

            // 设置完成日期和送达日期
            LocalDateTime deliveredDate = paymentTime.plusHours(random.nextInt(48)); // 0-48小时后送达
            request.setDeliveredDate(deliveredDate);
            request.setCompletionDate(deliveredDate.plusMinutes(random.nextInt(120))); // 0-120分钟后确认

            // 设置费用信息
            BigDecimal deliveryFee = BigDecimal.valueOf(10 + random.nextInt(20)).setScale(2, RoundingMode.HALF_UP); // 10-30元
            BigDecimal serviceFee = deliveryFee.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP); // 10%服务费
            BigDecimal totalAmount = expectedPrice.add(deliveryFee).add(serviceFee);

            request.setDeliveryFee(deliveryFee);
            request.setServiceFee(serviceFee);
            request.setTotalAmount(totalAmount);
            request.setUserIncome(deliveryFee.multiply(BigDecimal.valueOf(0.8)).doubleValue()); // 配送员获得80%
            request.setPlatformIncome(deliveryFee.multiply(BigDecimal.valueOf(0.2)).add(serviceFee).doubleValue()); // 平台获得20%+服务费

            // 设置物品重量（可选）
            if (random.nextBoolean()) {
                request.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            }

            // 设置浏览量
            request.setViewCount(10 + random.nextInt(91)); // 10-100次浏览

            // 设置超时状态
            request.setTimeoutStatus(TimeoutStatus.NORMAL);
            request.setTimeoutWarningSent(false);
            request.setTimeoutCount(0);

            purchaseRequests.add(purchaseRequestRepository.save(request));
        }

        // 2. 生成已取消订单
        for (int i = 0; i < cancelledCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = random.nextInt(10) < 3 ? getRandomUser(deliveryUsers) : null; // 30%的概率已分配配送员

            PurchaseRequest request = new PurchaseRequest();
            request.setRequestNumber(UUID.randomUUID());
            request.setUser(customer);

            // 设置基本信息
            request.setTitle(getRandomItem(purchaseTitles));
            request.setDescription("详细需求描述：请帮我购买这个商品，编号：" + (completedCount + i + 1));
            request.setCategory(getRandomProductCategory());

            // 设置价格信息
            BigDecimal expectedPrice = BigDecimal.valueOf(50 + random.nextInt(950)).setScale(2, RoundingMode.HALF_UP); // 50-1000元
            request.setExpectedPrice(expectedPrice);

            // 设置图片URL（50%概率有图片）
            if (random.nextBoolean()) {
                request.setImageUrl("purchase_image_" + (completedCount + i + 1) + ".jpg");
            }

            // 设置创建时间在过去1-30天
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1 + random.nextInt(30));
            request.setCreatedAt(createdAt);

            // 设置截止时间
            LocalDateTime deadline = createdAt.plusDays(1 + random.nextInt(6)); // 1-7天后
            request.setDeadline(deadline);

            // 设置配送类型
            request.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);

            // 设置采购地址和配送地址
            double[] purchaseCoordinates = generateRandomCoordinates();
            double[] deliveryCoordinates = generateRandomCoordinates();

            request.setPurchaseAddress(generateRandomAddress());
            request.setPurchaseLatitude(purchaseCoordinates[0]);
            request.setPurchaseLongitude(purchaseCoordinates[1]);

            request.setDeliveryAddress(generateRandomAddress());
            request.setDeliveryLatitude(deliveryCoordinates[0]);
            request.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送距离
            request.setDeliveryDistance(0.5 + random.nextDouble() * 9.5); // 0.5-10km

            // 设置收件人信息
            request.setRecipientName(customer.getUsername());
            request.setRecipientPhone("1380000" + String.format("%04d", completedCount + i + 1));

            // 设置为已取消状态
            request.setStatus(OrderStatus.CANCELLED);

            // 随机设置支付时间（50%概率未支付取消，50%概率已支付取消）
            if (random.nextBoolean()) {
                LocalDateTime paymentTime = createdAt.plusMinutes(random.nextInt(60)); // 0-60分钟后支付
                request.setPaymentTime(paymentTime);

                // 已支付的设置退款状态
                request.setRefundStatus("REFUNDED");
                request.setRefundReason(getRandomCancelReason());
                request.setRefundAmount(expectedPrice); // 退款商品金额
                request.setRefundDate(createdAt.plusHours(random.nextInt(24) + 1)); // 1-24小时后退款
            } else {
                // 未支付取消不设置退款信息
                request.setRefundStatus("NOT_REQUIRED");
                request.setRefundReason(getRandomCancelReason());
            }

            // 如果被分配了配送员，设置配送员
            request.setAssignedUser(deliveryUser);

            // 设置费用信息
            BigDecimal deliveryFee = BigDecimal.valueOf(10 + random.nextInt(20)).setScale(2, RoundingMode.HALF_UP); // 10-30元
            BigDecimal serviceFee = deliveryFee.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP); // 10%服务费
            BigDecimal totalAmount = expectedPrice.add(deliveryFee).add(serviceFee);

            request.setDeliveryFee(deliveryFee);
            request.setServiceFee(serviceFee);
            request.setTotalAmount(totalAmount);
            request.setUserIncome(0.0); // 已取消订单无收入
            request.setPlatformIncome(0.0); // 已取消订单无收入

            // 设置浏览量
            request.setViewCount(5 + random.nextInt(21)); // 5-25次浏览

            // 设置超时状态
            request.setTimeoutStatus(TimeoutStatus.NORMAL);
            request.setTimeoutWarningSent(false);
            request.setTimeoutCount(0);

            purchaseRequests.add(purchaseRequestRepository.save(request));
        }

        // 3. 生成支付待处理订单
        for (int i = 0; i < paymentPendingCount; i++) {
            User customer = getRandomUser(customers);

            PurchaseRequest request = new PurchaseRequest();
            request.setRequestNumber(UUID.randomUUID());
            request.setUser(customer);

            // 设置基本信息
            request.setTitle(getRandomItem(purchaseTitles));
            request.setDescription("详细需求描述：请帮我购买这个商品，编号：" + (completedCount + cancelledCount + i + 1));
            request.setCategory(getRandomProductCategory());

            // 设置价格信息
            BigDecimal expectedPrice = BigDecimal.valueOf(50 + random.nextInt(950)).setScale(2, RoundingMode.HALF_UP); // 50-1000元
            request.setExpectedPrice(expectedPrice);

            // 设置图片URL（30%概率有图片）
            if (random.nextInt(10) < 3) {
                request.setImageUrl("purchase_image_" + (completedCount + cancelledCount + i + 1) + ".jpg");
            }

            // 设置最近的创建时间
            LocalDateTime createdAt = LocalDateTime.now().minusHours(random.nextInt(24)); // 过去24小时内
            request.setCreatedAt(createdAt);

            // 设置截止时间
            LocalDateTime deadline = createdAt.plusDays(1 + random.nextInt(3)); // 1-3天后
            request.setDeadline(deadline);

            // 设置配送类型
            request.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);

            // 设置采购地址和配送地址
            double[] purchaseCoordinates = generateRandomCoordinates();
            double[] deliveryCoordinates = generateRandomCoordinates();

            request.setPurchaseAddress(generateRandomAddress());
            request.setPurchaseLatitude(purchaseCoordinates[0]);
            request.setPurchaseLongitude(purchaseCoordinates[1]);

            request.setDeliveryAddress(generateRandomAddress());
            request.setDeliveryLatitude(deliveryCoordinates[0]);
            request.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送距离
            request.setDeliveryDistance(0.5 + random.nextDouble() * 9.5); // 0.5-10km

            // 设置收件人信息
            request.setRecipientName(customer.getUsername());
            request.setRecipientPhone("1380000" + String.format("%04d", completedCount + cancelledCount + i + 1));

            // 设置状态为支付待处理
            request.setStatus(OrderStatus.PAYMENT_PENDING);

            // 设置费用信息
            BigDecimal deliveryFee = BigDecimal.valueOf(10 + random.nextInt(20)).setScale(2, RoundingMode.HALF_UP); // 10-30元
            BigDecimal serviceFee = deliveryFee.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP); // 10%服务费
            BigDecimal totalAmount = expectedPrice.add(deliveryFee).add(serviceFee);

            request.setDeliveryFee(deliveryFee);
            request.setServiceFee(serviceFee);
            request.setTotalAmount(totalAmount);
            request.setUserIncome(0.0); // 未支付订单无收入
            request.setPlatformIncome(0.0); // 未支付订单无收入

            // 设置物品重量（可选）
            if (random.nextBoolean()) {
                request.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            }

            // 设置浏览量
            request.setViewCount(5 + random.nextInt(16)); // 5-20次浏览

            // 设置超时状态
            request.setTimeoutStatus(TimeoutStatus.NORMAL);
            request.setTimeoutWarningSent(false);
            request.setTimeoutCount(0);

            purchaseRequests.add(purchaseRequestRepository.save(request));
        }

        // 4. 生成正常活跃订单（非超时）
        for (int i = 0; i < normalActiveCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = random.nextInt(10) < 7 ? getRandomUser(deliveryUsers) : null; // 70%的概率已分配配送员

            PurchaseRequest request = new PurchaseRequest();
            request.setRequestNumber(UUID.randomUUID());
            request.setUser(customer);

            // 设置基本信息
            request.setTitle(getRandomItem(purchaseTitles));
            request.setDescription("详细需求描述：请帮我购买这个商品，编号：" + (completedCount + cancelledCount + paymentPendingCount + i + 1));
            request.setCategory(getRandomProductCategory());

            // 设置价格信息
            BigDecimal expectedPrice = BigDecimal.valueOf(50 + random.nextInt(950)).setScale(2, RoundingMode.HALF_UP); // 50-1000元
            request.setExpectedPrice(expectedPrice);

            // 设置图片URL（40%概率有图片）
            if (random.nextInt(10) < 4) {
                request.setImageUrl("purchase_image_" + (completedCount + cancelledCount + paymentPendingCount + i + 1) + ".jpg");
            }

            // 设置较最近的创建时间
            LocalDateTime createdAt = LocalDateTime.now().minusHours(random.nextInt(72)); // 过去72小时内
            request.setCreatedAt(createdAt);

            // 设置截止时间
            LocalDateTime deadline = createdAt.plusDays(1 + random.nextInt(6)); // 1-7天后
            request.setDeadline(deadline);

            // 设置配送类型
            request.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);

            // 设置采购地址和配送地址
            double[] purchaseCoordinates = generateRandomCoordinates();
            double[] deliveryCoordinates = generateRandomCoordinates();

            request.setPurchaseAddress(generateRandomAddress());
            request.setPurchaseLatitude(purchaseCoordinates[0]);
            request.setPurchaseLongitude(purchaseCoordinates[1]);

            request.setDeliveryAddress(generateRandomAddress());
            request.setDeliveryLatitude(deliveryCoordinates[0]);
            request.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送距离
            request.setDeliveryDistance(0.5 + random.nextDouble() * 9.5); // 0.5-10km

            // 设置收件人信息
            request.setRecipientName(customer.getUsername());
            request.setRecipientPhone("1380000" + String.format("%04d", completedCount + cancelledCount + paymentPendingCount + i + 1));

            // 随机设置不同的活跃状态
            OrderStatus status;
            if (deliveryUser == null) {
                // 未分配配送员时，状态只能是PENDING
                status = OrderStatus.PENDING;
            } else {
                // 已分配配送员，随机设置状态
                OrderStatus[] activeStatuses = {
                        OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED
                };
                status = activeStatuses[random.nextInt(activeStatuses.length)];
            }
            request.setStatus(status);
            request.setAssignedUser(deliveryUser);

            // 根据状态设置相应的时间
            LocalDateTime paymentTime = createdAt.plusMinutes(random.nextInt(60)); // 0-60分钟后支付
            request.setPaymentTime(paymentTime);

            if (status == OrderStatus.IN_TRANSIT || status == OrderStatus.DELIVERED) {
                // 设置预计送达时间
                request.setDeliveryTime(paymentTime.plusHours(1 + random.nextInt(24))); // 1-25小时后送达
            }

            if (status == OrderStatus.DELIVERED) {
                LocalDateTime deliveredDate = request.getDeliveryTime().plusMinutes(random.nextInt(120) - 60); // 提前或超出1小时送达
                request.setDeliveredDate(deliveredDate);
            }

            // 设置费用信息
            BigDecimal deliveryFee = BigDecimal.valueOf(10 + random.nextInt(20)).setScale(2, RoundingMode.HALF_UP); // 10-30元
            BigDecimal serviceFee = deliveryFee.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP); // 10%服务费
            BigDecimal totalAmount = expectedPrice.add(deliveryFee).add(serviceFee);

            request.setDeliveryFee(deliveryFee);
            request.setServiceFee(serviceFee);
            request.setTotalAmount(totalAmount);

            if (deliveryUser != null) {
                request.setUserIncome(deliveryFee.multiply(BigDecimal.valueOf(0.8)).doubleValue()); // 配送员获得80%
                request.setPlatformIncome(deliveryFee.multiply(BigDecimal.valueOf(0.2)).add(serviceFee).doubleValue()); // 平台获得20%+服务费
            } else {
                request.setUserIncome(0.0);
                request.setPlatformIncome(0.0);
            }

            // 设置物品重量（可选）
            if (random.nextBoolean()) {
                request.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            }

            // 设置浏览量
            request.setViewCount(10 + random.nextInt(91)); // 10-100次浏览

            // 设置超时状态
            request.setTimeoutStatus(TimeoutStatus.NORMAL);
            request.setTimeoutWarningSent(false);
            request.setTimeoutCount(0);

            purchaseRequests.add(purchaseRequestRepository.save(request));
        }

        // 5. 生成超时订单
        for (int i = 0; i < timeoutCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = getRandomUser(deliveryUsers);

            PurchaseRequest request = new PurchaseRequest();
            request.setRequestNumber(UUID.randomUUID());
            request.setUser(customer);

            // 设置基本信息
            request.setTitle(getRandomItem(purchaseTitles));
            request.setDescription("详细需求描述：请帮我购买这个商品，编号：" + (completedCount + cancelledCount + paymentPendingCount + normalActiveCount + i + 1));
            request.setCategory(getRandomProductCategory());

            // 设置价格信息
            BigDecimal expectedPrice = BigDecimal.valueOf(50 + random.nextInt(950)).setScale(2, RoundingMode.HALF_UP); // 50-1000元
            request.setExpectedPrice(expectedPrice);

            // 设置图片URL
            if (random.nextBoolean()) {
                request.setImageUrl("purchase_image_" + (completedCount + cancelledCount + paymentPendingCount + normalActiveCount + i + 1) + ".jpg");
            }

            // 设置较早的创建时间，确保超时
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1 + random.nextInt(5)); // 1-5天前
            request.setCreatedAt(createdAt);

            // 设置已过期的截止时间
            LocalDateTime deadline = createdAt.plusHours(24 + random.nextInt(24)); // 24-48小时后
            request.setDeadline(deadline);

            // 设置配送类型
            request.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);

            // 设置采购地址和配送地址
            double[] purchaseCoordinates = generateRandomCoordinates();
            double[] deliveryCoordinates = generateRandomCoordinates();

            request.setPurchaseAddress(generateRandomAddress());
            request.setPurchaseLatitude(purchaseCoordinates[0]);
            request.setPurchaseLongitude(purchaseCoordinates[1]);

            request.setDeliveryAddress(generateRandomAddress());
            request.setDeliveryLatitude(deliveryCoordinates[0]);
            request.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送距离
            request.setDeliveryDistance(0.5 + random.nextDouble() * 9.5); // 0.5-10km

            // 设置收件人信息
            request.setRecipientName(customer.getUsername());
            request.setRecipientPhone("1380000" + String.format("%04d", completedCount + cancelledCount + paymentPendingCount + normalActiveCount + i + 1));

            // 设置配送员
            request.setAssignedUser(deliveryUser);

            // 设置状态和时间
            if (i % 2 == 0) {
                // 设置为平台介入状态
                request.setStatus(OrderStatus.PLATFORM_INTERVENTION);
                request.setInterventionTime(LocalDateTime.now().minusHours(random.nextInt(48)));
            } else {
                // 设置为其他活跃状态但已超时
                OrderStatus[] activeStatuses = {OrderStatus.PENDING, OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT};
                request.setStatus(activeStatuses[random.nextInt(activeStatuses.length)]);
            }

            // 设置支付时间
            LocalDateTime paymentTime = createdAt.plusMinutes(random.nextInt(60)); // 0-60分钟后支付
            request.setPaymentTime(paymentTime);

            // 设置费用信息
            BigDecimal deliveryFee = BigDecimal.valueOf(10 + random.nextInt(20)).setScale(2, RoundingMode.HALF_UP); // 10-30元
            BigDecimal serviceFee = deliveryFee.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP); // 10%服务费
            BigDecimal totalAmount = expectedPrice.add(deliveryFee).add(serviceFee);

            request.setDeliveryFee(deliveryFee);
            request.setServiceFee(serviceFee);
            request.setTotalAmount(totalAmount);
            request.setUserIncome(deliveryFee.multiply(BigDecimal.valueOf(0.8)).doubleValue()); // 配送员获得80%
            request.setPlatformIncome(deliveryFee.multiply(BigDecimal.valueOf(0.2)).add(serviceFee).doubleValue()); // 平台获得20%+服务费

            // 设置物品重量（可选）
            if (random.nextBoolean()) {
                request.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            }

            // 设置浏览量
            request.setViewCount(10 + random.nextInt(91)); // 10-100次浏览

            // 设置超时状态
            TimeoutStatus[] timeoutStatuses = {
                    TimeoutStatus.PICKUP_TIMEOUT, TimeoutStatus.DELIVERY_TIMEOUT,
                    TimeoutStatus.PICKUP_TIMEOUT_WARNING, TimeoutStatus.DELIVERY_TIMEOUT_WARNING
            };
            request.setTimeoutStatus(timeoutStatuses[random.nextInt(timeoutStatuses.length)]);
            request.setTimeoutWarningSent(true);
            request.setTimeoutCount(1 + random.nextInt(3)); // 1-3次超时

            purchaseRequests.add(purchaseRequestRepository.save(request));
        }

        return purchaseRequests;
    }

    /**
     * 生成随机地址
     */
    private String generateRandomAddress() {
        String city = getRandomItem(cities);
        String district = getRandomItem(districts);
        String street = getRandomItem(streets);
        String detail = getRandomItem(details);
        return city + district + street + detail;
    }

    /**
     * 生成随机经纬度（中国大致范围内）
     * @return [latitude, longitude] 纬度和经度
     */
    private double[] generateRandomCoordinates() {
        // 北京坐标为中心，加减随机偏移
        double baseLat = 39.9;
        double baseLng = 116.4;

        double latOffset = (random.nextDouble() - 0.5) * 2; // -1到1之间
        double lngOffset = (random.nextDouble() - 0.5) * 2; // -1到1之间

        double latitude = baseLat + latOffset;
        double longitude = baseLng + lngOffset;

        return new double[] {latitude, longitude};
    }

    /**
     * 生成随机商品名称
     */
    private String generateRandomProductName() {
        String prefix = getRandomItem(productPrefixes);
        String suffix = getRandomItem(productSuffixes);
        return prefix + suffix;
    }

    /**
     * 生成随机订单名称
     */
    private String generateRandomOrderName() {
        return getRandomItem(orderNamePrefixes) + " " + generateRandomCode();
    }

    /**
     * 生成随机取件码
     */
    private String generateRandomCode() {
        int code = 100000 + random.nextInt(900000); // 6位数字
        return String.valueOf(code);
    }

    /**
     * 获取随机取消原因
     */
    private String getRandomCancelReason() {
        return getRandomItem(cancelReasons);
    }

    /**
     * 从列表中随机获取一个项
     */
    private <T> T getRandomItem(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    /**
     * 随机获取一个用户
     */
    private User getRandomUser(List<User> users) {
        return users.get(random.nextInt(users.size()));
    }

    /**
     * 随机获取特定商店的商品
     */
    private Product getRandomProductFromStore(List<Product> products, Store store) {
        List<Product> storeProducts = products.stream()
                .filter(p -> p.getStore().getId().equals(store.getId()))
                .toList();

        if (storeProducts.isEmpty()) {
            // 如果该商店没有商品，随机返回一个
            return products.get(random.nextInt(products.size()));
        }

        return storeProducts.get(random.nextInt(storeProducts.size()));
    }

    /**
     * 获取随机的商家等级
     */
    private MerchantLevel getRandomMerchantLevel() {
        MerchantLevel[] levels = MerchantLevel.values();
        return levels[random.nextInt(levels.length)];
    }

    /**
     * 获取随机的商品分类，确保返回值符合数据库约束
     * @return 有效的商品分类
     */
    private ProductCategory getRandomProductCategory() {
        try {
            // 定义已知有效的分类
            List<ProductCategory> validCategories = Arrays.asList(
                    ProductCategory.FOOD,          // 食品
                    ProductCategory.ELECTRONICS,   // 电子产品
                    ProductCategory.CLOTHING,      // 服装
                    ProductCategory.BOOKS,         // 图书
                    ProductCategory.BEAUTY,        // 美妆
                    ProductCategory.SPORTS,        // 运动
                    ProductCategory.MEDICINE,      // 药品
                    ProductCategory.OTHER          // 其他
            );

            // 随机选择一个有效分类
            ProductCategory category = validCategories.get(random.nextInt(validCategories.size()));
            logger.debug("Selected product category: {}", category);
            return category;
        } catch (Exception e) {
            // 出现任何问题都返回FOOD作为安全的回退选项
            logger.warn("Error selecting category, falling back to FOOD: {}", e.getMessage());
            return ProductCategory.FOOD;
        }
    }
}