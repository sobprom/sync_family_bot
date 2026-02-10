package ru.syncfamily.repository.impl;

import jakarta.enterprise.context.ApplicationScoped;
import ru.syncfamily.repository.DbContext;
import ru.syncfamily.repository.FamilyRepository;
import ru.syncfamily.service.model.User;

import java.util.List;
import java.util.UUID;

import static ru.syncfamily.jooq.Tables.FAMILIES;
import static ru.syncfamily.jooq.Tables.USERS;

@ApplicationScoped
public class FamilyRepositoryImpl implements FamilyRepository {

    @Override
    public String createFamilyAndGetCode(DbContext ctx, long chatId, String userName) {

        String code = UUID.randomUUID().toString();

        // 1. Создаем семью
        var familyId = ctx.dsl().insertInto(FAMILIES)
                .set(FAMILIES.INVITE_CODE, code)
                .returning(FAMILIES.ID)
                .fetchOneInto(Long.class);

        // 2. Привязываем создателя
        upsertUserFamily(ctx, chatId, familyId, userName);
        return code;

    }

    @Override
    public List<User> getFamilyMembersByChatId(DbContext ctx, long chatId) {

        return ctx.dsl().selectFrom(USERS)
                .where(USERS.CHAT_ID.eq(chatId))
                .fetchInto(User.class);
    }


    @Override
    public boolean joinFamily(DbContext ctx, long chatId, String code, String userName) {

        // 1. Ищем ID семьи по коду
        Long familyId = ctx.dsl().select(FAMILIES.ID)
                .from(FAMILIES)
                .where(FAMILIES.INVITE_CODE.eq(code))
                .fetchOneInto(Long.class);

        if (familyId == null) return false;

        // 2. Привязываем пользователя
        upsertUserFamily(ctx, chatId, familyId, userName);
        return true;

    }

    @Override
    public void updateLastMessageId(DbContext ctx, List<User> users) {

        if (users == null || users.isEmpty()) {
            return;
        }

        // 1. Создаем список запросов для пакетного выполнения
        var batchQueries = users.stream()
                .filter(u -> u != null && u.getChatId() != null)
                .map(user -> ctx.dsl()
                        .update(USERS)
                        .set(USERS.LAST_MESSAGE_ID, user.getLastMessageId())
                        .where(USERS.CHAT_ID.eq(user.getChatId()))
                )
                .toList();

        // 2. Выполняем все запросы за один раз
        ctx.dsl().batch(batchQueries).execute();
    }

    private void upsertUserFamily(DbContext ctx, long chatId, Long familyId, String userName) {
        ctx.dsl().insertInto(USERS)
                .set(USERS.CHAT_ID, chatId)
                .set(USERS.FAMILY_ID, familyId)
                .set(USERS.USERNAME, userName)
                .onDuplicateKeyUpdate()
                .set(USERS.FAMILY_ID, familyId)
                .execute();
    }
}
