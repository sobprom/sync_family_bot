package ru.syncfamily.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@ApplicationScoped
public class BotConfig {

    @ConfigProperty(name = "sync-family.bot.token")
    String botToken;

    @Produces
    @ApplicationScoped
    public TelegramClient telegramClient() {
        // Создаем стандартный клиент на базе OkHttp
        return new OkHttpTelegramClient(botToken);
    }

    void onStart(@Observes StartupEvent ev, ru.syncfamily.SyncFamilyBot syncFamilyBot) {
        log.info("--------------------------------------------------");
        log.info("🤖 ПОПЫТКА ЗАПУСКА БОТА...");
        log.info("TOKEN: {} **********", botToken.substring(0, 4));

        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, syncFamilyBot);
            log.info("✅ БОТ УСПЕШНО ЗАРЕГИСТРИРОВАН В TELEGRAM");
            log.info("--------------------------------------------------");

            // Чтобы поток не закрылся сразу в некоторых режимах
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("❌ ОШИБКА ЗАПУСКА:", e);
        }
    }
}
