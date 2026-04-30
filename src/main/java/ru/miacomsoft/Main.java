package ru.miacomsoft;

import ru.miacomsoft.service.FileScannerService;
import ru.miacomsoft.service.ReportGeneratorService;
import ru.miacomsoft.service.TmisFormAnalyzerService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Главный класс приложения для анализа форм T-MIS
 */
public class Main {

    // Корневой каталог проекта T-MIS
    private static final String PROJECT_ROOT = "/var/www/t-mis/mis";
    //private static final String PROJECT_ROOT = "C:\\tMISS\\mis";

    // Файл со списком форм для анализа (опционально)
    private static final String FORMS_LIST_FILE = "forms_list.txt";

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("=== АНАЛИЗАТОР ФОРМ T-MIS (M2/D3) ===");
        System.out.println("=".repeat(80));
        System.out.println("Корневой каталог проекта: " + PROJECT_ROOT);
        System.out.println();

        try {
            // Создаем сервисы
            FileScannerService scannerService = new FileScannerService(PROJECT_ROOT);
            TmisFormAnalyzerService analyzerService = new TmisFormAnalyzerService(PROJECT_ROOT);
            ReportGeneratorService reportService = new ReportGeneratorService();

            // Получаем список форм для анализа
            Set<String> formsToAnalyze = getFormsToAnalyze(scannerService);

            System.out.println("Найдено форм для анализа: " + formsToAnalyze.size());
            System.out.println();

            // Анализируем каждую форму
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

            // Создаем директорию для отчетов
            Path outputDir = Paths.get("SQL_info");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            // Генерируем отчеты
            reportService.generateAllReports();


            System.out.println();
            System.out.println("=== АНАЛИЗ ЗАВЕРШЕН ===");
            System.out.println("Обработано форм: " + processed);
            System.out.println("Всего SQL запросов: " + reportService.getTotalSqlQueries());
            System.out.println("Уникальных таблиц/вьюх: " + reportService.getUniqueTablesViews().size());
            System.out.println("Уникальных пакетов/функций: " + reportService.getUniquePackagesFunctions().size());
            System.out.println();
            System.out.println("Результаты сохранены в директории: SQL_info/");

        } catch (Exception e) {
            System.err.println("Ошибка при выполнении анализа: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Получение списка форм для анализа
     * Поддерживает форматы:
     * - "Forms/ARMMainDoc/arm_director.frm"
     * - "ARMMainDoc/arm_director.frm"
     * - "UserFormsXXX/path/to/form.d/view.dfrm" -> преобразуется в "path/to/form.frm"
     *
     * Если файл forms_list.txt пустой или не существует - сканируем весь каталог Forms
     */
    private static Set<String> getFormsToAnalyze(FileScannerService scannerService) {
        Set<String> forms = new LinkedHashSet<>();

        // Проверяем наличие и содержимое файла со списком форм
        Path listFile = Paths.get(FORMS_LIST_FILE);
        boolean hasValidContent = false;

        if (Files.exists(listFile)) {
            try {
                List<String> lines = Files.readAllLines(listFile);

                // Проверяем, есть ли непустые строки (не комментарии)
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        hasValidContent = true;
                        break;
                    }
                }

                if (hasValidContent) {
                    System.out.println("Используем файл со списком форм: " + FORMS_LIST_FILE);

                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            // Преобразуем путь к нормализованному виду
                            String formPath = extractBaseFormPath(trimmed);
                            if (formPath != null) {
                                forms.add(formPath);
                            }
                        }
                    }
                    System.out.println("Загружено форм из списка: " + forms.size());
                } else {
                    System.out.println("Файл " + FORMS_LIST_FILE + " пуст или содержит только комментарии.");
                }
            } catch (IOException e) {
                System.err.println("Ошибка чтения файла списка: " + e.getMessage());
            }
        }

        // Если файл не существует или пуст - сканируем весь каталог Forms
        if (!hasValidContent) {
            System.out.println("Сканируем каталог Forms...");
            Set<String> allForms = scannerService.findAllBaseForms();
            forms.addAll(allForms);
            System.out.println("Найдено базовых форм: " + allForms.size());
        }

        return forms;
    }

    /**
     * Извлечь путь к базовой форме из различных форматов
     * Примеры:
     * - "ARMMainDoc/arm_director.frm" -> "ARMMainDoc/arm_director.frm"
     * - "UserFormsKaliningrad/ArmPatientsInDep/pat_in_dep_head_dep.d/view.dfrm"
     *   -> "ArmPatientsInDep/pat_in_dep_head_dep.frm"
     * - "UserFormsMoscowNIIBlohina/Sklad/incomingdocs/incoming_docs.d/view.dfrm"
     *   -> "Sklad/incomingdocs/incoming_docs.frm"
     */
    private static String extractBaseFormPath(String path) {
        // Если это уже путь к базовой форме (начинается не с UserForms)
        if (!path.startsWith("UserForms") && !path.startsWith("/UserForms")) {
            return normalizeFormPath(path);
        }

        // Убираем префикс UserFormsXXX/
        String withoutUserForms = path.replaceFirst("^/?UserForms[^/]*/", "");

        // Если это .d каталог, извлекаем базовую форму
        if (withoutUserForms.contains(".d/")) {
            // Пример: "ArmPatientsInDep/pat_in_dep_head_dep.d/view.dfrm"
            // -> "ArmPatientsInDep/pat_in_dep_head_dep.frm"
            String basePath = withoutUserForms.substring(0, withoutUserForms.indexOf(".d/"));
            return basePath + ".frm";
        }

        // Если это прямой .dfrm файл (не в .d каталоге)
        if (withoutUserForms.endsWith(".dfrm")) {
            String basePath = withoutUserForms.substring(0, withoutUserForms.length() - 5);
            return basePath + ".frm";
        }

        // Если это .frm файл в UserForms (полное переопределение)
        if (withoutUserForms.endsWith(".frm")) {
            return withoutUserForms;
        }

        return normalizeFormPath(withoutUserForms);
    }
    /**
     * Нормализация пути формы
     * Примеры:
     * - "/Forms/ARMMainDoc/arm_director.frm" -> "ARMMainDoc/arm_director.frm"
     * - "Forms/ARMMainDoc/arm_director.frm" -> "ARMMainDoc/arm_director.frm"
     * - "ARMMainDoc/arm_director.frm" -> "ARMMainDoc/arm_director.frm"
     */
    private static String normalizeFormPath(String path) {
        String normalized = path;

        // Убираем ведущий слеш если есть
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        // Убираем префикс Forms/ если есть
        if (normalized.startsWith("Forms/")) {
            normalized = normalized.substring(6);
        }

        // Убираем префикс forms/ (нижний регистр) если есть
        if (normalized.startsWith("forms/")) {
            normalized = normalized.substring(6);
        }

        // Добавляем .frm если нет расширения
        if (!normalized.endsWith(".frm") && !normalized.endsWith(".dfrm")) {
            normalized = normalized + ".frm";
        }

        return normalized;
    }
}