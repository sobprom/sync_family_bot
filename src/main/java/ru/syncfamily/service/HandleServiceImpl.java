package ru.syncfamily.service;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.syncfamily.repository.FamilyRepository;
import ru.syncfamily.repository.ProductRepository;
import ru.syncfamily.repository.PostgresDb;
import ru.syncfamily.service.model.Product;
import ru.syncfamily.service.model.User;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class HandleServiceImpl implements HandleService {

    public static final String BOT_NAME = "sync_family_bot";

    private final PostgresDb db;
    private final ListParser listParser;
    private final TelegramUiService uiService;
    private final TelegramClient telegramClient;
    private final FamilyRepository familyRepository;
    private final ProductRepository productRepository;

    @Override
    public Uni<Void> handleTextMessage(Update update) {
        return Uni.createFrom().deferred(() -> {
            long senderChatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();
            List<String> items = listParser.parse(text);

            return db.async(ctx -> {
                productRepository.addProducts(ctx, senderChatId, items);
                var users = familyRepository.getFamilyMembersByChatId(ctx, senderChatId);
                var products = productRepository.getAllProductsOrdered(ctx, senderChatId);
                return Map.entry(users, products);
            }).invoke(entry -> {
                List<User> users = entry.getKey();
                List<Product> products = entry.getValue();
                for (var user : users) {
                    if (user.getLastMessageId() != null && user.getLastMessageId() != 0) {
                        send(new DeleteMessage(String.valueOf(user.getChatId()), user.getLastMessageId()));
                    }
                    var message = SendMessage.builder()
                            .chatId(user.getChatId())
                            .text("🛒 Список покупок обновлен (" + update.getMessage().getFrom().getFirstName() + "):")
                            .replyMarkup(uiService.createShoppingListKeyboard(products))
                            .build();
                    send(message);
                }
            }).replaceWithVoid();
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());

    }

    @Override
    public Uni<Void> handleCommand(Update update) {
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        String userName = update.getMessage().getFrom().getFirstName();

        if (text.equals("/start")) {
            return Uni.createFrom().item(() -> {
                send(new SendMessage(String.valueOf(chatId),
                        """
                                👋 Привет! Я помогу синхронизировать список покупок в вашей семье.
                                
                                🔹 Напиши /create_family, чтобы создать новую группу.
                                """));
                return null;
            }).replaceWithVoid();

        } else if (text.startsWith("/start ")) {
            String inviteCode = text.replace("/start ", "").trim();
            return db.async(ctx -> familyRepository.joinFamily(ctx, chatId, inviteCode, userName))
                    .invoke(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            send(new SendMessage(String.valueOf(chatId), "🤝 Вы успешно вступили в семью по ссылке!"));
                        } else {
                            send(new SendMessage(String.valueOf(chatId), "❌ Ссылка недействительна или устарела."));
                        }
                    }).replaceWithVoid();

        } else if (text.startsWith("/create_family")) {
            return db.async(ctx -> familyRepository.createFamilyAndGetCode(ctx, chatId, userName))
                    .invoke(code -> {

                        String inviteLink = "https://t.me/" + BOT_NAME + "?start=" + code;
                        String shareUrl = "https://t.me/share/url?url="
                                + URLEncoder.encode(inviteLink, StandardCharsets.UTF_8)
                                + "&text=" + URLEncoder.encode("Присоединяйся к моей семье в боте покупок! 🛒", StandardCharsets.UTF_8);

                        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder()
                                                .text("👪 Отправить приглашение")
                                                .url(shareUrl)
                                                .build()
                                ))
                                .build();

                        send(SendMessage.builder()
                                .chatId(chatId)
                                .text("Семья создана! Нажми кнопку ниже, чтобы отправить ссылку:")
                                .replyMarkup(markup)
                                .build());

                    }).replaceWithVoid();
        }

        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackQueryId = update.getCallbackQuery().getId();
        String actor = update.getCallbackQuery().getFrom().getFirstName();

        if (callbackData.startsWith("buy_")) {
            // 1. Извлекаем ID товара
            int productId = Integer.parseInt(callbackData.replace("buy_", ""));
            answerCallback(callbackQueryId);

            // 2. Сначала отмечаем в БД, потом запрашиваем обновленный список
            return db.async(ctx -> {
                        productRepository.markAsBought(ctx, chatId, productId);
                        var products = productRepository.getAllProductsOrdered(ctx, chatId);
                        var users = familyRepository.getFamilyMembersByChatId(ctx, chatId);
                        return Map.entry(users, products);
                    })
                    .map(entry -> {
                        // Гасим "часики" в Telegram сразу после получения данных


                        var products = entry.getValue();
                        var users = entry.getKey();

                        List<User> updatedUsers = new ArrayList<>();

                        for (var user : users) {
                            // Находим имя купленного товара для заголовка
                            String productName = products.stream()
                                    .filter(p -> p.getId().equals(productId))
                                    .map(Product::getProductName)
                                    .findFirst()
                                    .orElse("товара");

                            // 2. Удаляем предыдущее сообщение со списком
                            if (user.getLastMessageId() != null && user.getLastMessageId() != 0) {
                                send(new DeleteMessage(String.valueOf(user.getChatId()), user.getLastMessageId()));
                            }

                            String messageText = String.format(
                                    "🛒 *Список обновлен* ✅ *%s* купил(а): *%s*",
                                    actor,
                                    productName
                            );
                            var message = SendMessage.builder()
                                    .chatId(user.getChatId())
                                    .text(messageText)
                                    .parseMode("Markdown")
                                    .replyMarkup(uiService.createShoppingListKeyboard(products))
                                    .build();
                            var m = send(message);
                            if (Objects.nonNull(m)) {
                                user.setLastMessageId(m.getMessageId());
                                updatedUsers.add(user);
                            }

                        }
                        return updatedUsers;

                    }).chain(usersToUpdate -> db.async(ctx -> {
                        familyRepository.updateLastMessageId(ctx, usersToUpdate);
                        return null;
                    }))
                    .replaceWithVoid();
        }

        return Uni.createFrom().voidItem();
    }

    private Message send(SendMessage message) {
        try {
            return telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения", e);
        }
        return null;
    }

    private void send(DeleteMessage message) {
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
