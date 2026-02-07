package ru.syncfamily.repository;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.DSLContext;

import java.util.Random;
import java.util.UUID;

import static ru.syncfamily.jooq.Tables.FAMILIES;
import static ru.syncfamily.jooq.Tables.USERS;

@ApplicationScoped
public class FamilyRepository {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final Random random = new Random();
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
     * Создает новую семью и привязывает к ней создателя
     */
    public Uni<String> createFamily(long chatId) {
        return Uni.createFrom().item(() -> {
            // 1. Генерируем уникальный код из 6 символов
            String inviteCode = generateCode();

            // 2. Вставляем запись в FAMILIES
            Integer familyId = dsl.insertInto(FAMILIES)
                    .set(FAMILIES.INVITE_CODE, inviteCode)
                    .returning(FAMILIES.ID)
                    .fetchOne(FAMILIES.ID);

            // 3. Обновляем пользователя (или создаем, если его нет)
            upsertUserFamily(chatId, familyId);

            return inviteCode;
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

    private String generateCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
