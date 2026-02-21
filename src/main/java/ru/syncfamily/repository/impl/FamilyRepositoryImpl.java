package ru.syncfamily.repository.impl;

import jakarta.enterprise.context.ApplicationScoped;
import ru.syncfamily.repository.DbContext;
import ru.syncfamily.repository.FamilyRepository;
import ru.syncfamily.service.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ru.syncfamily.jooq.Tables.FAMILIES;
import static ru.syncfamily.jooq.Tables.USERS;

@ApplicationScoped
public class FamilyRepositoryImpl implements FamilyRepository {

    @Override
    public String createFamilyAndGetCode(DbContext ctx, long chatId, String userName) {

        String code = UUID.randomUUID().toString();

        var familyId = ctx.dsl().insertInto(FAMILIES)
                .set(FAMILIES.INVITE_CODE, code)
                .returningResult(FAMILIES.ID)
                .fetchOneInto(Long.class);

        upsertUserFamily(ctx, chatId, familyId, userName);
        return code;

    }

    @Override
    public Optional<User> getFamilyMemberByChatId(DbContext ctx, long chatId) {

        return ctx.dsl().selectFrom(USERS)
                .where(USERS.CHAT_ID.eq(chatId))
                .fetchOptionalInto(User.class);
    }

    @Override
    public List<User> getFamilyMembersByFamilyId(DbContext ctx, long familyId) {

        return ctx.dsl().selectFrom(USERS)
                .where(USERS.FAMILY_ID.eq(familyId))
                .fetchInto(User.class);
    }

    @Override
    public boolean joinFamily(DbContext ctx, long chatId, String code, String userName) {

        Long familyId = ctx.dsl().select(FAMILIES.ID)
                .from(FAMILIES)
                .where(FAMILIES.INVITE_CODE.eq(code))
                .fetchOneInto(Long.class);

        if (familyId == null) return false;

        upsertUserFamily(ctx, chatId, familyId, userName);
        return true;

    }

    @Override
    public void updateLastMessageId(DbContext ctx, List<User> users) {

        if (users == null || users.isEmpty()) {
            return;
        }

        var batchQueries = users.stream()
                .map(user -> ctx.dsl()
                        .update(USERS)
                        .set(USERS.LAST_MESSAGE_ID, user.getLastMessageId())
                        .where(USERS.CHAT_ID.eq(user.getChatId()))
                )
                .toList();

        ctx.dsl().batch(batchQueries).execute();
    }

    @Override
    public User setShoppingEditMode(DbContext ctx, User user) {
        user.setShoppingListEditMode(true);
        ctx.dsl().update(USERS)
                .set(USERS.SHOPPING_LIST_EDIT_MODE, true)
                .where(USERS.CHAT_ID.eq(user.getChatId()))
                .execute();
        return user;
    }

    @Override
    public User dropShoppingEditMode(DbContext ctx, User user) {
        user.setShoppingListEditMode(false);
        ctx.dsl().update(USERS)
                .set(USERS.SHOPPING_LIST_EDIT_MODE, false)
                .where(USERS.CHAT_ID.eq(user.getChatId()))
                .execute();
        return user;
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
