package ru.syncfamily.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

@ApplicationScoped
public class TelegramUiService {

    public InlineKeyboardMarkup createShoppingListKeyboard(List<String> products) {
        List<InlineKeyboardRow> rows = products.stream().map(product -> {
            var button = InlineKeyboardButton.builder()
                    .text(product)
                    .callbackData("buy_" + product) // Префикс для обработки клика
                    .build();
            return new InlineKeyboardRow(button);
        }).toList();

        return new InlineKeyboardMarkup(rows);
    }
}
