package ru.syncfamily.service;

import io.smallrye.mutiny.Uni;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface CommandService {

    Uni<Void> start(Update update);

    Uni<Void> startWithInvite(Update update);

    Uni<Void> createFamily(Update update);
}
