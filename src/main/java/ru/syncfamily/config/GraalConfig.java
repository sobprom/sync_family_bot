package ru.syncfamily.config;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.syncfamily.service.model.Product;

@SuppressWarnings("unused")
@RegisterForReflection(targets = {
        ApiResponse.class,
        Update.class,
        Message.class,
        User.class,
        Chat.class,
        Product.class,
        User.class
})
public class GraalConfig {
}
