package com.server.anki.marketing.region;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "delivery_region")
@Getter
@Setter
public class DeliveryRegion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 区域名称，由管理员自定义
    @Column(name = "name", nullable = false)
    private String name;

    // 区域描述
    @Column(name = "description")
    private String description;

    // 区域边界，使用MySQL的POLYGON类型存储
    @JsonIgnore  // 在JSON响应中不包含实际的Polygon
    @Transient
    private Polygon boundary;

    // 添加此字段以存储坐标字符串
    @Setter
    @Transient
    private List<String> boundaryPoints = new ArrayList<>();

    // 添加此方法将Polygon转换为坐标字符串
    public List<String> getBoundaryPoints() {
        if (boundary == null) {
            return boundaryPoints;
        }

        if (boundaryPoints == null || boundaryPoints.isEmpty()) {
            boundaryPoints = new ArrayList<>();
            Coordinate[] coordinates = boundary.getExteriorRing().getCoordinates();

            // 跳过最后一个坐标，因为在闭合多边形中它与第一个坐标相同
            for (int i = 0; i < coordinates.length - 1; i++) {
                Coordinate coord = coordinates[i];
                // 格式化为"纬度,经度"，与前端期望一致
                boundaryPoints.add(coord.y + "," + coord.x);
            }
        }

        return boundaryPoints;
    }

    // 该区域的配送费率倍数，默认为1.0
    @Column(name = "rate_multiplier", nullable = false)
    private double rateMultiplier = 1.0;

    // 区域是否启用
    @Column(name = "active", nullable = false)
    private boolean active = true;

    // 当点位于多个区域时，使用优先级高的区域
    @Column(name = "priority", nullable = false)
    private int priority = 0;
}