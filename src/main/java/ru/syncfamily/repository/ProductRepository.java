package ru.syncfamily.repository;

import ru.syncfamily.service.model.Product;

import java.util.List;

public interface ProductRepository {
    void addProducts(DbContext ctx, long chatId, List<String> products);

    void markAsBought(DbContext ctx, long chatId, long productId);

    List<Product> getAllProductsOrdered(DbContext ctx, long chatId);
}
