package ru.syncfamily.repository.impl;

import jakarta.enterprise.context.ApplicationScoped;
import ru.syncfamily.repository.DbContext;
import ru.syncfamily.repository.ProductRepository;
import ru.syncfamily.service.model.Product;

import java.util.Collections;
import java.util.List;

import static ru.syncfamily.jooq.Tables.SHOPPING_LIST;
import static ru.syncfamily.jooq.Tables.USERS;

@ApplicationScoped
public class ProductRepositoryImpl implements ProductRepository {


    @Override
    public void addProducts(DbContext ctx, long chatId, List<String> products) {

        Long familyId = getFamilyIdInternal(ctx, chatId);

        // Используем batch insert для производительности (Middle+ style)
        var query = ctx.dsl().insertInto(SHOPPING_LIST,
                SHOPPING_LIST.PRODUCT_NAME,
                SHOPPING_LIST.FAMILY_ID);

        for (String name : products) {
            query.values(name, familyId);
        }

        query.execute();


    }

    @Override
    public void markAsBought(DbContext ctx, long chatId, long productId) {

        Long familyId = getFamilyIdInternal(ctx, chatId);

        ctx.dsl().update(SHOPPING_LIST)
                .set(SHOPPING_LIST.IS_BOUGHT, true)
                .where(SHOPPING_LIST.FAMILY_ID.eq(familyId))
                .and(SHOPPING_LIST.ID.eq(productId))
                .execute();
    }

    @Override
    public List<Product> getAllProductsOrdered(DbContext ctx, long chatId) {

        Long familyId = getFamilyIdInternal(ctx, chatId);

        if (familyId == null) return Collections.<Product>emptyList();

        // 2. Получаем все продукты семьи с сортировкой
        // Сначала те, у которых is_bought = false (0), потом true (1)
        // Внутри этих групп сортируем по времени создания (новые сверху)
        return ctx.dsl().selectFrom(SHOPPING_LIST)
                .where(SHOPPING_LIST.FAMILY_ID.eq(familyId))
                .orderBy(
                        SHOPPING_LIST.IS_BOUGHT.asc(),
                        SHOPPING_LIST.CREATED_AT.desc()
                )
                .fetchInto(Product.class);
    }

    /**
     * Вспомогательный метод для получения family_id пользователя.
     * Возвращает Integer, так как это тип первичного ключа в таблице FAMILIES.
     */
    private Long getFamilyIdInternal(DbContext ctx, long chatId) {
        return ctx.dsl().select(USERS.FAMILY_ID)
                .from(USERS)
                .where(USERS.CHAT_ID.eq(chatId))
                .fetchOneInto(Long.class);
    }


}
