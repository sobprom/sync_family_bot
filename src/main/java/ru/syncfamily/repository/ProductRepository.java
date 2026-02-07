package ru.syncfamily.repository;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import ru.syncfamily.service.model.Product;

import java.util.Collections;
import java.util.List;

import static ru.syncfamily.jooq.Tables.SHOPPING_LIST;
import static ru.syncfamily.jooq.Tables.USERS;

@ApplicationScoped
public class ProductRepository {

    @Inject
    DSLContext dsl;

    /**
     * Вспомогательный метод для получения family_id пользователя.
     * Возвращает Integer, так как это тип первичного ключа в таблице FAMILIES.
     */
    private Integer getFamilyIdInternal(long chatId) {
        return dsl.select(USERS.FAMILY_ID)
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

    public Uni<Void> addProducts(long chatId, List<String> products) {
        return Uni.createFrom().item(() -> {
            Integer familyId = getFamilyIdInternal(chatId);

            // Используем batch insert для производительности (Middle+ style)
            var query = dsl.insertInto(SHOPPING_LIST,
                    SHOPPING_LIST.CHAT_ID,
                    SHOPPING_LIST.PRODUCT_NAME,
                    SHOPPING_LIST.FAMILY_ID);

            for (String name : products) {
                query.values((int) chatId, name, familyId);
            }

            query.execute();
            return null;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).replaceWithVoid();
    }

    public Uni<List<Product>> getActiveProducts(long chatId) {
        return Uni.createFrom().item(() -> {
            Integer familyId = getFamilyIdInternal(chatId);
            Condition condition = getGroupCondition(chatId, familyId);

            return dsl.selectFrom(SHOPPING_LIST)
                    .where(condition)
                    .and(SHOPPING_LIST.IS_BOUGHT.eq(false))
                    .fetchInto(Product.class);
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    public Uni<Void> markAsBought(long chatId, int productId) {
        return Uni.createFrom().item(() -> {
            Integer familyId = getFamilyIdInternal(chatId);
            Condition condition = getGroupCondition(chatId, familyId);

            dsl.update(SHOPPING_LIST)
                    .set(SHOPPING_LIST.IS_BOUGHT, true)
                    .where(condition)
                    .and(SHOPPING_LIST.ID.eq(productId))
                    .execute();
            return null;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).replaceWithVoid();
    }

    public Uni<List<Product>> getAllProductsOrdered(long chatId) {
        return Uni.createFrom().item(() -> {
            // 1. Сначала находим family_id пользователя
            Integer familyId = dsl.select(USERS.FAMILY_ID)
                    .from(USERS)
                    .where(USERS.CHAT_ID.eq((int) chatId))
                    .fetchOneInto(Integer.class);

            if (familyId == null) return Collections.<Product>emptyList();

            // 2. Получаем все продукты семьи с сортировкой
            // Сначала те, у которых is_bought = false (0), потом true (1)
            // Внутри этих групп сортируем по времени создания (новые сверху)
            return dsl.selectFrom(SHOPPING_LIST)
                    .where(SHOPPING_LIST.FAMILY_ID.eq(familyId))
                    .orderBy(
                            SHOPPING_LIST.IS_BOUGHT.asc(),
                            SHOPPING_LIST.CREATED_AT.desc()
                    )
                    .fetchInto(Product.class);

        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

}
