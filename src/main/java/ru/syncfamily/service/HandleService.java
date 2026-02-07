package ru.syncfamily.service;

import io.smallrye.mutiny.Uni;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface HandleService {
    Uni<Void> handleTextMessage(Update update);

    Uni<Void> handleCommand(Update update);

    Uni<Void> handleCallbackQuery(Update update);
}
