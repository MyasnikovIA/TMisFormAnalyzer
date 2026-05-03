package ru.miacomsoft.service;

import ru.miacomsoft.model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import ru.miacomsoft.service.ViewDependencyAnalyzer;
import ru.miacomsoft.model.ViewTableDependencies;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для генерации отчетов по анализу форм
 */
public class ReportGeneratorService {

    private static final String OUTPUT_DIR = "SQL_info";
    private static final int BATCH_SIZE = 20;

    private List<FormInfo> analyzedForms;
    private Map<String, TableViewInfo> allTablesViews;
    private Map<String, PackageFunctionInfo> allPackagesFunctions;
    private int totalSqlQueries;
    private Map<String, UnknownObjectInfo> allUnknownObjects;
    private Map<String, ConstantInfo> allConstants;
    private SettingsModel settings;
    // Кэш для уже загруженных зависимостей вьюх
    private Map<String, ViewTableDependencies> loadedViewDependencies = new ConcurrentHashMap<>();

    public ReportGeneratorService(SettingsModel settings) {
        this.settings = settings;
        this.analyzedForms = new ArrayList<>();
        this.allTablesViews = new LinkedHashMap<>();
        this.allPackagesFunctions = new LinkedHashMap<>();
        this.allUnknownObjects = new LinkedHashMap<>();
        this.allConstants = new LinkedHashMap<>();
        this.totalSqlQueries = 0;
        this.loadedViewDependencies = new ConcurrentHashMap<>();
    }

    /**
     * Получить УНИКАЛЬНЫЕ таблицы из всех вьюх, используемых в форме
     * (без дубликатов)
     */
    private Set<String> getTablesFromViewsForForm(FormInfo formInfo, Map<String, ViewTableDependencies> viewDependencies) {
        Set<String> allTables = new LinkedHashSet<>();

        if (viewDependencies == null || viewDependencies.isEmpty()) {
            return allTables;
        }

        // Собираем все вьюхи, которые используются в этой форме
        Set<String> viewsUsed = new LinkedHashSet<>();
        for (String tv : formInfo.getTablesViews()) {
            if (tv.startsWith("D_V_")) {
                viewsUsed.add(tv);
            }
        }

        // Для каждой вьюхи собираем её таблицы (LinkedHashSet сам удалит дубликаты)
        for (String viewName : viewsUsed) {
            ViewTableDependencies deps = viewDependencies.get(viewName);
            if (deps != null && deps.isExistsInOracle()) {
                allTables.addAll(deps.getOracleTables());
            }
        }

        return allTables;
    }
    /**
     * Очистить основной файл отчета перед началом анализа
     */
    public void clearMainReport() throws IOException {
        Path mainReportPath = Paths.get(OUTPUT_DIR, "forms_report.txt");
        if (Files.exists(mainReportPath)) {
            Files.delete(mainReportPath);
            System.out.println("  Удален старый файл отчета: " + mainReportPath);
        }

        // Также очищаем кэш зависимостей вьюх
        loadedViewDependencies.clear();
        System.out.println("  Очищен кэш зависимостей вьюх");
    }

    /**
     * Создать заголовок нового отчета
     */
    public void createMainReportHeader() throws IOException {
        Path mainReportPath = Paths.get(OUTPUT_DIR, "forms_report.txt");

        // Создаем директорию если нужно
        createOutputDirectory();

        try (PrintWriter writer = new PrintWriter(new FileWriter(mainReportPath.toFile()))) {
            writer.println("=".repeat(100));
            writer.println("=== ОТЧЕТ ПО ФОРМАМ T-MIS ===");
            writer.println("Дата создания: " + new Date());
            writer.println("-".repeat(100));
            writer.println("КОНФИГУРАЦИЯ ОТЧЕТА:");
            writer.println("  SQL содержимое: " + (ReportConfig.isIncludeSqlContent() ? "ДА" : "НЕТ"));
            writer.println("  JS формы: " + (ReportConfig.isIncludeJsForms() ? "ДА" : "НЕТ"));
            writer.println("  Таблицы/вьюхи: " + (ReportConfig.isIncludeTablesViews() ? "ДА" : "НЕТ"));
            writer.println("  Таблицы через вьюхи: " + (ReportConfig.isIncludeViewTables() ? "ДА" : "НЕТ"));
            writer.println("  Композиции JS: " + (ReportConfig.isIncludeJsUnitCompositions() ? "ДА" : "НЕТ"));
            writer.println("  Детали вьюх: " + (ReportConfig.isIncludeViewDetails() ? "ДА" : "НЕТ"));
            writer.println("=".repeat(100));
            writer.println();
        }

        System.out.println("  Создан новый файл отчета: " + mainReportPath);
    }
    public ReportGeneratorService() {
        this(new SettingsModel());
    }

    public void addAnalysisResult(FormInfo formInfo) {
        analyzedForms.add(formInfo);
        totalSqlQueries += formInfo.getTotalSqlQueries();

        // Очищаем unknown объекты от D_PKG_CONSTANTS
        Set<String> cleanedUnknown = new LinkedHashSet<>();
        for (String unknown : formInfo.getUnknownObjects()) {
            if (!unknown.contains("D_PKG_CONSTANTS")) {
                cleanedUnknown.add(unknown);
            }
        }
        formInfo.getUnknownObjects().clear();
        formInfo.getUnknownObjects().addAll(cleanedUnknown);

        // Добавляем таблицы и вьюхи из формы
        for (String tvName : formInfo.getTablesViews()) {
            allTablesViews.computeIfAbsent(tvName, TableViewInfo::new)
                    .addUsage(formInfo.getFormPath(), getSqlPreview(formInfo, tvName));
        }

        // ========== ВАЖНО: ДОБАВЛЯЕМ ТАБЛИЦЫ ИЗ ВЬЮХ ==========
        // Здесь нужно добавить таблицы, которые были извлечены из вьюх
        // Они хранятся в методе formInfo.get... но их нет в formInfo
        // Поэтому нужно добавить их через другой механизм

        // Добавляем константы
        for (String constantName : formInfo.getConstants()) {
            allConstants.computeIfAbsent(constantName, ConstantInfo::new)
                    .addUsage(formInfo.getFormPath(), getSqlPreviewForConstant(formInfo, constantName));
        }

        // Добавляем пакеты и функции
        for (String pfName : formInfo.getPackagesFunctions()) {
            allPackagesFunctions.computeIfAbsent(pfName, PackageFunctionInfo::new)
                    .addUsage(formInfo.getFormPath(), getSqlPreview(formInfo, pfName));
        }

        // Добавляем неизвестные объекты
        for (String unknownName : formInfo.getUnknownObjects()) {
            allUnknownObjects.computeIfAbsent(unknownName, UnknownObjectInfo::new)
                    .addUsage(formInfo.getFormPath(), getSqlPreviewForUnknown(formInfo, unknownName));
        }
    }

    private String getSqlPreviewForConstant(FormInfo formInfo, String constantName) {
        for (SqlInfo sql : formInfo.getSqlQueries()) {
            if (sql.getConstants().contains(constantName)) {
                String cleanSql = sql.getCleanSql();
                if (cleanSql.length() > 200) {
                    return cleanSql.substring(0, 200) + "...";
                }
                return cleanSql;
            }
        }
        return "";
    }

    private String getSqlPreviewForUnknown(FormInfo formInfo, String name) {
        for (SqlInfo sql : formInfo.getSqlQueries()) {
            if (sql.getUnknownObjects().contains(name)) {
                String cleanSql = sql.getCleanSql();
                if (cleanSql.length() > 200) {
                    return cleanSql.substring(0, 200) + "...";
                }
                return cleanSql;
            }
        }
        return "";
    }

    private String getSqlPreview(FormInfo formInfo, String name) {
        for (SqlInfo sql : formInfo.getSqlQueries()) {
            if (sql.getTablesViews().contains(name) || sql.getPackagesFunctions().contains(name)) {
                String cleanSql = sql.getCleanSql();
                if (cleanSql.length() > 200) {
                    return cleanSql.substring(0, 200) + "...";
                }
                return cleanSql;
            }
        }
        return "";
    }

    public void generateAllReports() throws IOException {
        createOutputDirectory();
        generateFormReports();
        generateFormReportsWithoutSql();
        generateTablesViewsReport();
        generatePackagesFunctionsReport();
        generateFormsListReport();
        generateSummaryReport();
        generateViewDependenciesReport();
        generateConstantsReport();
        System.out.println("\n  Все отчеты успешно сгенерированы!");
    }

    private void createOutputDirectory() throws IOException {
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }
    }

    private void generateFormReports() throws IOException {
        int batchNumber = 1;
        Path summaryFilePath = Paths.get(OUTPUT_DIR, "forms_report_all.txt");
        PrintWriter summaryWriter = new PrintWriter(Files.newBufferedWriter(summaryFilePath));

        try {
            summaryWriter.println("=".repeat(100));
            summaryWriter.println("=== ПОЛНЫЙ ОТЧЕТ ПО ФОРМАМ T-MIS ===");
            summaryWriter.println("Дата создания: " + new Date());
            summaryWriter.println("Всего форм: " + analyzedForms.size());
            summaryWriter.println("Всего SQL запросов: " + totalSqlQueries);
            summaryWriter.println("=".repeat(100));
            summaryWriter.println();

            for (int i = 0; i < analyzedForms.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, analyzedForms.size());
                List<FormInfo> batch = analyzedForms.subList(i, end);

                String fileName = String.format("forms_report_%03d.txt", batchNumber);
                File resFragDir = new File(OUTPUT_DIR, "Frag");
                if (!resFragDir.exists()) {
                    resFragDir.mkdirs();
                }
                Path filePath = Paths.get(resFragDir.getAbsolutePath(), fileName);

                try (PrintWriter batchWriter = new PrintWriter(Files.newBufferedWriter(filePath))) {
                    writeFormReportHeader(batchWriter, batchNumber, batch.size());
                    for (FormInfo formInfo : batch) {
                        writeFormReport(batchWriter, formInfo);
                        writeFormReportToSummary(summaryWriter, formInfo);
                    }
                    writeFormReportFooter(batchWriter, batch);
                }

                System.out.println("  Создан: " + fileName + " (" + batch.size() + " форм)");
                batchNumber++;
            }

            summaryWriter.println();
            summaryWriter.println("=".repeat(100));
            summaryWriter.println("=== КОНЕЦ ПОЛНОГО ОТЧЕТА ===");
            summaryWriter.println("=".repeat(100));

        } finally {
            summaryWriter.close();
        }

        System.out.println("  Создан: forms_report_all.txt (полный отчет)");
    }

    /**
     * Генерация только основного отчета с выбранной конфигурацией
     * (используется только если не используется инкрементальный режим)
     * При инкрементальном режиме этот метод не вызывается, так как отчет
     * создается постепенно через appendFormToMainReport
     */
    public void generateMainReport() throws IOException {
        // Проверяем, существует ли уже отчет, созданный инкрементально
        Path mainReportPath = Paths.get(OUTPUT_DIR, "forms_report.txt");

        // Если отчет уже существует и есть проанализированные формы - значит он создан инкрементально
        if (Files.exists(mainReportPath) && !analyzedForms.isEmpty()) {
            System.out.println("  Отчет уже создан инкрементально. Для пересоздания удалите файл: " + mainReportPath);
            return;
        }

        // Иначе создаем отчет целиком (только если нет инкрементальных данных)
        createOutputDirectory();

        // Загружаем зависимости вьюх если нужно
        Map<String, ViewTableDependencies> viewDependencies = null;
        if (ReportConfig.isIncludeViewTables() || ReportConfig.isIncludeViewDetails()) {
            viewDependencies = loadViewDependencies();
        }

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(mainReportPath))) {
            // Заголовок отчета
            writer.println("=".repeat(100));
            writer.println("=== ОТЧЕТ ПО ФОРМАМ T-MIS ===");
            writer.println("Дата создания: " + new Date());
            writer.println("Всего форм: " + analyzedForms.size());
            writer.println("Всего SQL запросов: " + totalSqlQueries);
            writer.println("-".repeat(100));
            writer.println("КОНФИГУРАЦИЯ ОТЧЕТА:");
            writer.println("  SQL содержимое: " + (ReportConfig.isIncludeSqlContent() ? "ДА" : "НЕТ"));
            writer.println("  JS формы: " + (ReportConfig.isIncludeJsForms() ? "ДА" : "НЕТ"));
            writer.println("  Таблицы/вьюхи: " + (ReportConfig.isIncludeTablesViews() ? "ДА" : "НЕТ"));
            writer.println("  Таблицы через вьюхи: " + (ReportConfig.isIncludeViewTables() ? "ДА" : "НЕТ"));
            writer.println("  Композиции JS: " + (ReportConfig.isIncludeJsUnitCompositions() ? "ДА" : "НЕТ"));
            writer.println("  Детали вьюх: " + (ReportConfig.isIncludeViewDetails() ? "ДА" : "НЕТ"));
            writer.println("=".repeat(100));
            writer.println();

            // Выводим каждую форму
            for (FormInfo formInfo : analyzedForms) {
                writeFormReportWithConfig(writer, formInfo, viewDependencies);
            }

            // Итоговая статистика
            writer.println();
            writer.println("=".repeat(100));
            writer.println("=== ИТОГОВАЯ СТАТИСТИКА ===");
            writer.println("Всего проанализировано форм: " + analyzedForms.size());
            writer.println("Всего SQL запросов: " + totalSqlQueries);

            long baseCount = analyzedForms.stream().filter(f -> !f.isFullyReplaced() && f.getOverrides().isEmpty()).count();
            long fullCount = analyzedForms.stream().filter(FormInfo::isFullyReplaced).count();
            long partialCount = analyzedForms.stream().filter(f -> !f.isFullyReplaced() && !f.getOverrides().isEmpty()).count();

            writer.println("Базовые формы: " + baseCount);
            writer.println("Полностью замененные: " + fullCount);
            writer.println("Частично переопределенные: " + partialCount);

            long tableCount = allTablesViews.values().stream()
                    .filter(tv -> tv.getType() == TableViewInfo.Type.TABLE)
                    .count();
            long viewCount = allTablesViews.values().stream()
                    .filter(tv -> tv.getType() == TableViewInfo.Type.VIEW)
                    .count();

            writer.println("Уникальных таблиц: " + tableCount);
            writer.println("Уникальных представлений (вьюх): " + viewCount);
            writer.println("Уникальных пакетов/функций: " + allPackagesFunctions.size());
            writer.println("Уникальных констант: " + allConstants.size());
            writer.println("Объектов для разбора: " + allUnknownObjects.size());
            writer.println("=".repeat(100));
        }

        System.out.println("  Создан: forms_report.txt");
    }

    private Map<String, ViewTableDependencies> loadViewDependencies() {
        Map<String, ViewTableDependencies> result = new LinkedHashMap<>();

        Map<String, TableViewInfo> viewsOnly = new LinkedHashMap<>();
        for (Map.Entry<String, TableViewInfo> entry : allTablesViews.entrySet()) {
            if (entry.getValue().getType() == TableViewInfo.Type.VIEW) {
                viewsOnly.put(entry.getKey(), entry.getValue());
            }
        }

        if (viewsOnly.isEmpty()) {
            return result;
        }

        System.out.println("  Загрузка зависимостей для " + viewsOnly.size() + " вьюх...");

        ViewDependencyAnalyzer analyzer = new ViewDependencyAnalyzer();

        int count = 0;
        for (Map.Entry<String, TableViewInfo> entry : viewsOnly.entrySet()) {
            count++;
            String viewName = entry.getKey();
            System.out.print("    Анализ вьюхи [" + count + "/" + viewsOnly.size() + "]: " + viewName + " ... ");

            try {
                ViewTableDependencies deps = analyzer.analyzeViewPublic(viewName);
                result.put(viewName, deps);
                System.out.println("OK (таблиц: " + deps.getOracleTables().size() + ")");
            } catch (Exception e) {
                System.err.println("ОШИБКА: " + e.getMessage());
                ViewTableDependencies errorDeps = new ViewTableDependencies(viewName);
                errorDeps.setExistsInOracle(false);
                errorDeps.setOracleError(e.getMessage());
                result.put(viewName, errorDeps);
            }
        }

        return result;
    }


    private void writeFormReportWithConfig(PrintWriter writer, FormInfo formInfo,
                                           Map<String, ViewTableDependencies> viewDependencies) {
        writer.println("-".repeat(100));
        writer.println("ФОРМА: " + formInfo.getFormPath());
        writer.println("-".repeat(100));
        writer.println("Базовая форма: " + formInfo.getBaseFormPath());

        if (formInfo.isFullyReplaced()) {
            writer.println("СТАТУС: ПОЛНОСТЬЮ ЗАМЕНЕНА");
            writer.println("Файл замены: " + formInfo.getReplacementPath());
        } else if (!formInfo.getOverrides().isEmpty()) {
            writer.println("СТАТУС: ЧАСТИЧНО ПЕРЕОПРЕДЕЛЕНА");
            writer.println("Переопределения:");
            for (FormInfo.OverrideInfo override : formInfo.getOverrides()) {
                writer.println("  - " + override.toString());
            }
        } else {
            writer.println("СТАТУС: БАЗОВАЯ ФОРМА (без переопределений)");
        }

        writer.println();
        writeUserFormsSection(writer, formInfo);

        // Блок SubForm
        writer.println("Список подключаемых форм subForm:");
        if (formInfo.getSubForms().isEmpty()) {
            writer.println("     (не найдено)");
        } else {
            for (String subForm : formInfo.getSubForms()) {
                String cleanPath = subForm.replaceAll("^[\"']|[\"']$|/?>$", "");
                writer.println("     " + cleanPath);
            }
        }
        writer.println();

        if (ReportConfig.isIncludeJsForms()) {
            writer.println("Список вызываемых форм в JS:");
            if (formInfo.getJsForms().isEmpty()) {
                writer.println("     (не найдено)");
            } else {
                for (String jsForm : formInfo.getJsForms()) {
                    writer.println("     " + jsForm);
                }
            }
            writer.println();
        }

        if (ReportConfig.isIncludeSqlContent()) {
            writeSqlQueriesSectionFull(writer, formInfo);
        } else {
            writer.println("SQL ЗАПРОСЫ (" + formInfo.getSqlQueries().size() + "):");
            writer.println("     (содержимое SQL запросов исключено для краткости)");
            writer.println();
        }

        // ========== СПИСОК ВСЕХ ИСПОЛЬЗУЕМЫХ ВЬЮХ ==========
        if (ReportConfig.isIncludeTablesViews() && !formInfo.getTablesViews().isEmpty()) {
            writer.println("ИСПОЛЬЗУЕМЫЕ ВЬЮХИ:");
            for (String tv : formInfo.getTablesViews()) {
                if (tv.startsWith("D_V_")) {
                    writer.println("  - " + tv);
                }
            }
            writer.println();
        }

        // ========== ДЕТАЛЬНОЕ СОДЕРЖИМОЕ ВЬЮХ (СТАРАЯ ВЕРСИЯ) ==========
        if (ReportConfig.isIncludeViewDetails() && viewDependencies != null && !viewDependencies.isEmpty()) {
            writer.println("ДЕТАЛЬНОЕ СОДЕРЖИМОЕ ВЬЮХ (таблицы):");
            writer.println();

            for (Map.Entry<String, ViewTableDependencies> entry : viewDependencies.entrySet()) {
                String viewName = entry.getKey();
                ViewTableDependencies deps = entry.getValue();

                if (deps != null && deps.isExistsInOracle()) {
                    writer.println("  " + viewName + ":");
                    Set<String> tables = deps.getOracleTables();
                    if (tables.isEmpty()) {
                        writer.println("      (таблицы не найдены)");
                    } else {
                        for (String table : tables) {
                            writer.println("      - " + table);
                        }
                    }
                    writer.println();
                } else if (deps != null && deps.getOracleError() != null) {
                    writer.println("  " + viewName + ":");
                    writer.println("      Ошибка: " + deps.getOracleError());
                    writer.println();
                }
            }
        }

        // ========== УНИКАЛЬНЫЕ ТАБЛИЦЫ ИЗ ВСЕХ ВЬЮХ (НОВЫЙ БЛОК) ==========
        if (ReportConfig.isIncludeViewTables()) {
            Set<String> uniqueTables = getTablesFromViewsForForm(formInfo, viewDependencies);
            if (!uniqueTables.isEmpty()) {
                writer.println("УНИКАЛЬНЫЕ ТАБЛИЦЫ ИЗ ВСЕХ ВЬЮХ НА ФОРМЕ (без дубликатов):");
                for (String table : uniqueTables) {
                    writer.println("  - " + table);
                }
                writer.println();
                writer.println("  Всего уникальных таблиц из вьюх: " + uniqueTables.size());
                writer.println();
            }
        }

        // ========== ТАБЛИЦЫ, ИСПОЛЬЗУЕМЫЕ НАПРЯМУЮ В SQL ==========
        if (ReportConfig.isIncludeTablesViews() && !formInfo.getTablesViews().isEmpty()) {
            Set<String> directTables = new LinkedHashSet<>();
            for (String tv : formInfo.getTablesViews()) {
                if (!tv.startsWith("D_V_") && !tv.startsWith("D_PKG_")) {
                    directTables.add(tv);
                }
            }
            if (!directTables.isEmpty()) {
                writer.println("ТАБЛИЦЫ, ИСПОЛЬЗУЕМЫЕ НАПРЯМУЮ В SQL:");
                for (String table : directTables) {
                    writer.println("  - " + table);
                }
                writer.println();
            }
        }

        if (!formInfo.getPackagesFunctions().isEmpty()) {
            writer.println("ИСПОЛЬЗУЕМЫЕ ПАКЕТЫ И ФУНКЦИИ:");
            for (String pf : formInfo.getPackagesFunctions()) {
                writer.println("  - " + pf);
            }
            writer.println();
        }

        if (!formInfo.getUserProcedures().isEmpty()) {
            writer.println("ПОЛЬЗОВАТЕЛЬСКИЕ ПРОЦЕДУРЫ:");
            for (String proc : formInfo.getUserProcedures()) {
                writer.println("  - " + proc);
            }
            writer.println();
        }

        if (!formInfo.getSystemOptions().isEmpty()) {
            writer.println("СИСТЕМНЫЕ ОПЦИИ:");
            for (String opt : formInfo.getSystemOptions()) {
                writer.println("  - " + opt);
            }
            writer.println();
        }

        if (formInfo.getConstants() != null && !formInfo.getConstants().isEmpty()) {
            writer.println("КОНСТАНТЫ:");
            for (String constant : formInfo.getConstants()) {
                writer.println("  - " + constant);
            }
            writer.println();
        }

        // Композиции в тэгах UnitEdit
        if (formInfo.getUnitCompositions() != null && !formInfo.getUnitCompositions().isEmpty()) {
            writer.println("КОМПОЗИЦИИ В ТЭГАХ UnitEdit:");
            for (String composition : formInfo.getUnitCompositions()) {
                writer.println(composition + ";");
            }
            writer.println();
        }

        // Композиции из JS
        if (ReportConfig.isIncludeJsUnitCompositions() &&
                formInfo.getJsUnitCompositions() != null &&
                !formInfo.getJsUnitCompositions().isEmpty()) {
            writer.println("КОМПОЗИЦИИ ИЗ JS (UniversalComposition):");
            for (String composition : formInfo.getJsUnitCompositions()) {
                writer.println(composition + ";");
            }
            writer.println();
        }

        if (!formInfo.getUnknownObjects().isEmpty()) {
            writer.println("РАЗОБРАТЬ АНАЛИТИКОМ:");
            for (String obj : formInfo.getUnknownObjects()) {
                writer.println("  - " + obj);
            }
            writer.println();
        }
    }



    public void appendFormToMainReport(FormInfo formInfo) throws IOException {
        createOutputDirectory();

        // Получаем зависимости вьюх если нужно
        Map<String, ViewTableDependencies> viewDependencies = null;
        if (ReportConfig.isIncludeViewTables() || ReportConfig.isIncludeViewDetails()) {
            viewDependencies = loadViewDependenciesForForm(formInfo);
        }

        Path mainReportPath = Paths.get(OUTPUT_DIR, "forms_report.txt");

        // Проверяем, существует ли файл (если нет - значит что-то пошло не так)
        if (!Files.exists(mainReportPath)) {
            System.err.println("  ОШИБКА: Файл отчета не существует! Заголовок не был создан.");
            return;
        }

        // Дописываем информацию о форме в конец файла
        try (PrintWriter writer = new PrintWriter(new FileWriter(mainReportPath.toFile(), true))) {
            writeFormReportWithConfig(writer, formInfo, viewDependencies);
        }
    }

    /**
     * Загрузка зависимостей только для вьюх, используемых в конкретной форме
     */
    private Map<String, ViewTableDependencies> loadViewDependenciesForForm(FormInfo formInfo) {
        Map<String, ViewTableDependencies> result = new LinkedHashMap<>();

        // Собираем только вьюхи, используемые в этой форме
        Set<String> viewsUsed = new LinkedHashSet<>();
        for (String tv : formInfo.getTablesViews()) {
            if (tv.startsWith("D_V_")) {
                viewsUsed.add(tv);
            }
        }

        if (viewsUsed.isEmpty()) {
            return result;
        }

        ViewDependencyAnalyzer analyzer = new ViewDependencyAnalyzer();

        for (String viewName : viewsUsed) {
            // Проверяем локальный кэш
            if (loadedViewDependencies.containsKey(viewName)) {
                result.put(viewName, loadedViewDependencies.get(viewName));
                // ДОБАВЛЯЕМ ТАБЛИЦЫ ИЗ ВЬЮХИ В ОБЩИЙ СПИСОК
                ViewTableDependencies deps = loadedViewDependencies.get(viewName);
                for (String table : deps.getOracleTables()) {
                    if (!allTablesViews.containsKey(table)) {
                        allTablesViews.computeIfAbsent(table, TableViewInfo::new)
                                .addUsage(formInfo.getFormPath(), "Из вьюхи " + viewName);
                    }
                }
                continue;
            }

            // Проверяем глобальный кэш
            if (ViewDependencyAnalyzer.isInCache(viewName)) {
                ViewTableDependencies cached = analyzer.analyzeViewPublic(viewName);
                result.put(viewName, cached);
                loadedViewDependencies.put(viewName, cached);
                // ДОБАВЛЯЕМ ТАБЛИЦЫ ИЗ ВЬЮХИ В ОБЩИЙ СПИСОК
                for (String table : cached.getOracleTables()) {
                    if (!allTablesViews.containsKey(table)) {
                        allTablesViews.computeIfAbsent(table, TableViewInfo::new)
                                .addUsage(formInfo.getFormPath(), "Из вьюхи " + viewName);
                    }
                }
                continue;
            }

            try {
                System.out.print("    Анализ вьюхи: " + viewName + " ... ");
                ViewTableDependencies deps = analyzer.analyzeViewPublic(viewName);
                result.put(viewName, deps);
                loadedViewDependencies.put(viewName, deps);

                // ДОБАВЛЯЕМ ТАБЛИЦЫ ИЗ ВЬЮХИ В ОБЩИЙ СПИСОК
                for (String table : deps.getOracleTables()) {
                    if (!allTablesViews.containsKey(table)) {
                        allTablesViews.computeIfAbsent(table, TableViewInfo::new)
                                .addUsage(formInfo.getFormPath(), "Из вьюхи " + viewName);
                        System.out.println("      Добавлена таблица из вьюхи: " + table);
                    }
                }

                System.out.println("OK (таблиц: " + deps.getOracleTables().size() + ")");
            } catch (Exception e) {
                System.err.println("ОШИБКА: " + e.getMessage());
                ViewTableDependencies errorDeps = new ViewTableDependencies(viewName);
                errorDeps.setExistsInOracle(false);
                errorDeps.setOracleError(e.getMessage());
                result.put(viewName, errorDeps);
                loadedViewDependencies.put(viewName, errorDeps);
            }
        }

        return result;
    }

    /**
     * Завершить формирование отчета (добавить итоговую статистику)
     */
    public void finishMainReport() throws IOException {
        Path mainReportPath = Paths.get(OUTPUT_DIR, "forms_report.txt");

        if (!Files.exists(mainReportPath)) {
            System.err.println("  ОШИБКА: Файл отчета не существует!");
            return;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(mainReportPath.toFile(), true))) {
            writer.println();
            writer.println("=".repeat(100));
            writer.println("=== ИТОГОВАЯ СТАТИСТИКА ===");
            writer.println("Всего проанализировано форм: " + analyzedForms.size());
            writer.println("Всего SQL запросов: " + totalSqlQueries);

            long baseCount = analyzedForms.stream().filter(f -> !f.isFullyReplaced() && f.getOverrides().isEmpty()).count();
            long fullCount = analyzedForms.stream().filter(FormInfo::isFullyReplaced).count();
            long partialCount = analyzedForms.stream().filter(f -> !f.isFullyReplaced() && !f.getOverrides().isEmpty()).count();

            writer.println("Базовые формы: " + baseCount);
            writer.println("Полностью замененные: " + fullCount);
            writer.println("Частично переопределенные: " + partialCount);

            long tableCount = allTablesViews.values().stream()
                    .filter(tv -> tv.getType() == TableViewInfo.Type.TABLE)
                    .count();
            long viewCount = allTablesViews.values().stream()
                    .filter(tv -> tv.getType() == TableViewInfo.Type.VIEW)
                    .count();

            writer.println("Уникальных таблиц: " + tableCount);
            writer.println("Уникальных представлений (вьюх): " + viewCount);
            writer.println("Уникальных пакетов/функций: " + allPackagesFunctions.size());
            writer.println("Уникальных констант: " + allConstants.size());
            writer.println("Объектов для разбора: " + allUnknownObjects.size());
            writer.println("=".repeat(100));
        }

        System.out.println("  Отчет завершен: forms_report.txt");
    }


    private void writeSqlQueriesSectionFull(PrintWriter writer, FormInfo formInfo) {
        writer.println("SQL ЗАПРОСЫ (" + formInfo.getSqlQueries().size() + "):");
        writer.println();

        int sqlNum = 1;
        for (SqlInfo sql : formInfo.getSqlQueries()) {
            writer.println("  [" + sqlNum + "] " + sql.getSourceType() + ": " +
                    (sql.getComponentName().isEmpty() ? "unnamed" : sql.getComponentName()));
            writer.println("      Источник: " + sql.getSourcePath());
            if (sql.getBaseFormPath() != null) {
                writer.println("      Базовая форма: " + sql.getBaseFormPath());
            }
            writer.println("      SQL:");

            String fullXml = sql.getSqlContent();
            if (fullXml != null && !fullXml.isEmpty()) {
                String[] lines = fullXml.split("\\r?\\n");
                for (String line : lines) {
                    writer.println("      " + line);
                }
            } else {
                writer.println("      (SQL запрос не найден)");
            }

            writer.println();

            if (!sql.getTablesViews().isEmpty()) {
                writer.println("      Таблицы/вьюхи:");
                for (String tv : sql.getTablesViews()) {
                    writer.println("          " + tv + ";");
                }
            }

            if (!sql.getPackagesFunctions().isEmpty()) {
                writer.println("      Пакеты/функции:");
                for (String pf : sql.getPackagesFunctions()) {
                    writer.println("          " + pf + ";");
                }
            }

            if (!sql.getUserProcedures().isEmpty()) {
                writer.println("      Пользовательские процедуры:");
                for (String proc : sql.getUserProcedures()) {
                    writer.println("          " + proc + ";");
                }
            }

            if (!sql.getConstants().isEmpty()) {
                writer.println("      Константы (D_PKG_CONSTANTS):");
                for (String constant : sql.getConstants()) {
                    writer.println("          " + constant + ";");
                }
            }

            writer.println();
            sqlNum++;
        }
    }

    private void writeViewDetailsSection(PrintWriter writer, FormInfo formInfo,
                                         Map<String, ViewTableDependencies> viewDependencies) {
        Set<String> viewsUsed = new LinkedHashSet<>();
        for (String tv : formInfo.getTablesViews()) {
            if (tv.startsWith("D_V_")) {
                viewsUsed.add(tv);
            }
        }

        if (viewsUsed.isEmpty()) {
            return;
        }

        writer.println("ДЕТАЛЬНОЕ СОДЕРЖИМОЕ ВЬЮХ:");
        writer.println();

        for (String viewName : viewsUsed) {
            ViewTableDependencies deps = viewDependencies.get(viewName);
            if (deps != null && deps.isExistsInOracle()) {
                writer.println("  " + viewName + ":");
                Set<String> tables = deps.getOracleTables();
                if (tables.isEmpty()) {
                    writer.println("      (таблицы не найдены)");
                } else {
                    for (String table : tables) {
                        writer.println("      - " + table);
                    }
                }
                writer.println();
            } else if (deps != null && deps.getOracleError() != null) {
                writer.println("  " + viewName + ":");
                writer.println("      Ошибка: " + deps.getOracleError());
                writer.println();
            }
        }
    }

    private void writeFormReportToSummary(PrintWriter writer, FormInfo formInfo) {
        writer.println("-".repeat(100));
        writer.println("ФОРМА: " + formInfo.getFormPath());
        writer.println("-".repeat(100));
        writer.println("Базовая форма: " + formInfo.getBaseFormPath());

        if (formInfo.isFullyReplaced()) {
            writer.println("СТАТУС: ПОЛНОСТЬЮ ЗАМЕНЕНА");
            writer.println("Файл замены: " + formInfo.getReplacementPath());
        } else if (!formInfo.getOverrides().isEmpty()) {
            writer.println("СТАТУС: ЧАСТИЧНО ПЕРЕОПРЕДЕЛЕНА");
            writer.println("Переопределения:");
            for (FormInfo.OverrideInfo override : formInfo.getOverrides()) {
                writer.println("  - " + override.toString());
            }
        } else {
            writer.println("СТАТУС: БАЗОВАЯ ФОРМА (без переопределений)");
        }

        writer.println();
        writeUserFormsSection(writer, formInfo);

        writer.println("Список подключаемых форм subForm:");
        if (formInfo.getSubForms().isEmpty()) {
            writer.println("     (не найдено)");
        } else {
            for (String subForm : formInfo.getSubForms()) {
                writer.println("     " + subForm);
            }
        }
        writer.println();

        writer.println("Список вызываемых форм в JS:");
        if (formInfo.getJsForms().isEmpty()) {
            writer.println("     (не найдено)");
        } else {
            for (String jsForm : formInfo.getJsForms()) {
                writer.println("     " + jsForm);
            }
        }
        writer.println();

        writer.println("SQL ЗАПРОСЫ (" + formInfo.getSqlQueries().size() + "):");
        writer.println();

        int sqlNum = 1;
        for (SqlInfo sql : formInfo.getSqlQueries()) {
            writer.println("  [" + sqlNum + "] " + sql.getSourceType() + ": " +
                    (sql.getComponentName().isEmpty() ? "unnamed" : sql.getComponentName()));
            writer.println("      Источник: " + sql.getSourcePath());
            if (sql.getBaseFormPath() != null) {
                writer.println("      Базовая форма: " + sql.getBaseFormPath());
            }
            writer.println("      SQL:");

            String fullXml = sql.getSqlContent();
            if (fullXml != null && !fullXml.isEmpty()) {
                String[] lines = fullXml.split("\\r?\\n");
                for (String line : lines) {
                    writer.println("      " + line);
                }
            } else {
                writer.println("      (SQL запрос не найден)");
            }

            writer.println();

            if (!sql.getTablesViews().isEmpty()) {
                writer.println("      Таблицы/вьюхи:");
                for (String tv : sql.getTablesViews()) {
                    writer.println("          " + tv + ";");
                }
            }

            if (!sql.getPackagesFunctions().isEmpty()) {
                writer.println("      Пакеты/функции:");
                for (String pf : sql.getPackagesFunctions()) {
                    writer.println("          " + pf + ";");
                }
            }

            if (!sql.getUserProcedures().isEmpty()) {
                writer.println("      Пользовательские процедуры:");
                for (String proc : sql.getUserProcedures()) {
                    writer.println("          " + proc + ";");
                }
            }

            if (!formInfo.getSystemOptions().isEmpty()) {
                writer.println("СИСТЕМНЫЕ ОПЦИИ:");
                for (String opt : formInfo.getSystemOptions()) {
                    writer.println("  - " + opt);
                }
                writer.println();
            }

            writer.println();
            sqlNum++;
        }

        if (formInfo.getSqlQueries().isEmpty()) {
            writer.println("  (SQL запросы не найдены)");
            writer.println();
        }

        if (!formInfo.getTablesViews().isEmpty()) {
            writer.println("ИСПОЛЬЗУЕМЫЕ ТАБЛИЦЫ И ВЬЮХИ:");
            for (String tv : formInfo.getTablesViews()) {
                writer.println("  - " + tv);
            }
            writer.println();
        }

        if (!formInfo.getPackagesFunctions().isEmpty()) {
            writer.println("ИСПОЛЬЗУЕМЫЕ ПАКЕТЫ И ФУНКЦИИ:");
            for (String pf : formInfo.getPackagesFunctions()) {
                writer.println("  - " + pf);
            }
            writer.println();
        }

        if (!formInfo.getUserProcedures().isEmpty()) {
            writer.println("ПОЛЬЗОВАТЕЛЬСКИЕ ПРОЦЕДУРЫ:");
            for (String proc : formInfo.getUserProcedures()) {
                writer.println("  - " + proc);
            }
            writer.println();
        }

        if (!formInfo.getSystemOptions().isEmpty()) {
            writer.println("СИСТЕМНЫЕ ОПЦИИ:");
            for (String opt : formInfo.getSystemOptions()) {
                writer.println("  - " + opt);
            }
            writer.println();
        }

        if (formInfo.getUnitCompositions() != null && !formInfo.getUnitCompositions().isEmpty()) {
            writer.println("КОМПОЗИЦИИ В ТЭГАХ UnitEdit:");
            for (String composition : formInfo.getUnitCompositions()) {
                writer.println(composition + ";");
            }
            writer.println();
        }

        if (formInfo.getJsUnitCompositions() != null && !formInfo.getJsUnitCompositions().isEmpty()) {
            writer.println("КОМПОЗИЦИИ ИЗ JS (UniversalComposition):");
            for (String composition : formInfo.getJsUnitCompositions()) {
                writer.println(composition + ";");
            }
            writer.println();
        }

        if (!formInfo.getUnknownObjects().isEmpty()) {
            writer.println("РАЗОБРАТЬ АНАЛИТИКОМ:");
            for (String obj : formInfo.getUnknownObjects()) {
                writer.println("  - " + obj);
            }
            writer.println();
        }
    }

    private void writeViewTablesSection(PrintWriter writer, FormInfo formInfo) {
        if (!AnalysisConfig.isIncludeViewTableDependencies()) {
            return;
        }

        Set<String> viewsUsed = new LinkedHashSet<>();
        for (String tv : formInfo.getTablesViews()) {
            if (tv.startsWith("D_V_")) {
                viewsUsed.add(tv);
            }
        }

        if (viewsUsed.isEmpty()) {
            return;
        }

        Set<String> allTables = new LinkedHashSet<>();
        ViewDependencyAnalyzer analyzer = new ViewDependencyAnalyzer();

        for (String viewName : viewsUsed) {
            try {
                ViewTableDependencies deps = analyzer.analyzeView(viewName);
                if (deps.isExistsInOracle()) {
                    allTables.addAll(deps.getOracleTables());
                }
            } catch (Exception e) {
                System.err.println("Ошибка при анализе вьюхи " + viewName + ": " + e.getMessage());
            }
        }

        if (allTables.isEmpty()) {
            return;
        }

        writer.println("ТАБЛИЦЫ, ИСПОЛЬЗУЕМЫЕ ЧЕРЕЗ ВЬЮХИ:");
        for (String table : allTables) {
            writer.println("  - " + table);
        }
        writer.println();
    }


    private void writeFormReportToSummaryWithoutSql(PrintWriter writer, FormInfo formInfo) {
        writer.println("-".repeat(100));
        writer.println("ФОРМА: " + formInfo.getFormPath());
        writer.println("-".repeat(100));
        writer.println("Базовая форма: " + formInfo.getBaseFormPath());

        if (formInfo.isFullyReplaced()) {
            writer.println("СТАТУС: ПОЛНОСТЬЮ ЗАМЕНЕНА");
            writer.println("Файл замены: " + formInfo.getReplacementPath());
        } else if (!formInfo.getOverrides().isEmpty()) {
            writer.println("СТАТУС: ЧАСТИЧНО ПЕРЕОПРЕДЕЛЕНА");
            writer.println("Переопределения:");
            for (FormInfo.OverrideInfo override : formInfo.getOverrides()) {
                writer.println("  - " + override.toString());
            }
        } else {
            writer.println("СТАТУС: БАЗОВАЯ ФОРМА (без переопределений)");
        }

        writer.println();
        writeUserFormsSection(writer, formInfo);

        writer.println("Список подключаемых форм subForm:");
        if (formInfo.getSubForms().isEmpty()) {
            writer.println("     (не найдено)");
        } else {
            for (String subForm : formInfo.getSubForms()) {
                writer.println("     " + subForm);
            }
        }
        writer.println();

        writer.println("Список вызываемых форм в JS:");
        if (formInfo.getJsForms().isEmpty()) {
            writer.println("     (не найдено)");
        } else {
            for (String jsForm : formInfo.getJsForms()) {
                writer.println("     " + jsForm);
            }
        }
        writer.println();

        writer.println("SQL ЗАПРОСЫ (" + formInfo.getSqlQueries().size() + "):");
        writer.println("     (содержимое SQL запросов исключено для краткости)");
        writer.println();

        if (!formInfo.getTablesViews().isEmpty()) {
            writer.println("ИСПОЛЬЗУЕМЫЕ ТАБЛИЦЫ И ВЬЮХИ:");
            for (String tv : formInfo.getTablesViews()) {
                writer.println("  - " + tv);
            }
            writer.println();
        }

        writeViewTablesSection(writer, formInfo);

        if (!formInfo.getPackagesFunctions().isEmpty()) {
            writer.println("ИСПОЛЬЗУЕМЫЕ ПАКЕТЫ И ФУНКЦИИ:");
            for (String pf : formInfo.getPackagesFunctions()) {
                writer.println("  - " + pf);
            }
            writer.println();
        }

        if (!formInfo.getUserProcedures().isEmpty()) {
            writer.println("ПОЛЬЗОВАТЕЛЬСКИЕ ПРОЦЕДУРЫ:");
            for (String proc : formInfo.getUserProcedures()) {
                writer.println("  - " + proc);
            }
            writer.println();
        }

        if (!formInfo.getSystemOptions().isEmpty()) {
            writer.println("СИСТЕМНЫЕ ОПЦИИ:");
            for (String opt : formInfo.getSystemOptions()) {
                writer.println("  - " + opt);
            }
            writer.println();
        }

        if (formInfo.getConstants() != null && !formInfo.getConstants().isEmpty()) {
            writer.println("КОНСТАНТЫ:");
            for (String constant : formInfo.getConstants()) {
                writer.println("  - " + constant);
            }
            writer.println();
        }

        if (formInfo.getUnitCompositions() != null && !formInfo.getUnitCompositions().isEmpty()) {
            writer.println("КОМПОЗИЦИИ В ТЭГАХ UnitEdit:");
            for (String composition : formInfo.getUnitCompositions()) {
                writer.println(composition + ";");
            }
            writer.println();
        }

        if (formInfo.getJsUnitCompositions() != null && !formInfo.getJsUnitCompositions().isEmpty()) {
            writer.println("КОМПОЗИЦИИ ИЗ JS (UniversalComposition):");
            for (String composition : formInfo.getJsUnitCompositions()) {
                writer.println(composition + ";");
            }
            writer.println();
        }

        if (!formInfo.getUnknownObjects().isEmpty()) {
            writer.println("РАЗОБРАТЬ АНАЛИТИКОМ:");
            for (String obj : formInfo.getUnknownObjects()) {
                writer.println("  - " + obj);
            }
            writer.println();
        }
    }

    private void writeFormReportHeader(PrintWriter writer, int batchNumber, int batchSize) {
        writer.println("=".repeat(100));
        writer.println("=== ОТЧЕТ ПО ФОРМАМ T-MIS (ПАКЕТ " + batchNumber + ") ===");
        writer.println("Дата создания: " + new Date());
        writer.println("Количество форм в пакете: " + batchSize);
        writer.println("=".repeat(100));
        writer.println();
    }

    private void generateConstantsReport() throws IOException {
        Path filePath = Paths.get(OUTPUT_DIR, "constants_report.txt");

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
            writer.println("=".repeat(100));
            writer.println("=== ОТЧЕТ ПО КОНСТАНТАМ (D_PKG_CONSTANTS.SEARCH_*) ===");
            writer.println("Дата создания: " + new Date());
            writer.println("Всего уникальных констант: " + allConstants.size());
            writer.println("=".repeat(100));
            writer.println();

            List<ConstantInfo> sortedConstants = new ArrayList<>(allConstants.values());
            sortedConstants.sort(Comparator.comparing(ConstantInfo::getName));

            for (ConstantInfo constant : sortedConstants) {
                writer.println("- " + constant.getName());
                writer.println("    Используется в формах: " + constant.getUsedInForms().size());
                writer.println("    Формы:");
                for (String form : constant.getUsedInForms()) {
                    writer.println("         " + form + ";");
                }
                writer.println();
            }

            writer.println("=".repeat(100));
            writer.println("=== КОНЕЦ ОТЧЕТА ПО КОНСТАНТАМ ===");
            writer.println("=".repeat(100));
        }

        System.out.println("  Создан: constants_report.txt");
    }

    private void writeFormReport(PrintWriter writer, FormInfo formInfo) {
        writer.println("-".repeat(100));
        writer.println("ФОРМА: " + formInfo.getFormPath());
        writer.println("-".repeat(100));
        writer.println("Базовая форма: " + formInfo.getBaseFormPath());

        if (formInfo.isFullyReplaced()) {
            writer.println("СТАТУС: ПОЛНОСТЬЮ ЗАМЕНЕНА");
            writer.println("Файл замены: " + formInfo.getReplacementPath());
        } else if (!formInfo.getOverrides().isEmpty()) {
            writer.println("СТАТУС: ЧАСТИЧНО ПЕРЕОПРЕДЕЛЕНА");
            writer.println("Переопределения:");
            for (FormInfo.OverrideInfo override : formInfo.getOverrides()) {
                writer.println("  - " + override.toString());
            }
        } else {
            writer.println("СТАТУС: БАЗОВАЯ ФОРМА (без переопределений)");
        }

        writer.println();
        writeUserFormsSection(writer, formInfo);

        writer.println("Список подключаемых форм subForm:");
        if (formInfo.getSubForms().isEmpty()) {
            writer.println("     (не найдено)");
        } else {
            for (String subForm : formInfo.getSubForms()) {
                writer.println("     " + subForm);
            }
        }
        writer.println();

        writer.println("Список вызываемых форм в JS:");
        if (formInfo.getJsForms().isEmpty()) {
            writer.println("     (не найдено)");
        } else {
            for (String jsForm : formInfo.getJsForms()) {
                writer.println("     " + jsForm);
            }
        }
        writer.println();

        if (!formInfo.getConstants().isEmpty()) {
            writer.println("КОНСТАНТЫ (D_PKG_CONSTANTS.SEARCH_*):");
            for (String constant : formInfo.getConstants()) {
                writer.println("  - " + constant);
            }
            writer.println();
        }

        writer.println("SQL ЗАПРОСЫ (" + formInfo.getSqlQueries().size() + "):");
        writer.println();

        int sqlNum = 1;
        for (SqlInfo sql : formInfo.getSqlQueries()) {
            writer.println("  [" + sqlNum + "] " + sql.getSourceType() + ": " +
                    (sql.getComponentName().isEmpty() ? "unnamed" : sql.getComponentName()));
            writer.println("      Источник: " + sql.getSourcePath());
            if (sql.getBaseFormPath() != null) {
                writer.println("      Базовая форма: " + sql.getBaseFormPath());
            }
            writer.println("      SQL:");

            String fullXml = sql.getSqlContent();
            if (fullXml != null && !fullXml.isEmpty()) {
                String[] lines = fullXml.split("\\r?\\n");
                for (String line : lines) {
                    writer.println("      " + line);
                }
            } else {
                writer.println("      (SQL запрос не найден)");
            }

            writer.println();

            if (!sql.getTablesViews().isEmpty()) {
                writer.println("      Таблицы/вьюхи:");
                for (String tv : sql.getTablesViews()) {
                    writer.println("          " + tv + ";");
                }
            }

            if (!sql.getPackagesFunctions().isEmpty()) {
                writer.println("      Пакеты/функции:");
                for (String pf : sql.getPackagesFunctions()) {
                    writer.println("          " + pf + ";");
                }
            }

            if (!sql.getUserProcedures().isEmpty()) {
                writer.println("      Пользовательские процедуры:");
                for (String proc : sql.getUserProcedures()) {
                    writer.println("          " + proc + ";");
                }
            }

            if (!sql.getConstants().isEmpty()) {
                writer.println("      Константы (D_PKG_CONSTANTS):");
                for (String constant : sql.getConstants()) {
                    writer.println("          " + constant + ";");
                }
            }

            if (formInfo.getUnitCompositions() != null && !formInfo.getUnitCompositions().isEmpty()) {
                writer.println("КОМПОЗИЦИИ В ТЭГАХ UnitEdit:");
                for (String composition : formInfo.getUnitCompositions()) {
                    writer.println(composition + ";");
                }
                writer.println();
            }

            if (formInfo.getJsUnitCompositions() != null && !formInfo.getJsUnitCompositions().isEmpty()) {
                writer.println("КОМПОЗИЦИИ ИЗ JS (UniversalComposition):");
                for (String composition : formInfo.getJsUnitCompositions()) {
                    writer.println(composition + ";");
                }
                writer.println();
            }

            writer.println();
            sqlNum++;
        }

        if (formInfo.getSqlQueries().isEmpty()) {
            writer.println("  (SQL запросы не найдены)");
            writer.println();
        }

        if (!formInfo.getTablesViews().isEmpty()) {
            writer.println("ИСПОЛЬЗУЕМЫЕ ТАБЛИЦЫ И ВЬЮХИ:");
            for (String tv : formInfo.getTablesViews()) {
                writer.println("  - " + tv);
            }
            writer.println();
        }

        if (!formInfo.getPackagesFunctions().isEmpty()) {
            writer.println("ИСПОЛЬЗУЕМЫЕ ПАКЕТЫ И ФУНКЦИИ:");
            for (String pf : formInfo.getPackagesFunctions()) {
                writer.println("  - " + pf);
            }
            writer.println();
        }

        if (!formInfo.getUnknownObjects().isEmpty()) {
            writer.println("РАЗОБРАТЬ АНАЛИТИКОМ:");
            for (String obj : formInfo.getUnknownObjects()) {
                writer.println("  - " + obj);
            }
            writer.println();
        }

        if (!formInfo.getUserProcedures().isEmpty()) {
            writer.println("ПОЛЬЗОВАТЕЛЬСКИЕ ПРОЦЕДУРЫ:");
            for (String proc : formInfo.getUserProcedures()) {
                writer.println("  - " + proc);
            }
            writer.println();
        }

        if (!formInfo.getSystemOptions().isEmpty()) {
            writer.println("СИСТЕМНЫЕ ОПЦИИ:");
            for (String opt : formInfo.getSystemOptions()) {
                writer.println("  - " + opt);
            }
            writer.println();
        }
    }

    private String getRelativePath(String absolutePath) {
        if (absolutePath == null) return "";
        String projectRoot = "/var/www/t-mis/mis";
        if (absolutePath.startsWith(projectRoot)) {
            return absolutePath.substring(projectRoot.length());
        }
        return absolutePath;
    }

    private void writeUserFormsSection(PrintWriter writer, FormInfo formInfo) {
        writer.println("ЮЗЕРФОРМЫ:");

        if (formInfo.getOverrides().isEmpty() && !formInfo.isFullyReplaced()) {
            writer.println("     (не найдено)");
            writer.println();
            return;
        }

        Map<String, List<FormInfo.OverrideInfo>> overridesByRegion = new LinkedHashMap<>();
        for (FormInfo.OverrideInfo override : formInfo.getOverrides()) {
            overridesByRegion.computeIfAbsent(override.getRegionName(), k -> new ArrayList<>()).add(override);
        }

        for (Map.Entry<String, List<FormInfo.OverrideInfo>> entry : overridesByRegion.entrySet()) {
            String region = entry.getKey();
            List<FormInfo.OverrideInfo> overrides = entry.getValue();

            writer.println("     ===== " + region + " =====");

            Set<String> fullReplacements = new LinkedHashSet<>();
            Set<String> partialDfrm = new LinkedHashSet<>();
            Map<String, Set<String>> dotDCatalogs = new LinkedHashMap<>();

            for (FormInfo.OverrideInfo override : overrides) {
                String path = override.getOverridePath();
                String fileName = path.substring(path.lastIndexOf("/") + 1);

                switch (override.getType()) {
                    case FULL_OVERRIDE:
                        fullReplacements.add(path);
                        break;
                    case PARTIAL_OVERRIDE:
                        partialDfrm.add(path);
                        break;
                    case DOT_D_OVERRIDE:
                        if (path.contains(".d/")) {
                            String catalogPath = path.substring(0, path.indexOf(".d/") + 2);
                            dotDCatalogs.computeIfAbsent(catalogPath, k -> new LinkedHashSet<>()).add(fileName);
                        } else {
                            partialDfrm.add(path);
                        }
                        break;
                }
            }

            for (String path : fullReplacements) {
                writer.println("        ПОЛНАЯ ЗАМЕНА: " + path);
            }

            for (Map.Entry<String, Set<String>> catalogEntry : dotDCatalogs.entrySet()) {
                String catalogPath = catalogEntry.getKey();
                writer.println("        КАТАЛОГ: " + catalogPath);
                for (String fileName : catalogEntry.getValue()) {
                    writer.println("            └── " + fileName);
                }
            }

            for (String path : partialDfrm) {
                writer.println("        ЧАСТИЧНОЕ ПЕРЕОПРЕДЕЛЕНИЕ: " + path);
            }

            writer.println();
        }

        writer.println();
    }

    private void writeFormReportFooter(PrintWriter writer, List<FormInfo> batch) {
        int totalQueries = batch.stream().mapToInt(FormInfo::getTotalSqlQueries).sum();
        writer.println("=".repeat(100));
        writer.println("=== ИТОГО ПО ПАКЕТУ ===");
        writer.println("Форм в пакете: " + batch.size());
        writer.println("SQL запросов: " + totalQueries);
        writer.println("=".repeat(100));
    }

    private void generateTablesViewsReport() throws IOException {
        Path filePath = Paths.get(OUTPUT_DIR, "tables_views_report.txt");

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
            writer.println("=".repeat(100));
            writer.println("=== ОТЧЕТ ПО ТАБЛИЦАМ И ВЬЮХАМ ===");
            writer.println("Дата создания: " + new Date());
            writer.println("Всего уникальных объектов: " + allTablesViews.size());
            writer.println("=".repeat(100));
            writer.println();

            List<TableViewInfo> tables = new ArrayList<>();
            List<TableViewInfo> views = new ArrayList<>();

            for (TableViewInfo tv : allTablesViews.values()) {
                if (tv.getType() == TableViewInfo.Type.VIEW) {
                    views.add(tv);
                } else {
                    tables.add(tv);
                }
            }

            tables.sort(Comparator.comparing(TableViewInfo::getName));
            views.sort(Comparator.comparing(TableViewInfo::getName));

            writer.println("=== ТАБЛИЦЫ (" + tables.size() + ") ===");
            writer.println();
            for (TableViewInfo tv : tables) {
                writer.println("- " + tv.getName());
                writer.println("    Используется в формах: " + tv.getUsedInForms().size());
                writer.println("    Формы:");
                for (String form : tv.getUsedInForms()) {
                    writer.println("         " + form + ";");
                }
                writer.println();
            }

            writer.println("=== ПРЕДСТАВЛЕНИЯ (ВЬЮХИ) (" + views.size() + ") ===");
            writer.println();
            for (TableViewInfo tv : views) {
                writer.println("- " + tv.getName());
                writer.println("    Используется в формах: " + tv.getUsedInForms().size());
                writer.println("    Формы:");
                for (String form : tv.getUsedInForms()) {
                    writer.println("         " + form + ";");
                }
                writer.println();
            }

            writer.println("=".repeat(100));
        }

        System.out.println("  Создан: tables_views_report.txt");
    }

    private void generatePackagesFunctionsReport() throws IOException {
        Path filePath = Paths.get(OUTPUT_DIR, "packages_functions_report.txt");

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
            writer.println("=".repeat(100));
            writer.println("=== ОТЧЕТ ПО ПАКЕТАМ И ФУНКЦИЯМ ===");
            writer.println("Дата создания: " + new Date());
            writer.println("Всего уникальных объектов: " + allPackagesFunctions.size());
            writer.println("=".repeat(100));
            writer.println();

            List<PackageFunctionInfo> packageFunctions = new ArrayList<>();
            List<PackageFunctionInfo> standaloneFunctions = new ArrayList<>();

            for (PackageFunctionInfo pf : allPackagesFunctions.values()) {
                if (pf.getType() == PackageFunctionInfo.Type.PACKAGE_FUNCTION) {
                    packageFunctions.add(pf);
                } else {
                    standaloneFunctions.add(pf);
                }
            }

            packageFunctions.sort(Comparator.comparing(PackageFunctionInfo::getFullName));
            standaloneFunctions.sort(Comparator.comparing(PackageFunctionInfo::getFullName));

            writer.println("=== ПАКЕТНЫЕ ФУНКЦИИ (" + packageFunctions.size() + ") ===");
            writer.println();
            for (PackageFunctionInfo pf : packageFunctions) {
                writer.println("- " + pf.getFullName());
                writer.println("    Пакет: " + pf.getPackageName());
                writer.println("    Функция: " + pf.getFunctionName());
                writer.println("    Используется в формах: " + pf.getUsedInForms().size());
                writer.println("    Формы:");
                for (String form : pf.getUsedInForms()) {
                    writer.println("         " + form + ";");
                }
                writer.println();
            }

            writer.println("=== САМОСТОЯТЕЛЬНЫЕ ФУНКЦИИ (" + standaloneFunctions.size() + ") ===");
            writer.println();
            for (PackageFunctionInfo pf : standaloneFunctions) {
                writer.println("- " + pf.getFullName());
                writer.println("    Используется в формах: " + pf.getUsedInForms().size());
                writer.println("    Формы:");
                for (String form : pf.getUsedInForms()) {
                    writer.println("         " + form + ";");
                }
                writer.println();
            }

            writer.println("=".repeat(100));
        }

        System.out.println("  Создан: packages_functions_report.txt");
    }

    private void generateFormsListReport() throws IOException {
        Path filePath = Paths.get(OUTPUT_DIR, "forms_list.txt");

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
            writer.println("# Список всех проанализированных форм T-MIS");
            writer.println("# Дата создания: " + new Date());
            writer.println("# Всего форм: " + analyzedForms.size());
            writer.println("#");
            writer.println("# Формат: СТАТУС | ПУТЬ К ФОРМЕ | БАЗОВАЯ ФОРМА | ЗАМЕНА/ПЕРЕОПРЕДЕЛЕНИЯ");
            writer.println("#");
            writer.println("# Статусы:");
            writer.println("#   [BASE]    - базовая форма (без переопределений)");
            writer.println("#   [FULL]    - полностью заменена UserForms");
            writer.println("#   [PARTIAL] - частично переопределена");
            writer.println("#");

            for (FormInfo formInfo : analyzedForms) {
                String status;
                if (formInfo.isFullyReplaced()) {
                    status = "[FULL]";
                } else if (!formInfo.getOverrides().isEmpty()) {
                    status = "[PARTIAL]";
                } else {
                    status = "[BASE]";
                }

                writer.print(status + " " + formInfo.getFormPath());

                if (formInfo.isFullyReplaced()) {
                    writer.print(" -> " + formInfo.getReplacementPath());
                } else if (!formInfo.getOverrides().isEmpty()) {
                    writer.print(" (переопределения: ");
                    List<String> overrides = new ArrayList<>();
                    for (FormInfo.OverrideInfo ov : formInfo.getOverrides()) {
                        overrides.add(ov.getRegionName());
                    }
                    writer.print(String.join(", ", overrides) + ")");
                }

                writer.println();
            }
        }

        System.out.println("  Создан: forms_list.txt");
    }

    private void generateSummaryReport() throws IOException {
        Path filePath = Paths.get(OUTPUT_DIR, "summary_report.txt");

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
            writer.println("=".repeat(80));
            writer.println("=== ОБЩАЯ СТАТИСТИКА АНАЛИЗА ФОРМ T-MIS ===");
            writer.println("Дата создания: " + new Date());
            writer.println("=".repeat(80));
            writer.println();

            writer.println("ОБЩАЯ СТАТИСТИКА:");
            writer.println("  Всего проанализировано форм: " + analyzedForms.size());
            writer.println("  Всего SQL запросов: " + totalSqlQueries);
            writer.println();

            long baseCount = analyzedForms.stream().filter(f -> !f.isFullyReplaced() && f.getOverrides().isEmpty()).count();
            long fullCount = analyzedForms.stream().filter(FormInfo::isFullyReplaced).count();
            long partialCount = analyzedForms.stream().filter(f -> !f.isFullyReplaced() && !f.getOverrides().isEmpty()).count();

            writer.println("СТАТИСТИКА ПО СТАТУСАМ:");
            writer.println("  Базовые формы: " + baseCount);
            writer.println("  Полностью замененные: " + fullCount);
            writer.println("  Частично переопределенные: " + partialCount);
            writer.println();

            long m2DatasetCount = analyzedForms.stream()
                    .flatMap(f -> f.getSqlQueries().stream())
                    .filter(sql -> sql.getSourceType().equals("M2 DataSet"))
                    .count();
            long d3DatasetCount = analyzedForms.stream()
                    .flatMap(f -> f.getSqlQueries().stream())
                    .filter(sql -> sql.getSourceType().equals("D3 DataSet"))
                    .count();
            long m2ActionCount = analyzedForms.stream()
                    .flatMap(f -> f.getSqlQueries().stream())
                    .filter(sql -> sql.getSourceType().equals("M2 Action"))
                    .count();
            long d3ActionCount = analyzedForms.stream()
                    .flatMap(f -> f.getSqlQueries().stream())
                    .filter(sql -> sql.getSourceType().equals("D3 Action"))
                    .count();

            writer.println("СТАТИСТИКА ПО ТИПАМ КОМПОНЕНТОВ:");
            writer.println("  M2 DataSet: " + m2DatasetCount);
            writer.println("  D3 DataSet: " + d3DatasetCount);
            writer.println("  M2 Action: " + m2ActionCount);
            writer.println("  D3 Action: " + d3ActionCount);
            writer.println();

            long tableCount = allTablesViews.values().stream()
                    .filter(tv -> tv.getType() == TableViewInfo.Type.TABLE)
                    .count();
            long viewCount = allTablesViews.values().stream()
                    .filter(tv -> tv.getType() == TableViewInfo.Type.VIEW)
                    .count();

            writer.println("СТАТИСТИКА ПО ТАБЛИЦАМ И ВЬЮХАМ:");
            writer.println("  Всего уникальных таблиц: " + tableCount);
            writer.println("  Всего уникальных представлений (вьюх): " + viewCount);
            writer.println("  Всего: " + allTablesViews.size());
            writer.println();

            long packageFuncCount = allPackagesFunctions.values().stream()
                    .filter(pf -> pf.getType() == PackageFunctionInfo.Type.PACKAGE_FUNCTION)
                    .count();
            long standaloneFuncCount = allPackagesFunctions.values().stream()
                    .filter(pf -> pf.getType() == PackageFunctionInfo.Type.STANDALONE_FUNCTION)
                    .count();

            writer.println("СТАТИСТИКА ПО ПАКЕТАМ И ФУНКЦИЯМ:");
            writer.println("  Всего пакетных функций: " + packageFuncCount);
            writer.println("  Всего самостоятельных функций: " + standaloneFuncCount);
            writer.println("  Всего: " + allPackagesFunctions.size());
            writer.println();

            long unknownCount = allUnknownObjects.size();
            writer.println("СТАТИСТИКА ПО НЕИЗВЕСТНЫМ ОБЪЕКТАМ:");
            writer.println("  Всего объектов для разбора: " + unknownCount);
            writer.println();

            writer.println("СТАТИСТИКА ПО КОНСТАНТАМ:");
            writer.println("  Всего уникальных констант: " + allConstants.size());
            writer.println();

            writer.println("ТОП-10 НАИБОЛЕЕ ЧАСТО ИСПОЛЬЗУЕМЫХ КОНСТАНТ:");
            allConstants.values().stream()
                    .sorted((a, b) -> Integer.compare(b.getUsedInForms().size(), a.getUsedInForms().size()))
                    .limit(10)
                    .forEach(c -> {
                        writer.println("  " + c.getName() + " - используется в " +
                                c.getUsedInForms().size() + " формах");
                    });
            writer.println();

            writer.println("ТОП-10 НЕИЗВЕСТНЫХ ОБЪЕКТОВ (требуют анализа):");
            allUnknownObjects.values().stream()
                    .sorted((a, b) -> Integer.compare(b.getUsedInForms().size(), a.getUsedInForms().size()))
                    .limit(10)
                    .forEach(obj -> {
                        writer.println("  " + obj.getName() + " - используется в " +
                                obj.getUsedInForms().size() + " формах");
                    });
            writer.println();

            writer.println("ТОП-10 НАИБОЛЕЕ ЧАСТО ИСПОЛЬЗУЕМЫХ ТАБЛИЦ/ВЬЮХ:");
            allTablesViews.values().stream()
                    .sorted((a, b) -> Integer.compare(b.getUsedInForms().size(), a.getUsedInForms().size()))
                    .limit(10)
                    .forEach(tv -> {
                        writer.println("  " + tv.getName() + " (" + tv.getType().getRussianName() + ") - используется в " +
                                tv.getUsedInForms().size() + " формах");
                    });
            writer.println();

            writer.println("ТОП-10 НАИБОЛЕЕ ЧАСТО ИСПОЛЬЗУЕМЫХ ПАКЕТОВ/ФУНКЦИЙ:");
            allPackagesFunctions.values().stream()
                    .sorted((a, b) -> Integer.compare(b.getUsedInForms().size(), a.getUsedInForms().size()))
                    .limit(10)
                    .forEach(pf -> {
                        writer.println("  " + pf.getFullName() + " (" + pf.getType().getRussianName() + ") - используется в " +
                                pf.getUsedInForms().size() + " формах");
                    });
            writer.println();

            writer.println("=".repeat(80));
            writer.println("=== КОНЕЦ ОТЧЕТА ===");
            writer.println("=".repeat(80));
        }

        System.out.println("  Создан: summary_report.txt");
    }

    public List<FormInfo> getAnalyzedForms() {
        return analyzedForms;
    }

    public Map<String, TableViewInfo> getUniqueTablesViews() {
        return allTablesViews;
    }

    public Map<String, PackageFunctionInfo> getUniquePackagesFunctions() {
        return allPackagesFunctions;
    }

    public int getTotalSqlQueries() {
        return totalSqlQueries;
    }

    public void generateViewDependenciesReport() throws IOException {
        System.out.println("\n=== АНАЛИЗ ЗАВИСИМОСТЕЙ ВЬЮХ (Oracle/PostgreSQL) ===");

        Map<String, TableViewInfo> viewsOnly = new LinkedHashMap<>();
        for (Map.Entry<String, TableViewInfo> entry : allTablesViews.entrySet()) {
            if (entry.getValue().getType() == TableViewInfo.Type.VIEW) {
                viewsOnly.put(entry.getKey(), entry.getValue());
            }
        }

        if (viewsOnly.isEmpty()) {
            System.out.println("  Нет вьюх для анализа");
            return;
        }

        System.out.println("  Найдено вьюх для анализа: " + viewsOnly.size());

        ViewDependencyAnalyzer analyzer = new ViewDependencyAnalyzer();

        analyzer.setProgressCallback(new ViewDependencyAnalyzer.ProgressCallback() {
            @Override
            public void onProgress(int current, int total, String viewName, int oracleTables, int postgresTables) {
                // Для консольной версии ничего не делаем
            }

            @Override
            public void onLog(String message) {
                System.out.println(message);
            }

            @Override
            public void onCancelled() {
                System.out.println("  Анализ вьюх был отменен");
            }
        });

        try {
            Map<String, ViewTableDependencies> dependencies = analyzer.analyzeAllViews(viewsOnly);
            analyzer.generateViewDependenciesReport(dependencies);
            System.out.println("  Анализ зависимостей вьюх завершен");
        } catch (InterruptedException e) {
            System.out.println("  Анализ зависимостей вьюх прерван: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public void generateFormReportsWithoutSql() throws IOException {
        int batchNumber = 1;

        Path summaryFilePath = Paths.get(OUTPUT_DIR, "forms_report_all_without_sql.txt");
        PrintWriter summaryWriter = new PrintWriter(Files.newBufferedWriter(summaryFilePath));

        try {
            summaryWriter.println("=".repeat(100));
            summaryWriter.println("=== ПОЛНЫЙ ОТЧЕТ ПО ФОРМАМ T-MIS (БЕЗ SQL) ===");
            summaryWriter.println("Дата создания: " + new Date());
            summaryWriter.println("Всего форм: " + analyzedForms.size());
            summaryWriter.println("Всего SQL запросов: " + totalSqlQueries);
            summaryWriter.println("=".repeat(100));
            summaryWriter.println();

            for (int i = 0; i < analyzedForms.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, analyzedForms.size());
                List<FormInfo> batch = analyzedForms.subList(i, end);

                String fileName = String.format("forms_report_without_sql_%03d.txt", batchNumber);
                File resFragDir = new File(OUTPUT_DIR, "Frag");
                if (!resFragDir.exists()) {
                    resFragDir.mkdirs();
                }
                Path filePath = Paths.get(resFragDir.getAbsolutePath(), fileName);

                try (PrintWriter batchWriter = new PrintWriter(Files.newBufferedWriter(filePath))) {
                    writeFormReportHeaderWithoutSql(batchWriter, batchNumber, batch.size());
                    for (FormInfo formInfo : batch) {
                        writeFormReportWithoutSql(batchWriter, formInfo);
                        writeFormReportToSummaryWithoutSql(summaryWriter, formInfo);
                    }
                    writeFormReportFooterWithoutSql(batchWriter, batch);
                }

                System.out.println("  Создан: " + fileName + " (" + batch.size() + " форм, без SQL)");
                batchNumber++;
            }

            summaryWriter.println();
            summaryWriter.println("=".repeat(100));
            summaryWriter.println("=== КОНЕЦ ПОЛНОГО ОТЧЕТА (БЕЗ SQL) ===");
            summaryWriter.println("=".repeat(100));

        } finally {
            summaryWriter.close();
        }

        System.out.println("  Создан: forms_report_all_without_sql.txt (полный отчет без SQL)");
    }

    private void writeFormReportWithoutSql(PrintWriter writer, FormInfo formInfo) {
        writer.println("-".repeat(100));
        writer.println("ФОРМА: " + formInfo.getFormPath());
        writer.println("-".repeat(100));
        writer.println("Базовая форма: " + formInfo.getBaseFormPath());

        if (formInfo.isFullyReplaced()) {
            writer.println("СТАТУС: ПОЛНОСТЬЮ ЗАМЕНЕНА");
            writer.println("Файл замены: " + formInfo.getReplacementPath());
        } else if (!formInfo.getOverrides().isEmpty()) {
            writer.println("СТАТУС: ЧАСТИЧНО ПЕРЕОПРЕДЕЛЕНА");
            writer.println("Переопределения:");
            for (FormInfo.OverrideInfo override : formInfo.getOverrides()) {
                writer.println("  - " + override.toString());
            }
        } else {
            writer.println("СТАТУС: БАЗОВАЯ ФОРМА (без переопределений)");
        }

        writer.println();
        writeUserFormsSection(writer, formInfo);

        writer.println("Список подключаемых форм subForm:");
        if (formInfo.getSubForms().isEmpty()) {
            writer.println("     (не найдено)");
        } else {
            for (String subForm : formInfo.getSubForms()) {
                writer.println("     " + subForm);
            }
        }
        writer.println();

        writer.println("Список вызываемых форм в JS:");
        if (formInfo.getJsForms().isEmpty()) {
            writer.println("     (не найдено)");
        } else {
            for (String jsForm : formInfo.getJsForms()) {
                writer.println("     " + jsForm);
            }
        }
        writer.println();

        writer.println("SQL ЗАПРОСЫ (" + formInfo.getSqlQueries().size() + "):");
        writer.println("     (содержимое SQL запросов исключено для краткости)");
        writer.println();

        if (!formInfo.getTablesViews().isEmpty()) {
            writer.println("ИСПОЛЬЗУЕМЫЕ ТАБЛИЦЫ И ВЬЮХИ:");
            for (String tv : formInfo.getTablesViews()) {
                writer.println("  - " + tv);
            }
            writer.println();
        }

        writeViewTablesSection(writer, formInfo);

        if (!formInfo.getPackagesFunctions().isEmpty()) {
            writer.println("ИСПОЛЬЗУЕМЫЕ ПАКЕТЫ И ФУНКЦИИ:");
            for (String pf : formInfo.getPackagesFunctions()) {
                writer.println("  - " + pf);
            }
            writer.println();
        }

        if (!formInfo.getUserProcedures().isEmpty()) {
            writer.println("ПОЛЬЗОВАТЕЛЬСКИЕ ПРОЦЕДУРЫ:");
            for (String proc : formInfo.getUserProcedures()) {
                writer.println("  - " + proc);
            }
            writer.println();
        }

        if (!formInfo.getSystemOptions().isEmpty()) {
            writer.println("СИСТЕМНЫЕ ОПЦИИ:");
            for (String opt : formInfo.getSystemOptions()) {
                writer.println("  - " + opt);
            }
            writer.println();
        }

        if (formInfo.getConstants() != null && !formInfo.getConstants().isEmpty()) {
            writer.println("КОНСТАНТЫ:");
            for (String constant : formInfo.getConstants()) {
                writer.println("  - " + constant);
            }
            writer.println();
        }

        if (formInfo.getUnitCompositions() != null && !formInfo.getUnitCompositions().isEmpty()) {
            writer.println("КОМПОЗИЦИИ В ТЭГАХ UnitEdit:");
            for (String composition : formInfo.getUnitCompositions()) {
                writer.println(composition + ";");
            }
            writer.println();
        }

        if (formInfo.getJsUnitCompositions() != null && !formInfo.getJsUnitCompositions().isEmpty()) {
            writer.println("КОМПОЗИЦИИ ИЗ JS (UniversalComposition):");
            for (String composition : formInfo.getJsUnitCompositions()) {
                writer.println(composition + ";");
            }
            writer.println();
        }

        if (!formInfo.getUnknownObjects().isEmpty()) {
            writer.println("РАЗОБРАТЬ АНАЛИТИКОМ:");
            for (String obj : formInfo.getUnknownObjects()) {
                writer.println("  - " + obj);
            }
            writer.println();
        }
    }

    private void writeFormReportHeaderWithoutSql(PrintWriter writer, int batchNumber, int batchSize) {
        writer.println("=".repeat(100));
        writer.println("=== ОТЧЕТ ПО ФОРМАМ T-MIS (БЕЗ SQL) (ПАКЕТ " + batchNumber + ") ===");
        writer.println("Дата создания: " + new Date());
        writer.println("Количество форм в пакете: " + batchSize);
        writer.println("=".repeat(100));
        writer.println();
    }

    private void writeFormReportFooterWithoutSql(PrintWriter writer, List<FormInfo> batch) {
        int totalQueries = batch.stream().mapToInt(FormInfo::getTotalSqlQueries).sum();
        writer.println("=".repeat(100));
        writer.println("=== ИТОГО ПО ПАКЕТУ ===");
        writer.println("Форм в пакете: " + batch.size());
        writer.println("SQL запросов: " + totalQueries);
        writer.println("=".repeat(100));
    }
}