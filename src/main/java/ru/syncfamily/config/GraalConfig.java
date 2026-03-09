package ru.syncfamily.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberUpdated;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.ArrayList;

@SuppressWarnings("unused")
@RegisterForReflection(
        registerFullHierarchy = true,
        ignoreNested = false,
        targets = {
                // Базовые типы ответов
                ApiResponse.class,
                Update.class,

                // Все проблемные объекты с билдерами
                InlineQuery.class,
                ChatMemberUpdated.class,
                Message.class,
                User.class,
                Chat.class,

                // Регистрируем Jackson типы для работы со списками
                JsonNode.class,
                ArrayList.class
})
public class GraalConfig {
}
