package ru.syncfamily.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ListParser {
    /**
     * Превращает строку вида "Хлеб, молоко; сыр\nяблоки" в список объектов.
     * Используем Regex для поддержки разных разделителей: запятая, точка с запятой, перенос строки.
     */
    public List<String> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return Arrays.stream(text.split("[,;\\n]+")) // Режем по , ; или новой строке
                .map(String::trim)                  // Убираем пробелы по краям
                .filter(item -> !item.isEmpty())    // Убираем пустые строки
                .map(this::capitalize)              // Делаем первую букву заглавной (для красоты)
                .collect(Collectors.toList());
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
