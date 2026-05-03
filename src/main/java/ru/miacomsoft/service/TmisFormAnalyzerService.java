package ru.miacomsoft.service;

import ru.miacomsoft.model.AnalysisConfig;
import ru.miacomsoft.model.FormInfo;
import ru.miacomsoft.model.ReportConfig;
import ru.miacomsoft.model.SettingsModel;
import ru.miacomsoft.model.SqlInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Основной сервис анализа форм T-MIS
 * Координирует работу всех сервисов для анализа одной формы
 */
public class TmisFormAnalyzerService {

    private final String projectRoot;
    private final FileScannerService scannerService;
    private final UserFormsResolver userFormsResolver;
    private final SqlExtractorService sqlExtractor;
    private final SettingsModel settings;
    private ReportGeneratorService reportService;

    // Callback for progress updates
    private ProgressCallback progressCallback;
    private java.util.function.BooleanSupplier stopCondition = () -> false;

    // Separate interface for progress callback (not inner to avoid confusion)
    public interface ProgressCallback {
        void onProgress(int processed, int total, String currentForm);
    }

    public TmisFormAnalyzerService(String projectRoot) {
        this.projectRoot = projectRoot;
        this.scannerService = new FileScannerService(projectRoot);
        this.userFormsResolver = new UserFormsResolver(scannerService);
        this.sqlExtractor = new SqlExtractorService();
        this.settings = new SettingsModel();
    }

    public TmisFormAnalyzerService(SettingsModel settings) {
        this.projectRoot = settings.getProjectPath();
        this.scannerService = new FileScannerService(projectRoot);
        this.userFormsResolver = new UserFormsResolver(scannerService);
        this.sqlExtractor = new SqlExtractorService();
        this.settings = settings;
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    /**
     * Запуск полного анализа всех форм
     */
    public void runFullAnalysis() throws IOException {
        System.out.println("=".repeat(80));
        System.out.println("=== АНАЛИЗАТОР ФОРМ T-MIS (M2/D3) ===");
        System.out.println("=".repeat(80));

        System.out.println("Корневой каталог проекта: " + projectRoot);
        System.out.println();

        // Выводим конфигурацию отчета
        printReportConfig();
        System.out.println();

        System.out.println("Анализ таблиц через вьюхи: " +
                (AnalysisConfig.isIncludeViewTableDependencies() ? "ВКЛЮЧЕН" : "ВЫКЛЮЧЕН"));
        System.out.println();

        // Получаем список форм для анализа
        Set<String> formsToAnalyze = getFormsToAnalyze();

        System.out.println("Найдено форм для анализа: " + formsToAnalyze.size());
        System.out.println();

        // Создаем сервис генерации отчетов
        reportService = new ReportGeneratorService(settings);

        // Создаем выходную директорию
        Path outputDir = Paths.get(settings.getOutputDir());
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // ========== НОВЫЙ КОД: ОЧИЩАЕМ СТАРЫЙ ОТЧЕТ ==========
        reportService.clearMainReport();
        reportService.createMainReportHeader();
        // =====================================================

        // Анализируем каждую форму
        int processed = 0;
        int errors = 0;
        long startTime = System.currentTimeMillis();
        int totalForms = formsToAnalyze.size();

        for (String formPath : formsToAnalyze) {
            // Проверка на остановку
            if (isStopRequested()) {
                System.out.println("Анализ остановлен пользователем");
                break;
            }

            processed++;
            long formStartTime = System.currentTimeMillis();

            if (progressCallback != null) {
                progressCallback.onProgress(processed, totalForms, formPath);
            }

            System.out.print("Анализ [" + processed + "/" + formsToAnalyze.size() + "]: " + formPath + " ... ");

            try {
                FormInfo result = analyzeForm(formPath);
                if (result != null) {
                    reportService.addAnalysisResult(result);
                    reportService.appendFormToMainReport(result);

                    long formElapsed = System.currentTimeMillis() - formStartTime;
                    System.out.println("OK (SQL: " + result.getTotalSqlQueries() + ", " + formElapsed + "ms)");
                } else {
                    System.out.println("ПРОПУЩЕН (форма не найдена)");
                    errors++;
                }
            } catch (Exception e) {
                System.err.println("ОШИБКА: " + e.getMessage());
                errors++;
            }
        }

        long totalElapsed = System.currentTimeMillis() - startTime;
        long totalMinutes = totalElapsed / 60000;
        long totalSeconds = (totalElapsed % 60000) / 1000;

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("=== ЗАВЕРШЕНИЕ АНАЛИЗА ===");

        // Добавляем итоговую статистику в отчет
        reportService.finishMainReport();

        // Выводим итоговую статистику
        printFinalStatistics(processed, errors, totalMinutes, totalSeconds);
    }

    /**
     * Вывод конфигурации отчета
     */
    private void printReportConfig() {
        System.out.println("КОНФИГУРАЦИЯ ОТЧЕТА:");
        System.out.println("  SQL содержимое: " + (ReportConfig.isIncludeSqlContent() ? "ВКЛ" : "ВЫКЛ"));
        System.out.println("  JS формы: " + (ReportConfig.isIncludeJsForms() ? "ВКЛ" : "ВЫКЛ"));
        System.out.println("  Таблицы/вьюхи: " + (ReportConfig.isIncludeTablesViews() ? "ВКЛ" : "ВЫКЛ"));
        System.out.println("  Таблицы через вьюхи: " + (ReportConfig.isIncludeViewTables() ? "ВКЛ" : "ВЫКЛ"));
        System.out.println("  Композиции JS: " + (ReportConfig.isIncludeJsUnitCompositions() ? "ВКЛ" : "ВЫКЛ"));
        System.out.println("  Детали вьюх: " + (ReportConfig.isIncludeViewDetails() ? "ВКЛ" : "ВЫКЛ"));
    }

    /**
     * Вывод итоговой статистики
     */
    private void printFinalStatistics(int processed, int errors, long totalMinutes, long totalSeconds) {
        System.out.println();
        System.out.println("=== ИТОГОВАЯ СТАТИСТИКА ===");
        System.out.println("Обработано форм: " + processed);
        System.out.println("Ошибок: " + errors);
        System.out.println("Всего SQL запросов: " + reportService.getTotalSqlQueries());
        System.out.println("Уникальных таблиц/вьюх: " + reportService.getUniqueTablesViews().size());
        System.out.println("Уникальных пакетов/функций: " + reportService.getUniquePackagesFunctions().size());
        System.out.println("Общее время выполнения: " + totalMinutes + " мин " + totalSeconds + " сек");
        System.out.println();
        System.out.println("Результаты сохранены в директории: " + settings.getOutputDir() + "/");
        System.out.println("Основной отчет: " + settings.getOutputDir() + "/forms_report.txt");
    }

    /**
     * Получение списка форм для анализа
     */
    private Set<String> getFormsToAnalyze() {
        Set<String> forms = new LinkedHashSet<>();
        Path listFile = Paths.get(AnalysisConfig.getFormsListFile());

        if (Files.exists(listFile)) {
            try {
                List<String> lines = Files.readAllLines(listFile);
                boolean hasContent = false;

                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        hasContent = true;
                        String formPath = extractBaseFormPath(trimmed);
                        if (formPath != null) {
                            forms.add(formPath);
                        }
                    }
                }

                if (hasContent) {
                    System.out.println("Загружено форм из списка: " + forms.size());
                    return forms;
                } else if (AnalysisConfig.isScanAllFormsIfListEmpty()) {
                    System.out.println("Файл " + AnalysisConfig.getFormsListFile() + " пуст или содержит только комментарии.");
                    System.out.println("Выполняется сканирование всех форм в проекте...");
                }
            } catch (IOException e) {
                System.err.println("Ошибка чтения файла списка: " + e.getMessage());
                if (AnalysisConfig.isScanAllFormsIfListEmpty()) {
                    System.out.println("Выполняется сканирование всех форм в проекте...");
                }
            }
        } else if (AnalysisConfig.isScanAllFormsIfListEmpty()) {
            System.out.println("Файл " + AnalysisConfig.getFormsListFile() + " не найден.");
            System.out.println("Выполняется сканирование всех форм в проекте...");
        }

        // Сканируем все формы в проекте
        return scanAllForms();
    }

    /**
     * Сканирование всех форм в проекте (.frm и .dfrm)
     */
    private Set<String> scanAllForms() {
        Set<String> allForms = new LinkedHashSet<>();
        Path rootPath = Paths.get(projectRoot);

        try {
            // Сканируем каталог Forms (базовые формы)
            Path formsPath = rootPath.resolve("Forms");
            if (Files.exists(formsPath)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(formsPath)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".frm"))
                            .forEach(p -> {
                                String relativePath = formsPath.relativize(p).toString().replace("\\", "/");
                                allForms.add(relativePath);
                            });
                }
                System.out.println("  Найдено базовых форм (.frm) в Forms/: " +
                        allForms.stream().filter(f -> f.endsWith(".frm")).count());
            }

            // Сканируем все каталоги UserForms (переопределения)
            try (java.util.stream.Stream<Path> list = Files.list(rootPath)) {
                list.filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("UserForms"))
                        .forEach(userFormsDir -> {
                            try (java.util.stream.Stream<Path> walk = Files.walk(userFormsDir)) {
                                walk.filter(Files::isRegularFile)
                                        .filter(p -> p.toString().endsWith(".frm") || p.toString().endsWith(".dfrm"))
                                        .forEach(p -> {
                                            String relativePath = userFormsDir.relativize(p).toString().replace("\\", "/");
                                            String fullPath = userFormsDir.getFileName().toString() + "/" + relativePath;
                                            String baseFormPath = extractBaseFormPath(fullPath);
                                            if (baseFormPath != null && !allForms.contains(baseFormPath)) {
                                                allForms.add(baseFormPath);
                                            }
                                        });
                            } catch (IOException e) {
                                System.err.println("Ошибка сканирования " + userFormsDir + ": " + e.getMessage());
                            }
                        });
            }

            System.out.println("Всего найдено уникальных форм: " + allForms.size());

        } catch (IOException e) {
            System.err.println("Ошибка при сканировании проекта: " + e.getMessage());
        }

        return allForms;
    }

    /**
     * Извлечение базового пути формы из пути переопределения
     */
    private String extractBaseFormPath(String path) {
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

    /**
     * Нормализация пути формы
     */
    private String normalizeFormPath(String path) {
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

    /**
     * Проанализировать форму по заданному пути
     */
    public FormInfo analyzeForm(String formPath) {
        String normalizedPath = normalizeFormPath(formPath);

        if (!scannerService.baseFormExists(normalizedPath)) {
            System.err.println("Базовая форма не найдена: " + normalizedPath);
            return null;
        }

        // Информация о переопределениях
        FormInfo formInfo = userFormsResolver.resolveOverrides(normalizedPath);

        // Анализируем БАЗОВУЮ форму
        Path baseFormPathObj = scannerService.getBaseFormPath(normalizedPath);
        formInfo.setBaseFormPath(baseFormPathObj.toString());

        String baseContent = scannerService.readFileContent(baseFormPathObj);
        if (baseContent == null) {
            return null;
        }

        // Извлекаем объекты из базовой формы
        extractSubForms(baseContent, formInfo);
        extractJsForms(baseContent, formInfo);
        extractD3ApiShowForms(baseContent, formInfo);
        extractUnitCompositions(baseContent, formInfo);
        extractJsUnitCompositions(baseContent, formInfo);

        List<SqlInfo> sqlQueries = sqlExtractor.extractAllSqlQueries(
                baseContent, baseFormPathObj.toString(), null
        );

        for (SqlInfo sql : sqlQueries) {
            formInfo.addSqlQuery(sql);
            for (String tv : sql.getTablesViews()) formInfo.addTableView(tv);
            for (String pf : sql.getPackagesFunctions()) formInfo.addPackageFunction(pf);
            for (String proc : sql.getUserProcedures()) formInfo.addUserProcedure(proc);
            for (String opt : sql.getSystemOptions()) formInfo.addSystemOption(opt);
            for (String unknown : sql.getUnknownObjects()) formInfo.addUnknownObject(unknown);
            for (String constant : sql.getConstants()) {
                formInfo.addConstant(constant);
            }
        }

        // Если есть ПОЛНОЕ переопределение, можно также проанализировать и его
        if (formInfo.isFullyReplaced() && formInfo.getReplacementPath() != null) {
            String userContent = scannerService.readFileContent(Path.of(formInfo.getReplacementPath()));
            if (userContent != null) {
                // Анализируем пользовательскую форму (опционально)
            }
        }

        // Прямой поиск констант в содержимом формы
        Pattern directConstPattern = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_(?:STR|NUM|DATE)\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher directMatcher = directConstPattern.matcher(baseContent);
        Set<String> directConstants = new LinkedHashSet<>();
        while (directMatcher.find()) {
            String constant = directMatcher.group(1);
            if (constant != null && !constant.isEmpty()) {
                directConstants.add(constant);
            }
        }

        for (String constant : directConstants) {
            formInfo.addConstant(constant);
        }

        return formInfo;
    }

    /**
     * Извлечь SubForm из содержимого формы
     */
    private void extractSubForms(String content, FormInfo formInfo) {
        if (content == null || content.isEmpty()) return;

        Set<String> foundPaths = new LinkedHashSet<>();

        // Паттерн для D3 синтаксиса: <cmpSubForm path="...">
        Pattern d3SubFormPattern = Pattern.compile(
                "<cmpSubForm\\s+path\\s*=\\s*[\"']([^\"']+)[\"']",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для M2 синтаксиса: <component cmptype="SubForm" path="...">
        Pattern m2SubFormPattern = Pattern.compile(
                "<component\\s+cmptype\\s*=\\s*[\"']SubForm[\"'][^>]*path\\s*=\\s*[\"']([^\"']+)[\"']",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher d3Matcher = d3SubFormPattern.matcher(content);
        while (d3Matcher.find()) {
            String path = d3Matcher.group(1).trim();
            if (!path.isEmpty()) {
                path = path.replaceAll("^[\"']|[\"']$", "");
                foundPaths.add(path);
            }
        }

        Matcher m2Matcher = m2SubFormPattern.matcher(content);
        while (m2Matcher.find()) {
            String path = m2Matcher.group(1).trim();
            if (!path.isEmpty()) {
                path = path.replaceAll("^[\"']|[\"']$", "");
                foundPaths.add(path);
            }
        }

        for (String path : foundPaths) {
            formInfo.addSubForm(path);
            System.out.println("  Найден SubForm: " + path);
        }
    }

    /**
     * Извлечь JS формы (openWindow, openD3Form) из содержимого формы
     */
    private void extractJsForms(String content, FormInfo formInfo) {
        if (content == null || content.isEmpty()) return;

        Set<String> foundForms = new LinkedHashSet<>();

        Pattern openD3FormPattern = Pattern.compile(
                "openD3Form\\s*\\(\\s*['\"]([^'\"]+)['\"]",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Pattern openWindowPattern = Pattern.compile(
                "openWindow\\s*\\(\\s*['\"]([^'\"]+)['\"]",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Pattern openD3FormObjectPattern = Pattern.compile(
                "openD3Form\\s*\\(\\s*\\{\\s*name\\s*:\\s*['\"]([^'\"]+)['\"]",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher d3Matcher = openD3FormPattern.matcher(content);
        while (d3Matcher.find()) {
            String formPath = d3Matcher.group(1).trim();
            if (formPath != null && !formPath.isEmpty()) {
                foundForms.add(formPath);
                System.out.println("  Найдена openD3Form: " + formPath);
            }
        }

        Matcher windowMatcher = openWindowPattern.matcher(content);
        while (windowMatcher.find()) {
            String formPath = windowMatcher.group(1).trim();
            if (formPath != null && !formPath.isEmpty()) {
                foundForms.add(formPath);
                System.out.println("  Найдена openWindow: " + formPath);
            }
        }

        Matcher objectMatcher = openD3FormObjectPattern.matcher(content);
        while (objectMatcher.find()) {
            String formPath = objectMatcher.group(1).trim();
            if (formPath != null && !formPath.isEmpty()) {
                foundForms.add(formPath);
                System.out.println("  Найдена openD3Form (объект): " + formPath);
            }
        }

        for (String form : foundForms) {
            formInfo.addJsForm(form);
        }
    }

    /**
     * Извлечь формы из D3Api.showForm вызовов
     */
    private void extractD3ApiShowForms(String content, FormInfo formInfo) {
        if (content == null || content.isEmpty()) return;

        Set<String> foundForms = new LinkedHashSet<>();

        Pattern d3ApiPattern = Pattern.compile(
                "D3Api\\.showForm\\s*\\(\\s*['\"]([^'\"]+)['\"]",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = d3ApiPattern.matcher(content);
        while (matcher.find()) {
            String formPath = matcher.group(1).trim();
            if (formPath != null && !formPath.isEmpty()) {
                foundForms.add(formPath);
                System.out.println("  Найдена D3Api.showForm: " + formPath);
            }
        }

        for (String form : foundForms) {
            formInfo.addSubForm(form);
        }
    }

    /**
     * Извлечь композиции из UnitEdit компонентов
     */
    private void extractUnitCompositions(String content, FormInfo formInfo) {
        if (content == null || content.isEmpty()) return;

        Set<String> compositions = new LinkedHashSet<>();

        Pattern m2UnitEditPattern = Pattern.compile(
                "<component\\s+cmptype\\s*=\\s*[\"']UnitEdit[\"'][^>]*?\\s+unit\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?\\s+composition\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Pattern d3UnitEditPattern = Pattern.compile(
                "<cmpUnitEdit[^>]*?\\s+unit\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?\\s+composition\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher m2Matcher = m2UnitEditPattern.matcher(content);
        while (m2Matcher.find()) {
            String unit = m2Matcher.group(1);
            String composition = m2Matcher.group(2);
            compositions.add(String.format("        unit=\"%s\"  composition=\"%s\"", unit, composition));
        }

        Matcher d3Matcher = d3UnitEditPattern.matcher(content);
        while (d3Matcher.find()) {
            String unit = d3Matcher.group(1);
            String composition = d3Matcher.group(2);
            compositions.add(String.format("        unit=\"%s\"  composition=\"%s\"", unit, composition));
        }

        for (String comp : compositions) {
            formInfo.addUnitComposition(comp);
        }
    }

    /**
     * Извлечь композиции из JS вызовов UniversalComposition
     */
    private void extractJsUnitCompositions(String content, FormInfo formInfo) {
        if (content == null || content.isEmpty()) return;

        Set<String> compositions = new LinkedHashSet<>();

        Pattern openWindowPattern = Pattern.compile(
                "openWindow\\s*\\(\\s*\\{\\s*name\\s*:\\s*['\"]UniversalComposition/UniversalComposition['\"][^}]*\\}",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Pattern openD3FormPattern = Pattern.compile(
                "openD3Form\\s*\\(\\s*\\{\\s*name\\s*:\\s*['\"]UniversalComposition/UniversalComposition['\"][^}]*\\}",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher openWindowMatcher = openWindowPattern.matcher(content);
        while (openWindowMatcher.find()) {
            String objectBlock = openWindowMatcher.group();
            extractUnitAndComposition(objectBlock, compositions);
        }

        Matcher openD3FormMatcher = openD3FormPattern.matcher(content);
        while (openD3FormMatcher.find()) {
            String objectBlock = openD3FormMatcher.group();
            extractUnitAndComposition(objectBlock, compositions);
        }

        for (String comp : compositions) {
            formInfo.addJsUnitComposition(comp);
        }
    }

    /**
     * Извлечь unit и composition из блока объекта
     */
    private void extractUnitAndComposition(String objectBlock, Set<String> compositions) {
        Pattern unitPattern = Pattern.compile(
                "unit\\s*:\\s*['\"]([^'\"]+)['\"]",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Pattern compositionPattern = Pattern.compile(
                "composition\\s*:\\s*['\"]([^'\"]+)['\"]",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher unitMatcher = unitPattern.matcher(objectBlock);
        Matcher compositionMatcher = compositionPattern.matcher(objectBlock);

        if (unitMatcher.find() && compositionMatcher.find()) {
            String unit = unitMatcher.group(1);
            String composition = compositionMatcher.group(1);
            compositions.add(String.format("        unit=\"%s\"  composition=\"%s\"", unit, composition));
        }
    }
    public void setStopRequested(java.util.function.BooleanSupplier stopCondition) {
        this.stopCondition = stopCondition;
    }

    private boolean isStopRequested() {
        return stopCondition.getAsBoolean() || Thread.currentThread().isInterrupted();
    }

}
