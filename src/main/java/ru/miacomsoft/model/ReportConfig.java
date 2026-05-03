// ReportConfig.java
package ru.miacomsoft.model;

/**
 * Конфигурация для генерации отчета forms_report_all_without_sql.txt
 */
public class ReportConfig {
    // Флаги для включения/выключения блоков отчета
    private static boolean includeSqlContent = false;           // 1. Вывод SQL запросов
    private static boolean includeJsForms = true;               // 2. Вывод "Список вызываемых форм в JS:"
    private static boolean includeTablesViews = true;           // 3. Вывод "ИСПОЛЬЗУЕМЫЕ ТАБЛИЦЫ И ВЬЮХИ:"
    private static boolean includeViewTables = true;            // 4. Вывод "ТАБЛИЦЫ, ИСПОЛЬЗУЕМЫЕ ЧЕРЕЗ ВЬЮХИ:"
    private static boolean includeJsUnitCompositions = true;    // 5. Вывод "КОМПОЗИЦИИ ИЗ JS (UniversalComposition):"
    private static boolean includeViewDetails = false;          // 6. Вывод содержимого каждой вьюхи

    // Getters and Setters
    public static boolean isIncludeSqlContent() { return includeSqlContent; }
    public static void setIncludeSqlContent(boolean value) { includeSqlContent = value; }

    public static boolean isIncludeJsForms() { return includeJsForms; }
    public static void setIncludeJsForms(boolean value) { includeJsForms = value; }

    public static boolean isIncludeTablesViews() { return includeTablesViews; }
    public static void setIncludeTablesViews(boolean value) { includeTablesViews = value; }

    public static boolean isIncludeViewTables() { return includeViewTables; }
    public static void setIncludeViewTables(boolean value) { includeViewTables = value; }

    public static boolean isIncludeJsUnitCompositions() { return includeJsUnitCompositions; }
    public static void setIncludeJsUnitCompositions(boolean value) { includeJsUnitCompositions = value; }

    public static boolean isIncludeViewDetails() { return includeViewDetails; }
    public static void setIncludeViewDetails(boolean value) { includeViewDetails = value; }

    /**
     * Установить предустановленную конфигурацию
     */
    public static void setPreset(Preset preset) {
        switch (preset) {
            case MINIMAL:
                includeSqlContent = false;
                includeJsForms = false;
                includeTablesViews = false;
                includeViewTables = false;
                includeJsUnitCompositions = false;
                includeViewDetails = false;
                break;
            case STANDARD:
                includeSqlContent = false;
                includeJsForms = true;
                includeTablesViews = true;
                includeViewTables = true;
                includeJsUnitCompositions = true;
                includeViewDetails = false;
                break;
            case FULL:
                includeSqlContent = true;
                includeJsForms = true;
                includeTablesViews = true;
                includeViewTables = true;
                includeJsUnitCompositions = true;
                includeViewDetails = true;
                break;
            case DEBUG:
                includeSqlContent = true;
                includeJsForms = true;
                includeTablesViews = true;
                includeViewTables = true;
                includeJsUnitCompositions = true;
                includeViewDetails = true;
                break;
        }
    }

    public enum Preset {
        MINIMAL,    // Минимальный отчет (только структура)
        STANDARD,   // Стандартный отчет (без SQL, но с таблицами)
        FULL,       // Полный отчет (со всем содержимым)
        DEBUG       // Отладка (полный отчет с деталями вьюх)
    }
}