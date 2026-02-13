package ru.syncfamily.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private Integer id;

    private Integer chatId;

    private String productName;

    private boolean isBought;

    private LocalDateTime createdAt;

    private Integer familyId;
}
