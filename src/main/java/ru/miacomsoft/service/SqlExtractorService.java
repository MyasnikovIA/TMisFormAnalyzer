package ru.miacomsoft.service;

import ru.miacomsoft.model.SqlInfo;

import java.util.*;
import java.util.regex.*;

/**
 * Сервис для извлечения SQL запросов из XML содержимого форм
 */
public class SqlExtractorService {

    // Паттерны для поиска DataSet (M2 и D3) - захватываем весь компонент для извлечения имени
    private static final Pattern M2_DATASET_PATTERN = Pattern.compile(
            "<component\\s+cmptype\\s*=\\s*[\"']DataSet[\"'][^>]*name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>" +
                    ".*?" +
                    "<!\\[CDATA\\[(.*?)\\]\\]>" +
                    ".*?" +
                    "</component>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private static final Pattern D3_DATASET_PATTERN = Pattern.compile(
            "<cmp[dD]ata[Ss]et\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</cmp[dD]ata[Ss]et>",
            Pattern.DOTALL
    );



    // Паттерн для DataSet с SQL прямо в теле (без CDATA)
    private static final Pattern M2_DATASET_DIRECT_PATTERN = Pattern.compile(
            "<component\\s+cmptype\\s*=\\s*[\"']DataSet[\"'][^>]*name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>" +
                    "\\s*" +
                    "((?!<component).*?)" +
                    "\\s*" +
                    "(?:<component|</component>)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Паттерн для D3 DataSet с SQL прямо в теле
    private static final Pattern D3_DATASET_DIRECT_PATTERN = Pattern.compile(
            "<cmpDataSet\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>" +
                    "\\s*" +
                    "((?!<cmpDataSet).*?)" +
                    "\\s*" +
                    "(?:<cmpDataSet|</cmpDataSet>)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Паттерны для поиска Action (M2 и D3)
    private static final Pattern M2_ACTION_PATTERN = Pattern.compile(
            "<component\\s+cmptype\\s*=\\s*[\"']Action[\"'][^>]*name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>" +
                    ".*?" +
                    "<!\\[CDATA\\[(.*?)\\]\\]>" +
                    ".*?" +
                    "</component>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private static final Pattern D3_ACTION_PATTERN = Pattern.compile(
            "<cmpAction\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>" +
                    ".*?" +
                    "<!\\[CDATA\\[(.*?)\\]\\]>" +
                    ".*?" +
                    "</cmpAction>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );


    // Паттерн для извлечения PR_CODE из D_V_USERPROCS
    private static final Pattern USERPROCS_PATTERN = Pattern.compile(
            "D_V_USERPROCS\\s+[^w]*where\\s+t\\.PR_CODE\\s*=\\s*'([^']+)'",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Альтернативный паттерн для разных форматов
    private static final Pattern USERPROCS_PATTERN2 = Pattern.compile(
            "D_V_USERPROCS\\s+.*?PR_CODE\\s*=\\s*'([^']+)'",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );



    // Паттерны для извлечения таблиц и вьюх (только D_ и D_V_, исключая D_PKG_)
    private static final Pattern TABLE_VIEW_PATTERN = Pattern.compile(
            "\\b(D_V_[A-Z][A-Z0-9_]*|D_[A-Z][A-Z0-9_]*)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Паттерны для извлечения пакетов (D_PKG_...)
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "\\b(D_PKG_[A-Z][A-Z0-9_]*(?:\\.[A-Z][A-Z0-9_]*)?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Паттерн для извлечения standalone функций
    private static final Pattern STANDALONE_FN_PATTERN = Pattern.compile(
            "\\b(D_PKG_FN_[A-Z][A-Z0-9_]*)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );


    // Паттерн для извлечения системных опций (констант) из D_PKG_CONSTANTS
    // D_PKG_CONSTANTS.SEARCH_STR('CodeDepOtkaz',:LPU)
    // D_PKG_CONSTANTS.SEARCH_NUM('CloseContract', :pnLPU)
    private static final Pattern SYSTEM_OPTIONS_PATTERN = Pattern.compile(
            "D_PKG_CONSTANTS\\.SEARCH_(?:STR|NUM)\\s*\\(\\s*'([^']+)'",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Дополнительный паттерн для других форматов
    private static final Pattern SYSTEM_OPTIONS_PATTERN2 = Pattern.compile(
            "D_PKG_OPTIONS\\.GET\\s*\\(\\s*'([^']+)'",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Паттерн для всех D_ объектов (кроме D_PKG_ и D_V_)
    private static final Pattern UNKNOWN_D_PATTERN = Pattern.compile(
            "\\bD_[A-Z][A-Z0-9_]*(?:_[A-Z0-9]+)*\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Паттерн для D_STRAGG, D_P_EXC и подобных
    private static final Pattern UNKNOWN_SPECIAL_PATTERN = Pattern.compile(
            "\\b(D_STRAGG|D_P_EXC|D_[A-Z]{2,}[0-9]*)\\b",
            Pattern.CASE_INSENSITIVE
    );



    // SQL ключевые слова для исключения
    private static final Set<String> SQL_KEYWORDS = new HashSet<>(Arrays.asList(
            "SYSDATE", "to_date", "to_char", "trunc", "last_day", "months_between",
            "to_number", "to_clob", "to_nchar", "length", "substr", "instr", "replace",
            "regexp_substr", "regexp_replace", "round", "ceil", "floor", "mod", "abs",
            "nvl", "nvl2", "coalesce", "nullif", "case", "decode", "sum", "count", "avg",
            "max", "min", "listagg", "row_number", "dense_rank", "rank", "lead", "lag",
            "SELECT", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON",
            "AND", "OR", "NOT", "IN", "EXISTS", "BETWEEN", "LIKE", "IS", "NULL", "AS"
    ));

    /**
     * Извлечь все SQL запросы из содержимого формы
     */
    public List<SqlInfo> extractAllSqlQueries(String content, String sourcePath, String baseFormPath) {
        List<SqlInfo> queries = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return queries;
        }

        System.out.println("=== ИЗВЛЕЧЕНИЕ SQL ===");
        System.out.println("Source: " + sourcePath);

        // Используем универсальный метод для всех компонентов
        queries.addAll(extractAllComponents(content, sourcePath, baseFormPath));

        // Выводим статистику по константам
        int totalConstants = 0;
        for (SqlInfo sql : queries) {
            totalConstants += sql.getConstants().size();
        }
        System.out.println("Извлечено SQL запросов: " + queries.size() + ", Констант: " + totalConstants);

        return queries;
    }

    /**
     * Удалить SQL комментарии из текста
     * - Многострочные /* ... *​/
     * - Однострочные -- ...
     */
    private String removeSqlComments(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        String result = sql;

        // 1. Удаляем многострочные комментарии /* ... */
        // Используем DOTALL флаг для обработки переносов строк
        Pattern multiLineComment = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
        result = multiLineComment.matcher(result).replaceAll("");

        // 2. Удаляем однострочные комментарии -- до конца строки
        // Учитываем, что -- может быть внутри строки (например, в 'text--text')
        // Удаляем только -- в начале строки или после пробелов, и до конца строки
        result = result.replaceAll("(?m)^\\s*--[^\\n]*", "");
        result = result.replaceAll("(?m)\\s+--[^\\n]*", "");

        // 3. Очищаем пустые строки
        result = result.replaceAll("\\n\\s*\\n", "\n");

        return result.trim();
    }
    /**
     * Удалить все типы комментариев из текста
     */
    private String removeAllComments(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // 1. Удаляем многострочные комментарии /* ... */
        Pattern multiLineComment = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
        result = multiLineComment.matcher(result).replaceAll("");

        // 2. Удаляем однострочные комментарии -- (SQL)
        result = result.replaceAll("(?m)^\\s*--[^\\n]*", "");
        result = result.replaceAll("(?m)\\s+--[^\\n]*", "");

        // 3. Удаляем однострочные комментарии // (JavaScript)
        result = result.replaceAll("(?m)^\\s*//[^\\n]*", "");
        result = result.replaceAll("(?m)\\s+//[^\\n]*", "");

        // 4. Очищаем пустые строки
        result = result.replaceAll("\\n\\s*\\n", "\n");

        return result.trim();
    }

    /**
     * Извлечь SQL из паттерна - сохраняем полный XML компонента
     */
    // Найдите метод extractSqlFromPattern и добавьте вызов extractUnknownObjects:

    private List<SqlInfo> extractSqlFromPattern(String content, String sourcePath,
                                                String baseFormPath, Pattern pattern,
                                                String componentTypeName) {
        List<SqlInfo> queries = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String componentName = matcher.group(1);
            String sqlContent = matcher.group(2);

            if (sqlContent == null || sqlContent.trim().isEmpty()) {
                continue;
            }

            if (!isSqlContent(sqlContent)) {
                continue;
            }

            SqlInfo sqlInfo = new SqlInfo();
            sqlInfo.setSourceType(componentTypeName);
            sqlInfo.setSourcePath(sourcePath);
            sqlInfo.setBaseFormPath(baseFormPath);
            sqlInfo.setComponentName(componentName);

            String fullComponentXml = extractFullComponentXml(content, matcher.start(), componentTypeName);
            sqlInfo.setSqlContent(fullComponentXml != null ? fullComponentXml : sqlContent);
            sqlInfo.setCleanSql(cleanSql(sqlContent));

            extractTablesViews(sqlInfo);
            extractPackagesFunctions(sqlInfo);
            extractUserProcedures(sqlInfo);
            extractSystemOptions(sqlInfo);
            extractUnknownObjects(sqlInfo);  // ДОБАВИТЬ ЭТУ СТРОКУ

            queries.add(sqlInfo);
        }

        return queries;
    }

    /**
     * Извлечь полный XML компонента (открывающий и закрывающий тэг)
     */
    private String extractFullComponentXml(String content, int matchStart, String componentType) {
        // Ищем начало тэга - ищем предыдущий '<' который является началом тэга
        int tagStart = matchStart;
        while (tagStart > 0 && content.charAt(tagStart) != '<') {
            tagStart--;
        }

        // Определяем тип закрывающего тэга
        String closeTag;
        if ("cmpDataSet".equalsIgnoreCase(componentType)) {
            closeTag = "</cmpDataSet>";
        } else if ("component".equalsIgnoreCase(componentType)) {
            closeTag = "</component>";
        } else {
            // По умолчанию ищем закрывающий тэг для того же имени
            closeTag = "</" + componentType + ">";
        }

        // Ищем закрывающий тэг
        int tagEnd = content.indexOf(closeTag, matchStart);
        if (tagEnd < 0) return null;
        tagEnd += closeTag.length();

        return content.substring(tagStart, tagEnd);
    }

    /**
     * Проверка, что содержимое является SQL запросом
     * (отличаем от PHP или JavaScript)
     */
    private boolean isSqlContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String lowerContent = content.toLowerCase().trim();

        // Если начинается с SELECT, INSERT, UPDATE, DELETE, BEGIN, WITH
        if (lowerContent.startsWith("select") ||
                lowerContent.startsWith("insert") ||
                lowerContent.startsWith("update") ||
                lowerContent.startsWith("delete") ||
                lowerContent.startsWith("begin") ||
                lowerContent.startsWith("declare") ||
                lowerContent.startsWith("with") ||
                lowerContent.matches("^\\s*begin\\s+.*end\\s*;?\\s*$")) {
            return true;
        }

        // Содержит SQL ключевые слова
        return lowerContent.contains("select") &&
                (lowerContent.contains("from") || lowerContent.contains("into"));
    }
    /**
     * Очистить SQL от лишних пробелов
     */
    private String cleanSql(String sql) {
        if (sql == null) return "";

        // Заменяем множественные пробелы на один
        String cleaned = sql.replaceAll("\\s+", " ").trim();

        // Ограничиваем длину для вывода
        if (cleaned.length() > 500) {
            cleaned = cleaned.substring(0, 500) + "...";
        }

        return cleaned;
    }



    /**
     * Извлечь таблицы и вьюхи из SQL
     * Только имена с префиксами D_ или D_V_, исключая D_PKG_
     * Все имена приводятся к ВЕРХНЕМУ РЕГИСТРУ
     */
    private void extractTablesViews(SqlInfo sqlInfo) {
        String sql = sqlInfo.getSqlContent();
        if (sql == null) return;

        Matcher matcher = TABLE_VIEW_PATTERN.matcher(sql);
        while (matcher.find()) {
            String name = matcher.group(1).toUpperCase();

            // Исключаем пакеты (D_PKG_...)
            if (name.startsWith("D_PKG_")) {
                continue;
            }

            // Исключаем специальные неизвестные объекты
            if (name.matches("D_STRAGG|D_P_EXC|D_[A-Z]{2,}[0-9]*")) {
                continue; // Они будут обработаны отдельно
            }

            // Проверяем, что это не SQL ключевое слово
            if (!SQL_KEYWORDS.contains(name)) {
                sqlInfo.addTableView(name);
            }
        }
    }
    /**
     * Извлечь неизвестные объекты (требующие разбора аналитиком)
     * Это объекты с префиксом D_, которые не являются:
     * - D_PKG_* (пакеты/функции)
     * - D_V_* (вьюхи)
     */
    // В файле SqlExtractorService.java добавьте новый метод:

    /**
     * Извлечь неизвестные объекты (требующие разбора аналитиком)
     * Это объекты с префиксом D_, которые не являются:
     * - D_PKG_* (пакеты/функции)
     * - D_V_* (вьюхи)
     */
    private void extractUnknownObjects(SqlInfo sqlInfo) {
        String sql = sqlInfo.getSqlContent();
        if (sql == null) return;

        Set<String> unknownObjects = new LinkedHashSet<>();

        // Паттерн для всех D_ объектов (кроме D_PKG_ и D_V_)
        Pattern unknownPattern = Pattern.compile(
                "\\b(D_(?!PKG_|V_)[A-Z][A-Z0-9_]*)\\b",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = unknownPattern.matcher(sql);
        while (matcher.find()) {
            String name = matcher.group(1).toUpperCase();

            // Пропускаем SQL ключевые слова
            if (SQL_KEYWORDS.contains(name)) {
                continue;
            }

            // Пропускаем если уже есть в таблицах/вьюхах или пакетах
            if (sqlInfo.getTablesViews().contains(name) ||
                    sqlInfo.getPackagesFunctions().contains(name)) {
                continue;
            }

            unknownObjects.add(name);
        }

        for (String obj : unknownObjects) {
            sqlInfo.addUnknownObject(obj);
        }
    }


    /**
     * Извлечь пакеты и функции из SQL
     * Имена с префиксом D_PKG_ (пакеты и пакетные функции)
     * и D_PKG_FN_ (standalone функции)
     * Все имена приводятся к ВЕРХНЕМУ РЕГИСТРУ
     */
    private void extractPackagesFunctions(SqlInfo sqlInfo) {
        String sql = sqlInfo.getSqlContent();
        if (sql == null) return;

        // Ищем пакетные функции (D_PKG_...)
        Matcher pkgMatcher = PACKAGE_PATTERN.matcher(sql);
        while (pkgMatcher.find()) {
            String pkgFunc = pkgMatcher.group(1).toUpperCase();  // Приводим к верхнему регистру

            // Проверяем, что это действительно пакет/функция
            if (pkgFunc.startsWith("D_PKG_")) {
                // Разбираем на пакет и функцию
                String[] parts = pkgFunc.split("\\.");
                String funcName = parts.length > 1 ? parts[1] : pkgFunc;

                // Исключаем SQL ключевые слова
                if (!SQL_KEYWORDS.contains(funcName) && !SQL_KEYWORDS.contains(pkgFunc)) {
                    sqlInfo.addPackageFunction(pkgFunc);
                }
            }
        }

        // Ищем standalone функции (D_PKG_FN_...)
        Matcher fnMatcher = STANDALONE_FN_PATTERN.matcher(sql);
        while (fnMatcher.find()) {
            String funcName = fnMatcher.group(1).toUpperCase();  // Приводим к верхнему регистру
            if (!SQL_KEYWORDS.contains(funcName)) {
                sqlInfo.addPackageFunction(funcName);
            }
        }
    }


    /**
     * Извлечь системные опции из D_PKG_OPTIONS.GET
     */
    private void extractSystemOptions(SqlInfo sqlInfo) {
        String sql = sqlInfo.getSqlContent();
        if (sql == null || sql.isEmpty()) {
            return;
        }

        Set<String> options = new LinkedHashSet<>();

        Pattern optionPattern = Pattern.compile(
                "D_PKG_OPTIONS\\.GET\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher optionMatcher = optionPattern.matcher(sql);
        while (optionMatcher.find()) {
            String option = optionMatcher.group(1);
            if (option != null && !option.isEmpty()) {
                options.add(option);
                System.out.println("      Найдена системная опция: " + option);
            }
        }

        for (String option : options) {
            sqlInfo.addSystemOption(option);
        }
    }

    private void processSqlComponent(String componentType, String componentName, String sqlContent,
                                     String fullXml, String sourcePath, String baseFormPath,
                                     List<SqlInfo> queries) {
        if (sqlContent == null || sqlContent.trim().isEmpty()) {
            return;
        }

        // Удаляем комментарии
        String cleanedSql = removeAllComments(sqlContent);

        SqlInfo sqlInfo = new SqlInfo();
        sqlInfo.setSourceType(componentType);
        sqlInfo.setSourcePath(sourcePath);
        sqlInfo.setBaseFormPath(baseFormPath);
        sqlInfo.setComponentName(componentName);

        // Сохраняем полный XML с тегами
        sqlInfo.setSqlContent(fullXml != null ? fullXml : sqlContent);
        sqlInfo.setCleanSql(cleanSql(cleanedSql));

        // Извлекаем все объекты
        extractAllObjectsDirectly(cleanedSql, sqlInfo);
        extractUserProcedures(sqlInfo);
        extractSystemOptions(sqlInfo);

        // Список объектов для принудительного переноса в unknown
        Set<String> forcedToUnknown = new HashSet<>(Arrays.asList(
                "D_PKG_STD.FRM_D",
                "D_PKG_STD.FRM_T",
                "D_PKG_STR_TOOLS.FIO",
                "D_P_EXC",
                "D_C_ID",
                "D_STRAGG_EX",
                "D_PKG_STD.TREF",
                "D_PKG_OPTIONS.GET",
                "D_PKG_OPTION_SPECS.GET",
                "D_TP_STRAGG_REC",
                "D_STRAGG",
                "D_ID",
                "D_LPU",
                "D_CODE",
                "D_DEPARTURE",
                "D_P_HH_NUMB_RES_DEL",
                "D_CL_ID",
                "D_PKG_STD.FRM_DT",
                "D_PKG_CONSTANTS.SEARCH_DATE",
                "D_PKG_CONSTANTS.SEARCH_NUM",
                "D_PKG_CONSTANTS.SEARCH_STR"
        ));

        // Очищаем пакетные функции от объектов из forced списка
        cleanPackageFunctionsByForcedList(sqlInfo, forcedToUnknown);

        queries.add(sqlInfo);
        System.out.println("  Извлечен компонент: " + componentType + " - " + componentName);
    }


    /**
     * Извлечь константы из D_PKG_CONSTANTS.SEARCH_STR, SEARCH_NUM, SEARCH_DATE
     */
    private void extractConstants(SqlInfo sqlInfo) {
        String sql = sqlInfo.getSqlContent();
        if (sql == null || sql.isEmpty()) {
            return;
        }

        Set<String> constants = new LinkedHashSet<>();

        // Паттерн для SEARCH_STR
        Pattern strPattern = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_STR\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_NUM
        Pattern numPattern = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_NUM\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_DATE
        Pattern datePattern = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_DATE\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Извлекаем из SEARCH_STR
        Matcher strMatcher = strPattern.matcher(sql);
        while (strMatcher.find()) {
            String constant = strMatcher.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      Найдена константа (STR): " + constant);
            }
        }

        // Извлекаем из SEARCH_NUM
        Matcher numMatcher = numPattern.matcher(sql);
        while (numMatcher.find()) {
            String constant = numMatcher.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      Найдена константа (NUM): " + constant);
            }
        }

        // Извлекаем из SEARCH_DATE
        Matcher dateMatcher = datePattern.matcher(sql);
        while (dateMatcher.find()) {
            String constant = dateMatcher.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      Найдена константа (DATE): " + constant);
            }
        }

        // Добавляем в SqlInfo
        for (String constant : constants) {
            sqlInfo.addConstant(constant);
        }

        // Логируем общее количество найденных констант
        if (!constants.isEmpty()) {
            System.out.println("    Всего найдено констант в SQL: " + constants.size());
        }
    }

    /**
     * Извлечь пользовательские процедуры из D_V_USERPROCS.PR_CODE
     */
    private void extractUserProcedures(SqlInfo sqlInfo) {
        String sql = sqlInfo.getSqlContent();
        if (sql == null || sql.isEmpty()) {
            return;
        }

        Set<String> userProcs = new LinkedHashSet<>();

        Pattern prCodePattern = Pattern.compile(
                "D_V_USERPROCS\\s+.*?PR_CODE\\s*=\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = prCodePattern.matcher(sql);
        while (matcher.find()) {
            String prCode = matcher.group(1);
            if (prCode != null && !prCode.isEmpty()) {
                userProcs.add(prCode);
                System.out.println("      Найдена пользовательская процедура: " + prCode);
            }
        }

        for (String proc : userProcs) {
            sqlInfo.addUserProcedure(proc);
        }
    }
    private List<SqlInfo> extractAllComponents(String content, String sourcePath, String baseFormPath) {
        List<SqlInfo> queries = new ArrayList<>();

        // 1. Паттерн для D3 компонентов c CDATA (cmpDataSet) - только DataSet
        Pattern d3DatasetPattern = Pattern.compile(
                "<cmpDataSet\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</cmpDataSet>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher d3DatasetMatcher = d3DatasetPattern.matcher(content);
        while (d3DatasetMatcher.find()) {
            String componentName = d3DatasetMatcher.group(1);
            String sqlContent = d3DatasetMatcher.group(2);
            String fullXml = extractFullComponentXml(content, d3DatasetMatcher.start(), "cmpDataSet");
            processSqlComponent("D3 DataSet", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
        }

        // 2. Паттерн для D3 компонентов БЕЗ CDATA (cmpDataSet)
        Pattern d3DatasetDirectPattern = Pattern.compile(
                "<cmpDataSet\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</cmpDataSet>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher d3DatasetDirectMatcher = d3DatasetDirectPattern.matcher(content);
        while (d3DatasetDirectMatcher.find()) {
            String componentName = d3DatasetDirectMatcher.group(1);
            String body = d3DatasetDirectMatcher.group(2);

            if (body != null && !body.contains("CDATA")) {
                String sqlContent = cleanSqlBody(body);
                if (sqlContent != null && !sqlContent.trim().isEmpty() && isSqlContent(sqlContent)) {
                    String fullXml = extractFullComponentXml(content, d3DatasetDirectMatcher.start(), "cmpDataSet");
                    processSqlComponent("D3 DataSet", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
                }
            }
        }

        // 3. Паттерн для M2 DataSet с CDATA
        Pattern m2DatasetWithCDataPattern = Pattern.compile(
                "<component\\s+cmptype\\s*=\\s*[\"']DataSet[\"'][^>]*name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>.*?<!\\[CDATA\\[(.*?)\\]\\]>.*?</component>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher m2DatasetWithCDataMatcher = m2DatasetWithCDataPattern.matcher(content);
        while (m2DatasetWithCDataMatcher.find()) {
            String componentName = m2DatasetWithCDataMatcher.group(1);
            String sqlContent = m2DatasetWithCDataMatcher.group(2);
            String fullXml = extractFullComponentXml(content, m2DatasetWithCDataMatcher.start(), "component");
            processSqlComponent("M2 DataSet", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
        }

        // 4. Паттерн для M2 DataSet БЕЗ CDATA
        Pattern m2DatasetWithoutCDataPattern = Pattern.compile(
                "<component\\s+cmptype\\s*=\\s*[\"']DataSet[\"'][^>]*name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</component>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher m2DatasetWithoutCDataMatcher = m2DatasetWithoutCDataPattern.matcher(content);
        while (m2DatasetWithoutCDataMatcher.find()) {
            String componentName = m2DatasetWithoutCDataMatcher.group(1);
            String body = m2DatasetWithoutCDataMatcher.group(2);

            if (body != null && !body.contains("CDATA")) {
                String sqlContent = cleanSqlBody(body);
                if (sqlContent != null && !sqlContent.trim().isEmpty() && isSqlContent(sqlContent)) {
                    String fullXml = extractFullComponentXml(content, m2DatasetWithoutCDataMatcher.start(), "component");
                    processSqlComponent("M2 DataSet", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
                }
            }
        }

        // 5. Паттерн для M2 Action с CDATA
        Pattern m2ActionWithCDataPattern = Pattern.compile(
                "<component\\s+cmptype\\s*=\\s*[\"']Action[\"'][^>]*name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>.*?<!\\[CDATA\\[(.*?)\\]\\]>.*?</component>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher m2ActionWithCDataMatcher = m2ActionWithCDataPattern.matcher(content);
        while (m2ActionWithCDataMatcher.find()) {
            String componentName = m2ActionWithCDataMatcher.group(1);
            String sqlContent = m2ActionWithCDataMatcher.group(2);
            String fullXml = extractFullComponentXml(content, m2ActionWithCDataMatcher.start(), "component");
            processSqlComponent("M2 Action", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
        }

        // 6. Паттерн для M2 Action БЕЗ CDATA
        Pattern m2ActionWithoutCDataPattern = Pattern.compile(
                "<component\\s+cmptype\\s*=\\s*[\"']Action[\"'][^>]*name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</component>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher m2ActionWithoutCDataMatcher = m2ActionWithoutCDataPattern.matcher(content);
        while (m2ActionWithoutCDataMatcher.find()) {
            String componentName = m2ActionWithoutCDataMatcher.group(1);
            String body = m2ActionWithoutCDataMatcher.group(2);

            if (body != null && !body.contains("CDATA")) {
                String sqlContent = cleanSqlBody(body);
                if (sqlContent != null && !sqlContent.trim().isEmpty() && isSqlContent(sqlContent)) {
                    String fullXml = extractFullComponentXml(content, m2ActionWithoutCDataMatcher.start(), "component");
                    processSqlComponent("M2 Action", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
                }
            }
        }

        // 7. Паттерн для D3 Action с CDATA (cmpAction)
        Pattern d3ActionWithCDataPattern = Pattern.compile(
                "<cmpAction\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</cmpAction>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher d3ActionWithCDataMatcher = d3ActionWithCDataPattern.matcher(content);
        while (d3ActionWithCDataMatcher.find()) {
            String componentName = d3ActionWithCDataMatcher.group(1);
            String sqlContent = d3ActionWithCDataMatcher.group(2);
            String fullXml = extractFullComponentXml(content, d3ActionWithCDataMatcher.start(), "cmpAction");
            processSqlComponent("D3 Action", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
        }

        // 8. Паттерн для D3 Action БЕЗ CDATA (cmpAction)
        Pattern d3ActionWithoutCDataPattern = Pattern.compile(
                "<cmpAction\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</cmpAction>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher d3ActionWithoutCDataMatcher = d3ActionWithoutCDataPattern.matcher(content);
        while (d3ActionWithoutCDataMatcher.find()) {
            String componentName = d3ActionWithoutCDataMatcher.group(1);
            String body = d3ActionWithoutCDataMatcher.group(2);

            if (body != null && !body.contains("CDATA")) {
                String sqlContent = cleanSqlBody(body);
                if (sqlContent != null && !sqlContent.trim().isEmpty() && isSqlContent(sqlContent)) {
                    String fullXml = extractFullComponentXml(content, d3ActionWithoutCDataMatcher.start(), "cmpAction");
                    processSqlComponent("D3 Action", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
                }
            }
        }

        return queries;
    }



    /**
     * Очистить тело компонента от вложенных тегов
     */
    private String cleanSqlBody(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }

        // Удаляем вложенные компоненты (ActionVar, Variable и т.д.)
        String cleaned = body.replaceAll("<component[^>]*>.*?</component>", "");
        // Удаляем одиночные теги
        cleaned = cleaned.replaceAll("<[^>]+>", "");

        // Удаляем SQL комментарии
        cleaned = removeSqlComments(cleaned);

        // Удаляем лишние пробелы и пустые строки
        cleaned = cleaned.replaceAll("\\n\\s*\\n", "\n");

        return cleaned.trim();
    }


    /**
     * Извлечь все объекты из SQL текста напрямую (независимо от регистра)
     */
    private void extractAllObjectsDirectly(String sqlText, SqlInfo sqlInfo) {
        if (sqlText == null || sqlText.isEmpty()) return;

        // Очищаем от комментариев
        String cleanText = removeAllComments(sqlText);

        // Временные множества для сбора всех объектов
        Set<String> potentialViews = new HashSet<>();
        Set<String> potentialPackageFunctions = new HashSet<>();
        Set<String> potentialUnknown = new HashSet<>();
        Set<String> potentialConstants = new HashSet<>();  // ДЛЯ КОНСТАНТ

        // ========== 1. СПЕЦИАЛЬНАЯ ОБРАБОТКА ДЛЯ КОНСТАНТ ==========
        Pattern constantSearchPattern = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_(?:STR|NUM|DATE)\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher constantMatcher = constantSearchPattern.matcher(cleanText);
        while (constantMatcher.find()) {
            String constant = constantMatcher.group(1);
            if (constant != null && !constant.isEmpty()) {
                potentialConstants.add(constant);
                System.out.println("      [DEBUG] Найдена константа: '" + constant + "'");
            }
        }

        // ========== 2. Собираем все D_V_* как потенциальные вьюхи ==========
        Pattern viewPattern = Pattern.compile("\\b[Dd]_[Vv]_[A-Za-z0-9_]+\\b");
        Matcher viewMatcher = viewPattern.matcher(cleanText);
        while (viewMatcher.find()) {
            String name = viewMatcher.group().toUpperCase();
            if (!isSystemAlias(name)) {
                potentialViews.add(name);
            }
        }

        // ========== 3. Собираем все D_PKG_*.XXX как потенциальные пакетные функции ==========
        // ИСКЛЮЧАЕМ D_PKG_CONSTANTS (уже обработаны)
        Pattern pkgPattern = Pattern.compile("\\b[Dd]_[Pp][Kk][Gg]_(?!CONSTANTS)[A-Za-z0-9_]+\\s*\\.\\s*[A-Za-z0-9_]+\\b");
        Matcher pkgMatcher = pkgPattern.matcher(cleanText);
        while (pkgMatcher.find()) {
            String fullName = pkgMatcher.group().toUpperCase();
            potentialPackageFunctions.add(fullName);
        }

        // ========== 4. Собираем все остальные D_* объекты ==========
        Pattern otherPattern = Pattern.compile("\\b[Dd]_[A-Za-z0-9_]+\\b");
        Matcher otherMatcher = otherPattern.matcher(cleanText);
        while (otherMatcher.find()) {
            String name = otherMatcher.group().toUpperCase();
            // Пропускаем вьюхи, пакетные функции и константы
            if (!name.startsWith("D_V_") &&
                    !name.startsWith("D_PKG_") &&
                    !isSystemAlias(name)) {
                potentialUnknown.add(name);
            }
        }

        // ========== 5. ДОБАВЛЯЕМ КОНСТАНТЫ В SQLINFO ==========
        for (String constant : potentialConstants) {
            sqlInfo.addConstant(constant);
            System.out.println("      [DEBUG] Добавлена константа в sqlInfo: " + constant);
        }
        System.out.println("      [DEBUG] Всего констант в sqlInfo: " + sqlInfo.getConstants().size());

        // ========== 6. СПИСОК ОБЪЕКТОВ, КОТОРЫЕ ДОЛЖНЫ БЫТЬ В "РАЗОБРАТЬ АНАЛИТИКОМ" ==========
        Set<String> forcedToUnknown = new HashSet<>(Arrays.asList(
                "D_PKG_STD.FRM_D",
                "D_PKG_STD.FRM_T",
                "D_PKG_STR_TOOLS.FIO",
                "D_P_EXC",
                "D_C_ID",
                "D_STRAGG_EX",
                "D_PKG_STD.TREF",
                "D_PKG_OPTIONS.GET",
                "D_PKG_OPTION_SPECS.GET",
                "D_TP_STRAGG_REC",
                "D_STRAGG",
                "D_ID",
                "D_LPU",
                "D_CODE",
                "D_DEPARTURE",
                "D_P_HH_NUMB_RES_DEL",
                "D_CL_ID",
                "D_PKG_STD.FRM_DT"
        ));

        // ========== 7. РАСПРЕДЕЛЯЕМ ОБЪЕКТЫ ==========
        // Вьюхи
        for (String view : potentialViews) {
            if (!forcedToUnknown.contains(view)) {
                sqlInfo.addTableView(view);
            } else {
                sqlInfo.addUnknownObject(view);
            }
        }

        // Пакетные функции - проверяем, не нужно ли переместить в unknown
        for (String pkgFunc : potentialPackageFunctions) {
            if (!forcedToUnknown.contains(pkgFunc)) {
                sqlInfo.addPackageFunction(pkgFunc);
            } else {
                sqlInfo.addUnknownObject(pkgFunc);
            }
        }

        // Остальные объекты
        for (String unknown : potentialUnknown) {
            if (!forcedToUnknown.contains(unknown)) {
                // Проверяем, не является ли это константой
                if (!potentialConstants.contains(unknown)) {
                    sqlInfo.addUnknownObject(unknown);
                }
            }
        }
    }

    /**
     * Очистить пакетные функции от объектов, которые должны быть в unknown
     */
    private void cleanPackageFunctionsByForcedList(SqlInfo sqlInfo, Set<String> forcedToUnknown) {
        Set<String> toRemove = new HashSet<>();
        for (String pkgFunc : sqlInfo.getPackagesFunctions()) {
            if (forcedToUnknown.contains(pkgFunc)) {
                toRemove.add(pkgFunc);
            }
        }
        for (String remove : toRemove) {
            sqlInfo.getPackagesFunctions().remove(remove);
            sqlInfo.addUnknownObject(remove);
        }
    }

    /**
     * Проверка, является ли имя алиасом (не реальным объектом БД)
     */
    private boolean isSystemAlias(String name) {
        Set<String> aliases = new HashSet<>(Arrays.asList(
                "D_PREF", "D_NUMB", "D_DATE", "OD_NUMB", "LPU_TO", "LPU_TO_HANDLE",
                "PATIENT", "POLIS", "PHONE", "PAYMENT_KIND", "DIR_COMMENT",
                "HOSP_DEP_NAME", "DIRECTION_KIND", "LAST_HPK_DATE", "HH_STATUS",
                "LAST_HOSP_DATE", "REG_DATE", "HOSP_PLAN_DATE", "PAT_BIRTHDATE",
                "IS_CANCELED", "CANC_REASON_CODE", "CANC_REASON_NAME", "VISIT_DATE"
        ));
        return aliases.contains(name);
    }

    /**
     * Проверка, является ли текст частью комментария
     */
    private boolean isCommentText(String text) {
        // Список слов, которые часто встречаются в комментариях
        Set<String> commentWords = new HashSet<>(Arrays.asList(
                "ПРОВЕРЯЕМ", "ИСПОЛЬЗОВАНИЕ", "НАПРАВЛЕННИЯ", "НАПРАВЛЕНИЕ",
                "ПОЛУЧАЕМ", "ВНУТРЕННЕГО", "СВЯЗИ", "КОММЕНТАРИЙ", "ВРЕМЕННО"
        ));
        return commentWords.contains(text);
    }



}