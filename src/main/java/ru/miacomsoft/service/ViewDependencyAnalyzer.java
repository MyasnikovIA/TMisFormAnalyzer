package ru.miacomsoft.service;

import ru.miacomsoft.model.TableViewInfo;
import ru.miacomsoft.model.ViewTableDependencies;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

public class ViewDependencyAnalyzer {

    // Конфигурация Oracle
    private static String ORACLE_URL = "jdbc:oracle:thin:@192.168.241.141:1521/med2dev";
    private static String ORACLE_USER = "dev";
    private static String ORACLE_PASSWORD = "def";
    private static String ORACLE_URL_FORMAT = "auto"; // auto, sid, service, tns

    // Конфигурация PostgreSQL
    private static String POSTGRES_URL = "jdbc:postgresql://192.168.241.137:5432/med2dev";
    private static String POSTGRES_USER = "postgres";
    private static String POSTGRES_PASSWORD = "postgres";

    // Кэширование
    private static final Map<String, ViewTableDependencies> viewCache = new ConcurrentHashMap<>();
    private static final Map<String, String> oracleDDLCache = new ConcurrentHashMap<>();
    private static final Map<String, String> postgresDDLCache = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> parsedTablesCache = new ConcurrentHashMap<>();
    private static int cacheHits = 0;
    private static int cacheMisses = 0;

    // Флаги для управления процессом
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicBoolean isCancelled = new AtomicBoolean(false);
    private java.util.function.BooleanSupplier externalStopCondition = () -> false;

    private ProgressCallback progressCallback;

    public interface ProgressCallback {
        void onProgress(int current, int total, String viewName, int oracleTables, int postgresTables);
        void onLog(String message);
        void onCancelled();
    }

    static {
        // Определяем ОС и настраиваем формат URL
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Для Windows используем формат с двоеточием (SID)
            ORACLE_URL_FORMAT = "sid";
            System.out.println("Обнаружена Windows, используем формат Oracle URL с SID");
        } else {
            // Для Linux используем формат с слешем (Service Name)
            ORACLE_URL_FORMAT = "service";
            System.out.println("Обнаружен Linux, используем формат Oracle URL с Service Name");
        }
    }

    /**
     * Получить правильный URL для Oracle в зависимости от ОС
     */
    public static String getOracleConnectionUrl() {
        String host = "192.168.241.141";
        String port = "1521";
        String database = "med2dev";

        // Извлекаем параметры из текущего URL если он уже установлен
        String currentUrl = ORACLE_URL;
        if (currentUrl != null && !currentUrl.isEmpty()) {
            try {
                if (currentUrl.contains("@")) {
                    String afterAt = currentUrl.substring(currentUrl.indexOf("@") + 1);
                    if (afterAt.contains(":")) {
                        String[] parts = afterAt.split(":");
                        if (parts.length >= 2) {
                            host = parts[0];
                            String portAndDb = parts[1];
                            if (portAndDb.contains("/")) {
                                port = portAndDb.substring(0, portAndDb.indexOf("/"));
                                database = portAndDb.substring(portAndDb.indexOf("/") + 1);
                            } else if (portAndDb.contains(":")) {
                                port = portAndDb.substring(0, portAndDb.indexOf(":"));
                                database = portAndDb.substring(portAndDb.indexOf(":") + 1);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Используем значения по умолчанию
            }
        }

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows: формат с двоеточием (SID)
            return String.format("jdbc:oracle:thin:@%s:%s:%s", host, port, database);
        } else {
            // Linux: формат с слешем (Service Name)
            return String.format("jdbc:oracle:thin:@%s:%s/%s", host, port, database);
        }
    }

    /**
     * Попробовать все возможные форматы подключения к Oracle
     */
    private static String[] getAllOracleUrlFormats() {
        String host = "192.168.241.141";
        String port = "1521";
        String database = "med2dev";

        return new String[]{
                // Формат 1: Service Name (Linux)
                String.format("jdbc:oracle:thin:@%s:%s/%s", host, port, database),
                // Формат 2: SID (Windows)
                String.format("jdbc:oracle:thin:@%s:%s:%s", host, port, database),
                // Формат 3: Полный TNS формат
                String.format("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=%s)(PORT=%s))(CONNECT_DATA=(SERVICE_NAME=%s)))",
                        host, port, database),
                // Формат 4: TNS формат с SID
                String.format("jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=%s)(PORT=%s))(CONNECT_DATA=(SID=%s)))",
                        host, port, database)
        };
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void setExternalStopCondition(java.util.function.BooleanSupplier condition) {
        this.externalStopCondition = condition;
    }

    public void setPaused(boolean paused) {
        boolean wasPaused = isPaused.get();
        isPaused.set(paused);
        if (progressCallback != null && wasPaused != paused) {
            progressCallback.onLog(paused ? "  >>> ПАУЗА <<<" : "  >>> ВОЗОБНОВЛЕНО <<<");
        }
    }

    public void setCancelled(boolean cancelled) {
        isCancelled.set(cancelled);
        if (cancelled && progressCallback != null) {
            progressCallback.onLog("  >>> ОСТАНОВЛЕНИЕ <<<");
            progressCallback.onCancelled();
        }
    }

    public boolean isCancelled() {
        return isCancelled.get() || externalStopCondition.getAsBoolean();
    }

    public static void clearCache() {
        viewCache.clear();
        oracleDDLCache.clear();
        postgresDDLCache.clear();
        parsedTablesCache.clear();
        cacheHits = 0;
        cacheMisses = 0;
        System.out.println("Кэш вьюх очищен");
    }

    public static String getCacheStats() {
        return String.format("Кэш: просмотров=%d, попаданий=%d, промахов=%d, процент попаданий=%.1f%%",
                cacheHits + cacheMisses, cacheHits, cacheMisses,
                (cacheHits + cacheMisses) > 0 ? (cacheHits * 100.0 / (cacheHits + cacheMisses)) : 0);
    }

    public static int getCacheSize() {
        return viewCache.size();
    }

    public static boolean isInCache(String viewName) {
        return viewCache.containsKey(viewName.toUpperCase());
    }

    /**
     * Проверка подключения к Oracle с автоматическим определением формата
     */
    public static boolean testOracleConnection() {
        String[] urls = getAllOracleUrlFormats();
        String user = ORACLE_USER;
        String password = ORACLE_PASSWORD;

        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
        props.setProperty("oracle.jdbc.ReadTimeout", "30000");

        for (String url : urls) {
            try {
                Class.forName("oracle.jdbc.OracleDriver");
                try (Connection conn = DriverManager.getConnection(url, props)) {
                    // Успешное подключение - сохраняем правильный URL
                    ORACLE_URL = url;
                    System.out.println("Oracle подключение успешно! Использован URL: " + url);
                    return true;
                }
            } catch (Exception e) {
                // Пробуем следующий формат
                System.out.println("Не удалось подключиться по URL: " + url + " - " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Проверка подключения к PostgreSQL
     */
    public static boolean testPostgresConnection() {
        try {
            Class.forName("org.postgresql.Driver");
            DriverManager.setLoginTimeout(5);
            try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD)) {
                System.out.println("PostgreSQL подключение успешно!");
                return true;
            }
        } catch (Exception e) {
            System.err.println("PostgreSQL ошибка подключения: " + e.getMessage());
            return false;
        }
    }

    private void checkPauseAndCancel() throws InterruptedException {
        if (isCancelled()) {
            throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН");
        }
        while (isPaused.get() && !isCancelled()) {
            Thread.sleep(200);
        }
        if (isCancelled()) {
            throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН");
        }
    }

    private static final Pattern TABLE_FROM_JOIN_PATTERN = Pattern.compile(
            "\\b(FROM|JOIN)\\s+([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)?)\\b",
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
     * Анализ всех вьюх с поддержкой кэширования
     */
    public Map<String, ViewTableDependencies> analyzeAllViews(Map<String, TableViewInfo> viewsInfo) throws InterruptedException {
        Map<String, ViewTableDependencies> result = new LinkedHashMap<>();

        if (progressCallback != null) {
            progressCallback.onLog("\n=== АНАЛИЗ ЗАВИСИМОСТЕЙ ВЬЮХ ===");
            progressCallback.onLog("Всего вьюх для анализа: " + viewsInfo.size());

            int cachedCount = 0;
            for (String viewName : viewsInfo.keySet()) {
                if (viewCache.containsKey(viewName.toUpperCase())) {
                    cachedCount++;
                }
            }
            if (cachedCount > 0) {
                progressCallback.onLog("Из них уже в кэше: " + cachedCount);
                progressCallback.onLog(getCacheStats());
            }
        }

        int total = viewsInfo.size();
        int processed = 0;
        int fromCache = 0;
        int fromDB = 0;

        for (Map.Entry<String, TableViewInfo> entry : viewsInfo.entrySet()) {
            if (isCancelled()) {
                if (progressCallback != null) {
                    progressCallback.onLog("  Анализ прерван пользователем на вьюхе " + entry.getKey());
                    progressCallback.onCancelled();
                }
                throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН ПОЛЬЗОВАТЕЛЕМ");
            }

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

            if (isCancelled()) {
                throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН ПОЛЬЗОВАТЕЛЕМ");
            }

            ViewTableDependencies dependencies = getFromCache(viewName);
            boolean fromCacheFlag = (dependencies != null);

            if (dependencies == null) {
                dependencies = analyzeViewWithCache(viewName);
                fromDB++;
                putInCache(viewName, dependencies);
            } else {
                fromCache++;
            }

            if (isCancelled()) {
                throw new InterruptedException("АНАЛИЗ ОСТАНОВЛЕН ПОЛЬЗОВАТЕЛЕМ");
            }

            dependencies.setOriginalViewInfo(viewInfo);
            result.put(viewName, dependencies);

            int oracleCount = dependencies.getOracleTables().size();
            int postgresCount = dependencies.getPostgresTables().size();

            String cacheInfo = fromCacheFlag ? " (из кэша)" : " (из БД)";
            String status;
            if (dependencies.isExistsInOracle() || dependencies.isExistsInPostgres()) {
                status = "OK (Oracle: " + oracleCount + ", PG: " + postgresCount + ")" + cacheInfo;
            } else {
                status = "НЕ НАЙДЕНА" + cacheInfo;
            }

            String logMessage = "  [" + processed + "/" + total + "] " + viewName + " ... " + status;
            if (progressCallback != null) {
                progressCallback.onLog(logMessage);
                progressCallback.onProgress(processed, total, viewName, oracleCount, postgresCount);
            }
        }

        String completionMessage = "Анализ завершен. Обработано вьюх: " + processed +
                " (из кэша: " + fromCache + ", из БД: " + fromDB + ")";
        if (progressCallback != null) {
            progressCallback.onLog(completionMessage);
            progressCallback.onLog(getCacheStats());
            progressCallback.onLog("Размер кэша: " + viewCache.size());
        }

        return result;
    }

    private ViewTableDependencies getFromCache(String viewName) {
        String key = viewName.toUpperCase();
        ViewTableDependencies cached = viewCache.get(key);

        if (cached != null) {
            cacheHits++;
            if (progressCallback != null) {
                progressCallback.onLog("    Кэш HIT для " + viewName);
            }
        } else {
            cacheMisses++;
        }

        return cached;
    }

    private void putInCache(String viewName, ViewTableDependencies dependencies) {
        String key = viewName.toUpperCase();
        viewCache.put(key, dependencies);
    }

    private ViewTableDependencies analyzeViewWithCache(String viewName) {
        ViewTableDependencies dependencies = new ViewTableDependencies(viewName);

        if (isCancelled()) {
            dependencies.setExistsInOracle(false);
            dependencies.setExistsInPostgres(false);
            dependencies.setOracleError("Остановлено пользователем");
            dependencies.setPostgresError("Остановлено пользователем");
            return dependencies;
        }

        analyzeInOracleWithCache(viewName, dependencies);

        if (isCancelled()) {
            return dependencies;
        }

        analyzeInPostgresWithCache(viewName, dependencies);

        return dependencies;
    }

    private void analyzeInOracleWithCache(String viewName, ViewTableDependencies dependencies) {
        if (isCancelled()) {
            dependencies.setExistsInOracle(false);
            dependencies.setOracleError("Остановлено пользователем");
            return;
        }

        String key = viewName.toUpperCase();
        String ddl = oracleDDLCache.get(key);

        if (ddl == null) {
            if (progressCallback != null) {
                progressCallback.onLog("    Загрузка Oracle DDL для " + viewName + " ...");
            }
            ddl = getOracleViewDDL(viewName);
            if (ddl != null && !isCancelled()) {
                oracleDDLCache.put(key, ddl);
                if (progressCallback != null) {
                    progressCallback.onLog("    Oracle DDL для " + viewName + " закэширован");
                }
            }
        } else {
            if (progressCallback != null) {
                progressCallback.onLog("    Oracle DDL для " + viewName + " взят из кэша");
            }
        }

        if (isCancelled()) {
            dependencies.setExistsInOracle(false);
            dependencies.setOracleError("Остановлено пользователем");
            return;
        }

        if (ddl == null) {
            dependencies.setExistsInOracle(false);
            dependencies.setOracleError("Вьюха не найдена в Oracle");
            return;
        }

        dependencies.setExistsInOracle(true);

        // Пытаемся получить распарсенные таблицы из кэша
        Set<String> tables = getParsedTablesFromCache(key + "_ORACLE");
        if (tables == null) {
            tables = extractTablesFromDDL(ddl);
            putParsedTablesToCache(key + "_ORACLE", tables);
        }

        dependencies.addAllOracleTables(tables);
    }

    private void analyzeInPostgresWithCache(String viewName, ViewTableDependencies dependencies) {
        if (isCancelled()) {
            dependencies.setExistsInPostgres(false);
            dependencies.setPostgresError("Остановлено пользователем");
            return;
        }

        String key = viewName.toLowerCase();
        String ddl = postgresDDLCache.get(key);

        if (ddl == null) {
            if (progressCallback != null) {
                progressCallback.onLog("    Загрузка PostgreSQL DDL для " + viewName + " ...");
            }
            ddl = getPostgresViewDDL(viewName);
            if (ddl != null && !isCancelled()) {
                postgresDDLCache.put(key, ddl);
                if (progressCallback != null) {
                    progressCallback.onLog("    PostgreSQL DDL для " + viewName + " закэширован");
                }
            }
        } else {
            if (progressCallback != null) {
                progressCallback.onLog("    PostgreSQL DDL для " + viewName + " взят из кэша");
            }
        }

        if (isCancelled()) {
            dependencies.setExistsInPostgres(false);
            dependencies.setPostgresError("Остановлено пользователем");
            return;
        }

        if (ddl == null) {
            dependencies.setExistsInPostgres(false);
            dependencies.setPostgresError("Вьюха не найдена в PostgreSQL");
            return;
        }

        dependencies.setExistsInPostgres(true);

        // Пытаемся получить распарсенные таблицы из кэша
        Set<String> tables = getParsedTablesFromCache(key + "_POSTGRES");
        if (tables == null) {
            tables = extractTablesFromDDL(ddl);
            putParsedTablesToCache(key + "_POSTGRES", tables);
        }

        dependencies.addAllPostgresTables(tables);
    }

    private Set<String> getParsedTablesFromCache(String key) {
        Set<String> cached = parsedTablesCache.get(key);
        if (cached != null && progressCallback != null) {
            progressCallback.onLog("    Распарсенные таблицы для " + key + " взяты из кэша");
        }
        return cached;
    }

    private void putParsedTablesToCache(String key, Set<String> tables) {
        parsedTablesCache.put(key, tables);
    }

    public ViewTableDependencies analyzeView(String viewName) {
        ViewTableDependencies cached = getFromCache(viewName);
        if (cached != null) {
            return cached;
        }

        ViewTableDependencies dependencies = analyzeViewWithCache(viewName);
        putInCache(viewName, dependencies);

        return dependencies;
    }

    private String getOracleViewDDL(String viewName) {
        if (isCancelled()) {
            return null;
        }

        // Пробуем разные форматы URL для Oracle
        String[] urls = getAllOracleUrlFormats();
        String user = ORACLE_USER;
        String password = ORACLE_PASSWORD;

        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            props.setProperty("oracle.net.CONNECT_TIMEOUT", "30000");
            props.setProperty("oracle.jdbc.ReadTimeout", "60000");
            props.setProperty("oracle.net.disableOob", "true");
        } else {
            props.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
            props.setProperty("oracle.jdbc.ReadTimeout", "30000");
        }

        String sql = "SELECT TEXT FROM ALL_VIEWS WHERE VIEW_NAME = ?";

        for (String url : urls) {
            if (isCancelled()) {
                return null;
            }

            final String currentUrl = url;
            final String[] result = {null};
            final Exception[] exception = {null};

            Thread queryThread = new Thread(() -> {
                try {
                    Class.forName("oracle.jdbc.OracleDriver");
                    try (Connection conn = DriverManager.getConnection(currentUrl, props);
                         PreparedStatement pstmt = conn.prepareStatement(sql)) {

                        if (isCancelled()) {
                            return;
                        }

                        pstmt.setString(1, viewName.toUpperCase());
                        pstmt.setQueryTimeout(30);
                        ResultSet rs = pstmt.executeQuery();

                        if (rs.next() && !isCancelled()) {
                            result[0] = rs.getString("TEXT");
                            // Если подключились успешно, сохраняем правильный URL
                            ORACLE_URL = currentUrl;
                        }
                    }
                } catch (Exception e) {
                    exception[0] = e;
                }
            });

            int timeout = os.contains("win") ? 30000 : 15000;
            queryThread.start();

            try {
                queryThread.join(timeout);
                if (queryThread.isAlive()) {
                    queryThread.interrupt();
                    if (progressCallback != null) {
                        progressCallback.onLog("    Oracle запрос для " + viewName + " прерван по таймауту");
                    }
                    continue; // Пробуем следующий URL
                }
            } catch (InterruptedException e) {
                queryThread.interrupt();
                Thread.currentThread().interrupt();
                return null;
            }

            if (result[0] != null) {
                if (progressCallback != null) {
                    progressCallback.onLog("    Oracle DDL для " + viewName + " получен (URL: " + currentUrl + ")");
                }
                return result[0];
            }
        }

        if (progressCallback != null && !isCancelled()) {
            progressCallback.onLog("    Вьюха " + viewName + " не найдена в Oracle ни по одному URL");
        }
        return null;
    }

    private String getPostgresViewDDL(String viewName) {
        if (isCancelled()) {
            return null;
        }

        final String[] result = {null};
        final Exception[] exception = {null};

        Thread queryThread = new Thread(() -> {
            try {
                Class.forName("org.postgresql.Driver");
                DriverManager.setLoginTimeout(10);

                try (Connection conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD)) {

                    if (isCancelled()) {
                        return;
                    }

                    String getOidSql = "SELECT oid FROM pg_class WHERE relname = ? AND relkind = 'v'";
                    try (PreparedStatement oidStmt = conn.prepareStatement(getOidSql)) {
                        oidStmt.setString(1, viewName.toLowerCase());
                        oidStmt.setQueryTimeout(30);
                        ResultSet oidRs = oidStmt.executeQuery();

                        if (oidRs.next() && !isCancelled()) {
                            int oid = oidRs.getInt("oid");

                            String sql = "SELECT pg_get_viewdef(?, true) as viewdef";
                            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                                pstmt.setInt(1, oid);
                                pstmt.setQueryTimeout(30);
                                ResultSet rs = pstmt.executeQuery();

                                if (rs.next() && !isCancelled()) {
                                    result[0] = rs.getString("viewdef");
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                exception[0] = e;
            }
        });

        int timeout = System.getProperty("os.name").toLowerCase().contains("win") ? 30000 : 15000;
        queryThread.start();

        try {
            queryThread.join(timeout);
            if (queryThread.isAlive()) {
                queryThread.interrupt();
                if (progressCallback != null) {
                    progressCallback.onLog("    PostgreSQL запрос для " + viewName + " прерван по таймауту");
                }
                return null;
            }
        } catch (InterruptedException e) {
            queryThread.interrupt();
            Thread.currentThread().interrupt();
            return null;
        }

        if (exception[0] != null && progressCallback != null && !isCancelled()) {
            progressCallback.onLog("    PostgreSQL ошибка: " + exception[0].getMessage());
        }

        return result[0];
    }

    private Set<String> extractTablesFromDDL(String ddl) {
        Set<String> tables = new LinkedHashSet<>();

        if (ddl == null || ddl.isEmpty()) {
            return tables;
        }

        String cleanDdl = ddl.replaceAll("\\s+", " ").toUpperCase();

        Set<String> potentialTables = new LinkedHashSet<>();
        Matcher matcher = TABLE_FROM_JOIN_PATTERN.matcher(cleanDdl);
        while (matcher.find()) {
            String tableName = matcher.group(2);
            if (tableName.contains(".")) {
                tableName = tableName.substring(tableName.lastIndexOf(".") + 1);
            }

            if (!SQL_KEYWORDS.contains(tableName)) {
                potentialTables.add(tableName);
            }
        }

        Set<String> aliases = new LinkedHashSet<>();

        Pattern explicitAliasPattern = Pattern.compile("\\b(?:FROM|JOIN)\\s+[A-Z0-9_]+\\s+AS\\s+([A-Z0-9_]+)\\b");
        Matcher explicitMatcher = explicitAliasPattern.matcher(cleanDdl);
        while (explicitMatcher.find()) {
            String alias = explicitMatcher.group(1);
            if (!SQL_KEYWORDS.contains(alias) && alias.length() > 1) {
                aliases.add(alias);
            }
        }

        Pattern implicitAliasPattern = Pattern.compile("\\b(?:FROM|JOIN)\\s+[A-Z0-9_]+\\s+([A-Z0-9_]+)\\b");
        Matcher implicitMatcher = implicitAliasPattern.matcher(cleanDdl);
        while (implicitMatcher.find()) {
            String alias = implicitMatcher.group(1);
            if (!SQL_KEYWORDS.contains(alias) && alias.length() > 2 && !alias.matches("^[A-Z]{1,2}$")) {
                aliases.add(alias);
            }
        }

        Pattern subqueryAliasPattern = Pattern.compile("\\)\\s+([A-Z0-9_]+)\\s+(?:WHERE|LEFT|RIGHT|JOIN|ON|$)");
        Matcher subqueryMatcher = subqueryAliasPattern.matcher(cleanDdl);
        while (subqueryMatcher.find()) {
            String alias = subqueryMatcher.group(1);
            if (!SQL_KEYWORDS.contains(alias) && alias.length() > 1) {
                aliases.add(alias);
            }
        }

        for (String tableName : potentialTables) {
            if (aliases.contains(tableName)) {
                continue;
            }

            if (tableName.matches("^[A-Z]{1,2}$")) {
                continue;
            }

            if (tableName.startsWith("D_") || tableName.contains("_")) {
                tables.add(tableName);
            }
        }

        return tables;
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
            writer.println("Размер кэша: " + viewCache.size());
            writer.println("Статистика кэша: " + getCacheStats());
            writer.println("=".repeat(100));
            writer.println();

            int count = 0;
            for (Map.Entry<String, ViewTableDependencies> entry : dependencies.entrySet()) {
                ViewTableDependencies dep = entry.getValue();
                count++;

                boolean fromCache = viewCache.containsKey(dep.getViewName().toUpperCase());
                String cacheMarker = fromCache ? " [ИЗ КЭША]" : "";

                writer.println("- " + dep.getViewName() + ":" + cacheMarker);

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
            progressCallback.onLog("  " + getCacheStats());
        }
    }

    public static void setOracleConfig(String url, String user, String password) {
        ORACLE_URL = url;
        ORACLE_USER = user;
        ORACLE_PASSWORD = password;
        clearCache();
    }

    public static void setPostgresConfig(String url, String user, String password) {
        POSTGRES_URL = url;
        POSTGRES_USER = user;
        POSTGRES_PASSWORD = password;
        clearCache();
    }

    public ViewTableDependencies analyzeViewPublic(String viewName) {
        return analyzeView(viewName);
    }
}
