package ru.syncfamily.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
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
}
