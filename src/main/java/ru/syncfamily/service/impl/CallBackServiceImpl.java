package ru.syncfamily.service.impl;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.syncfamily.repository.FamilyRepository;
import ru.syncfamily.repository.PostgresDb;
import ru.syncfamily.repository.ProductRepository;
import ru.syncfamily.service.CallBackService;
import ru.syncfamily.service.SendService;
import ru.syncfamily.service.TelegramUiService;
import ru.syncfamily.service.model.CallBack;
import ru.syncfamily.service.model.Product;
import ru.syncfamily.service.model.User;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CallBackServiceImpl implements CallBackService {

    private final PostgresDb db;
    private final FamilyRepository familyRepository;
    private final ProductRepository productRepository;

    private final SendService sendService;
    private final TelegramUiService uiService;

    @Override
    public Uni<Void> handleBuy(Update update) {

        var callbackQuery = update.getCallbackQuery();
        String callbackData = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId();
        String actor = callbackQuery.getFrom().getFirstName();

        int productId = Integer.parseInt(callbackData.replace(CallBack.BUY.getAction(), ""));

        return db.async(ctx -> {
                    var user = familyRepository.getFamilyMemberByChatId(ctx, chatId)
                            .orElseThrow();
                    var familyId = user.getFamilyId();
                    productRepository.inverseBought(ctx, familyId, productId);
                    List<Product> products = productRepository.getAllProductsOrdered(ctx, familyId);
                    List<User> users = familyRepository.getFamilyMembersByFamilyId(ctx, familyId);
                    return Pair.of(users, products);
                })
                .map(pair -> {

                    var users = pair.getLeft();
                    var products = pair.getRight();

                    List<User> updatedUsers = new ArrayList<>();

                    var productOpt = products.stream()
                            .filter(p -> p.getId().equals(productId))
                            .findFirst();

                    if (productOpt.isEmpty()) {
                        return updatedUsers;
                    }

                    var product = productOpt.get();

                    for (var user : users) {

                        String action = product.isBought() ? "купил(а)" : "отменил(а) покупку";

                        String messageText = String.format(
                                "🛒 *Список обновлен* ✅ *%s* %s: *%s*",
                                actor, action, product.getProductName()
                        );

                        if (user.getLastMessageId() != null && user.getLastMessageId() != 0) {
                            // РЕДАКТИРУЕМ старое сообщение
                            var edit = EditMessageText.builder()
                                    .chatId(user.getChatId())
                                    .messageId(user.getLastMessageId())
                                    .text(messageText)
                                    .parseMode("Markdown")
                                    .replyMarkup(uiService.createShoppingListKeyboard(products))
                                    .build();
                            sendService.send(edit);
                        } else {
                            // Если сообщения еще нет (первый раз), отправляем новое
                            var send = SendMessage.builder()
                                    .chatId(user.getChatId())
                                    .text(messageText)
                                    .parseMode("Markdown")
                                    .replyMarkup(uiService.createShoppingListKeyboard(products))
                                    .build();
                            var m = sendService.send(send);
                            if (m != null) {
                                user.setLastMessageId(m.getMessageId());
                                updatedUsers.add(user);
                            }
                        }


                    }
                    return updatedUsers;

                }).chain(usersToUpdate -> db.async(ctx -> {
                    familyRepository.updateLastMessageId(ctx, usersToUpdate);
                    return null;
                }))
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> handleConfirmClear(Update update) {
        return Uni.createFrom().item(() -> {
                    var callbackQuery = update.getCallbackQuery();
                    long chatId = callbackQuery.getMessage().getChatId();
                    var messageId = callbackQuery.getMessage().getMessageId();
                    var confirmMarkup = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("✅ ДА, УДАЛИТЬ").callbackData("clear_all").build(),
                                    InlineKeyboardButton.builder().text("❌ ОТМЕНА").callbackData("refresh_list").build()
                            ))
                            .build();

                    sendService.send(EditMessageReplyMarkup.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .replyMarkup(confirmMarkup)
                            .build());
                    return true;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .replaceWithVoid();

    }

    @Override
    public Uni<Void> handleClearAll(Update update) {
        return null;
    }

    @Override
    public Uni<Void> handleRefresh(Update update) {
        return null;
    }
}
