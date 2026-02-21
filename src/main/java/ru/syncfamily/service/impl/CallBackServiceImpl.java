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

import static ru.syncfamily.service.model.CallBack.CLEAR_ALL;
import static ru.syncfamily.service.model.CallBack.DELETE_PRODUCT;
import static ru.syncfamily.service.model.CallBack.EDIT_PRODUCT;
import static ru.syncfamily.service.model.CallBack.REFRESH;
import static ru.syncfamily.service.model.CallBack.TOGGLE_MODE_EDIT;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CallBackServiceImpl implements CallBackService {

    private final PostgresDb db;
    private final FamilyRepository familyRepository;
    private final ProductRepository productRepository;

    private final SendService sendService;
    private final TelegramUiService uiService;

    private static int getProductId(String data, CallBack confirmEditProduct) {
        return Integer.parseInt(data.replace(confirmEditProduct.getAction(), ""));
    }

    @Override
    public Uni<Void> handleBuy(Update update) {

        var callbackQuery = update.getCallbackQuery();
        String callbackData = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId();
        String actor = callbackQuery.getFrom().getFirstName();

        int productId = getProductId(callbackData, CallBack.BUY);

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
                                    .replyMarkup(uiService.createShoppingListKeyboard(products, user.isShoppingListEditMode()))
                                    .build();
                            sendService.send(edit);
                        } else {
                            // Если сообщения еще нет (первый раз), отправляем новое
                            var send = SendMessage.builder()
                                    .chatId(user.getChatId())
                                    .text(messageText)
                                    .parseMode("Markdown")
                                    .replyMarkup(uiService.createShoppingListKeyboard(products, user.isShoppingListEditMode()))
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
                                    InlineKeyboardButton.builder().text("✅ ДА, УДАЛИТЬ").callbackData(CLEAR_ALL.getAction()).build(),
                                    InlineKeyboardButton.builder().text("❌ ОТМЕНА").callbackData(REFRESH.getAction()).build()
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
    public Uni<Void> handleConfirmEdit(Update update) {
        var callbackQuery = update.getCallbackQuery();
        long chatId = callbackQuery.getMessage().getChatId();
        var messageId = callbackQuery.getMessage().getMessageId();
        String data = callbackQuery.getData();
        int productId = getProductId(data, CallBack.CONFIRM_EDIT_PRODUCT);
        return db.async(ctx -> {
                    User user = familyRepository.getFamilyMemberByChatId(ctx, chatId)
                            .orElseThrow();
                    var familyId = user.getFamilyId();
                    return productRepository.findProduct(ctx, familyId, productId).orElseThrow();


                })
                .map(product -> {

                    var productName = product.getProductName();

                    var confirmMarkup = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("\uD83D\uDCDD Изменить")
                                            .callbackData(EDIT_PRODUCT.getAction() + productId).build(),
                                    InlineKeyboardButton.builder().text("\uD83D\uDDD1 Удалить")
                                            .callbackData(DELETE_PRODUCT.getAction() + productId).build(),
                                    InlineKeyboardButton.builder().text("❌ ОТМЕНА")
                                            .callbackData(TOGGLE_MODE_EDIT.getAction()).build()
                            ))
                            .build();

                    var edit = EditMessageText.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .text("\uD83E\uDDE9 *Что делаем с:* " + productName + " ?")
                            .parseMode("Markdown")
                            .replyMarkup(confirmMarkup)
                            .build();

                    return sendService.send(edit);


                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> handleEditProduct(Update update) {
        return null;
    }

    @Override
    public Uni<Void> handleDeleteProduct(Update update) {
        return null;
    }

    @Override
    public Uni<Void> handleEditMode(Update update) {
        var callbackQuery = update.getCallbackQuery();
        long chatId = callbackQuery.getMessage().getChatId();

        return db.async(ctx -> {
            User user = familyRepository.getFamilyMemberByChatId(ctx, chatId)
                    .orElseThrow();
            user = familyRepository.setShoppingEditMode(ctx, user);
            return Pair.of(user, productRepository.getAllProductsOrdered(ctx, user.getFamilyId()));
        }).map(pair -> {

            var user = pair.getLeft();
            var products = pair.getRight();
            var edit = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(user.getLastMessageId())
                    .text("🛒 *Режим редактирования:*")
                    .parseMode("Markdown")
                    .replyMarkup(uiService.createShoppingListKeyboard(products, user.isShoppingListEditMode()))
                    .build();

            return sendService.send(edit);
        }).replaceWithVoid();
    }

    @Override
    public Uni<Void> handleClearAll(Update update) {
        var callbackQuery = update.getCallbackQuery();
        long chatId = callbackQuery.getMessage().getChatId();
        String actor = callbackQuery.getFrom().getFirstName();
        return db.async(ctx -> {

            var user = familyRepository.getFamilyMemberByChatId(ctx, chatId).orElseThrow();
            Long familyId = user.getFamilyId();

            productRepository.deleteAllByFamilyId(ctx, familyId);

            return familyRepository.getFamilyMembersByFamilyId(ctx, familyId);
        }).map(users -> {

            String messageText = String.format("🗑 *%s* очистил(а) список покупок", actor);

            for (var member : users) {
                if (member.getLastMessageId() != null) {
                    var edit = EditMessageText.builder()
                            .chatId(member.getChatId())
                            .messageId(member.getLastMessageId())
                            .text(messageText)
                            .parseMode("Markdown")
                            .replyMarkup(uiService.createShoppingListKeyboard(List.of(), member.isShoppingListEditMode()))
                            .build();
                    sendService.send(edit);
                }
            }
            return users;
        }).replaceWithVoid();
    }

    @Override
    public Uni<Void> handleRefresh(Update update) {
        var callbackQuery = update.getCallbackQuery();
        long chatId = callbackQuery.getMessage().getChatId();

        return db.async(ctx -> {
            User user = familyRepository.getFamilyMemberByChatId(ctx, chatId)
                    .orElseThrow();
            user = familyRepository.dropShoppingEditMode(ctx, user);
            return Pair.of(user, productRepository.getAllProductsOrdered(ctx, user.getFamilyId()));
        }).map(pair -> {

            var user = pair.getLeft();
            var products = pair.getRight();
            var edit = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(user.getLastMessageId())
                    .text("🛒 *Актуальный список покупок:*")
                    .parseMode("Markdown")
                    .replyMarkup(uiService.createShoppingListKeyboard(products, user.isShoppingListEditMode()))
                    .build();

            return sendService.send(edit);
        }).replaceWithVoid();
    }
}
