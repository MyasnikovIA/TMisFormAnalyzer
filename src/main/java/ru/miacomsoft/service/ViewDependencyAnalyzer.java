package ru.miacomsoft.service;

import ru.miacomsoft.model.TableViewInfo;
import ru.miacomsoft.model.ViewTableDependencies;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

public class ViewDependencyAnalyzer {

    // Конфигурация Oracle
    private static String ORACLE_URL = "jdbc:oracle:thin:@192.168.241.141:1521/med2dev";
    private static String ORACLE_USER = "dev";
    private static String ORACLE_PASSWORD = "def";

    // Конфигурация PostgreSQL
    private static String POSTGRES_URL = "jdbc:postgresql://192.168.241.137:5432/med2dev";
    private static String POSTGRES_USER = "postgres";
    private static String POSTGRES_PASSWORD = "postgres";

    // Флаги для управления процессом
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicBoolean isCancelled = new AtomicBoolean(false);

    // Колбэки для прогресса
    private ProgressCallback progressCallback;

    public interface ProgressCallback {
        void onProgress(int current, int total, String viewName, int oracleTables, int postgresTables);
        void onLog(String message);
        void onCancelled();
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void setPaused(boolean paused) {
        boolean wasPaused = isPaused.get();
        isPaused.set(paused);
        if (progressCallback != null && wasPaused != paused) {
            progressCallback.onLog(paused ? "  >>> ПАУЗА <<<" : "  >>> ВОЗОБНОВЛЕНО <<<");
        }
    }

    // Добавляем публичный метод в ViewDependencyAnalyzer (если его нет):
    public ViewTableDependencies analyzeViewPublic(String viewName) {
        return analyzeView(viewName);
    }

    public void setCancelled(boolean cancelled) {
        isCancelled.set(cancelled);
        if (cancelled && progressCallback != null) {
            progressCallback.onLog("  >>> ОСТАНОВЛЕНИЕ <<<");
            progressCallback.onCancelled();
        }
    }

    public boolean isCancelled() {
        return isCancelled.get();
    }

    private void checkPauseAndCancel() throws InterruptedException {
        if (isCancelled.get()) {
            throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН");
        }

        while (isPaused.get() && !isCancelled.get()) {
            if (progressCallback != null) {
                //progressCallback.onLog("  ⏸ Ожидание возобновления...");
            }
            Thread.sleep(200);
        }

        if (isCancelled.get()) {
            throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН");
        }
    }

    private static final Pattern TABLE_FROM_JOIN_PATTERN = Pattern.compile(
            "\\b(FROM|JOIN)\\s+([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TABLE_IN_QUERY_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Z0-9_]+)\\s+(?:WHERE|LEFT|RIGHT|INNER|OUTER|ON|,|\\()",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> SQL_KEYWORDS = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "AND", "OR", "ON", "IN", "NOT", "EXISTS",
            "AS", "LEFT", "RIGHT", "INNER", "OUTER", "CROSS", "FULL", "JOIN",
            "UNION", "INTERSECT", "MINUS", "WITH", "RECURSIVE", "ORDER", "GROUP",
            "HAVING", "BY", "ASC", "DESC", "NULLS", "FIRST", "LAST", "CASE",
            "WHEN", "THEN", "ELSE", "END", "DISTINCT", "ALL", "ANY", "SOME"
    ));

    /**
     * Анализ всех вьюх с поддержкой остановки
     */
    public Map<String, ViewTableDependencies> analyzeAllViews(Map<String, TableViewInfo> viewsInfo) throws InterruptedException {
        Map<String, ViewTableDependencies> result = new LinkedHashMap<>();

        if (progressCallback != null) {
            progressCallback.onLog("\n=== АНАЛИЗ ЗАВИСИМОСТЕЙ ВЬЮХ ===");
            progressCallback.onLog("Всего вьюх для анализа: " + viewsInfo.size());
        }

        int total = viewsInfo.size();
        int processed = 0;

        for (Map.Entry<String, TableViewInfo> entry : viewsInfo.entrySet()) {
            // КРИТИЧНО: Проверяем отмену перед обработкой каждой вьюхи
            if (isCancelled.get()) {
                if (progressCallback != null) {
                    progressCallback.onLog("  Анализ прерван пользователем на вьюхе " + entry.getKey());
                    progressCallback.onCancelled();
                }
                throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН ПОЛЬЗОВАТЕЛЕМ");
            }

            // Проверка паузы
            checkPauseAndCancel();

            String viewName = entry.getKey();
            TableViewInfo viewInfo = entry.getValue();

            if (viewInfo.getType() != TableViewInfo.Type.VIEW) {
                continue;
            }

            processed++;

            if (progressCallback != null) {
                progressCallback.onProgress(processed, total, viewName, -1, -1);
            }

            // КРИТИЧНО: Проверяем еще раз перед анализом
            if (isCancelled.get()) {
                throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН ПОЛЬЗОВАТЕЛЕМ");
            }

            ViewTableDependencies dependencies = analyzeView(viewName);

            // КРИТИЧНО: Проверяем после анализа
            if (isCancelled.get()) {
                throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН ПОЛЬЗОВАТЕЛЕМ");
            }

            dependencies.setOriginalViewInfo(viewInfo);
            result.put(viewName, dependencies);

            int oracleCount = dependencies.getOracleTables().size();
            int postgresCount = dependencies.getPostgresTables().size();

            String status;
            if (dependencies.isExistsInOracle() || dependencies.isExistsInPostgres()) {
                status = "OK (Oracle: " + oracleCount + ", PG: " + postgresCount + ")";
            } else {
                status = "НЕ НАЙДЕНА";
            }

            String logMessage = "  [" + processed + "/" + total + "] " + viewName + " ... " + status;
            if (progressCallback != null) {
                progressCallback.onLog(logMessage);
                progressCallback.onProgress(processed, total, viewName, oracleCount, postgresCount);
            }
        }

        String completionMessage = "Анализ завершен. Обработано вьюх: " + processed;
        if (progressCallback != null) {
            progressCallback.onLog(completionMessage);
        }

        return result;
    }


    public ViewTableDependencies analyzeView(String viewName) {
        ViewTableDependencies dependencies = new ViewTableDependencies(viewName);

        analyzeInOracle(viewName, dependencies);
        analyzeInPostgres(viewName, dependencies);

        return dependencies;
    }

    private void analyzeInOracle(String viewName, ViewTableDependencies dependencies) {
        if (isCancelled.get()) {
            dependencies.setExistsInOracle(false);
            dependencies.setOracleError("Остановлено пользователем");
            return;
        }

        String ddl = getOracleViewDDL(viewName);

        if (ddl == null) {
            dependencies.setExistsInOracle(false);
            dependencies.setOracleError("Вьюха не найдена в Oracle");
            return;
        }

        dependencies.setExistsInOracle(true);
        Set<String> tables = extractTablesFromDDL(ddl);
        dependencies.addAllOracleTables(tables);
    }

    private void analyzeInPostgres(String viewName, ViewTableDependencies dependencies) {
        if (isCancelled.get()) {
            dependencies.setExistsInPostgres(false);
            dependencies.setPostgresError("Остановлено пользователем");
            return;
        }

        String ddl = getPostgresViewDDL(viewName);

        if (ddl == null) {
            dependencies.setExistsInPostgres(false);
            dependencies.setPostgresError("Вьюха не найдена в PostgreSQL");
            return;
        }

        dependencies.setExistsInPostgres(true);
        Set<String> tables = extractTablesFromDDL(ddl);
        dependencies.addAllPostgresTables(tables);
    }

    private String getOracleViewDDL(String viewName) {
        if (isCancelled.get()) {
            return null;
        }

        String sql = "SELECT TEXT FROM ALL_VIEWS WHERE VIEW_NAME = ?";

        try (Connection conn = DriverManager.getConnection(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, viewName.toUpperCase());
            pstmt.setQueryTimeout(10);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("TEXT");
            }

        } catch (SQLException e) {
            if (progressCallback != null && !isCancelled.get()) {
                progressCallback.onLog("  Oracle ошибка: " + e.getMessage());
            }
        }

        return null;
    }

    private String getPostgresViewDDL(String viewName) {
        if (isCancelled.get()) {
            return null;
        }

        String sql = "SELECT pg_get_viewdef(?, true) as viewdef";

        try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String getOidSql = "SELECT oid FROM pg_class WHERE relname = ? AND relkind = 'v'";
            try (PreparedStatement oidStmt = conn.prepareStatement(getOidSql)) {
                oidStmt.setString(1, viewName.toLowerCase());
                oidStmt.setQueryTimeout(10);
                ResultSet oidRs = oidStmt.executeQuery();

                if (oidRs.next()) {
                    int oid = oidRs.getInt("oid");
                    pstmt.setInt(1, oid);
                    pstmt.setQueryTimeout(10);
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        return rs.getString("viewdef");
                    }
                }
            }

        } catch (SQLException e) {
            if (progressCallback != null && !isCancelled.get()) {
                progressCallback.onLog("  PostgreSQL ошибка: " + e.getMessage());
            }
        }

        return null;
    }



    private Set<String> extractTablesFromDDL(String ddl) {
        Set<String> tables = new LinkedHashSet<>();

        if (ddl == null || ddl.isEmpty()) {
            return tables;
        }

        String cleanDdl = ddl.replaceAll("\\s+", " ").toUpperCase();

        // SQL ключевые слова для исключения
        Set<String> sqlKeywords = new HashSet<>(Arrays.asList(
                "SELECT", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER",
                "CROSS", "FULL", "ON", "AND", "OR", "NOT", "IN", "EXISTS", "BETWEEN",
                "LIKE", "IS", "NULL", "AS", "UNION", "INTERSECT", "MINUS", "WITH",
                "RECURSIVE", "ORDER", "GROUP", "HAVING", "BY", "ASC", "DESC", "NULLS",
                "FIRST", "LAST", "CASE", "WHEN", "THEN", "ELSE", "END", "DISTINCT",
                "ALL", "ANY", "SOME"
        ));

        // 1. Собираем все потенциальные имена таблиц из FROM, JOIN
        Set<String> potentialTables = new LinkedHashSet<>();
        Matcher matcher = TABLE_FROM_JOIN_PATTERN.matcher(cleanDdl);
        while (matcher.find()) {
            String tableName = matcher.group(2);
            if (tableName.contains(".")) {
                tableName = tableName.substring(tableName.lastIndexOf(".") + 1);
            }

            // Исключаем SQL ключевые слова
            if (!sqlKeywords.contains(tableName)) {
                potentialTables.add(tableName);
            }
        }

        // 2. Собираем все алиасы из AS или после имени таблицы
        Set<String> aliases = new LinkedHashSet<>();

        // Паттерн для явных алиасов с AS: FROM table AS alias
        Pattern explicitAliasPattern = Pattern.compile("\\b(?:FROM|JOIN)\\s+[A-Z0-9_]+\\s+AS\\s+([A-Z0-9_]+)\\b");
        Matcher explicitMatcher = explicitAliasPattern.matcher(cleanDdl);
        while (explicitMatcher.find()) {
            String alias = explicitMatcher.group(1);
            if (!sqlKeywords.contains(alias) && alias.length() > 1) {
                aliases.add(alias);
            }
        }

        // Паттерн для неявных алиасов без AS: FROM table alias
        Pattern implicitAliasPattern = Pattern.compile("\\b(?:FROM|JOIN)\\s+[A-Z0-9_]+\\s+([A-Z0-9_]+)\\b");
        Matcher implicitMatcher = implicitAliasPattern.matcher(cleanDdl);
        while (implicitMatcher.find()) {
            String alias = implicitMatcher.group(1);
            // Исключаем SQL ключевые слова и слишком короткие имена (1-2 буквы)
            if (!sqlKeywords.contains(alias) && alias.length() > 2 && !alias.matches("^[A-Z]{1,2}$")) {
                aliases.add(alias);
            }
        }

        // 3. Собираем также алиасы из подзапросов
        Pattern subqueryAliasPattern = Pattern.compile("\\)\\s+([A-Z0-9_]+)\\s+(?:WHERE|LEFT|RIGHT|JOIN|ON|$)");
        Matcher subqueryMatcher = subqueryAliasPattern.matcher(cleanDdl);
        while (subqueryMatcher.find()) {
            String alias = subqueryMatcher.group(1);
            if (!sqlKeywords.contains(alias) && alias.length() > 1) {
                aliases.add(alias);
            }
        }

        // 4. Фильтруем потенциальные таблицы, исключая алиасы
        for (String tableName : potentialTables) {
            // Исключаем, если это алиас
            if (aliases.contains(tableName)) {
                continue;
            }

            // Исключаем слишком короткие имена (1-2 буквы) - это почти всегда алиасы
            if (tableName.matches("^[A-Z]{1,2}$")) {
                continue;
            }

            // Оставляем только имена, начинающиеся с D_ или содержащие подчеркивание
            if (tableName.startsWith("D_") || tableName.contains("_")) {
                tables.add(tableName);
            }
        }

        return tables;
    }


    private void checkCancelled() throws InterruptedException {
        if (isCancelled.get()) {
            throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН ПОЛЬЗОВАТЕЛЕМ");
        }
    }


    public void generateViewDependenciesReport(Map<String, ViewTableDependencies> dependencies) throws IOException {
        String outputPath = "SQL_info/view_dependencies_report.txt";
        java.nio.file.Path filePath = java.nio.file.Paths.get(outputPath);

        java.nio.file.Path parentDir = filePath.getParent();
        if (parentDir != null && !java.nio.file.Files.exists(parentDir)) {
            java.nio.file.Files.createDirectories(parentDir);
        }

        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(outputPath), "UTF-8"))) {

            writer.println("=".repeat(100));
            writer.println("=== ОТЧЕТ ПО ЗАВИСИМОСТЯМ ТАБЛИЦ ОТ ВЬЮХ ===");
            writer.println("Дата создания: " + new Date());
            writer.println("Всего проанализировано вьюх: " + dependencies.size());
            writer.println("=".repeat(100));
            writer.println();

            int count = 0;
            for (Map.Entry<String, ViewTableDependencies> entry : dependencies.entrySet()) {
                ViewTableDependencies dep = entry.getValue();
                count++;

                writer.println("- " + dep.getViewName() + ":");

                if (dep.isExistsInOracle()) {
                    writer.println("    OracleSQL:");
                    if (dep.getOracleTables().isEmpty()) {
                        writer.println("        (таблицы не найдены)");
                    } else {
                        for (String table : dep.getOracleTables()) {
                            writer.println("        " + table);
                        }
                    }
                } else {
                    writer.println("    OracleSQL: (вьюха не найдена)");
                    if (dep.getOracleError() != null) {
                        writer.println("        Ошибка: " + dep.getOracleError());
                    }
                }

                if (dep.isExistsInPostgres()) {
                    writer.println("    PostgreSQL:");
                    if (dep.getPostgresTables().isEmpty()) {
                        writer.println("        (таблицы не найдены)");
                    } else {
                        for (String table : dep.getPostgresTables()) {
                            writer.println("        " + table);
                        }
                    }
                } else {
                    writer.println("    PostgreSQL: (вьюха не найдена)");
                    if (dep.getPostgresError() != null) {
                        writer.println("        Ошибка: " + dep.getPostgresError());
                    }
                }

                writer.println("    ---");

                TableViewInfo viewInfo = dep.getOriginalViewInfo();
                if (viewInfo != null) {
                    writer.println("    Используется в формах: " + viewInfo.getUsedInForms().size());
                    writer.println("    Формы:");
                    for (String form : viewInfo.getUsedInForms()) {
                        writer.println("         " + form + ";");
                    }
                } else {
                    writer.println("    Используется в формах: 0");
                    writer.println("    Формы:");
                    writer.println("         (не найдено)");
                }

                writer.println();

                if (count < dependencies.size()) {
                    writer.println("-".repeat(80));
                    writer.println();
                }
            }

            writer.println("=".repeat(100));
            writer.println("=== КОНЕЦ ОТЧЕТА ПО ЗАВИСИМОСТЯМ ===");
            writer.println("=".repeat(100));
        }

        if (progressCallback != null) {
            progressCallback.onLog("  Создан: " + outputPath);
        }
    }

    public static void setOracleConfig(String url, String user, String password) {
        ORACLE_URL = url;
        ORACLE_USER = user;
        ORACLE_PASSWORD = password;
    }

    public static void setPostgresConfig(String url, String user, String password) {
        POSTGRES_URL = url;
        POSTGRES_USER = user;
        POSTGRES_PASSWORD = password;
    }
}
