package ru.miacomsoft;

import ru.miacomsoft.model.AnalysisConfig;
import ru.miacomsoft.model.ReportConfig;
import ru.miacomsoft.model.SettingsModel;
import ru.miacomsoft.service.TmisFormAnalyzerService;
import ru.miacomsoft.service.ViewDependencyAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) {
        // Загружаем настройки
        SettingsModel settings = new SettingsModel();

        // ========== НАСТРОЙКА ПУТИ К ПРОЕКТУ ==========
        // Приоритет: аргумент командной строки > settings.properties > значение по умолчанию
        String projectPath = null;

        // 1. Проверяем аргументы командной строки
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--project-path") || args[i].equals("-p")) {
                if (i + 1 < args.length) {
                    projectPath = args[i + 1];
                    System.out.println("Путь к проекту указан в аргументе: " + projectPath);
                }
            }
        }

        // 2. Если не указан в аргументах, берем из настроек
        if (projectPath == null || projectPath.isEmpty()) {
            projectPath = settings.getProjectPath();
            System.out.println("Путь к проекту из настроек: " + projectPath);
        }

        // 3. Проверяем существование пути
        Path projectPathObj = Paths.get(projectPath);
        if (!Files.exists(projectPathObj)) {
            System.err.println("ОШИБКА: Путь к проекту не существует: " + projectPath);
            System.err.println("Использование: java -jar TMisFormAnalyzer.jar --project-path /путь/к/mis");
            System.err.println("   или: java -jar TMisFormAnalyzer.jar -p /путь/к/mis");
            System.exit(1);
        }

        // Устанавливаем путь в настройках
        settings.setProjectPath(projectPath);

        // ========== НАСТРОЙКА КОНФИГУРАЦИИ ==========
        configureAnalysis(settings);

        // Запускаем анализ
        try {
            TmisFormAnalyzerService analyzerService = new TmisFormAnalyzerService(settings);
            analyzerService.runFullAnalysis();
        } catch (IOException e) {
            System.err.println("Ошибка при выполнении анализа: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Конфигурация анализа
     */
    private static void configureAnalysis(SettingsModel settings) {
        // Настройка анализа таблиц через вьюхи
        AnalysisConfig.setIncludeViewTableDependencies(true);

        // Настройка файла списка форм
        AnalysisConfig.setFormsListFile("forms_list.txt");
        AnalysisConfig.setScanAllFormsIfListEmpty(true);

        // ========== НАСТРОЙКА КОНФИГУРАЦИИ ОТЧЕТА ==========
        // Раскомментируйте нужный вариант:

        // Вариант 1: Стандартный отчет (без SQL, но с таблицами и композициями)
        // ReportConfig.setPreset(ReportConfig.Preset.STANDARD);

        // Вариант 2: Минимальный отчет (только базовая структура)
        // ReportConfig.setPreset(ReportConfig.Preset.MINIMAL);

        // Вариант 3: Полный отчет (всё)
        // ReportConfig.setPreset(ReportConfig.Preset.FULL);

        // Вариант 4: Отладка (с деталями вьюх)
        // ReportConfig.setPreset(ReportConfig.Preset.DEBUG);

        // ИЛИ настроить вручную:
        ReportConfig.setIncludeSqlContent(false);            // 1. Показать SQL
        ReportConfig.setIncludeJsForms(true);                // 2. Показать JS формы
        ReportConfig.setIncludeTablesViews(true);            // 3. Показать таблицы/вьюхи
        ReportConfig.setIncludeViewTables(true);             // 4. Показать таблицы через вьюхи
        ReportConfig.setIncludeJsUnitCompositions(true);     // 5. Показать композиции JS
        ReportConfig.setIncludeViewDetails(false);           // 6. Показать детали вьюх

        // Настройка подключения к базам данных (можно тоже переопределить через аргументы)
        ViewDependencyAnalyzer.setOracleConfig(
                settings.getOracleUrl(),
                settings.getOracleUser(),
                settings.getOraclePassword()
        );
        ViewDependencyAnalyzer.setPostgresConfig(
                settings.getPostgresUrl(),
                settings.getPostgresUser(),
                settings.getPostgresPassword()
        );
    }
}