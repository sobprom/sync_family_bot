package ru.syncfamily.service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long chatId;
    private String username;
    private Integer familyId;
    private Integer lastMessageId;
}
