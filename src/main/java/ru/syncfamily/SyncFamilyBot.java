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
import ru.syncfamily.repository.FamilyRepository;
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
    FamilyRepository familyRepository;

    @Inject
    TelegramUiService uiService;

    private final TelegramClient telegramClient;

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            // Если текст начинается с /, обрабатываем как команду
            if (text.startsWith("/")) {
                handleCommand(update);
            } else {
                handleTextMessage(update);
            }
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    /**
     * Логика системных команд (/start, /create_family, /join)
     */
    private void handleCommand(Update update) {
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        if (text.startsWith("/start ")) {
            String inviteCode = text.replace("/start ", "").trim();
            familyRepository.joinFamily(chatId, inviteCode)
                    .subscribe().with(success -> {
                        if (success) {
                            send(new SendMessage(String.valueOf(chatId), "🤝 Вы успешно вступили в семью по ссылке!"));
                        } else {
                            send(new SendMessage(String.valueOf(chatId), "❌ Ссылка недействительна или устарела."));
                        }
                    });
        }
        else if (text.equals("/start")) {
            send(new SendMessage(String.valueOf(chatId),
                    """
                            👋 Привет! Я помогу синхронизировать список покупок в вашей семье.
                            
                            🔹 Напиши /create_family, чтобы создать новую группу.
                            🔹 Напиши /join [код], чтобы вступить в существующую."""));
        } else if (text.startsWith("/create_family")) {
            familyRepository.createFamily(chatId)
                    .subscribe().with(code ->
                            send(new SendMessage(String.valueOf(chatId),
                                    "✅ Семья создана!\n\nКод для вступления: `" + code + "`\n\n" +
                                            "Перешли этот код члену семьи. После вступления ваш список станет общим.")));
        } else if (text.startsWith("/join ")) {
            String code = text.replace("/join ", "").trim().toUpperCase();
            familyRepository.joinFamily(chatId, code)
                    .subscribe().with(success -> {
                        String response = success
                                ? "🤝 Поздравляю! Вы успешно присоединились к семье. Теперь ваши списки синхронизированы."
                                : "❌ Ошибка: Семья с таким кодом не найдена. Проверь правильность написания.";
                        send(new SendMessage(String.valueOf(chatId), response));
                    });
        }
    }

    /**
     * Логика получения нового списка (сообщения от жены/мужа)
     */
    private void handleTextMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        // 1. Парсим текст в список строк
        List<String> items = listParser.parse(text);

        // 2. Сохраняем (репозиторий сам определит family_id) и выводим кнопки
        productRepository.addProducts(chatId, items)
                .chain(() -> productRepository.getActiveProducts(chatId))
                .subscribe().with(products -> {
                    SendMessage message = SendMessage.builder()
                            .chatId(chatId)
                            .text("🛒 Список покупок обновлен:")
                            .replyMarkup(uiService.createShoppingListKeyboard(products))
                            .build();
                    send(message);
                }, failure -> {
                    failure.printStackTrace();
                    send(new SendMessage(String.valueOf(chatId), "⚠️ Ошибка при сохранении списка."));
                });
    }

    /**
     * Логика нажатия на кнопки "Куплено"
     */
    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();
        String callbackQueryId = update.getCallbackQuery().getId();

        if (callbackData.startsWith("buy_")) {
            String productName = callbackData.replace("buy_", "");

            // 1. Помечаем в БД как купленное (на уровне семьи)
            productRepository.markAsBought(chatId, productName)
                    .chain(() -> productRepository.getActiveProducts(chatId))
                    .subscribe().with(remainingProducts -> {
                        try {
                            // 2. Убираем анимацию загрузки на кнопке
                            telegramClient.execute(new AnswerCallbackQuery(callbackQueryId));

                            // 3. Обновляем существующее сообщение новым списком
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
