package ru.miacomsoft.model;

import java.util.*;

/**
 * Информация о вьюхе и её табличных зависимостях
 */
public class ViewTableDependencies {

    private String viewName;
    private Set<String> oracleTables;      // Таблицы из Oracle DDL
    private Set<String> postgresTables;    // Таблицы из PostgreSQL DDL
    private boolean existsInOracle;
    private boolean existsInPostgres;
    private String oracleError;
    private String postgresError;

    // Связь с исходной информацией из отчетов
    private TableViewInfo originalViewInfo;

    public ViewTableDependencies(String viewName) {
        this.viewName = viewName;
        this.oracleTables = new LinkedHashSet<>();
        this.postgresTables = new LinkedHashSet<>();
        this.existsInOracle = false;
        this.existsInPostgres = false;
    }



    public void addAllOracleTables(Collection<String> tables) {
        if (tables != null) {
            tables.forEach(t -> this.oracleTables.add(t.toUpperCase()));
        }
    }

    public void addAllPostgresTables(Collection<String> tables) {
        if (tables != null) {
            tables.forEach(t -> this.postgresTables.add(t.toUpperCase()));
        }
    }

    public void setOriginalViewInfo(TableViewInfo info) {
        this.originalViewInfo = info;
    }

    public TableViewInfo getOriginalViewInfo() {
        return originalViewInfo;
    }

    public void setExistsInOracle(boolean exists) {
        this.existsInOracle = exists;
    }

    public void setExistsInPostgres(boolean exists) {
        this.existsInPostgres = exists;
    }

    public void setOracleError(String error) {
        this.oracleError = error;
    }

    public void setPostgresError(String error) {
        this.postgresError = error;
    }

    public Set<String> getOracleTables() {
        return oracleTables;
    }

    public Set<String> getPostgresTables() {
        return postgresTables;
    }

    public boolean isExistsInOracle() {
        return existsInOracle;
    }

    public boolean isExistsInPostgres() {
        return existsInPostgres;
    }

    public String getOracleError() {
        return oracleError;
    }

    public String getPostgresError() {
        return postgresError;
    }

    public String getViewName() {
        return viewName;
    }
}
