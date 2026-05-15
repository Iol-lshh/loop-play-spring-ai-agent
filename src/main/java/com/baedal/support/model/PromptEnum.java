package com.baedal.support.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public interface PromptEnum {

    String label();

    String criteria();

    default List<String> examples() {
        return List.of();
    }
    default String manual() {
        return "";
    }

    default String toPromptRow() {
        String name = ((Enum<?>) this).name();
        String exs  = examples().isEmpty() ? "-" : String.join(" / ", examples());
        return "| %s | %s | %s | %s |".formatted(name, label(), criteria(), exs);
    }

    static <E extends Enum<E> & PromptEnum> String toPromptTable(Class<E> type) {
        StringBuilder sb = new StringBuilder();

        String header = """
            | value | label | criteria | examples |
            | --- | --- | --- | --- |
            """;
        String rows = Arrays.stream(type.getEnumConstants())
                .map(PromptEnum::toPromptRow)
                .collect(Collectors.joining("\n"));

        String manuals = Arrays.stream(type.getEnumConstants())
                .filter(e -> !e.manual().isBlank())
                .map(e -> "- %s: %s".formatted(e.name(), e.manual()))
                .collect(Collectors.joining("\n"));

        sb.append(header).append(rows);
        if (!manuals.isEmpty()) {
            sb.append("\n\n[manual]\n").append(manuals);
        }
        return sb.toString();
    }
}
