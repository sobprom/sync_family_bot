package ru.syncfamily.repository;

import ru.syncfamily.service.model.User;

import java.util.List;

public interface FamilyRepository {
    String createFamilyAndGetCode(DbContext ctx, long chatId, String userName);

    List<User> getFamilyMembersByChatId(DbContext ctx, long chatId);

    boolean joinFamily(DbContext ctx, long chatId, String code, String userName);

    void updateLastMessageId(DbContext ctx, List<User> users);
}
