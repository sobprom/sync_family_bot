package ru.syncfamily.service;

import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;

import java.io.Serializable;

public interface SendService {
    <T extends Serializable> T send(BotApiMethod<T> message);

    void answerCallback(String callbackQueryId);
}
