package ru.miacomsoft.model;

import java.util.*;

/**
 * Информация о форме после применения всех переопределений
 * Содержит:
 * - Путь к базовой форме
 * - Список переопределений из UserForms
 * - Финальное содержимое формы (после применения .dfrm)
 */
public class FormInfo {

    // Относительный путь к форме (например: "HospPlanJournal/hp_add_direction.frm")
    private String formPath;

    // Абсолютный путь к базовой форме
    private String baseFormPath;

    // Информация о переопределениях
    private List<OverrideInfo> overrides;

    // Финальное содержимое формы (после применения всех переопределений)
    private String finalContent;

    // Флаг, была ли форма полностью заменена (.frm в UserForms)
    private boolean fullyReplaced;

    // Путь к файлу-заменителю (если есть полное переопределение)
    private String replacementPath;

    // SQL запросы, найденные в форме
    private List<SqlInfo> sqlQueries;

    // Таблицы и вьюхи, используемые в SQL
    private Set<String> tablesViews;

    // Пакеты и функции, используемые в SQL
    private Set<String> packagesFunctions;

    private Set<String> userProcedures;

    private Set<String> systemOptions;
    private Set<String> subForms;
    private Set<String> jsForms;
    private Set<String> unknownObjects;  // Объекты для разбора аналитиком
    private Set<String> constants;  // КОНСТАНТЫ из D_PKG_CONSTANTS.SEARCH_*


    public FormInfo(String formPath) {
        this.formPath = formPath;
        this.overrides = new ArrayList<>();
        this.sqlQueries = new ArrayList<>();
        this.tablesViews = new LinkedHashSet<>();
        this.packagesFunctions = new LinkedHashSet<>();
        this.userProcedures = new LinkedHashSet<>();
        this.systemOptions = new LinkedHashSet<>();
        this.subForms = new LinkedHashSet<>();
        this.jsForms = new LinkedHashSet<>();
        this.unknownObjects = new LinkedHashSet<>();
        this.constants = new LinkedHashSet<>();  // ДОБАВИТЬ
        this.fullyReplaced = false;
    }

    // Getters и Setters
    public String getFormPath() { return formPath; }
    public void setFormPath(String formPath) { this.formPath = formPath; }

    public String getBaseFormPath() { return baseFormPath; }
    public void setBaseFormPath(String baseFormPath) { this.baseFormPath = baseFormPath; }

    public List<OverrideInfo> getOverrides() { return overrides; }
    public void setOverrides(List<OverrideInfo> overrides) { this.overrides = overrides; }
    public void addOverride(OverrideInfo override) { this.overrides.add(override); }

    public String getFinalContent() { return finalContent; }
    public void setFinalContent(String finalContent) { this.finalContent = finalContent; }

    public boolean isFullyReplaced() { return fullyReplaced; }
    public void setFullyReplaced(boolean fullyReplaced) { this.fullyReplaced = fullyReplaced; }

    public String getReplacementPath() { return replacementPath; }
    public void setReplacementPath(String replacementPath) { this.replacementPath = replacementPath; }

    public List<SqlInfo> getSqlQueries() { return sqlQueries; }
    public void setSqlQueries(List<SqlInfo> sqlQueries) { this.sqlQueries = sqlQueries; }
    public void addSqlQuery(SqlInfo sql) { this.sqlQueries.add(sql); }

    public Set<String> getTablesViews() { return tablesViews; }
    public void setTablesViews(Set<String> tablesViews) { this.tablesViews = tablesViews; }
    public void addTableView(String tv) { this.tablesViews.add(tv); }

    public Set<String> getPackagesFunctions() { return packagesFunctions; }
    public void setPackagesFunctions(Set<String> packagesFunctions) { this.packagesFunctions = packagesFunctions; }
    public void addPackageFunction(String pf) { this.packagesFunctions.add(pf); }

    public int getTotalSqlQueries() { return sqlQueries.size(); }

    public Set<String> getSubForms() { return subForms; }
    public void setSubForms(Set<String> subForms) { this.subForms = subForms; }
    public void addSubForm(String subForm) { this.subForms.add(subForm); }

    public Set<String> getJsForms() { return jsForms; }
    public void setJsForms(Set<String> jsForms) { this.jsForms = jsForms; }
    public void addJsForm(String jsForm) { this.jsForms.add(jsForm); }

    public Set<String> getUnknownObjects() { return unknownObjects; }
    public void setUnknownObjects(Set<String> unknownObjects) { this.unknownObjects = unknownObjects; }
    public void addUnknownObject(String obj) { this.unknownObjects.add(obj); }

    public Set<String> getUserProcedures() { return userProcedures; }
    public void addUserProcedure(String proc) { this.userProcedures.add(proc); }

    public Set<String> getSystemOptions() { return systemOptions; }
    public void addSystemOption(String option) { this.systemOptions.add(option); }

    // ДОБАВИТЬ МЕТОДЫ ДЛЯ КОНСТАНТ
    public Set<String> getConstants() { return constants; }
    public void addConstant(String constant) { this.constants.add(constant); }

    @Override
    public String toString() {
        return "FormInfo{" +
                "formPath='" + formPath + '\'' +
                ", fullyReplaced=" + fullyReplaced +
                ", sqlCount=" + sqlQueries.size() +
                '}';
    }

    /**
     * Информация о переопределении формы
     */
    public static class OverrideInfo {
        private String regionName;      // Имя региона (UserFormsElab, UserFormsNso и т.д.)
        private String overridePath;    // Абсолютный путь к файлу переопределения
        private OverrideType type;      // Тип переопределения
        private String baseTarget;      // Для .dfrm - target атрибут
        private String position;        // Для .dfrm - pos (after, before, replace, delete)

        public enum OverrideType {
            FULL_OVERRIDE(".frm - полная замена формы"),
            PARTIAL_OVERRIDE(".dfrm - частичное переопределение"),
            DOT_D_OVERRIDE(".d/*.dfrm - переопределение из каталога .d");

            private String description;
            OverrideType(String description) { this.description = description; }
            public String getDescription() { return description; }
        }

        public OverrideInfo(String regionName, String overridePath, OverrideType type) {
            this.regionName = regionName;
            this.overridePath = overridePath;
            this.type = type;
        }

        public OverrideInfo(String regionName, String overridePath, OverrideType type,
                            String baseTarget, String position) {
            this(regionName, overridePath, type);
            this.baseTarget = baseTarget;
            this.position = position;
        }

        // Getters
        public String getRegionName() { return regionName; }
        public String getOverridePath() { return overridePath; }
        public OverrideType getType() { return type; }
        public String getBaseTarget() { return baseTarget; }
        public String getPosition() { return position; }

        @Override
        public String toString() {
            return String.format("[%s] %s -> %s", regionName, type.getDescription(),
                    type == OverrideType.PARTIAL_OVERRIDE ? baseTarget : overridePath);
        }
    }
}