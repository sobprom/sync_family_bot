package ru.syncfamily.repository;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.DSLContext;

import java.util.List;

import static ru.syncfamily.jooq.Tables.SHOPPING_LIST;

@ApplicationScoped
public class ProductRepository {

    @Inject
    DSLContext dsl;

    public Uni<Void> addProducts(long chatId, List<String> products) {
        return Uni.createFrom().item(() -> {
            var insert = dsl.insertInto(SHOPPING_LIST,
                    SHOPPING_LIST.CHAT_ID,
                    SHOPPING_LIST.PRODUCT_NAME);
            products.forEach(name -> insert.values((int) chatId, name));
            insert.execute();
            return null;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).replaceWithVoid();
    }

    public Uni<List<String>> getActiveProducts(long chatId) {
        return Uni.createFrom().item(() ->
                dsl.selectFrom(SHOPPING_LIST)
                        .where(SHOPPING_LIST.CHAT_ID.eq((int) chatId))
                        .and(SHOPPING_LIST.IS_BOUGHT.eq(false))
                        .fetch(SHOPPING_LIST.PRODUCT_NAME)
        ).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    public Uni<Void> markAsBought(long chatId, String productName) {
        return Uni.createFrom().item(() -> {
            dsl.update(SHOPPING_LIST)
                    .set(SHOPPING_LIST.IS_BOUGHT, true)
                    .where(SHOPPING_LIST.CHAT_ID.eq((int) chatId))
                    .and(SHOPPING_LIST.PRODUCT_NAME.eq(productName))
                    .execute();
            return null;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).replaceWithVoid();
    }
}
