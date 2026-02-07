package ru.syncfamily.repository;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.DSLContext;

import java.util.List;
import java.util.UUID;

import static ru.syncfamily.jooq.Tables.FAMILIES;
import static ru.syncfamily.jooq.Tables.USERS;

@ApplicationScoped
public class FamilyRepository {

    @Inject
    DSLContext dsl;

    public Uni<String> createFamilyAndGetCode(long chatId, String userName) {
        return Uni.createFrom().item(() -> {
            String code = UUID.randomUUID().toString();
            return dsl.transactionResult(config -> {

                // 1. Создаем семью
                var familyId = dsl.insertInto(FAMILIES)
                        .set(FAMILIES.INVITE_CODE, code)
                        .returning(FAMILIES.ID)
                        .fetchOne()          // Возвращает FamiliesRecord
                        .getValue(FAMILIES.ID);

                // 2. Привязываем создателя
                upsertUserFamily(chatId, familyId, userName);
                return code;
            });
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    public Uni<List<Long>> getFamilyMembersByChatId(long chatId) {
        return Uni.createFrom().item(() ->
                dsl.select(USERS.CHAT_ID)
                        .from(USERS)
                        .where(USERS.FAMILY_ID.eq(
                                dsl.select(USERS.FAMILY_ID).from(USERS).where(USERS.CHAT_ID.eq((int) chatId))
                        ))
                        .fetchInto(Long.class)
        ).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }


    /**
     * Позволяет второму члену семьи вступить в группу по коду
     */
    public Uni<Boolean> joinFamily(long chatId, String code, String userName) {
        return Uni.createFrom().item(() -> {
            // 1. Ищем ID семьи по коду
            Integer familyId = dsl.select(FAMILIES.ID)
                    .from(FAMILIES)
                    .where(FAMILIES.INVITE_CODE.eq(code))
                    .fetchOneInto(Integer.class);

            if (familyId == null) return false;

            // 2. Привязываем пользователя
            upsertUserFamily(chatId, familyId, userName);
            return true;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private void upsertUserFamily(long chatId, Integer familyId, String userName) {
        dsl.insertInto(USERS)
                .set(USERS.CHAT_ID, (int) chatId)
                .set(USERS.FAMILY_ID, familyId)
                .set(USERS.USERNAME, userName)
                .onDuplicateKeyUpdate()
                .set(USERS.FAMILY_ID, familyId)
                .execute();
    }
}
