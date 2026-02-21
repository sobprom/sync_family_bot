package ru.syncfamily.service;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.syncfamily.service.model.Product;

import java.util.List;

public interface TelegramUiService {

    InlineKeyboardMarkup createShoppingListKeyboard(List<Product> products, boolean edit);

}
