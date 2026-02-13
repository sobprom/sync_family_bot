package ru.syncfamily.repository;

import ru.syncfamily.service.model.User;

import java.util.List;
import java.util.Optional;

public interface FamilyRepository {
    String createFamilyAndGetCode(DbContext ctx, long chatId, String userName);

    Optional<User> getFamilyMemberByChatId(DbContext ctx, long chatId);

    List<User> getFamilyMembersByFamilyId(DbContext ctx, long familyId);

    boolean joinFamily(DbContext ctx, long chatId, String code, String userName);

    void updateLastMessageId(DbContext ctx, List<User> users);
}
