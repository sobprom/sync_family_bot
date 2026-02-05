package ru.syncfamily.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

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
        System.out.println("--------------------------------------------------");
        System.out.println("🤖 ПОПЫТКА ЗАПУСКА БОТА...");
        System.out.println("TOKEN: " + botToken.substring(0, 4) + "**********");

        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, syncFamilyBot);
            System.out.println("✅ БОТ УСПЕШНО ЗАРЕГИСТРИРОВАН В TELEGRAM");
            System.out.println("--------------------------------------------------");

            // Чтобы поток не закрылся сразу в некоторых режимах
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("❌ ОШИБКА ЗАПУСКА: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
