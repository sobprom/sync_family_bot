package ru.syncfamily.repository.impl;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import ru.syncfamily.repository.DbContext;

@RequiredArgsConstructor(staticName = "of")
public class DbContextImpl implements DbContext {
    private final DSLContext ctx;

    @Override
    public DSLContext dsl() {
        return ctx;
    }
}
