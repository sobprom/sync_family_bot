package ru.syncfamily.repository;

import org.jooq.DSLContext;

public interface DbContext {
    DSLContext dsl();
}
