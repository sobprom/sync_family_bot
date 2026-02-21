package ru.syncfamily.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CallBack {

    BUY("buy"),
    CONFIRM_CLEAR("confirm_clear"),
    TOGGLE_MODE_EDIT("toggle_mode_edit"),
    CONFIRM_EDIT_PRODUCT("confirm_edit_product"),
    CLEAR_ALL("clear_all"),
    REFRESH("refresh"),
    EDIT_PRODUCT("edit_product"),
    DELETE_PRODUCT("delete_product"),
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
