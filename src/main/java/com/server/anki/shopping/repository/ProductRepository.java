package com.server.anki.shopping.repository;

import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.ProductCategory;
import com.server.anki.shopping.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // 根据店铺查询所有商品（分页）
    Page<Product> findByStore(Store store, Pageable pageable);

    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);
    long countByStatus(ProductStatus status);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
    // 根据店铺和状态查找商品
    Page<Product> findByStoreAndStatus(Store store, ProductStatus status, Pageable pageable);

    // 根据类别和状态查找商品
    Page<Product> findByCategoryAndStatus(ProductCategory category, ProductStatus status, Pageable pageable);

    // 根据价格范围查找商品
    List<Product> findByPriceBetweenAndStatus(BigDecimal minPrice, BigDecimal maxPrice, ProductStatus status);

    // 搜索商品（名称和描述）
    @Query("SELECT p FROM Product p WHERE (p.name LIKE %:keyword% OR p.description LIKE %:keyword%) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<Product> searchProducts(@Param("keyword") String keyword,
                                 @Param("status") ProductStatus status,
                                 Pageable pageable);

    // 更新商品库存
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :productId AND p.stock >= :quantity")
    int updateStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    // 查找库存不足的商品
    List<Product> findByStockLessThanAndStatus(Integer threshold, ProductStatus status);

    // 统计每个类别的商品数量
    @Query("SELECT p.category, COUNT(p) FROM Product p GROUP BY p.category")
    List<Object[]> countByCategory();
}