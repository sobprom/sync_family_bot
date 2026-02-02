package ru.syncfamily;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.syncfamily.repository.ProductRepository;
import ru.syncfamily.service.ListParser;
import ru.syncfamily.service.TelegramUiService;

import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor
public class SyncFamilyBot implements LongPollingSingleThreadUpdateConsumer {

    @Inject
    ListParser listParser;
    @Inject
    ProductRepository productRepository;
    @Inject
    TelegramUiService uiService;

    private final TelegramClient telegramClient;

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    // Логика получения нового списка (сообщения от жены)
    private void handleTextMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        // 1. Парсим текст
        List<String> items = listParser.parse(text);

        // 2. Реактивно сохраняем и выводим кнопки
        productRepository.addProducts(chatId, items)
                .chain(() -> productRepository.getActiveProducts(chatId))
                .subscribe().with(products -> {
                    SendMessage message = SendMessage.builder()
                            .chatId(chatId)
                            .text("🛒 Список покупок обновлен:")
                            .replyMarkup(uiService.createShoppingListKeyboard(products))
                            .build();
                    send(message);
                }, failure -> System.err.println("Ошибка сохранения: " + failure.getMessage()));
    }

    // Логика нажатия на кнопки "Куплено"
    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackQueryId = update.getCallbackQuery().getId();

        if (callbackData.startsWith("buy_")) {
            String productName = callbackData.replace("buy_", "");

            // 1. Помечаем в БД как купленное
            productRepository.markAsBought(chatId, productName)
                    .chain(() -> productRepository.getActiveProducts(chatId))
                    .subscribe().with(remainingProducts -> {
                        try {
                            // 2. Убираем "часики" с кнопки
                            telegramClient.execute(new AnswerCallbackQuery(callbackQueryId));

                            // 3. Обновляем клавиатуру в том же сообщении
                            EditMessageReplyMarkup edit = EditMessageReplyMarkup.builder()
                                    .chatId(chatId)
                                    .messageId(messageId)
                                    .replyMarkup(uiService.createShoppingListKeyboard(remainingProducts))
                                    .build();
                            telegramClient.execute(edit);
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    private void send(SendMessage message) {
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
