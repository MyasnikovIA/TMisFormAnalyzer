package ru.miacomsoft;

import ru.miacomsoft.model.AnalysisConfig;
import ru.miacomsoft.model.SettingsModel;
import ru.miacomsoft.service.FileScannerService;
import ru.miacomsoft.service.ReportGeneratorService;
import ru.miacomsoft.service.TmisFormAnalyzerService;
import ru.miacomsoft.service.ViewDependencyAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Main {

    private static final String FORMS_LIST_FILE = "forms_list.txt";

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("=== АНАЛИЗАТОР ФОРМ T-MIS (M2/D3) ===");
        System.out.println("=".repeat(80));

        // Загружаем настройки
        SettingsModel settings = new SettingsModel();
        System.out.println("Корневой каталог проекта: " + settings.getProjectPath());
        System.out.println();

        // ВКЛЮЧАЕМ анализ таблиц через вьюхи
        AnalysisConfig.setIncludeViewTableDependencies(true);
        System.out.println("Анализ таблиц через вьюхи: ВКЛЮЧЕН");
        System.out.println();

        // Устанавливаем настройки БД для ViewDependencyAnalyzer
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

        try {
            FileScannerService scannerService = new FileScannerService(settings.getProjectPath());
            TmisFormAnalyzerService analyzerService = new TmisFormAnalyzerService(settings.getProjectPath());
            ReportGeneratorService reportService = new ReportGeneratorService(settings);

            Set<String> formsToAnalyze = getFormsToAnalyze(scannerService);

            System.out.println("Найдено форм для анализа: " + formsToAnalyze.size());
            System.out.println();

            int processed = 0;
            for (String formPath : formsToAnalyze) {
                processed++;
                System.out.print("Анализ [" + processed + "/" + formsToAnalyze.size() + "]: " + formPath + " ... ");

                var result = analyzerService.analyzeForm(formPath);
                if (result != null) {
                    reportService.addAnalysisResult(result);
                    System.out.println("OK (SQL: " + result.getTotalSqlQueries() + ")");
                } else {
                    System.out.println("ПРОПУЩЕН (форма не найдена)");
                }
            }

            System.out.println();
            System.out.println("=".repeat(80));
            System.out.println("=== ГЕНЕРАЦИЯ ОТЧЕТОВ ===");

            Path outputDir = Paths.get(settings.getOutputDir());
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            reportService.generateAllReports();

            System.out.println();
            System.out.println("=== АНАЛИЗ ЗАВЕРШЕН ===");
            System.out.println("Обработано форм: " + processed);
            System.out.println("Всего SQL запросов: " + reportService.getTotalSqlQueries());
            System.out.println("Уникальных таблиц/вьюх: " + reportService.getUniqueTablesViews().size());
            System.out.println("Уникальных пакетов/функций: " + reportService.getUniquePackagesFunctions().size());
            System.out.println();
            System.out.println("Результаты сохранены в директории: " + settings.getOutputDir() + "/");

        } catch (Exception e) {
            System.err.println("Ошибка при выполнении анализа: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Set<String> getFormsToAnalyze(FileScannerService scannerService) {
        Set<String> forms = new LinkedHashSet<>();
        Path listFile = Paths.get(FORMS_LIST_FILE);

        if (Files.exists(listFile)) {
            try {
                List<String> lines = Files.readAllLines(listFile);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        String formPath = extractBaseFormPath(trimmed);
                        if (formPath != null) {
                            forms.add(formPath);
                        }
                    }
                }
                System.out.println("Загружено форм из списка: " + forms.size());
            } catch (IOException e) {
                System.err.println("Ошибка чтения файла списка: " + e.getMessage());
            }
        } else {
            System.out.println("Сканируем каталог Forms...");
            Set<String> allForms = scannerService.findAllBaseForms();
            forms.addAll(allForms);
            System.out.println("Найдено базовых форм: " + allForms.size());
        }

        return forms;
    }

    private static String extractBaseFormPath(String path) {
        if (!path.startsWith("UserForms") && !path.startsWith("/UserForms")) {
            return normalizeFormPath(path);
        }

        String withoutUserForms = path.replaceFirst("^/?UserForms[^/]*/", "");

        if (withoutUserForms.contains(".d/")) {
            String basePath = withoutUserForms.substring(0, withoutUserForms.indexOf(".d/"));
            return basePath + ".frm";
        }

        if (withoutUserForms.endsWith(".dfrm")) {
            String basePath = withoutUserForms.substring(0, withoutUserForms.length() - 5);
            return basePath + ".frm";
        }

        if (withoutUserForms.endsWith(".frm")) {
            return withoutUserForms;
        }

        return normalizeFormPath(withoutUserForms);
    }

    private static String normalizeFormPath(String path) {
        String normalized = path;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("Forms/")) {
            normalized = normalized.substring(6);
        }
        if (normalized.startsWith("forms/")) {
            normalized = normalized.substring(6);
        }
        if (!normalized.endsWith(".frm") && !normalized.endsWith(".dfrm")) {
            normalized = normalized + ".frm";
        }
        return normalized;
    }
}