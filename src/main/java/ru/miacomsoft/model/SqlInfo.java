package ru.miacomsoft.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Информация об SQL запросе, найденном в форме
 */
public class SqlInfo {

    // Источник (базовая форма или UserForms)
    private String sourceType;

    // Путь к файлу, где найден SQL
    private String sourcePath;

    // Путь к базовой форме (если это переопределение)
    private String baseFormPath;

    // Тип компонента (DataSet, Action)
    private String componentType;

    // Имя компонента
    private String componentName;

    // Содержимое SQL запроса (оригинальное)
    private String sqlContent;

    // Обработанный SQL (очищенный от CDATA)
    private String cleanSql;

    // Список таблиц/вьюх в запросе
    private Set<String> tablesViews;

    // Список пакетов/функций в запросе
    private Set<String> packagesFunctions;

    private Set<String> userProcedures;

    private Set<String> systemOptions;

    private Set<String> unknownObjects;  // Объекты для разбора аналитиком

    private Set<String> constants;  // КОНСТАНТЫ из D_PKG_CONSTANTS.SEARCH_*


    public SqlInfo() {
        this.tablesViews = new LinkedHashSet<>();
        this.packagesFunctions = new LinkedHashSet<>();
        this.userProcedures = new LinkedHashSet<>();
        this.systemOptions = new LinkedHashSet<>();
        this.unknownObjects = new LinkedHashSet<>();
        this.constants = new LinkedHashSet<>();  // ДОБАВИТЬ
    }

    // Getters и Setters
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

    public String getBaseFormPath() { return baseFormPath; }
    public void setBaseFormPath(String baseFormPath) { this.baseFormPath = baseFormPath; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }

    public String getSqlContent() { return sqlContent; }
    public void setSqlContent(String sqlContent) { this.sqlContent = sqlContent; }

    public String getCleanSql() { return cleanSql; }
    public void setCleanSql(String cleanSql) { this.cleanSql = cleanSql; }

    public Set<String> getTablesViews() { return tablesViews; }
    public void addTableView(String tv) { this.tablesViews.add(tv); }

    public Set<String> getPackagesFunctions() { return packagesFunctions; }
    public void addPackageFunction(String pf) { this.packagesFunctions.add(pf); }

    public Set<String> getSystemOptions() { return systemOptions; }
    public void addSystemOption(String option) { this.systemOptions.add(option); }

    public Set<String> getUnknownObjects() { return unknownObjects; }
    public void addUnknownObject(String obj) { this.unknownObjects.add(obj); }

    public Set<String> getUserProcedures() { return userProcedures; }
    public void addUserProcedure(String proc) { this.userProcedures.add(proc); }

    // ДОБАВИТЬ МЕТОДЫ ДЛЯ КОНСТАНТ
    public Set<String> getConstants() { return constants; }
    public void addConstant(String constant) {
        if (constant != null && !constant.isEmpty()) {
            this.constants.add(constant);
            System.out.println("[DEBUG] SqlInfo.addConstant: " + constant);
        }
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", sourceType, componentName,
                cleanSql != null ? cleanSql.substring(0, Math.min(100, cleanSql.length())) : "");
    }
}