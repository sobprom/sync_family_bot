package ru.syncfamily.repository;

import io.smallrye.mutiny.Uni;

import java.util.function.Function;

public interface PostgresDb {
    <T> Uni<T> async(Function<DbContext, T> var);
}
