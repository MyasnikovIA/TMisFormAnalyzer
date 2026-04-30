package ru.miacomsoft.model;

import java.util.*;

/**
 * Информация о пакете или функции, используемой в SQL запросах
 */
public class PackageFunctionInfo {

    // Полное имя (например: D_PKG_HOSP_HISTORIES.GET_STATUS)
    private String fullName;

    // Имя пакета (например: D_PKG_HOSP_HISTORIES)
    private String packageName;

    // Имя функции (например: GET_STATUS)
    private String functionName;

    // Тип (PACKAGE_FUNCTION или STANDALONE_FUNCTION)
    private Type type;

    // Список форм, где используется
    private Set<String> usedInForms;

    // Список SQL запросов, где используется
    private List<String> usedInSql;

    public enum Type {
        PACKAGE_FUNCTION("Пакетная функция"),
        STANDALONE_FUNCTION("Самостоятельная функция");

        private String russianName;
        Type(String russianName) { this.russianName = russianName; }
        public String getRussianName() { return russianName; }
    }

    public PackageFunctionInfo(String fullName) {
        this.fullName = fullName;
        this.usedInForms = new LinkedHashSet<>();
        this.usedInSql = new ArrayList<>();

        // Разбираем имя
        if (fullName.contains(".")) {
            String[] parts = fullName.split("\\.");
            this.packageName = parts[0];
            this.functionName = parts[1];
            this.type = Type.PACKAGE_FUNCTION;
        } else {
            this.packageName = "";
            this.functionName = fullName;
            this.type = Type.STANDALONE_FUNCTION;
        }
    }

    public String getFullName() { return fullName; }
    public String getPackageName() { return packageName; }
    public String getFunctionName() { return functionName; }
    public Type getType() { return type; }
    public Set<String> getUsedInForms() { return usedInForms; }
    public List<String> getUsedInSql() { return usedInSql; }

    public void addUsage(String formPath, String sqlPreview) {
        usedInForms.add(formPath);
        if (usedInSql.size() < 5) {
            usedInSql.add(sqlPreview);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackageFunctionInfo that = (PackageFunctionInfo) o;
        return Objects.equals(fullName, that.fullName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullName);
    }
}