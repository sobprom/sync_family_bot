package ru.syncfamily.repository.impl;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import ru.syncfamily.repository.DbContext;
import ru.syncfamily.repository.SqlLiteDb;

import java.util.function.Function;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SqlLiteDbImpl implements SqlLiteDb {

    private final DSLContext dsl;

    @Override
    public <T> Uni<T> async(Function<DbContext, T> func) {
        return Uni.createFrom().item(() -> dsl.transactionResult(configuration -> {
            DbContext ctx = () -> DSL.using(configuration);
            return func.apply(ctx);
        })).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
