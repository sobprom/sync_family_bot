package ru.syncfamily.service.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.syncfamily.repository.FamilyRepository;
import ru.syncfamily.repository.PostgresDb;
import ru.syncfamily.service.CommandService;
import ru.syncfamily.service.SendService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static ru.syncfamily.config.BotConfig.BOT_NAME;
import static ru.syncfamily.service.model.CallBack.CONFIRM_LEAVE_FAMILY;
import static ru.syncfamily.service.model.CallBack.REFRESH;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CommandServiceImpl implements CommandService {

    private final PostgresDb db;
    private final FamilyRepository familyRepository;

    private final SendService sendService;

    @Override
    public Uni<Void> start(Update update) {
        long chatId = update.getMessage().getChatId();

        // 1. Проверяем, состоит ли пользователь в семье
        return db.async(ctx -> familyRepository.getFamilyMemberByChatId(ctx, chatId))
                .onItem().transformToUni(memberOpt -> {

                    // 2. Если пользователь найден (уже в семье)
                    if (memberOpt.isPresent()) {
                        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder()
                                                .text("🛒 Перейти к покупкам")
                                                .callbackData(REFRESH.getAction())
                                                .build()
                                ))
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder()
                                                .text("🚪 Выйти из группы")
                                                .callbackData(CONFIRM_LEAVE_FAMILY.getAction())
                                                .build()
                                ))
                                .build();

                        sendService.send(SendMessage.builder()
                                .chatId(chatId)
                                .text("🏠 Вы находитесь в семейном чате. Что хотите сделать?")
                                .replyMarkup(markup)
                                .build());

                        return Uni.createFrom().voidItem();
                    }

                    // 3. Если пользователя нет в базе (новый пользователь)
                    sendService.send(new SendMessage(String.valueOf(chatId),
                            """
                                    👋 Привет! Я помогу синхронизировать список покупок в вашей семье.
                                    
                                    🔹 Напиши /create_family, чтобы создать новую группу.
                                    🔹 Или перейди по ссылке-приглашению от члена семьи.
                                    """));

                    return Uni.createFrom().voidItem();
                });
    }


    @Override
    public Uni<Void> startWithInvite(Update update) {

        String text = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        String userName = update.getMessage().getFrom().getFirstName();

        String inviteCode = text.replace("/start ", "").trim();
        return db.async(ctx -> familyRepository.joinFamily(ctx, chatId, inviteCode, userName))
                .invoke(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        sendService.send(new SendMessage(String.valueOf(chatId), "🤝 Вы успешно вступили в семью по ссылке!"));
                    } else {
                        sendService.send(new SendMessage(String.valueOf(chatId), "❌ Ссылка недействительна или устарела."));
                    }
                }).replaceWithVoid();
    }

    @Override
    public Uni<Void> createFamily(Update update) {
        long chatId = update.getMessage().getChatId();
        String userName = update.getMessage().getFrom().getFirstName();

        return db.async(ctx -> familyRepository.getFamilyMemberByChatId(ctx, chatId)
                        .map(user -> familyRepository.getFamilyCode(ctx, user))
                        .orElseGet(() -> familyRepository.createFamilyAndGetCode(ctx, chatId, userName)))
                .map(code -> {

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

                    return sendService.send(SendMessage.builder()
                            .chatId(chatId)
                            .text("Семья создана! Нажми кнопку ниже, чтобы отправить ссылку:")
                            .replyMarkup(markup)
                            .build());

                }).replaceWithVoid();
    }
}
