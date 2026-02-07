package ru.syncfamily;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
import ru.syncfamily.service.model.Product;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
        long senderChatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        List<String> items = listParser.parse(text);

        // 1. Добавляем товары
        productRepository.addProducts(senderChatId, items)
                .chain(() -> {
                    // 2. Получаем список всех chatId членов этой семьи
                    // Вам нужно создать такой метод в репозитории
                    return familyRepository.getFamilyMembersByChatId(senderChatId)
                            .chain(members -> {
                                // 3. Получаем актуальный список продуктов
                                return productRepository.getAllProductsOrdered(senderChatId)
                                        .map(products -> Map.entry(members, products));
                            });
                })
                .subscribe().with(entry -> {
                    List<Long> memberIds = entry.getKey();
                    List<Product> products = entry.getValue();

                    // 4. Рассылаем сообщение каждому члену семьи
                    for (Long memberId : memberIds) {
                        SendMessage message = SendMessage.builder()
                                .chatId(memberId)
                                .text("🛒 Список покупок обновлен (" + update.getMessage().getFrom().getFirstName() + "):")
                                .replyMarkup(uiService.createShoppingListKeyboard(products))
                                .build();
                        send(message);
                    }
                }, failure -> {
                    log.error("Ошибка синхронизации списка", failure);
                    send(new SendMessage(String.valueOf(senderChatId), "⚠️ Ошибка при обновлении списка."));
                });
    }


    /**
     * Логика нажатия на кнопки "Куплено"
     */
    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackQueryId = update.getCallbackQuery().getId();

        if (callbackData.startsWith("buy_")) {
            int productId = Integer.parseInt(callbackData.replace("buy_", ""));

            productRepository.markAsBought(chatId, productId)
                    .chain(() -> familyRepository.getFamilyMembersByChatId(chatId)) // Ищем всех своих
                    .chain(members -> productRepository.getAllProductsOrdered(chatId) // Получаем список: сначала активные, потом купленные
                            .map(products -> Map.entry(members, products)))
                    .subscribe().with(entry -> {
                        List<Long> memberIds = entry.getKey();
                        List<Product> products = entry.getValue();

                        String productName = products.stream()
                                .filter(p -> p.getId().equals(productId))
                                .map(Product::getProductName)
                                .findFirst()
                                .orElse("товара");

                        for (Long memberId : memberIds) {
                            // Отправляем НОВОЕ сообщение с актуальным списком,
                            // так как отредактировать чужие сообщения бот не всегда может без хранения message_id
                            SendMessage sm = SendMessage.builder()
                                    .chatId(memberId)
                                    .text("🔄 Список обновлен (куплено: " + productName + ")")
                                    .replyMarkup(uiService.createShoppingListKeyboard(products))
                                    .build();
                            send(sm);
                        }
                        // Гасим часики на кнопке
                        answerCallback(callbackQueryId);
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

    private void answerCallback(String callbackQueryId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка при ответе на CallbackQuery: {}", e.getMessage());
        }
    }
}
