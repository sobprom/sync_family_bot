package ru.syncfamily.service;

import io.smallrye.mutiny.Uni;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface CallBackService {
    Uni<Void> handleBuy(Update update);

    Uni<Void> handleConfirmClear(Update update);

    Uni<Void> handleClearAll(Update update);

    Uni<Void> handleRefresh(Update update);
}
