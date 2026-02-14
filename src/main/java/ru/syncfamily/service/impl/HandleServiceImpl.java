package ru.syncfamily.service.impl;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.syncfamily.repository.FamilyRepository;
import ru.syncfamily.repository.PostgresDb;
import ru.syncfamily.repository.ProductRepository;
import ru.syncfamily.service.CallBackService;
import ru.syncfamily.service.CommandService;
import ru.syncfamily.service.HandleService;
import ru.syncfamily.service.SendService;
import ru.syncfamily.service.TelegramUiService;
import ru.syncfamily.service.model.CallBack;
import ru.syncfamily.service.model.Command;
import ru.syncfamily.service.model.Product;
import ru.syncfamily.service.model.User;

import java.util.List;
import java.util.Objects;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class HandleServiceImpl implements HandleService {


    private final PostgresDb db;
    private final ListParser listParser;
    private final TelegramUiService uiService;
    private final CommandService commandService;
    private final SendService sendService;
    private final CallBackService callBackService;
    private final FamilyRepository familyRepository;
    private final ProductRepository productRepository;

    @Override
    public Uni<Void> handleTextMessage(Update update) {

        return Uni.createFrom().deferred(() -> {
            long senderChatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();
            List<String> productsFromChat = listParser.parse(text);

            return db.async(ctx -> {
                var currentUser = familyRepository.getFamilyMemberByChatId(ctx, senderChatId)
                        .orElseThrow();
                var familyId = currentUser.getFamilyId();
                productRepository.addProducts(ctx, familyId, productsFromChat);
                List<Product> productsOrdered = productRepository.getAllProductsOrdered(ctx, familyId);
                List<User> allUsers = familyRepository.getFamilyMembersByFamilyId(ctx, familyId);
                return Pair.of(allUsers, productsOrdered);
            }).flatMap(tuple -> {
                var allUsers = tuple.getLeft();
                var productsOrdered = tuple.getRight();
                for (var user : allUsers) {
                    if (user.getLastMessageId() != null && user.getLastMessageId() != 0) {
                        sendService.send(new DeleteMessage(String.valueOf(user.getChatId()), user.getLastMessageId()));
                    }
                    var message = SendMessage.builder()
                            .chatId(user.getChatId())
                            .text("🛒 Список покупок обновлен (" + update.getMessage().getFrom().getFirstName() + "):")
                            .replyMarkup(uiService.createShoppingListKeyboard(productsOrdered))
                            .build();
                    var sent = sendService.send(message);
                    if (Objects.nonNull(sent)) {
                        user.setLastMessageId(sent.getMessageId());
                    }
                }

                return db.async(ctx -> {
                    familyRepository.updateLastMessageId(ctx, allUsers);
                    return true;
                });
            }).replaceWithVoid();
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> handleCommand(Update update) {

        var command = Command.getCommand(update.getMessage().getText());

        return switch (command) {
            case START -> commandService.start(update);
            case START_WITH_INVITE -> commandService.startWithInvite(update);
            case CREATE_FAMILY -> commandService.createFamily(update);
            case UNKNOWN -> Uni.createFrom().voidItem();
        };
    }

    @Override
    public Uni<Void> handleCallbackQuery(Update update) {

        var callbackQuery = update.getCallbackQuery();
        String callbackData = callbackQuery.getData();
        String callbackQueryId = callbackQuery.getId();
        var action = CallBack.getAction(callbackData);

        sendService.answerCallback(callbackQueryId);

        return switch (action) {
            case BUY -> callBackService.handleBuy(update);
            case REFRESH -> callBackService.handleRefresh(update);
            case CLEAR_ALL -> callBackService.handleClearAll(update);
            case CONFIRM_CLEAR -> callBackService.handleConfirmClear(update);
            case UNKNOWN -> Uni.createFrom().voidItem();
        };

    }
}
