package ru.syncfamily;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.syncfamily.repository.FamilyRepository;
import ru.syncfamily.repository.ProductRepository;
import ru.syncfamily.service.ListParser;
import ru.syncfamily.service.TelegramUiService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SyncFamilyBot implements LongPollingSingleThreadUpdateConsumer {

    public static final String BOT_NAME = "sync_family_bot";

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
        if (text.equals("/start")) {
            send(new SendMessage(String.valueOf(chatId),
                    """
                            👋 Привет! Я помогу синхронизировать список покупок в вашей семье.
                            
                            🔹 Напиши /create_family, чтобы создать новую группу.
                            """));
        } else if (text.startsWith("/start ")) {
            String inviteCode = text.replace("/start ", "").trim();
            familyRepository.joinFamily(chatId, inviteCode)
                    .subscribe().with(success -> {
                        if (success) {
                            send(new SendMessage(String.valueOf(chatId), "🤝 Вы успешно вступили в семью по ссылке!"));
                        } else {
                            send(new SendMessage(String.valueOf(chatId), "❌ Ссылка недействительна или устарела."));
                        }
                    });
        } else if (text.startsWith("/create_family")) {
            familyRepository.createFamilyAndGetCode(chatId).subscribe().with(code -> {

                String inviteLink = "https://t.me/" + BOT_NAME + "?start=" + code;

                String shareUrl = "https://t.me/share/url?url="
                        + URLEncoder.encode(inviteLink, StandardCharsets.UTF_8)
                        + "&text=" + URLEncoder.encode("Присоединяйся к моей семье в боте покупок! 🛒", StandardCharsets.UTF_8);

                // 1. Создаем кнопку через Builder
                InlineKeyboardButton btn = InlineKeyboardButton.builder()
                        .text("👪 Отправить приглашение")
                        .url(shareUrl)
                        .build();

                // 2. Формируем клавиатуру (в 7.x используется InlineKeyboardRow)
                InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(btn))
                        .build();

                // 3. Собираем само сообщение
                SendMessage sm = SendMessage.builder()
                        .chatId(chatId) // Можно передавать long напрямую в новых версиях
                        .text("Семья создана! Нажми кнопку ниже, чтобы отправить ссылку:")
                        .replyMarkup(markup)
                        .build();

                send(sm);
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
                    log.error("Ошибка сохранения в репозиторий", failure);
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
                            log.error("Ошибка нажатия кнопки куплено", e);
                        }
                    });
        }
    }

    private void send(SendMessage message) {
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения", e);
        }
    }
}
