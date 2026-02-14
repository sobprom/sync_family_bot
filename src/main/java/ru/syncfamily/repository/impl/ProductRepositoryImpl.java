package ru.syncfamily.repository.impl;

import jakarta.enterprise.context.ApplicationScoped;
import ru.syncfamily.repository.DbContext;
import ru.syncfamily.repository.ProductRepository;
import ru.syncfamily.service.model.Product;

import java.util.List;

import static org.jooq.impl.DSL.condition;
import static org.jooq.impl.DSL.field;
import static ru.syncfamily.jooq.Tables.SHOPPING_LIST;

@ApplicationScoped
public class ProductRepositoryImpl implements ProductRepository {


    @Override
    public void addProducts(DbContext ctx, long familyId, List<String> products) {

        var query = ctx.dsl()
                .insertInto(SHOPPING_LIST)
                .columns(
                        SHOPPING_LIST.FAMILY_ID,
                        SHOPPING_LIST.PRODUCT_NAME
                )
                .values((Long) null, null)
                .onConflictDoNothing();

        var batchInsert = ctx.dsl().batch(query);

        products.forEach(name -> batchInsert.bind(
                familyId,
                name
        ));

        batchInsert.execute();

    }

    @Override
    public void inverseBought(DbContext ctx, long familyId, long productId) {

        ctx.dsl().update(SHOPPING_LIST)
                .set(SHOPPING_LIST.IS_BOUGHT, field(condition(SHOPPING_LIST.IS_BOUGHT).not()))
                .where(SHOPPING_LIST.FAMILY_ID.eq(familyId))
                .and(SHOPPING_LIST.ID.eq(productId))
                .execute();
    }

    @Override
    public List<Product> getAllProductsOrdered(DbContext ctx, long familyId) {

        return ctx.dsl().selectFrom(SHOPPING_LIST)
                .where(SHOPPING_LIST.FAMILY_ID.eq(familyId))
                .orderBy(
                        SHOPPING_LIST.IS_BOUGHT.asc(),
                        SHOPPING_LIST.CREATED_AT.desc()
                )
                .fetchInto(Product.class);
    }


}
