package ru.syncfamily.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.syncfamily.service.SendService;

import java.io.Serializable;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SendServiceImpl implements SendService {
    private final TelegramClient telegramClient;

    @Override
    public <T extends Serializable> T send(BotApiMethod<T> message) {
        try {
            return telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения", e);
        }
        return null;
    }

    @Override
    public void answerCallback(String callbackQueryId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка при ответе на CallbackQuery: {}", e.getMessage());
        }
    }
}
