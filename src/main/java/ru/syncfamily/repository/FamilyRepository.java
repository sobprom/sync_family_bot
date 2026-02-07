package ru.syncfamily.repository;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.DSLContext;

import java.util.UUID;

import static ru.syncfamily.jooq.Tables.FAMILIES;
import static ru.syncfamily.jooq.Tables.USERS;

@ApplicationScoped
public class FamilyRepository {

    @Inject
    DSLContext dsl;

    public Uni<String> createFamilyAndGetCode(long chatId) {
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
                dsl.insertInto(USERS)
                        .set(USERS.CHAT_ID, (int) chatId)
                        .set(USERS.FAMILY_ID, familyId)
                        .onConflict(USERS.CHAT_ID)
                        .doUpdate()
                        .set(USERS.FAMILY_ID, familyId)
                        .execute();
                return code;
            });
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }


    /**
     * Позволяет второму члену семьи вступить в группу по коду
     */
    public Uni<Boolean> joinFamily(long chatId, String code) {
        return Uni.createFrom().item(() -> {
            // 1. Ищем ID семьи по коду
            Integer familyId = dsl.select(FAMILIES.ID)
                    .from(FAMILIES)
                    .where(FAMILIES.INVITE_CODE.eq(code))
                    .fetchOneInto(Integer.class);

            if (familyId == null) return false;

            // 2. Привязываем пользователя
            upsertUserFamily(chatId, familyId);
            return true;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private void upsertUserFamily(long chatId, Integer familyId) {
        dsl.insertInto(USERS)
                .set(USERS.CHAT_ID, (int) chatId)
                .set(USERS.FAMILY_ID, familyId)
                .onDuplicateKeyUpdate()
                .set(USERS.FAMILY_ID, familyId)
                .execute();
    }
}
