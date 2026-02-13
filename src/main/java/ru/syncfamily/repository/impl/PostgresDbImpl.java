package ru.syncfamily.repository.impl;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import ru.syncfamily.repository.DbContext;
import ru.syncfamily.repository.PostgresDb;

import java.util.function.Function;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PostgresDbImpl implements PostgresDb {

    private final DSLContext dsl;

    @Override
    public <T> Uni<T> async(Function<DbContext, T> func) {
        return Uni.createFrom().item(() -> dsl.transactionResult(configuration ->
                        func.apply(DbContextImpl.of(configuration.dsl()))))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
