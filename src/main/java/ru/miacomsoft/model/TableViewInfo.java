package ru.miacomsoft.model;

import java.util.*;

/**
 * Информация о таблице или вьюхе, используемой в SQL запросах
 */
public class TableViewInfo {

    // Имя таблицы/вьюхи
    private String name;

    // Тип (TABLE или VIEW)
    private Type type;

    // Список форм, где используется
    private Set<String> usedInForms;

    // Список SQL запросов, где используется
    private List<String> usedInSql;

    public enum Type {
        TABLE("Таблица"),
        VIEW("Представление"),
        UNKNOWN("Неизвестно");

        private String russianName;
        Type(String russianName) { this.russianName = russianName; }
        public String getRussianName() { return russianName; }
    }

    public TableViewInfo(String name) {
        this.name = name;
        this.usedInForms = new LinkedHashSet<>();
        this.usedInSql = new ArrayList<>();

        // Определяем тип по префиксу
        if (name.startsWith("D_V_")) {
            this.type = Type.VIEW;
        } else if (name.startsWith("D_")) {
            this.type = Type.TABLE;
        } else {
            this.type = Type.UNKNOWN;
        }
    }

    public String getName() { return name; }
    public Type getType() { return type; }
    public Set<String> getUsedInForms() { return usedInForms; }
    public List<String> getUsedInSql() { return usedInSql; }

    public void addUsage(String formPath, String sqlPreview) {
        usedInForms.add(formPath);
        if (usedInSql.size() < 5) { // Ограничим количество примеров
            usedInSql.add(sqlPreview);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TableViewInfo that = (TableViewInfo) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}