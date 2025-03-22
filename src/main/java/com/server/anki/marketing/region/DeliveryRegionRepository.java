package com.server.anki.marketing.region;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 配送区域数据访问接口
 * 继承JpaRepository以获得基本的CRUD功能
 */
@Repository
public interface DeliveryRegionRepository extends JpaRepository<DeliveryRegion, Long> {

    /**
     * 查找所有激活状态的配送区域
     */
    List<DeliveryRegion> findAllByActiveTrue();

    /**
     * 根据名称查找配送区域
     */
    Optional<DeliveryRegion> findByName(String name);

    /**
     * 查找包含指定点的区域
     * 使用MySQL的ST_Contains函数进行空间查询
     * 按优先级排序并返回优先级最高的区域
     */
    @Query(value =
            "SELECT * FROM delivery_region " +
                    "WHERE active = true " +
                    "AND ST_Contains(boundary, ST_GeomFromText(:point, 4326)) = 1 " +
                    "ORDER BY priority DESC LIMIT 1",
            nativeQuery = true)
    Optional<DeliveryRegion> findRegionContainingPoint(@Param("point") String point);

    /**
     * 查找与指定点距离最近的区域
     * 使用MySQL的ST_Distance函数计算距离
     */
    @Query(value =
            "SELECT * FROM delivery_region " +
                    "WHERE active = true " +
                    "ORDER BY ST_Distance(boundary, ST_GeomFromText(:point, 4326)) " +
                    "LIMIT 1",
            nativeQuery = true)
    Optional<DeliveryRegion> findNearestRegion(@Param("point") String point);

    /**
     * 更新区域边界
     * 使用MySQL的ST_GeomFromText函数将WKT格式的多边形文本转换为空间数据
     */
    @Modifying
    @Query(value =
            "UPDATE delivery_region " +
                    "SET boundary = ST_GeomFromText(:polygon, 4326) " +
                    "WHERE id = :regionId",
            nativeQuery = true)
    int updateRegionBoundary(
            @Param("regionId") Long regionId,
            @Param("polygon") String polygonWkt
    );

    /**
     * 检查两个区域是否相交
     * 用于验证新建或修改的区域是否与现有区域重叠
     */
    @Query(value =
            "SELECT COUNT(*) > 0 FROM delivery_region " +
                    "WHERE id != :excludeId " +
                    "AND active = true " +
                    "AND ST_Intersects(boundary, ST_GeomFromText(:polygon, 4326)) = 1",
            nativeQuery = true)
    boolean existsIntersectingRegion(
            @Param("excludeId") Long excludeId,
            @Param("polygon") String polygonWkt
    );

    /**
     * 查找指定区域的邻接区域
     * 使用MySQL的ST_Touches函数找出边界相接的区域
     */
    @Query(value =
            "SELECT * FROM delivery_region r1 " +
                    "WHERE r1.active = true " +
                    "AND r1.id != :regionId " +
                    "AND ST_Touches(r1.boundary, (" +
                    "    SELECT boundary FROM delivery_region r2 " +
                    "    WHERE r2.id = :regionId" +
                    ")) = 1",
            nativeQuery = true)
    List<DeliveryRegion> findAdjacentRegions(@Param("regionId") Long regionId);

    /**
     * 计算区域面积（平方米）
     * 使用MySQL的ST_Area函数计算
     */
    @Query(value =
            "SELECT ST_Area(" +
                    "    ST_Transform(boundary, 3857)" +  // 转换到墨卡托投影以获得更准确的面积
                    ") AS area " +
                    "FROM delivery_region " +
                    "WHERE id = :regionId",
            nativeQuery = true)
    Double calculateRegionArea(@Param("regionId") Long regionId);

    /**
     * 查找指定半径范围内的所有区域
     * 使用MySQL的ST_DWithin函数进行范围查询
     */
    @Query(value =
            "SELECT * FROM delivery_region " +
                    "WHERE active = true " +
                    "AND ST_DWithin(" +
                    "    boundary, " +
                    "    ST_GeomFromText(:point, 4326), " +
                    "    :radiusMeters / (111.045 * COS(RADIANS(ST_Y(ST_GeomFromText(:point, 4326)))))" +
                    ") = 1",
            nativeQuery = true)
    List<DeliveryRegion> findRegionsWithinRadius(
            @Param("point") String point,
            @Param("radiusMeters") double radiusMeters
    );

    /**
     * 检查区域的边界是否有效
     * 使用MySQL的ST_IsValid函数验证多边形的有效性
     */
    @Query(value =
            "SELECT ST_IsValid(ST_GeomFromText(:polygon, 4326)) = 1",
            nativeQuery = true)
    boolean isValidBoundary(@Param("polygon") String polygonWkt);

    /**
     * 获取区域的边界框
     * 使用MySQL的ST_Envelope函数获取包围盒
     */
    @Query(value =
            "SELECT ST_AsText(ST_Envelope(boundary)) " +
                    "FROM delivery_region " +
                    "WHERE id = :regionId",
            nativeQuery = true)
    String getRegionBoundingBox(@Param("regionId") Long regionId);

    /**
     * 插入新的配送区域
     * 使用原生 SQL 以保证空间数据的正确处理
     */
    @Modifying
    @Query(value =
            "INSERT INTO delivery_region " +
                    "(name, description, boundary, rate_multiplier, priority, active) " +
                    "VALUES (:name, :description, ST_GeomFromText(:boundary, :srid), :rateMultiplier, :priority, :active)",
            nativeQuery = true)
    int insertRegion(
            @Param("name") String name,
            @Param("description") String description,
            @Param("boundary") String boundary,
            @Param("srid") int srid,
            @Param("rateMultiplier") double rateMultiplier,
            @Param("priority") int priority,
            @Param("active") boolean active
    );

    /**
     * 使用原生 SQL 查询配送区域，并返回 WKT 格式的边界数据
     */
    @Query(value =
            "SELECT id, name, description, " +
                    "ST_AsText(boundary) as boundary_wkt, " +
                    "rate_multiplier, active, priority " +
                    "FROM delivery_region WHERE name = :name",
            nativeQuery = true)
    Optional<Object[]> findByNameWithWkt(@Param("name") String name);

    /**
     * 查询所有配送区域，并包含WKT格式的边界数据
     */
    @Query(value =
            "SELECT id, name, description, " +
                    "ST_AsText(boundary) as boundary_wkt, " +
                    "rate_multiplier, active, priority " +
                    "FROM delivery_region",
            nativeQuery = true)
    List<Object[]> findAllWithWkt();

    /**
     * 查询所有激活状态的配送区域，并包含WKT格式的边界数据
     */
    @Query(value =
            "SELECT id, name, description, " +
                    "ST_AsText(boundary) as boundary_wkt, " +
                    "rate_multiplier, active, priority " +
                    "FROM delivery_region WHERE active = true",
            nativeQuery = true)
    List<Object[]> findAllActiveWithWkt();
}