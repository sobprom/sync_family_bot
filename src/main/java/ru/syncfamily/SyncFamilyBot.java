package ru.syncfamily;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.syncfamily.service.HandleService;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SyncFamilyBot implements LongPollingSingleThreadUpdateConsumer {


    private final HandleService handleService;

    @Override
    public void consume(Update update) {
        Uni<Void> processingUni;

        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            processingUni = text.startsWith("/")
                    ? handleService.handleCommand(update)
                    : handleService.handleTextMessage(update);
        } else if (update.hasCallbackQuery()) {
            processingUni = handleService.handleCallbackQuery(update);
        } else {
            return; // Игнорируем другие типы обновлений
        }
        // Централизованная подписка с логированием
        processingUni.subscribe().with(
                success -> log.debug("Обновление успешно обработано"),
                failure -> log.error("Критическая ошибка при обработке Update: {}", failure.getMessage())
        );
    }

}
