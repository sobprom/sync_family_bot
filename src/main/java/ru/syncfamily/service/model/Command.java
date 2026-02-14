package ru.syncfamily.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Command {

    START("/start"),
    START_WITH_INVITE("/start "),
    CREATE_FAMILY("/create_family"),
    UNKNOWN("");
    private final String command;

    public static Command getCommand(String command) {
        if (command == null) return UNKNOWN;
        for (Command a : Command.values()) {
            if (command.startsWith(a.command)) {
                return a;
            }
        }
        return UNKNOWN;
    }

}
