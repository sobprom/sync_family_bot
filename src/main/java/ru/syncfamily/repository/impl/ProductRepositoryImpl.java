package ru.syncfamily.repository.impl;

import jakarta.enterprise.context.ApplicationScoped;
import org.jooq.Condition;
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

        Integer familyId = getFamilyIdInternal(ctx, chatId);

        // Используем batch insert для производительности (Middle+ style)
        var query = ctx.dsl().insertInto(SHOPPING_LIST,
                SHOPPING_LIST.CHAT_ID,
                SHOPPING_LIST.PRODUCT_NAME,
                SHOPPING_LIST.FAMILY_ID);

        for (String name : products) {
            query.values((int) chatId, name, familyId);
        }

        query.execute();


    }

    @Override
    public void markAsBought(DbContext ctx, long chatId, int productId) {

        Integer familyId = getFamilyIdInternal(ctx, chatId);
        Condition condition = getGroupCondition(chatId, familyId);

        ctx.dsl().update(SHOPPING_LIST)
                .set(SHOPPING_LIST.IS_BOUGHT, true)
                .where(condition)
                .and(SHOPPING_LIST.ID.eq(productId))
                .execute();
    }

    @Override
    public List<Product> getAllProductsOrdered(DbContext ctx, long chatId) {

        // 1. Сначала находим family_id пользователя
        Integer familyId = ctx.dsl().select(USERS.FAMILY_ID)
                .from(USERS)
                .where(USERS.CHAT_ID.eq((int) chatId))
                .fetchOneInto(Integer.class);

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
    private Integer getFamilyIdInternal(DbContext ctx, long chatId) {
        return ctx.dsl().select(USERS.FAMILY_ID)
                .from(USERS)
                .where(USERS.CHAT_ID.eq((int) chatId))
                .fetchOneInto(Integer.class);
    }

    /**
     * Универсальный метод для создания условия поиска (по семье или по chat_id).
     */
    private Condition getGroupCondition(long chatId, Integer familyId) {
        if (familyId != null) {
            return SHOPPING_LIST.FAMILY_ID.eq(familyId);
        } else {
            return SHOPPING_LIST.CHAT_ID.eq((int) chatId);
        }
    }

}
