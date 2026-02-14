package ru.syncfamily.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CallBack {

    BUY("buy"),
    CONFIRM_CLEAR("confirm_clear"),
    CLEAR_ALL("clear_all"),
    REFRESH("refresh"),
    UNKNOWN("");

    private final String action;

    public static CallBack getAction(String action) {
        if (action == null) return UNKNOWN;
        for (CallBack a : CallBack.values()) {
            if (action.startsWith(a.action)) {
                return a;
            }
        }
        return UNKNOWN;
    }
}
