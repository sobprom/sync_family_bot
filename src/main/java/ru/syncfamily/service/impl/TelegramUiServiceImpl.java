package ru.syncfamily.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.syncfamily.service.TelegramUiService;
import ru.syncfamily.service.model.CallBack;
import ru.syncfamily.service.model.Product;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class TelegramUiServiceImpl implements TelegramUiService {

    @Override
    public InlineKeyboardMarkup createShoppingListKeyboard(List<Product> products, boolean edit) {
        if (edit) {
            return createEditListKeyboard(products);
        }
        List<InlineKeyboardRow> rows = products.stream().map(product -> {
            // Формируем текст: если куплено, зачеркиваем
            String label = product.isBought() ? "✅ " + product.getProductName() : product.getProductName();

            var button = InlineKeyboardButton.builder()
                    .text(label)
                    .callbackData(CallBack.BUY.getAction() + product.getId())
                    .build();
            return new InlineKeyboardRow(button);
        }).collect(Collectors.toList());


        if (!products.isEmpty()) {
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text("⚙ Редактировать список")
                    .callbackData(CallBack.TOGGLE_MODE_EDIT.getAction()) // Сначала просим подтверждение
                    .build()));
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text("\uD83C\uDFC1 Завершить покупки")
                    .callbackData(CallBack.CONFIRM_CLEAR.getAction()) // Сначала просим подтверждение
                    .build()));

        }

        return new InlineKeyboardMarkup(rows);
    }


    private InlineKeyboardMarkup createEditListKeyboard(List<Product> products) {
        List<InlineKeyboardRow> rows = products.stream().map(product -> {

            var editButton = InlineKeyboardButton.builder()
                    .text("✏️ " + product.getProductName())
                    .callbackData(CallBack.CONFIRM_EDIT_PRODUCT.getAction() + product.getId())
                    .build();


            return new InlineKeyboardRow(editButton);

        }).collect(Collectors.toList());


        if (!products.isEmpty()) {

            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text("⬅️ Назад к покупкам")
                    .callbackData(CallBack.REFRESH.getAction()) // Сначала просим подтверждение
                    .build()));

        }

        return new InlineKeyboardMarkup(rows);
    }
}
