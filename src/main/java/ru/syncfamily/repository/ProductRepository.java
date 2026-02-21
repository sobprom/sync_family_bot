package ru.syncfamily.repository;

import ru.syncfamily.service.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    void addProducts(DbContext ctx, long familyId, List<String> products);

    void inverseBought(DbContext ctx, long familyId, long productId);

    List<Product> getAllProductsOrdered(DbContext ctx, long familyId);

    Optional<Product> findProduct(DbContext ctx, long familyId, long productId);

    void deleteAllByFamilyId(DbContext ctx, long familyId);

    void deleteByProductId(DbContext ctx, long familyId, long productId);
}
