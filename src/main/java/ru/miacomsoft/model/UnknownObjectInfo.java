// Новый файл: UnknownObjectInfo.java
package ru.miacomsoft.model;

import java.util.*;

public class UnknownObjectInfo {
    private String name;
    private Set<String> usedInForms;
    private List<String> usedInSql;

    public UnknownObjectInfo(String name) {
        this.name = name;
        this.usedInForms = new LinkedHashSet<>();
        this.usedInSql = new ArrayList<>();
    }

    public void addUsage(String formPath, String sqlPreview) {
        usedInForms.add(formPath);
        if (usedInSql.size() < 5) {
            usedInSql.add(sqlPreview);
        }
    }

    // Getters...
    public String getName() { return name; }
    public Set<String> getUsedInForms() { return usedInForms; }
    public List<String> getUsedInSql() { return usedInSql; }
}