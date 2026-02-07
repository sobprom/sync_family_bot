package ru.syncfamily.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.syncfamily.service.model.Product;

import java.util.List;

@ApplicationScoped
public class TelegramUiService {

    public InlineKeyboardMarkup createShoppingListKeyboard(List<Product> products) {
        List<InlineKeyboardRow> rows = products.stream().map(product -> {
            // Формируем текст: если куплено, зачеркиваем
            String label = product.isBought() ? "✅ " + strikethrough(product.getProductName()) : product.getProductName();

            var button = InlineKeyboardButton.builder()
                    .text(label)
                    .callbackData("buy_" + product.getId()) // Префикс для обработки клика
                    .build();
            return new InlineKeyboardRow(button);
        }).toList();

        return new InlineKeyboardMarkup(rows);
    }

    private String strikethrough(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c).append('\u0336'); // Добавляет черту после каждого символа
        }
        return sb.toString();
    }
}
