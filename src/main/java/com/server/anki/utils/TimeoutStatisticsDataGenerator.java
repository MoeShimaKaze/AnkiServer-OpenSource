package com.server.anki.utils;

import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.repository.MailOrderRepository;
import com.server.anki.marketing.region.DeliveryRegion;
import com.server.anki.marketing.region.RegionService;
import com.server.anki.marketing.region.model.RegionCreateRequest;
import com.server.anki.shopping.entity.*;
import com.server.anki.shopping.enums.*;
import com.server.anki.shopping.repository.*;
import com.server.anki.timeout.core.Timeoutable;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import com.server.anki.user.enums.UserIdentity;
import com.server.anki.user.enums.UserVerificationStatus;
import com.server.anki.wallet.message.BalanceChangeMessage;
import com.server.anki.wallet.message.WalletInitMessage;
import com.server.anki.wallet.message.WalletMessageUtils;
import com.server.anki.wallet.service.WalletTransactionService;
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
 * 增强版：添加测试区域生成和地址信息完整性验证
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

    @Autowired
    private RegionService regionService;

    // 添加钱包交易服务依赖
    @Autowired
    private WalletTransactionService walletTransactionService;

    private final Random random = new Random();

    // 添加测试标记常量
    private static final String TEST_ORDER_PREFIX = "[TEST]_";
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

    // 用于生成地址中的联系人信息
    private final List<String> contactNames = Arrays.asList("张先生", "李女士", "王小姐", "赵先生", "陈女士", "刘先生", "郑女士", "周先生", "杨小姐", "吴先生");
    private final List<String> contactPhones = Arrays.asList("13800138000", "13900139000", "13700137000", "13600136000", "13500135000",
            "13400134000", "13300133000", "13200132000", "13100131000", "13000130000");

    // 添加用于生成门牌号的列表
    private final List<String> buildingNumbers = Arrays.asList("1号楼", "2号楼", "3号楼", "A座", "B座", "C座", "主楼", "东楼", "西楼");
    private final List<String> unitNumbers = Arrays.asList("1单元", "2单元", "3单元", "东单元", "西单元", "");
    private final List<String> roomNumbers = Arrays.asList("101室", "202室", "303室", "405室", "506室", "608室", "701室", "801室", "902室", "1001室");

    // 钱包余额范围
    private static final int MIN_WALLET_BALANCE = 100;
    private static final int MAX_WALLET_BALANCE = 1000;

    /**
     * 生成随机门牌号详情
     * @return 格式化的门牌号字符串
     */
    private String generateRandomPickupDetail() {
        String building = getRandomItem(buildingNumbers);
        String unit = getRandomItem(unitNumbers);
        String room = getRandomItem(roomNumbers);

        // 某些情况下不需要单元信息
        if (unit.isEmpty()) {
            return building + " " + room;
        }

        return building + " " + unit + " " + room;
    }

    /**
     * 生成测试数据的入口方法
     * @return 生成的总记录数
     */
    @Transactional
    public int generateTestData() {
        logger.info("开始生成超时统计系统测试数据...");
        int totalRecords = 0;

        try {
            // 首先生成配送区域数据，确保后续订单可以找到对应区域
            generateTestDeliveryRegions();

            // 1. 生成用户数据
            List<User> normalUsers = generateNormalUsers(1000);
            List<User> deliveryUsers = generateDeliveryUsers(100);
            List<User> allUsers = new ArrayList<>(normalUsers);
            allUsers.addAll(deliveryUsers);
            logger.info("已生成 {} 个普通用户和 {} 个配送用户", normalUsers.size(), deliveryUsers.size());

            // 1.1 新增：为所有用户创建钱包并添加初始余额
            initializeWalletsForUsers(allUsers);
            logger.info("已为 {} 个用户创建钱包并添加初始余额", allUsers.size());

            // 2. 生成商家数据
            List<MerchantInfo> merchants = generateMerchants(200, normalUsers);
            logger.info("已生成 {} 个商家信息", merchants.size());

            // 3. 生成店铺数据
            List<Store> stores = generateStores(200, merchants);
            logger.info("已生成 {} 个店铺", stores.size());

            // 4. 生成商品数据
            List<Product> products = generateProducts(3000, stores);
            logger.info("已生成 {} 个商品", products.size());

            // 5. 生成MailOrder数据（快递代拿订单）
            List<MailOrder> mailOrders = generateMailOrders(3000, normalUsers, deliveryUsers);
            totalRecords += mailOrders.size();
            logger.info("已生成 {} 个快递代拿订单", mailOrders.size());

            // 6. 生成ShoppingOrder数据（商家订单）
            List<ShoppingOrder> shoppingOrders = generateShoppingOrders(3000, normalUsers, deliveryUsers, stores, products);
            totalRecords += shoppingOrders.size(); // 注意这里原来的代码有错误，使用了mailOrders.size()
            logger.info("已生成 {} 个商家订单", shoppingOrders.size());

            // 7. 生成PurchaseRequest数据（代购订单）
            List<PurchaseRequest> purchaseRequests = generatePurchaseRequests(3000, normalUsers, deliveryUsers);
            totalRecords += purchaseRequests.size(); // 注意这里原来的代码有错误，使用了mailOrders.size()
            logger.info("已生成 {} 个代购订单", purchaseRequests.size());

            // 8. 最后进行额外验证，确保所有订单地址信息完整
            validateAllOrderAddresses(mailOrders, shoppingOrders, purchaseRequests);

            logger.info("测试数据生成完成，共生成 {} 条订单数据", totalRecords);
            return totalRecords;
        } catch (Exception e) {
            logger.error("生成测试数据时发生错误", e);
            throw new RuntimeException("生成测试数据失败", e);
        }
    }

    /**
     * 为用户初始化钱包并添加随机初始余额
     * 使用系统的handleWalletInit方法确保一致性
     *
     * @param users 需要初始化钱包的用户列表
     */
    private void initializeWalletsForUsers(List<User> users) {
        logger.info("开始为用户初始化钱包和基础余额...");

        int createdCount = 0;
        int updatedCount = 0;

        for (User user : users) {
            try {
                // 1. 创建钱包初始化消息
                WalletInitMessage initMessage = WalletMessageUtils.createInitMessage(
                        user.getId(),
                        user.getUsername(),
                        user.getUserVerificationStatus().name()
                );

                // 2. 调用现有服务初始化钱包
                walletTransactionService.handleWalletInit(initMessage);

                // 3. 添加初始余额
                BigDecimal initialBalance = BigDecimal.valueOf(
                        MIN_WALLET_BALANCE + random.nextInt(MAX_WALLET_BALANCE - MIN_WALLET_BALANCE + 1)
                );

                // 4. 创建余额变更消息
                BalanceChangeMessage balanceMessage = WalletMessageUtils.createBalanceChangeMessage(
                        user.getId(),
                        initialBalance,
                        "测试数据初始余额",
                        BalanceChangeMessage.BalanceChangeType.AVAILABLE
                );

                // 5. 处理余额变更
                walletTransactionService.handleBalanceChange(balanceMessage);

                createdCount++;
                logger.debug("为用户 {} 创建钱包并设置初始余额: {}", user.getId(), initialBalance);

                // 每处理10个用户记录一次进度
                if (createdCount % 10 == 0) {
                    logger.info("已处理 {} 个用户的钱包初始化", createdCount);
                }

            } catch (Exception e) {
                logger.error("为用户 {} 初始化钱包时发生错误: {}", user.getId(), e.getMessage(), e);
                // 继续处理其他用户，不中断整个初始化过程
            }
        }

        logger.info("用户钱包初始化完成，共处理 {} 个用户", createdCount);
    }

    /**
     * 根据业务场景确定适合MailOrder的订单状态
     * 快递代取订单不支持MERCHANT_PENDING状态
     * @param scenario 业务场景
     * @return 适合该场景的订单状态
     */
    private OrderStatus determineMailOrderStatus(String scenario) {
        switch(scenario) {
            case "AWAITING_PAYMENT":
                return OrderStatus.PAYMENT_PENDING;
            case "AWAITING_PICKUP":
                return OrderStatus.PENDING;
            case "ASSIGNED_TO_DELIVERY":
                return OrderStatus.ASSIGNED;
            case "IN_DELIVERY":
                return OrderStatus.IN_TRANSIT;
            case "DELIVERED_PENDING_CONFIRM":
                return OrderStatus.DELIVERED;
            case "COMPLETED_ORDER":
                return OrderStatus.COMPLETED;
            case "CANCELLED_ORDER":
                return OrderStatus.CANCELLED;
            case "DISPUTE":
                return OrderStatus.PLATFORM_INTERVENTION;
            case "REFUND_PROCESSING":
                return OrderStatus.REFUNDING;
            case "REFUND_COMPLETE":
                return OrderStatus.REFUNDED;
            case "LOCKED_ORDER":
                return OrderStatus.LOCKED;
            case "PAYMENT_EXPIRED":
                return OrderStatus.PAYMENT_TIMEOUT;
            default:
                return OrderStatus.PENDING; // 默认安全状态
        }
    }

    /**
     * 根据业务场景确定适合ShoppingOrder的订单状态
     * 商品订单支持MERCHANT_PENDING状态
     * @param scenario 业务场景
     * @return 适合该场景的订单状态
     */
    private OrderStatus determineOrderStatus(String scenario) {
        switch(scenario) {
            case "STORE_CONFIRMATION":
                // 商家订单需要商家确认
                return OrderStatus.MERCHANT_PENDING;
            case "PAYMENT_NEEDED":
                return OrderStatus.PAYMENT_PENDING;
            case "COMPLETED_ORDER":
                return OrderStatus.COMPLETED;
            case "CANCELLED_ORDER":
                return OrderStatus.CANCELLED;
            case "IN_DELIVERY":
                return OrderStatus.IN_TRANSIT;
            case "DISPUTE":
                return OrderStatus.PLATFORM_INTERVENTION;
            case "PENDING_DELIVERY":
                return OrderStatus.PENDING;
            case "ASSIGNED_DELIVERY":
                return OrderStatus.ASSIGNED;
            case "DELIVERED_ORDER":
                return OrderStatus.DELIVERED;
            case "REFUNDING_ORDER":
                return OrderStatus.REFUNDING;
            case "REFUNDED_ORDER":
                return OrderStatus.REFUNDED;
            case "LOCKED_ORDER":
                return OrderStatus.LOCKED;
            case "PAYMENT_TIMEOUT_ORDER":
                return OrderStatus.PAYMENT_TIMEOUT;
            default:
                // 返回默认的安全状态
                return OrderStatus.PENDING;
        }
    }

    /**
     * 根据代购业务场景确定适合的订单状态
     * 代购订单不使用MERCHANT_PENDING状态
     * @param scenario 业务场景
     * @return 适合该场景的订单状态
     */
    private OrderStatus determinePurchaseStatus(String scenario) {
        switch(scenario) {
            case "STORE_CONFIRMATION":
                // 代购订单不需要商家确认，直接为待处理
                return OrderStatus.PENDING;
            case "PAYMENT_NEEDED":
                return OrderStatus.PAYMENT_PENDING;
            case "COMPLETED_ORDER":
                return OrderStatus.COMPLETED;
            case "CANCELLED_ORDER":
                return OrderStatus.CANCELLED;
            case "IN_DELIVERY":
                return OrderStatus.IN_TRANSIT;
            case "DISPUTE":
                return OrderStatus.PLATFORM_INTERVENTION;
            case "PENDING_DELIVERY":
                return OrderStatus.PENDING;
            case "ASSIGNED_DELIVERY":
                return OrderStatus.ASSIGNED;
            case "DELIVERED_ORDER":
                return OrderStatus.DELIVERED;
            case "REFUNDING_ORDER":
                return OrderStatus.REFUNDING;
            case "REFUNDED_ORDER":
                return OrderStatus.REFUNDED;
            case "LOCKED_ORDER":
                return OrderStatus.LOCKED;
            case "PAYMENT_TIMEOUT_ORDER":
                return OrderStatus.PAYMENT_TIMEOUT;
            default:
                return OrderStatus.PENDING;
        }
    }

    /**
     * 生成随机代购订单标题
     * 修改：添加测试标记前缀
     */
    private String generateRandomPurchaseTitle() {
        return TEST_ORDER_PREFIX + getRandomItem(purchaseTitles);
    }

    /**
     * 生成测试配送区域
     * 为测试中使用的每个城市创建一个配送区域
     */
    private void generateTestDeliveryRegions() {
        logger.info("开始生成测试配送区域数据...");

        // 检查是否已存在配送区域，避免重复创建
        List<DeliveryRegion> existingRegions = regionService.getAllRegions();
        if (!existingRegions.isEmpty()) {
            logger.info("系统中已存在{}个配送区域，跳过测试区域生成", existingRegions.size());
            return;
        }

        // 为每个城市创建测试区域
        for (int i = 0; i < cities.size(); i++) {
            String city = cities.get(i);

            try {
                // 生成一个覆盖该城市的矩形配送区域
                createCityDeliveryRegion(city, i);
                logger.info("成功创建{}的配送区域", city);
            } catch (Exception e) {
                logger.warn("创建{}配送区域失败: {}", city, e.getMessage());
            }
        }

        // 创建一个覆盖所有城市的全局配送区域，作为兜底
        try {
            createGlobalDeliveryRegion();
            logger.info("成功创建全局配送区域");
        } catch (Exception e) {
            logger.warn("创建全局配送区域失败: {}", e.getMessage());
        }

        logger.info("测试配送区域数据生成完成");
    }

    /**
     * 为单个城市创建配送区域
     */
    private void createCityDeliveryRegion(String city, int index) {
        // 为每个城市设置不同的基准坐标，避免区域重叠
        double baseLat = 20.0 + index * 2.0;  // 基础纬度（北纬20-40度范围内）
        double baseLng = 100.0 + index * 2.0; // 基础经度（东经100-120度范围内）

        // 创建一个矩形区域，边长约为1度（大约111公里）
        double offset = 0.5; // 向四周扩展0.5度

        // 创建4个点的多边形（四个角），按照"经度,纬度"格式
        List<String> boundaryPoints = new ArrayList<>();

        // 左下角
        boundaryPoints.add(String.format("%.6f,%.6f", baseLng - offset, baseLat - offset));
        // 右下角
        boundaryPoints.add(String.format("%.6f,%.6f", baseLng + offset, baseLat - offset));
        // 右上角
        boundaryPoints.add(String.format("%.6f,%.6f", baseLng + offset, baseLat + offset));
        // 左上角
        boundaryPoints.add(String.format("%.6f,%.6f", baseLng - offset, baseLat + offset));

        // 设置配送费率（不同城市费率有所不同）
        double rateMultiplier = 1.0 + (index % 4) * 0.1; // 1.0, 1.1, 1.2, 1.3循环
        int priority = 10 - index % 5; // 优先级10,9,8,7,6循环，数字越大优先级越高

        // 创建请求对象 - 注意参数顺序
        RegionCreateRequest request = new RegionCreateRequest(
                city + "配送区",           // name
                city + "及周边地区的配送范围", // description
                rateMultiplier,           // rateMultiplier
                priority,                 // priority
                true,                     // active
                boundaryPoints            // boundaryPoints
        );

        // 调用服务创建区域
        regionService.createRegion(request);
    }

    /**
     * 创建一个覆盖所有测试城市的全局配送区域
     * 作为兜底区域，确保所有坐标都能找到对应区域
     */
    private void createGlobalDeliveryRegion() {
        // 定义一个覆盖中国大部分地区的矩形区域
        double minLng = 73.0;  // 最西点约为新疆
        double maxLng = 135.0; // 最东点约为黑龙江
        double minLat = 18.0;  // 最南点约为海南
        double maxLat = 53.0;  // 最北点约为黑龙江

        // 创建一个大矩形，按照"经度,纬度"格式
        List<String> boundaryPoints = new ArrayList<>();

        // 左下角
        boundaryPoints.add(String.format("%.6f,%.6f", minLng, minLat));
        // 右下角
        boundaryPoints.add(String.format("%.6f,%.6f", maxLng, minLat));
        // 右上角
        boundaryPoints.add(String.format("%.6f,%.6f", maxLng, maxLat));
        // 左上角
        boundaryPoints.add(String.format("%.6f,%.6f", minLng, maxLat));

        // 创建请求对象 - 注意参数顺序
        RegionCreateRequest request = new RegionCreateRequest(
                "全国配送区",              // name
                "覆盖全国范围的兜底配送区域",  // description
                1.5,                      // rateMultiplier
                1,                        // priority
                true,                     // active
                boundaryPoints            // boundaryPoints
        );

        // 调用服务创建区域
        regionService.createRegion(request);
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
            user.setUserGroup("messenger");
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
     * 生成随机地址
     * 改进版：确保地址格式一致且完整
     */
    private String generateRandomAddress() {
        String city = getRandomItem(cities);
        String district = getRandomItem(districts);
        String street = getRandomItem(streets);
        String detail = getRandomItem(details);

        // 确保地址每个部分都不为null且不为空
        if (city == null || city.trim().isEmpty()) {
            city = "北京市";  // 默认城市
        }

        if (district == null || district.trim().isEmpty()) {
            district = "海淀区";  // 默认区
        }

        if (street == null || street.trim().isEmpty()) {
            street = "中关村大街";  // 默认街道
        }

        if (detail == null || detail.trim().isEmpty()) {
            detail = "1号楼101室";  // 默认详细地址
        }

        // 添加随机门牌号，确保每个地址都略有不同
        String randomNumber = String.valueOf(100 + random.nextInt(900));

        return city + district + street + randomNumber + "号 " + detail;
    }

    /**
     * 生成随机详细地址
     * 包含更详细的信息，适用于配送地址
     */
    private String generateRandomDetailedAddress() {
        String baseAddress = generateRandomAddress();
        String contactName = getRandomItem(contactNames);
        String contactPhone = getRandomItem(contactPhones);

        return baseAddress + " " + contactName + " " + contactPhone;
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
     * 修改：添加测试标记前缀
     */
    private String generateRandomOrderName() {
        return TEST_ORDER_PREFIX + getRandomItem(orderNamePrefixes) + " " + generateRandomCode();
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

    /**
     * 生成快递代拿订单(MailOrder)
     * 增强版：确保地址信息完整并添加详细日志记录
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

            // 设置地址信息，确保完整性
            String pickupAddress = generateRandomAddress();
            String deliveryAddress = generateRandomDetailedAddress();

            // 验证地址完整性
            if (isNullOrEmpty(pickupAddress)) {
                pickupAddress = "北京市海淀区中关村大街1号";  // 提供默认值
                logger.warn("订单 {} 的取件地址为空，已设置默认地址", order.getOrderNumber());
            }

            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路2号 李先生 13800138000";  // 提供默认值
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", order.getOrderNumber());
            }

            order.setPickupAddress(pickupAddress);
            order.setPickupLatitude(generateRandomCoordinates()[0]);
            order.setPickupLongitude(generateRandomCoordinates()[1]);

            // 设置取件门牌号详情
            order.setPickupDetail(generateRandomPickupDetail());

            order.setDeliveryAddress(deliveryAddress);
            order.setDeliveryLatitude(generateRandomCoordinates()[0]);
            order.setDeliveryLongitude(generateRandomCoordinates()[1]);

            // 设置配送门牌号详情
            order.setDeliveryDetail(generateRandomPickupDetail());

            // 设置收件信息
            order.setPickupCode(generateRandomCode());
            order.setContactInfo(customer.getUsername() + ": " + "1380000" + String.format("%04d", i + 1));

            // 设置物流信息
            order.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            order.setLargeItem(random.nextInt(10) == 0); // 10%的概率是大件

            // 设置配送服务和距离
            order.setDeliveryService(random.nextBoolean() ? DeliveryService.STANDARD : DeliveryService.EXPRESS);
            order.setDeliveryDistance(1.0 + random.nextDouble() * 9.0); // 1-10km

            // ===== 添加详细日志 - 设置状态前 =====
            OrderStatus statusToSet = determineMailOrderStatus("COMPLETED_ORDER");
            logger.info("==================== 快递订单[{}]状态详细信息 ====================", order.getOrderNumber());
            logger.info("准备设置订单状态: {}", statusToSet);
            logger.info("状态对象类型: {}", statusToSet.getClass().getName());
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态ordinal(): {}", statusToSet.ordinal());

            // 检查此状态是否在枚举定义中有效
            boolean isInEnum = false;
            for (OrderStatus validStatus : OrderStatus.values()) {
                if (validStatus == statusToSet) {
                    isInEnum = true;
                    break;
                }
            }
            logger.info("状态值在OrderStatus枚举中是否有效: {}", isInEnum);

            // 检查状态名称是否在数据库ENUM定义中
            String[] validDbValues = {
                    "PAYMENT_PENDING", "CANCELLED", "PENDING",
                    "ASSIGNED", "IN_TRANSIT", "DELIVERED", "COMPLETED",
                    "PLATFORM_INTERVENTION", "REFUNDING", "REFUNDED", "LOCKED",
                    "PAYMENT_TIMEOUT"
            };
            boolean isInDbEnum = Arrays.asList(validDbValues).contains(statusToSet.name());
            logger.info("状态名称在数据库ENUM定义中是否有效: {}", isInDbEnum);
            logger.info("===========================================================");

            // 设置配送员和状态
            try {
                order.setAssignedUser(deliveryUser);
                order.setOrderStatus(statusToSet);
                logger.info("状态设置成功！");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

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

            // 记录其他关键字段的值
            logger.info("快递订单其他关键字段信息:");
            logger.info("- order_number: {}", order.getOrderNumber());
            logger.info("- delivery_service: {}", order.getDeliveryService());
            logger.info("- pickup_code: {}", order.getPickupCode());
            logger.info("- fee: {}", order.getFee());

            try {
                MailOrder savedOrder = mailOrderRepository.save(order);
                logger.info("已完成快递订单保存成功，ID: {}", savedOrder.getId());
                mailOrders.add(savedOrder);
            } catch (Exception e) {
                logger.error("保存已完成快递订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
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

            // 设置地址信息，确保完整性
            String pickupAddress = generateRandomAddress();
            String deliveryAddress = generateRandomDetailedAddress();

            // 验证地址完整性
            if (isNullOrEmpty(pickupAddress)) {
                pickupAddress = "北京市海淀区中关村大街1号";
                logger.warn("订单 {} 的取件地址为空，已设置默认地址", order.getOrderNumber());
            }

            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路2号 李先生 13800138000";
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", order.getOrderNumber());
            }

            order.setPickupDetail(generateRandomPickupDetail());
            order.setDeliveryDetail(generateRandomPickupDetail());
            order.setPickupAddress(pickupAddress);
            order.setPickupLatitude(generateRandomCoordinates()[0]);
            order.setPickupLongitude(generateRandomCoordinates()[1]);
            order.setDeliveryAddress(deliveryAddress);
            order.setDeliveryLatitude(generateRandomCoordinates()[0]);
            order.setDeliveryLongitude(generateRandomCoordinates()[1]);

            // 设置收件信息
            order.setPickupCode(generateRandomCode());
            order.setContactInfo(customer.getUsername() + ": " + "1380000" + String.format("%04d", completedCount + i + 1));

            // 设置物流信息
            order.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            order.setLargeItem(random.nextInt(10) == 0); // 10%的概率是大件

            // 设置配送服务和距离
            order.setDeliveryService(random.nextBoolean() ? DeliveryService.STANDARD : DeliveryService.EXPRESS);
            order.setDeliveryDistance(1.0 + random.nextDouble() * 9.0); // 1-10km

            // ===== 添加详细日志 - 设置状态前 =====
            OrderStatus statusToSet = determineMailOrderStatus("CANCELLED_ORDER");
            logger.info("==================== 快递订单[{}]状态详细信息 ====================", order.getOrderNumber());
            logger.info("准备设置订单状态: {}", statusToSet);
            logger.info("状态对象类型: {}", statusToSet.getClass().getName());
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态ordinal(): {}", statusToSet.ordinal());

            // 检查此状态是否在枚举定义中有效
            boolean isInEnum = false;
            for (OrderStatus validStatus : OrderStatus.values()) {
                if (validStatus == statusToSet) {
                    isInEnum = true;
                    break;
                }
            }
            logger.info("状态值在OrderStatus枚举中是否有效: {}", isInEnum);

            // 检查状态名称是否在数据库ENUM定义中
            String[] validDbValues = {
                    "PAYMENT_PENDING", "CANCELLED", "PENDING",
                    "ASSIGNED", "IN_TRANSIT", "DELIVERED", "COMPLETED",
                    "PLATFORM_INTERVENTION", "REFUNDING", "REFUNDED", "LOCKED",
                    "PAYMENT_TIMEOUT"
            };
            boolean isInDbEnum = Arrays.asList(validDbValues).contains(statusToSet.name());
            logger.info("状态名称在数据库ENUM定义中是否有效: {}", isInDbEnum);
            logger.info("===========================================================");

            // 如果随机分配了配送员，则设置
            try {
                order.setAssignedUser(deliveryUser);
                // 设置状态为已取消
                order.setOrderStatus(statusToSet);
                logger.info("状态设置成功！");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

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

            // 记录其他关键字段的值
            logger.info("已取消快递订单其他关键字段信息:");
            logger.info("- order_number: {}", order.getOrderNumber());
            logger.info("- lock_reason: {}", order.getLockReason());
            logger.info("- user_income: {}", order.getUserIncome());

            try {
                MailOrder savedOrder = mailOrderRepository.save(order);
                logger.info("已取消快递订单保存成功，ID: {}", savedOrder.getId());
                mailOrders.add(savedOrder);
            } catch (Exception e) {
                logger.error("保存已取消快递订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
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

            // 设置地址信息，确保完整性
            String pickupAddress = generateRandomAddress();
            String deliveryAddress = generateRandomDetailedAddress();

            // 验证地址完整性
            if (isNullOrEmpty(pickupAddress)) {
                pickupAddress = "北京市海淀区中关村大街1号";
                logger.warn("订单 {} 的取件地址为空，已设置默认地址", order.getOrderNumber());
            }

            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路2号 李先生 13800138000";
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", order.getOrderNumber());
            }
            order.setPickupDetail(generateRandomPickupDetail());
            order.setDeliveryDetail(generateRandomPickupDetail());
            order.setPickupAddress(pickupAddress);
            order.setPickupLatitude(generateRandomCoordinates()[0]);
            order.setPickupLongitude(generateRandomCoordinates()[1]);
            order.setDeliveryAddress(deliveryAddress);
            order.setDeliveryLatitude(generateRandomCoordinates()[0]);
            order.setDeliveryLongitude(generateRandomCoordinates()[1]);

            // 设置收件信息
            order.setPickupCode(generateRandomCode());
            order.setContactInfo(customer.getUsername() + ": " + "1380000" + String.format("%04d", completedCount + cancelledCount + i + 1));

            // 设置物流信息
            order.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            order.setLargeItem(random.nextInt(10) == 0); // 10%的概率是大件

            // 设置配送服务和距离
            order.setDeliveryService(random.nextBoolean() ? DeliveryService.STANDARD : DeliveryService.EXPRESS);
            order.setDeliveryDistance(1.0 + random.nextDouble() * 9.0); // 1-10km

            // 随机设置不同的活跃状态
            String scenario;
            if (deliveryUser == null) {
                // 未分配配送员时，状态只能是PENDING
                scenario = "AWAITING_PICKUP";
            } else {
                // 已分配配送员，随机设置状态
                String[] activeScenarios = {
                        "ASSIGNED_TO_DELIVERY", "IN_DELIVERY", "DELIVERED_PENDING_CONFIRM"
                };
                scenario = activeScenarios[random.nextInt(activeScenarios.length)];
            }

            // ===== 添加详细日志 - 设置状态前 =====
            OrderStatus statusToSet = determineMailOrderStatus(scenario);
            logger.info("==================== 快递订单[{}]状态详细信息 ====================", order.getOrderNumber());
            logger.info("准备设置订单状态: {}", statusToSet);
            logger.info("状态对象类型: {}", statusToSet.getClass().getName());
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态ordinal(): {}", statusToSet.ordinal());
            logger.info("活跃订单场景: {}", scenario);

            // 检查此状态是否在枚举定义中有效
            boolean isInEnum = false;
            for (OrderStatus validStatus : OrderStatus.values()) {
                if (validStatus == statusToSet) {
                    isInEnum = true;
                    break;
                }
            }
            logger.info("状态值在OrderStatus枚举中是否有效: {}", isInEnum);

            // 检查状态名称是否在数据库ENUM定义中
            String[] validDbValues = {
                    "PAYMENT_PENDING", "CANCELLED", "PENDING",
                    "ASSIGNED", "IN_TRANSIT", "DELIVERED", "COMPLETED",
                    "PLATFORM_INTERVENTION", "REFUNDING", "REFUNDED", "LOCKED",
                    "PAYMENT_TIMEOUT"
            };
            boolean isInDbEnum = Arrays.asList(validDbValues).contains(statusToSet.name());
            logger.info("状态名称在数据库ENUM定义中是否有效: {}", isInDbEnum);
            logger.info("===========================================================");

            // 设置配送员
            try {
                order.setAssignedUser(deliveryUser);
                order.setOrderStatus(statusToSet);
                logger.info("状态设置成功！");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

            // 根据状态设置相应的时间
            LocalDateTime deliveryTime = createdAt.plusMinutes(30 + random.nextInt(90)); // 30-120分钟后送达
            order.setDeliveryTime(deliveryTime);

            if (statusToSet == OrderStatus.IN_TRANSIT || statusToSet == OrderStatus.DELIVERED) {
                // 设置取件时间但不设置送达时间（IN_TRANSIT）
            }

            if (statusToSet == OrderStatus.DELIVERED) {
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

            // 记录其他关键字段的值
            logger.info("活跃快递订单其他关键字段信息:");
            logger.info("- order_number: {}", order.getOrderNumber());
            logger.info("- delivery_time: {}", order.getDeliveryTime());
            if (order.getDeliveredDate() != null) {
                logger.info("- delivered_date: {}", order.getDeliveredDate());
            }

            try {
                MailOrder savedOrder = mailOrderRepository.save(order);
                logger.info("活跃快递订单保存成功，ID: {}", savedOrder.getId());
                mailOrders.add(savedOrder);
            } catch (Exception e) {
                logger.error("保存活跃快递订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
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

            // 设置地址信息，确保完整性
            String pickupAddress = generateRandomAddress();
            String deliveryAddress = generateRandomDetailedAddress();

            // 验证地址完整性
            if (isNullOrEmpty(pickupAddress)) {
                pickupAddress = "北京市海淀区中关村大街1号";
                logger.warn("订单 {} 的取件地址为空，已设置默认地址", order.getOrderNumber());
            }

            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路2号 李先生 13800138000";
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", order.getOrderNumber());
            }
            order.setPickupDetail(generateRandomPickupDetail());
            order.setDeliveryDetail(generateRandomPickupDetail());
            order.setPickupAddress(pickupAddress);
            order.setPickupLatitude(generateRandomCoordinates()[0]);
            order.setPickupLongitude(generateRandomCoordinates()[1]);
            order.setDeliveryAddress(deliveryAddress);
            order.setDeliveryLatitude(generateRandomCoordinates()[0]);
            order.setDeliveryLongitude(generateRandomCoordinates()[1]);

            // 设置收件信息
            order.setPickupCode(generateRandomCode());
            order.setContactInfo(customer.getUsername() + ": " + "1380000" + String.format("%04d", completedCount + cancelledCount + normalActiveCount + i + 1));

            // 设置物流信息
            order.setWeight(0.5 + random.nextDouble() * 4.5); // 0.5-5kg
            order.setLargeItem(random.nextInt(10) == 0); // 10%的概率是大件

            // 设置配送服务和距离
            order.setDeliveryService(random.nextBoolean() ? DeliveryService.STANDARD : DeliveryService.EXPRESS);
            order.setDeliveryDistance(1.0 + random.nextDouble() * 9.0); // 1-10km

            // 设置状态和时间
            String scenario;
            if (i % 2 == 0) {
                // 设置为平台介入状态
                scenario = "DISPUTE";
            } else {
                // 设置为其他活跃状态但已超时
                String[] timeoutScenarios = {"ASSIGNED_TO_DELIVERY", "IN_DELIVERY"};
                scenario = timeoutScenarios[random.nextInt(timeoutScenarios.length)];
            }

            // ===== 添加详细日志 - 设置状态前 =====
            OrderStatus statusToSet = determineMailOrderStatus(scenario);
            logger.info("==================== 快递订单[{}]状态详细信息 ====================", order.getOrderNumber());
            logger.info("准备设置订单状态: {}", statusToSet);
            logger.info("状态对象类型: {}", statusToSet.getClass().getName());
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态ordinal(): {}", statusToSet.ordinal());
            logger.info("超时订单场景: {}", scenario);

            // 检查此状态是否在枚举定义中有效
            boolean isInEnum = false;
            for (OrderStatus validStatus : OrderStatus.values()) {
                if (validStatus == statusToSet) {
                    isInEnum = true;
                    break;
                }
            }
            logger.info("状态值在OrderStatus枚举中是否有效: {}", isInEnum);

            // 检查状态名称是否在数据库ENUM定义中
            String[] validDbValues = {
                    "PAYMENT_PENDING", "CANCELLED", "PENDING",
                    "ASSIGNED", "IN_TRANSIT", "DELIVERED", "COMPLETED",
                    "PLATFORM_INTERVENTION", "REFUNDING", "REFUNDED", "LOCKED",
                    "PAYMENT_TIMEOUT"
            };
            boolean isInDbEnum = Arrays.asList(validDbValues).contains(statusToSet.name());
            logger.info("状态名称在数据库ENUM定义中是否有效: {}", isInDbEnum);
            logger.info("===========================================================");

            // 设置配送员
            try {
                order.setAssignedUser(deliveryUser);
                order.setOrderStatus(statusToSet);
                logger.info("状态设置成功！");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

            // 设置状态相关字段
            if (statusToSet == OrderStatus.PLATFORM_INTERVENTION) {
                order.setInterventionTime(LocalDateTime.now().minusHours(random.nextInt(24)));
            }

            // 设置很早的送达时间，确保超时
            LocalDateTime deliveryTime = createdAt.plusMinutes(30);
            order.setDeliveryTime(deliveryTime);

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

            // 记录其他关键字段的值
            logger.info("超时快递订单其他关键字段信息:");
            logger.info("- order_number: {}", order.getOrderNumber());
            logger.info("- timeout_status: {}", order.getTimeoutStatus());
            logger.info("- timeout_count: {}", order.getTimeoutCount());
            if (order.getInterventionTime() != null) {
                logger.info("- intervention_time: {}", order.getInterventionTime());
            }

            try {
                MailOrder savedOrder = mailOrderRepository.save(order);
                logger.info("超时快递订单保存成功，ID: {}", savedOrder.getId());
                mailOrders.add(savedOrder);
            } catch (Exception e) {
                logger.error("保存超时快递订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
        }

        return mailOrders;
    }

    /**
     * 生成商家订单(ShoppingOrder)
     * 增强版：确保地址信息完整，使用正确的状态决定方法
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

            // 设置收件信息，确保完整性
            String recipientName = getRandomItem(contactNames);
            String recipientPhone = getRandomItem(contactPhones);
            if (isNullOrEmpty(recipientName)) {
                recipientName = "默认收件人";
                logger.warn("订单 {} 的收件人姓名为空，已设置默认值", order.getOrderNumber());
            }
            if (isNullOrEmpty(recipientPhone)) {
                recipientPhone = "13800138000";
                logger.warn("订单 {} 的收件人电话为空，已设置默认值", order.getOrderNumber());
            }
            order.setRecipientName(recipientName);
            order.setRecipientPhone(recipientPhone);

            // 设置配送地址，确保完整性
            String deliveryAddress = generateRandomDetailedAddress();
            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路100号 " + recipientName + " " + recipientPhone;
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", order.getOrderNumber());
            }
            order.setDeliveryAddress(deliveryAddress);

            // 设置坐标，确保完整性
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送类型和距离
            order.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);
            order.setDeliveryDistance(0.5 + random.nextDouble() * 5.5); // 0.5-6km

            // 使用状态决定方法
            OrderStatus statusToSet = determineOrderStatus("COMPLETED_ORDER");

            logger.info("==================== 订单[{}]状态详细信息 ====================", order.getOrderNumber());
            logger.info("准备设置订单状态: {}", statusToSet);
            logger.info("状态对象类型: {}", statusToSet.getClass().getName());
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态ordinal(): {}", statusToSet.ordinal());

            // 检查此状态是否在枚举定义中有效
            boolean isInEnum = false;
            for (OrderStatus validStatus : OrderStatus.values()) {
                if (validStatus == statusToSet) {
                    isInEnum = true;
                    break;
                }
            }
            logger.info("状态值在OrderStatus枚举中是否有效: {}", isInEnum);

            // 检查状态名称是否在数据库ENUM定义中
            String[] validDbValues = {
                    "MERCHANT_PENDING", "PAYMENT_PENDING", "CANCELLED", "PENDING",
                    "ASSIGNED", "IN_TRANSIT", "DELIVERED", "COMPLETED",
                    "PLATFORM_INTERVENTION", "REFUNDING", "REFUNDED", "LOCKED",
                    "PAYMENT_TIMEOUT"
            };
            boolean isInDbEnum = Arrays.asList(validDbValues).contains(statusToSet.name());
            logger.info("状态名称在数据库ENUM定义中是否有效: {}", isInDbEnum);
            logger.info("===========================================================");

            // 设置配送员和状态
            try {
                order.setAssignedUser(deliveryUser);
                order.setOrderStatus(statusToSet);
                logger.info("状态设置成功!");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

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
                order.setRemark(TEST_ORDER_PREFIX + "请尽快配送，谢谢！");
            }

            // 记录其他关键字段的值
            logger.info("订单其他关键字段信息:");
            logger.info("- order_number: {}", order.getOrderNumber());
            logger.info("- delivery_type: {}", order.getDeliveryType());
            logger.info("- quantity: {}", order.getQuantity());

            try {
                ShoppingOrder savedOrder = shoppingOrderRepository.save(order);
                logger.info("订单保存成功，ID: {}", savedOrder.getId());
                shoppingOrders.add(savedOrder);
            } catch (Exception e) {
                logger.error("保存订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
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

            // 设置收件信息，确保完整性
            String recipientName = getRandomItem(contactNames);
            String recipientPhone = getRandomItem(contactPhones);
            if (isNullOrEmpty(recipientName)) {
                recipientName = "默认收件人";
                logger.warn("订单 {} 的收件人姓名为空，已设置默认值", order.getOrderNumber());
            }
            if (isNullOrEmpty(recipientPhone)) {
                recipientPhone = "13800138000";
                logger.warn("订单 {} 的收件人电话为空，已设置默认值", order.getOrderNumber());
            }
            order.setRecipientName(recipientName);
            order.setRecipientPhone(recipientPhone);

            // 设置配送地址，确保完整性
            String deliveryAddress = generateRandomDetailedAddress();
            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路100号 " + recipientName + " " + recipientPhone;
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", order.getOrderNumber());
            }
            order.setDeliveryAddress(deliveryAddress);

            // 设置坐标，确保完整性
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送类型和距离
            order.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);
            order.setDeliveryDistance(0.5 + random.nextDouble() * 5.5); // 0.5-6km

            // 使用状态决定方法
            OrderStatus statusToSet = determineOrderStatus("CANCELLED_ORDER");

            logger.info("==================== 订单[{}]状态详细信息 ====================", order.getOrderNumber());
            logger.info("准备设置订单状态: {}", statusToSet);
            logger.info("状态对象类型: {}", statusToSet.getClass().getName());
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态ordinal(): {}", statusToSet.ordinal());
            logger.info("状态值在OrderStatus枚举中是否有效: {}", Arrays.asList(OrderStatus.values()).contains(statusToSet));
            logger.info("状态名称在数据库ENUM定义中是否有效: {}",
                    Arrays.asList("MERCHANT_PENDING","PAYMENT_PENDING","CANCELLED","PENDING",
                            "ASSIGNED","IN_TRANSIT","DELIVERED","COMPLETED",
                            "PLATFORM_INTERVENTION","REFUNDING","REFUNDED","LOCKED",
                            "PAYMENT_TIMEOUT").contains(statusToSet.name()));
            logger.info("===========================================================");

            // 设置为已取消状态
            try {
                order.setOrderStatus(statusToSet);
                logger.info("状态设置成功!");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

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

            try {
                ShoppingOrder savedOrder = shoppingOrderRepository.save(order);
                logger.info("已取消订单保存成功，ID: {}", savedOrder.getId());
                shoppingOrders.add(savedOrder);
            } catch (Exception e) {
                logger.error("保存已取消订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
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

            // 设置收件信息，确保完整性
            String recipientName = getRandomItem(contactNames);
            String recipientPhone = getRandomItem(contactPhones);
            if (isNullOrEmpty(recipientName)) {
                recipientName = "默认收件人";
                logger.warn("订单 {} 的收件人姓名为空，已设置默认值", order.getOrderNumber());
            }
            if (isNullOrEmpty(recipientPhone)) {
                recipientPhone = "13800138000";
                logger.warn("订单 {} 的收件人电话为空，已设置默认值", order.getOrderNumber());
            }
            order.setRecipientName(recipientName);
            order.setRecipientPhone(recipientPhone);

            // 设置配送地址，确保完整性
            String deliveryAddress = generateRandomDetailedAddress();
            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路100号 " + recipientName + " " + recipientPhone;
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", order.getOrderNumber());
            }
            order.setDeliveryAddress(deliveryAddress);

            // 设置坐标，确保完整性
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送类型和距离
            order.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);
            order.setDeliveryDistance(0.5 + random.nextDouble() * 5.5); // 0.5-6km

            // 使用状态决定方法
            OrderStatus statusToSet = determineOrderStatus("PAYMENT_NEEDED");

            logger.info("==================== 订单[{}]状态详细信息 ====================", order.getOrderNumber());
            logger.info("准备设置订单状态: {}", statusToSet);
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态值在OrderStatus枚举中是否有效: {}", Arrays.asList(OrderStatus.values()).contains(statusToSet));
            logger.info("===========================================================");

            // 设置状态为支付待处理
            try {
                order.setOrderStatus(statusToSet);
                logger.info("状态设置成功!");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

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

            try {
                ShoppingOrder savedOrder = shoppingOrderRepository.save(order);
                logger.info("支付待处理订单保存成功，ID: {}", savedOrder.getId());
                shoppingOrders.add(savedOrder);
            } catch (Exception e) {
                logger.error("保存支付待处理订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
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

            // 设置收件信息，确保完整性
            String recipientName = getRandomItem(contactNames);
            String recipientPhone = getRandomItem(contactPhones);
            if (isNullOrEmpty(recipientName)) {
                recipientName = "默认收件人";
                logger.warn("订单 {} 的收件人姓名为空，已设置默认值", order.getOrderNumber());
            }
            if (isNullOrEmpty(recipientPhone)) {
                recipientPhone = "13800138000";
                logger.warn("订单 {} 的收件人电话为空，已设置默认值", order.getOrderNumber());
            }
            order.setRecipientName(recipientName);
            order.setRecipientPhone(recipientPhone);

            // 设置配送地址，确保完整性
            String deliveryAddress = generateRandomDetailedAddress();
            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路100号 " + recipientName + " " + recipientPhone;
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", order.getOrderNumber());
            }
            order.setDeliveryAddress(deliveryAddress);

            // 设置坐标，确保完整性
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送类型和距离
            order.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);
            order.setDeliveryDistance(0.5 + random.nextDouble() * 5.5); // 0.5-6km

            // 根据情况决定状态场景
            String scenario;
            if (deliveryUser == null) {
                // 未分配配送员时，随机选择合适的状态
                String[] possibleScenarios = {"PENDING_DELIVERY", "STORE_CONFIRMATION", "PAYMENT_NEEDED"};
                scenario = possibleScenarios[random.nextInt(possibleScenarios.length)];
            } else {
                // 已分配配送员，随机设置状态
                String[] possibleScenarios = {
                        "STORE_CONFIRMATION", "PENDING_DELIVERY",
                        "ASSIGNED_DELIVERY", "IN_DELIVERY", "DELIVERED_ORDER"
                };
                scenario = possibleScenarios[random.nextInt(possibleScenarios.length)];
            }

            // 使用状态决定方法
            OrderStatus statusToSet = determineOrderStatus(scenario);

            logger.info("==================== 订单[{}]状态详细信息 ====================", order.getOrderNumber());
            logger.info("场景: {}, 准备设置订单状态: {}", scenario, statusToSet);
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态值在OrderStatus枚举中是否有效: {}", Arrays.asList(OrderStatus.values()).contains(statusToSet));
            logger.info("===========================================================");

            try {
                order.setOrderStatus(statusToSet);
                order.setAssignedUser(deliveryUser);
                logger.info("状态设置成功!");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

            // 根据状态设置相应的时间
            LocalDateTime paymentTime = createdAt.plusMinutes(random.nextInt(30)); // 0-30分钟后支付
            order.setPaymentTime(paymentTime);

            if (statusToSet == OrderStatus.ASSIGNED || statusToSet == OrderStatus.IN_TRANSIT ||
                    statusToSet == OrderStatus.DELIVERED) {
                LocalDateTime expectedDeliveryTime = paymentTime.plusMinutes(30 + random.nextInt(90)); // 30-120分钟后送达
                order.setExpectedDeliveryTime(expectedDeliveryTime);
            }

            if (statusToSet == OrderStatus.DELIVERED) {
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

            try {
                ShoppingOrder savedOrder = shoppingOrderRepository.save(order);
                logger.info("活跃订单保存成功，ID: {}", savedOrder.getId());
                shoppingOrders.add(savedOrder);
            } catch (Exception e) {
                logger.error("保存活跃订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
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

            // 设置收件信息，确保完整性
            String recipientName = getRandomItem(contactNames);
            String recipientPhone = getRandomItem(contactPhones);
            if (isNullOrEmpty(recipientName)) {
                recipientName = "默认收件人";
                logger.warn("订单 {} 的收件人姓名为空，已设置默认值", order.getOrderNumber());
            }
            if (isNullOrEmpty(recipientPhone)) {
                recipientPhone = "13800138000";
                logger.warn("订单 {} 的收件人电话为空，已设置默认值", order.getOrderNumber());
            }
            order.setRecipientName(recipientName);
            order.setRecipientPhone(recipientPhone);

            // 设置配送地址，确保完整性
            String deliveryAddress = generateRandomDetailedAddress();
            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路100号 " + recipientName + " " + recipientPhone;
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", order.getOrderNumber());
            }
            order.setDeliveryAddress(deliveryAddress);

            // 设置坐标，确保完整性
            double[] deliveryCoordinates = generateRandomCoordinates();
            order.setDeliveryLatitude(deliveryCoordinates[0]);
            order.setDeliveryLongitude(deliveryCoordinates[1]);

            // 设置配送类型和距离
            order.setDeliveryType(random.nextBoolean() ? DeliveryType.MUTUAL : DeliveryType.PLATFORM);
            order.setDeliveryDistance(0.5 + random.nextDouble() * 5.5); // 0.5-6km

            // 根据循环索引选择不同的超时状态场景
            String scenario;
            if (i % 3 == 0) {
                // 设置为平台介入状态
                scenario = "DISPUTE";
            } else if (i % 3 == 1) {
                // 设置为IN_TRANSIT但已超时
                scenario = "IN_DELIVERY";
            } else {
                // 设置为ASSIGNED但已超时
                scenario = "ASSIGNED_DELIVERY";
            }

            // 使用状态决定方法
            OrderStatus statusToSet = determineOrderStatus(scenario);

            logger.info("==================== 超时订单[{}]状态详细信息 ====================", order.getOrderNumber());
            logger.info("场景: {}, 准备设置订单状态: {}", scenario, statusToSet);
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态值在OrderStatus枚举中是否有效: {}", Arrays.asList(OrderStatus.values()).contains(statusToSet));
            logger.info("===========================================================");

            // 设置配送员
            try {
                order.setAssignedUser(deliveryUser);
                order.setOrderStatus(statusToSet);
                logger.info("状态设置成功!");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

            if (statusToSet == OrderStatus.PLATFORM_INTERVENTION) {
                order.setInterventionTime(LocalDateTime.now().minusHours(random.nextInt(48)));
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

            try {
                ShoppingOrder savedOrder = shoppingOrderRepository.save(order);
                logger.info("超时订单保存成功，ID: {}", savedOrder.getId());
                shoppingOrders.add(savedOrder);
            } catch (Exception e) {
                logger.error("保存超时订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
        }

        return shoppingOrders;
    }

    /**
     * 生成代购订单(PurchaseRequest)
     * 增强版：确保地址信息完整，避免使用MERCHANT_PENDING状态
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

            // 设置采购地址和配送地址，确保完整性
            String purchaseAddress = generateRandomAddress();
            String deliveryAddress = generateRandomDetailedAddress();

            // 验证地址完整性
            if (isNullOrEmpty(purchaseAddress)) {
                purchaseAddress = "北京市海淀区中关村大街1号";
                logger.warn("订单 {} 的采购地址为空，已设置默认地址", request.getRequestNumber());
            }

            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路2号 李先生 13800138000";
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", request.getRequestNumber());
            }

            request.setPurchaseAddress(purchaseAddress);
            request.setPurchaseLatitude(generateRandomCoordinates()[0]);
            request.setPurchaseLongitude(generateRandomCoordinates()[1]);
            request.setDeliveryAddress(deliveryAddress);
            request.setDeliveryLatitude(generateRandomCoordinates()[0]);
            request.setDeliveryLongitude(generateRandomCoordinates()[1]);

            // 设置配送距离
            request.setDeliveryDistance(0.5 + random.nextDouble() * 9.5); // 0.5-10km

            // 设置收件人信息，确保完整性
            String recipientName = getRandomItem(contactNames);
            String recipientPhone = getRandomItem(contactPhones);
            if (isNullOrEmpty(recipientName)) {
                recipientName = "默认收件人";
                logger.warn("订单 {} 的收件人姓名为空，已设置默认值", request.getRequestNumber());
            }
            if (isNullOrEmpty(recipientPhone)) {
                recipientPhone = "13800138000";
                logger.warn("订单 {} 的收件人电话为空，已设置默认值", request.getRequestNumber());
            }
            request.setRecipientName(recipientName);
            request.setRecipientPhone(recipientPhone);

            // 使用状态决定方法 - 代购订单不使用MERCHANT_PENDING
            OrderStatus statusToSet = determinePurchaseStatus("COMPLETED_ORDER");

            logger.info("==================== 代购订单[{}]状态详细信息 ====================", request.getRequestNumber());
            logger.info("准备设置订单状态: {}", statusToSet);
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态值在OrderStatus枚举中是否有效: {}", Arrays.asList(OrderStatus.values()).contains(statusToSet));
            logger.info("===========================================================");

            // 设置状态和配送员
            try {
                request.setStatus(statusToSet);
                request.setAssignedUser(deliveryUser);
                logger.info("状态设置成功!");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

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

            try {
                PurchaseRequest savedRequest = purchaseRequestRepository.save(request);
                logger.info("已完成代购订单保存成功，ID: {}", savedRequest.getId());
                purchaseRequests.add(savedRequest);
            } catch (Exception e) {
                logger.error("保存已完成代购订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
        }

        // 2. 生成已取消订单
        for (int i = 0; i < cancelledCount; i++) {
            User customer = getRandomUser(customers);
            User deliveryUser = random.nextInt(10) < 3 ? getRandomUser(deliveryUsers) : null; // 30%的概率已分配配送员

            PurchaseRequest request = new PurchaseRequest();
            request.setRequestNumber(UUID.randomUUID());
            request.setUser(customer);

            // 设置基本信息
            request.setTitle(generateRandomPurchaseTitle());
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

            // 设置采购地址和配送地址，确保完整性
            String purchaseAddress = generateRandomAddress();
            String deliveryAddress = generateRandomDetailedAddress();

            // 验证地址完整性
            if (isNullOrEmpty(purchaseAddress)) {
                purchaseAddress = "北京市海淀区中关村大街1号";
                logger.warn("订单 {} 的采购地址为空，已设置默认地址", request.getRequestNumber());
            }

            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路2号 李先生 13800138000";
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", request.getRequestNumber());
            }

            request.setPurchaseAddress(purchaseAddress);
            request.setPurchaseLatitude(generateRandomCoordinates()[0]);
            request.setPurchaseLongitude(generateRandomCoordinates()[1]);
            request.setDeliveryAddress(deliveryAddress);
            request.setDeliveryLatitude(generateRandomCoordinates()[0]);
            request.setDeliveryLongitude(generateRandomCoordinates()[1]);

            // 设置配送距离
            request.setDeliveryDistance(0.5 + random.nextDouble() * 9.5); // 0.5-10km

            // 设置收件人信息，确保完整性
            String recipientName = getRandomItem(contactNames);
            String recipientPhone = getRandomItem(contactPhones);
            if (isNullOrEmpty(recipientName)) {
                recipientName = "默认收件人";
                logger.warn("订单 {} 的收件人姓名为空，已设置默认值", request.getRequestNumber());
            }
            if (isNullOrEmpty(recipientPhone)) {
                recipientPhone = "13800138000";
                logger.warn("订单 {} 的收件人电话为空，已设置默认值", request.getRequestNumber());
            }
            request.setRecipientName(recipientName);
            request.setRecipientPhone(recipientPhone);

            // 使用状态决定方法 - 代购订单不使用MERCHANT_PENDING
            OrderStatus statusToSet = determinePurchaseStatus("CANCELLED_ORDER");

            logger.info("==================== 代购订单[{}]状态详细信息 ====================", request.getRequestNumber());
            logger.info("准备设置订单状态: {}", statusToSet);
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态值在OrderStatus枚举中是否有效: {}", Arrays.asList(OrderStatus.values()).contains(statusToSet));
            logger.info("===========================================================");

            // 设置为已取消状态
            try {
                request.setStatus(statusToSet);
                logger.info("状态设置成功!");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

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

            try {
                PurchaseRequest savedRequest = purchaseRequestRepository.save(request);
                logger.info("已取消代购订单保存成功，ID: {}", savedRequest.getId());
                purchaseRequests.add(savedRequest);
            } catch (Exception e) {
                logger.error("保存已取消代购订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
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

            // 设置采购地址和配送地址，确保完整性
            String purchaseAddress = generateRandomAddress();
            String deliveryAddress = generateRandomDetailedAddress();

            // 验证地址完整性
            if (isNullOrEmpty(purchaseAddress)) {
                purchaseAddress = "北京市海淀区中关村大街1号";
                logger.warn("订单 {} 的采购地址为空，已设置默认地址", request.getRequestNumber());
            }

            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路2号 李先生 13800138000";
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", request.getRequestNumber());
            }

            request.setPurchaseAddress(purchaseAddress);
            request.setPurchaseLatitude(generateRandomCoordinates()[0]);
            request.setPurchaseLongitude(generateRandomCoordinates()[1]);
            request.setDeliveryAddress(deliveryAddress);
            request.setDeliveryLatitude(generateRandomCoordinates()[0]);
            request.setDeliveryLongitude(generateRandomCoordinates()[1]);

            // 设置配送距离
            request.setDeliveryDistance(0.5 + random.nextDouble() * 9.5); // 0.5-10km

            // 设置收件人信息，确保完整性
            String recipientName = getRandomItem(contactNames);
            String recipientPhone = getRandomItem(contactPhones);
            if (isNullOrEmpty(recipientName)) {
                recipientName = "默认收件人";
                logger.warn("订单 {} 的收件人姓名为空，已设置默认值", request.getRequestNumber());
            }
            if (isNullOrEmpty(recipientPhone)) {
                recipientPhone = "13800138000";
                logger.warn("订单 {} 的收件人电话为空，已设置默认值", request.getRequestNumber());
            }
            request.setRecipientName(recipientName);
            request.setRecipientPhone(recipientPhone);

            // 使用状态决定方法 - 代购订单不使用MERCHANT_PENDING
            OrderStatus statusToSet = determinePurchaseStatus("PAYMENT_NEEDED");

            logger.info("==================== 代购订单[{}]状态详细信息 ====================", request.getRequestNumber());
            logger.info("准备设置订单状态: {}", statusToSet);
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态值在OrderStatus枚举中是否有效: {}", Arrays.asList(OrderStatus.values()).contains(statusToSet));
            logger.info("===========================================================");

            // 设置状态为支付待处理
            try {
                request.setStatus(statusToSet);
                logger.info("状态设置成功!");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

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

            try {
                PurchaseRequest savedRequest = purchaseRequestRepository.save(request);
                logger.info("支付待处理代购订单保存成功，ID: {}", savedRequest.getId());
                purchaseRequests.add(savedRequest);
            } catch (Exception e) {
                logger.error("保存支付待处理代购订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
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

            // 设置采购地址和配送地址，确保完整性
            String purchaseAddress = generateRandomAddress();
            String deliveryAddress = generateRandomDetailedAddress();

            // 验证地址完整性
            if (isNullOrEmpty(purchaseAddress)) {
                purchaseAddress = "北京市海淀区中关村大街1号";
                logger.warn("订单 {} 的采购地址为空，已设置默认地址", request.getRequestNumber());
            }

            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路2号 李先生 13800138000";
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", request.getRequestNumber());
            }

            request.setPurchaseAddress(purchaseAddress);
            request.setPurchaseLatitude(generateRandomCoordinates()[0]);
            request.setPurchaseLongitude(generateRandomCoordinates()[1]);
            request.setDeliveryAddress(deliveryAddress);
            request.setDeliveryLatitude(generateRandomCoordinates()[0]);
            request.setDeliveryLongitude(generateRandomCoordinates()[1]);

            // 设置配送距离
            request.setDeliveryDistance(0.5 + random.nextDouble() * 9.5); // 0.5-10km

            // 设置收件人信息，确保完整性
            String recipientName = getRandomItem(contactNames);
            String recipientPhone = getRandomItem(contactPhones);
            if (isNullOrEmpty(recipientName)) {
                recipientName = "默认收件人";
                logger.warn("订单 {} 的收件人姓名为空，已设置默认值", request.getRequestNumber());
            }
            if (isNullOrEmpty(recipientPhone)) {
                recipientPhone = "13800138000";
                logger.warn("订单 {} 的收件人电话为空，已设置默认值", request.getRequestNumber());
            }
            request.setRecipientName(recipientName);
            request.setRecipientPhone(recipientPhone);

            // 根据情况决定状态场景 - 代购订单不使用MERCHANT_PENDING
            String scenario;
            if (deliveryUser == null) {
                // 未分配配送员时，状态只能是PENDING
                scenario = "PENDING_DELIVERY";
            } else {
                // 已分配配送员，随机设置状态
                String[] activeScenarios = {
                        "ASSIGNED_DELIVERY", "IN_DELIVERY", "DELIVERED_ORDER"
                };
                scenario = activeScenarios[random.nextInt(activeScenarios.length)];
            }

            // 使用状态决定方法
            OrderStatus statusToSet = determinePurchaseStatus(scenario);

            logger.info("==================== 代购订单[{}]状态详细信息 ====================", request.getRequestNumber());
            logger.info("场景: {}, 准备设置订单状态: {}", scenario, statusToSet);
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态值在OrderStatus枚举中是否有效: {}", Arrays.asList(OrderStatus.values()).contains(statusToSet));
            logger.info("===========================================================");

            try {
                request.setStatus(statusToSet);
                request.setAssignedUser(deliveryUser);
                logger.info("状态设置成功!");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

            // 根据状态设置相应的时间
            LocalDateTime paymentTime = createdAt.plusMinutes(random.nextInt(60)); // 0-60分钟后支付
            request.setPaymentTime(paymentTime);

            if (statusToSet == OrderStatus.IN_TRANSIT || statusToSet == OrderStatus.DELIVERED) {
                // 设置预计送达时间
                request.setDeliveryTime(paymentTime.plusHours(1 + random.nextInt(24))); // 1-25小时后送达
            }

            if (statusToSet == OrderStatus.DELIVERED) {
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

            try {
                PurchaseRequest savedRequest = purchaseRequestRepository.save(request);
                logger.info("活跃代购订单保存成功，ID: {}", savedRequest.getId());
                purchaseRequests.add(savedRequest);
            } catch (Exception e) {
                logger.error("保存活跃代购订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
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

            // 设置采购地址和配送地址，确保完整性
            String purchaseAddress = generateRandomAddress();
            String deliveryAddress = generateRandomDetailedAddress();

            // 验证地址完整性
            if (isNullOrEmpty(purchaseAddress)) {
                purchaseAddress = "北京市海淀区中关村大街1号";
                logger.warn("订单 {} 的采购地址为空，已设置默认地址", request.getRequestNumber());
            }

            if (isNullOrEmpty(deliveryAddress)) {
                deliveryAddress = "北京市朝阳区建国路2号 李先生 13800138000";
                logger.warn("订单 {} 的配送地址为空，已设置默认地址", request.getRequestNumber());
            }

            request.setPurchaseAddress(purchaseAddress);
            request.setPurchaseLatitude(generateRandomCoordinates()[0]);
            request.setPurchaseLongitude(generateRandomCoordinates()[1]);
            request.setDeliveryAddress(deliveryAddress);
            request.setDeliveryLatitude(generateRandomCoordinates()[0]);
            request.setDeliveryLongitude(generateRandomCoordinates()[1]);

            // 设置配送距离
            request.setDeliveryDistance(0.5 + random.nextDouble() * 9.5); // 0.5-10km

            // 设置收件人信息，确保完整性
            String recipientName = getRandomItem(contactNames);
            String recipientPhone = getRandomItem(contactPhones);
            if (isNullOrEmpty(recipientName)) {
                recipientName = "默认收件人";
                logger.warn("订单 {} 的收件人姓名为空，已设置默认值", request.getRequestNumber());
            }
            if (isNullOrEmpty(recipientPhone)) {
                recipientPhone = "13800138000";
                logger.warn("订单 {} 的收件人电话为空，已设置默认值", request.getRequestNumber());
            }
            request.setRecipientName(recipientName);
            request.setRecipientPhone(recipientPhone);

            // 根据循环索引选择不同的超时状态场景 - 代购订单不使用MERCHANT_PENDING
            String scenario;
            if (i % 2 == 0) {
                // 设置为平台介入状态
                scenario = "DISPUTE";
            } else {
                // 设置为其他活跃状态但已超时
                String[] timeoutScenarios = {"PENDING_DELIVERY", "ASSIGNED_DELIVERY", "IN_DELIVERY"};
                scenario = timeoutScenarios[random.nextInt(timeoutScenarios.length)];
            }

            // 使用状态决定方法
            OrderStatus statusToSet = determinePurchaseStatus(scenario);

            logger.info("==================== 超时代购订单[{}]状态详细信息 ====================", request.getRequestNumber());
            logger.info("场景: {}, 准备设置订单状态: {}", scenario, statusToSet);
            logger.info("状态toString(): {}", statusToSet.toString());
            logger.info("状态name(): {}", statusToSet.name());
            logger.info("状态值在OrderStatus枚举中是否有效: {}", Arrays.asList(OrderStatus.values()).contains(statusToSet));
            logger.info("===========================================================");

            // 设置配送员
            try {
                request.setAssignedUser(deliveryUser);
                request.setStatus(statusToSet);
                logger.info("状态设置成功!");
            } catch (Exception e) {
                logger.error("设置状态时发生异常: {}", e.getMessage(), e);
            }

            if (statusToSet == OrderStatus.PLATFORM_INTERVENTION) {
                request.setInterventionTime(LocalDateTime.now().minusHours(random.nextInt(48)));
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

            try {
                PurchaseRequest savedRequest = purchaseRequestRepository.save(request);
                logger.info("超时代购订单保存成功，ID: {}", savedRequest.getId());
                purchaseRequests.add(savedRequest);
            } catch (Exception e) {
                logger.error("保存超时代购订单时出错: {}", e.getMessage());
                logger.error("详细错误信息: ", e);
            }
        }

        return purchaseRequests;
    }

    /**
     * 验证所有订单的地址信息
     */
    private void validateAllOrderAddresses(List<MailOrder> mailOrders, List<ShoppingOrder> shoppingOrders, List<PurchaseRequest> purchaseRequests) {
        logger.info("开始验证所有订单的地址信息...");

        int fixedCount = 0;

        // 验证快递代拿订单
        // 验证快递代拿订单
        for (MailOrder order : mailOrders) {
            if (isNullOrEmpty(order.getPickupAddress()) ||
                    isNullOrEmpty(order.getDeliveryAddress()) ||
                    isNullOrEmpty(order.getPickupDetail()) ||   // 增加门牌号验证
                    isNullOrEmpty(order.getDeliveryDetail())) { // 增加门牌号验证
                validateAndFixAddresses(order);
                mailOrderRepository.save(order);
                fixedCount++;
            }
        }

        // 验证商家订单
        for (ShoppingOrder order : shoppingOrders) {
            if (isNullOrEmpty(order.getDeliveryAddress())) {
                validateAndFixAddresses(order);
                shoppingOrderRepository.save(order);
                fixedCount++;
            }
        }

        // 验证代购订单
        for (PurchaseRequest request : purchaseRequests) {
            if (isNullOrEmpty(request.getPurchaseAddress()) || isNullOrEmpty(request.getDeliveryAddress())) {
                validateAndFixAddresses(request);
                purchaseRequestRepository.save(request);
                fixedCount++;
            }
        }

        logger.info("地址信息验证完成，修复了 {} 个订单的地址信息", fixedCount);
    }

    /**
     * 验证并修复订单地址信息
     * 确保所有必要的地址信息都已设置
     */
    private void validateAndFixAddresses(Timeoutable order) {
        switch (order.getTimeoutOrderType()) {
            case MAIL_ORDER -> {
                MailOrder mailOrder = (MailOrder) order;

                // 验证并修复取件地址
                if (isNullOrEmpty(mailOrder.getPickupAddress())) {
                    mailOrder.setPickupAddress(generateRandomAddress());
                    logger.info("修复订单 {} 的空取件地址", mailOrder.getOrderNumber());
                }

                // 验证并修复门牌号 - 新增
                if (isNullOrEmpty(mailOrder.getPickupDetail())) {
                    mailOrder.setPickupDetail(generateRandomPickupDetail());
                    logger.info("修复订单 {} 的空取件门牌号", mailOrder.getOrderNumber());
                }

                // 验证并修复配送地址
                if (isNullOrEmpty(mailOrder.getDeliveryAddress())) {
                    mailOrder.setDeliveryAddress(generateRandomDetailedAddress());
                    logger.info("修复订单 {} 的空配送地址", mailOrder.getOrderNumber());
                }

                // 验证并修复配送门牌号 - 新增
                if (isNullOrEmpty(mailOrder.getDeliveryDetail())) {
                    mailOrder.setDeliveryDetail(generateRandomPickupDetail());
                    logger.info("修复订单 {} 的空配送门牌号", mailOrder.getOrderNumber());
                }

                // 验证并修复坐标信息
                if (mailOrder.getPickupLatitude() == null || mailOrder.getPickupLongitude() == null) {
                    double[] coordinates = generateRandomCoordinates();
                    mailOrder.setPickupLatitude(coordinates[0]);
                    mailOrder.setPickupLongitude(coordinates[1]);
                    logger.info("修复订单 {} 的取件坐标", mailOrder.getOrderNumber());
                }

                if (mailOrder.getDeliveryLatitude() == null || mailOrder.getDeliveryLongitude() == null) {
                    double[] coordinates = generateRandomCoordinates();
                    mailOrder.setDeliveryLatitude(coordinates[0]);
                    mailOrder.setDeliveryLongitude(coordinates[1]);
                    logger.info("修复订单 {} 的配送坐标", mailOrder.getOrderNumber());
                }
            }
            case SHOPPING_ORDER -> {
                ShoppingOrder shoppingOrder = (ShoppingOrder) order;

                // 验证并修复配送地址
                if (isNullOrEmpty(shoppingOrder.getDeliveryAddress())) {
                    shoppingOrder.setDeliveryAddress(generateRandomDetailedAddress());
                    logger.info("修复订单 {} 的空配送地址", shoppingOrder.getOrderNumber());
                }

                // 验证并修复收件人信息
                if (isNullOrEmpty(shoppingOrder.getRecipientName()) || isNullOrEmpty(shoppingOrder.getRecipientPhone())) {
                    shoppingOrder.setRecipientName(getRandomItem(contactNames));
                    shoppingOrder.setRecipientPhone(getRandomItem(contactPhones));
                    logger.info("修复订单 {} 的收件人信息", shoppingOrder.getOrderNumber());
                }

                // 验证并修复坐标信息
                if (shoppingOrder.getDeliveryLatitude() == null || shoppingOrder.getDeliveryLongitude() == null) {
                    double[] coordinates = generateRandomCoordinates();
                    shoppingOrder.setDeliveryLatitude(coordinates[0]);
                    shoppingOrder.setDeliveryLongitude(coordinates[1]);
                    logger.info("修复订单 {} 的配送坐标", shoppingOrder.getOrderNumber());
                }
            }
            case PURCHASE_REQUEST -> {
                PurchaseRequest purchaseRequest = (PurchaseRequest) order;

                // 验证并修复采购地址
                if (isNullOrEmpty(purchaseRequest.getPurchaseAddress())) {
                    purchaseRequest.setPurchaseAddress(generateRandomAddress());
                    logger.info("修复订单 {} 的空采购地址", purchaseRequest.getRequestNumber());
                }

                // 验证并修复配送地址
                if (isNullOrEmpty(purchaseRequest.getDeliveryAddress())) {
                    purchaseRequest.setDeliveryAddress(generateRandomDetailedAddress());
                    logger.info("修复订单 {} 的空配送地址", purchaseRequest.getRequestNumber());
                }

                // 验证并修复收件人信息
                if (isNullOrEmpty(purchaseRequest.getRecipientName()) || isNullOrEmpty(purchaseRequest.getRecipientPhone())) {
                    purchaseRequest.setRecipientName(getRandomItem(contactNames));
                    purchaseRequest.setRecipientPhone(getRandomItem(contactPhones));
                    logger.info("修复订单 {} 的收件人信息", purchaseRequest.getRequestNumber());
                }

                // 验证并修复坐标信息
                if (purchaseRequest.getPurchaseLatitude() == null || purchaseRequest.getPurchaseLongitude() == null) {
                    double[] coordinates = generateRandomCoordinates();
                    purchaseRequest.setPurchaseLatitude(coordinates[0]);
                    purchaseRequest.setPurchaseLongitude(coordinates[1]);
                    logger.info("修复订单 {} 的采购坐标", purchaseRequest.getRequestNumber());
                }

                if (purchaseRequest.getDeliveryLatitude() == null || purchaseRequest.getDeliveryLongitude() == null) {
                    double[] coordinates = generateRandomCoordinates();
                    purchaseRequest.setDeliveryLatitude(coordinates[0]);
                    purchaseRequest.setDeliveryLongitude(coordinates[1]);
                    logger.info("修复订单 {} 的配送坐标", purchaseRequest.getRequestNumber());
                }
            }
        }
    }

    /**
     * 检查字符串是否为null或空
     */
    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}