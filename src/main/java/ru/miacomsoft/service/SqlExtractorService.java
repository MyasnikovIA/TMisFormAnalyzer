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
            System.out.println("  Содержимое файла пустое!");
            return queries;
        }

        System.out.println("=== ИЗВЛЕЧЕНИЕ SQL ===");
        System.out.println("Source: " + sourcePath);

        // Отладочный вывод: первые 500 символов файла
        System.out.println("  Первые 500 символов файла:");
        System.out.println("  " + content.substring(0, Math.min(500, content.length())).replace("\n", "\\n"));

        // Используем универсальный метод для всех компонентов
        queries.addAll(extractAllComponents(content, sourcePath, baseFormPath));

        // Выводим статистику
        int totalTablesViews = 0;
        int totalConstants = 0;
        int totalPackages = 0;

        for (SqlInfo sql : queries) {
            totalTablesViews += sql.getTablesViews().size();
            totalConstants += sql.getConstants().size();
            totalPackages += sql.getPackagesFunctions().size();
        }

        System.out.println("Извлечено SQL запросов: " + queries.size());
        System.out.println("  Таблиц/вьюх: " + totalTablesViews);
        System.out.println("  Пакетов/функций: " + totalPackages);
        System.out.println("  Констант: " + totalConstants);

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
            extractUnknownObjects(sqlInfo);

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
     */
    private boolean isSqlContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String lowerContent = content.toLowerCase().trim();

        // Если начинается с SQL ключевых слов
        if (lowerContent.startsWith("select") ||
                lowerContent.startsWith("insert") ||
                lowerContent.startsWith("update") ||
                lowerContent.startsWith("delete") ||
                lowerContent.startsWith("begin") ||
                lowerContent.startsWith("declare") ||
                lowerContent.startsWith("with") ||
                lowerContent.startsWith("merge") ||
                lowerContent.matches("^\\s*begin\\s+.*end\\s*;?\\s*$")) {
            return true;
        }

        // Содержит SQL ключевые слова
        return lowerContent.contains("select") &&
                (lowerContent.contains("from") || lowerContent.contains("into"));
    }

    /**
     * Очистить тело компонента от вложенных тегов
     */
    private String cleanSqlBody(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }

        // Удаляем вложенные компоненты
        String cleaned = body.replaceAll("<[^>]+>", "");
        cleaned = removeSqlComments(cleaned);
        cleaned = cleaned.replaceAll("\\n\\s*\\n", "\n");

        return cleaned.trim();
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
     * Все имена приводятся к ВЕРХНЕМУ РЕГИСТРУ
     */
    private void extractTablesViews(SqlInfo sqlInfo) {
        String sql = sqlInfo.getSqlContent();
        if (sql == null) return;

        // Приводим к верхнему регистру для поиска
        String upperSql = sql.toUpperCase();

        Set<String> allTablesViews = new LinkedHashSet<>();

        // 1. Паттерн для поиска D_V_* и D_* объектов (регистронезависимый)
        Pattern tablePattern = Pattern.compile(
                "\\b(D_[A-Z][A-Z0-9_]*)\\b",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = tablePattern.matcher(upperSql);
        while (matcher.find()) {
            String name = matcher.group(1).toUpperCase();

            // Исключаем пакеты (D_PKG_...)
            if (name.startsWith("D_PKG_")) {
                continue;
            }

            // Исключаем специальные объекты, которые будут в unknown
            Set<String> forcedToUnknown = new HashSet<>(Arrays.asList(
                    "D_STRAGG", "D_P_EXC", "D_C_ID", "D_STRAGG_EX", "D_ID", "D_LPU",
                    "D_CODE", "D_DEPARTURE", "D_P_HH_NUMB_RES_DEL", "D_CL_ID", "D_V_URPRIVS"
            ));

            if (forcedToUnknown.contains(name)) {
                continue;
            }

            // Проверяем, что это не SQL ключевое слово
            if (!SQL_KEYWORDS.contains(name)) {
                allTablesViews.add(name);
            }
        }

        // 2. ДОПОЛНИТЕЛЬНЫЙ ПАТТЕРН: поиск вьюх в вызовах функций
        // Например: D_PKG_AGENT_POLIS.GET_ACTUAL_ON_DATE(a.ID, sysdate, 0, 'P_WHO_CODE P_SER P_NUM')
        Pattern functionViewPattern = Pattern.compile(
                "D_PKG_[A-Z_]+\\.[A-Z_]+\\([^)]*\\b(D_V_[A-Z0-9_]+)\\b",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher funcMatcher = functionViewPattern.matcher(upperSql);
        while (funcMatcher.find()) {
            String name = funcMatcher.group(1).toUpperCase();
            allTablesViews.add(name);
            System.out.println("      [DEBUG] Найдена вьюха в функции: " + name);
        }

        // 3. ПАТТЕРН ДЛЯ ПОИСКА В ПОДЗАПРОСАХ
        // Ищем D_V_ после SELECT, FROM, JOIN
        Pattern subqueryPattern = Pattern.compile(
                "(?:SELECT|FROM|JOIN)\\s+(D_V_[A-Z0-9_]+)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher subMatcher = subqueryPattern.matcher(upperSql);
        while (subMatcher.find()) {
            String name = subMatcher.group(1).toUpperCase();
            allTablesViews.add(name);
            System.out.println("      [DEBUG] Найдена вьюха в подзапросе: " + name);
        }

        // 4. ПАТТЕРН ДЛЯ ПОИСКА В АЛИАСАХ
        Pattern aliasPattern = Pattern.compile(
                "(D_V_[A-Z0-9_]+)\\s+[A-Za-z0-9_]+",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher aliasMatcher = aliasPattern.matcher(upperSql);
        while (aliasMatcher.find()) {
            String name = aliasMatcher.group(1).toUpperCase();
            allTablesViews.add(name);
            System.out.println("      [DEBUG] Найдена вьюха с алиасом: " + name);
        }

        // Добавляем все найденные объекты
        for (String name : allTablesViews) {
            sqlInfo.addTableView(name);
            System.out.println("      [DEBUG] Добавлена таблица/вьюха: " + name);
        }

        System.out.println("      [DEBUG] Всего найдено таблиц/вьюх: " + allTablesViews.size());
    }
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
    /**
     * Извлечь пакеты и функции из SQL
     * Ищет все вхождения вида D_PKG_XXXX.YYYY
     */
    private void extractPackagesFunctions(SqlInfo sqlInfo) {
        String sql = sqlInfo.getSqlContent();
        if (sql == null) return;

        String upperSql = sql.toUpperCase();
        Set<String> packages = new LinkedHashSet<>();

        // Универсальный паттерн для поиска всех D_PKG_XXXX.YYYY
        // D_PKG_ - префикс
        // [A-Z0-9_]+ - имя пакета (буквы, цифры, подчеркивание)
        // \. - точка-разделитель
        // [A-Z0-9_]+ - имя функции/процедуры
        Pattern packagePattern = Pattern.compile(
                "\\b(D_PKG_[A-Z0-9_]+\\.[A-Z0-9_]+)\\b",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );


        Matcher packageMatcher = packagePattern.matcher(upperSql);
        while (packageMatcher.find()) {
            String pkgFunc = packageMatcher.group(1);
            // Исключаем константы и опции, они обрабатываются отдельно
            if (!pkgFunc.startsWith("D_PKG_CONSTANTS") &&
                    !pkgFunc.startsWith("D_PKG_OPTIONS") &&
                    !pkgFunc.startsWith("D_PKG_OPTION_SPECS")) {
                packages.add(pkgFunc);
                System.out.println("      [DEBUG] Найден пакет/функция: " + pkgFunc);
            }
        }

        // Дополнительный паттерн для standalone функций D_PKG_FN_*
        Pattern fnPattern = Pattern.compile(
                "\\b(D_PKG_FN_[A-Z0-9_]+)\\s*\\(",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher fnMatcher = fnPattern.matcher(upperSql);
        while (fnMatcher.find()) {
            String func = fnMatcher.group(1);
            packages.add(func);
            System.out.println("      [DEBUG] Найдена standalone функция: " + func);
        }

        for (String pkg : packages) {
            sqlInfo.addPackageFunction(pkg);
        }

        if (!packages.isEmpty()) {
            System.out.println("      [DEBUG] Всего найдено пакетов/функций: " + packages.size());
        }
    }


    /**
     * Извлечь системные опции из D_PKG_OPTIONS.GET
     */
    /**
     * Извлечь системные опции из D_PKG_OPTIONS.GET и D_PKG_OPTION_SPECS.GET
     */
    private void extractSystemOptions(SqlInfo sqlInfo) {
        String sql = sqlInfo.getSqlContent();
        if (sql == null || sql.isEmpty()) {
            return;
        }

        Set<String> options = new LinkedHashSet<>();

        // Паттерн для D_PKG_OPTIONS.GET('OPTION_NAME', ...)
        // Поддерживает различные форматы вызова:
        // 1. D_PKG_OPTIONS.GET('CheckDepBed_HeadNurse', :LPU)
        // 2. D_PKG_OPTIONS.GET(psSO_CODE => 'ShowMainDepPat', pnLPU => :LPU)
        // 3. D_PKG_OPTIONS.GET('VDAutoSet7VZNPriv', :LPU)
        // 4. D_PKG_OPTIONS.GET(psSO_CODE => 'FrlloConfirmLgot', pnLPU => :LPU, pnRAISE => 0)

        // Паттерн 1: D_PKG_OPTIONS.GET('OPTION_NAME', ...) - позиционные параметры
        Pattern optionPattern1 = Pattern.compile(
                "D_PKG_OPTIONS\\.GET\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн 2: D_PKG_OPTIONS.GET(psSO_CODE => 'OPTION_NAME', ...) - именованные параметры
        Pattern optionPattern2 = Pattern.compile(
                "D_PKG_OPTIONS\\.GET\\s*\\(\\s*psSO_CODE\\s*=>\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для D_PKG_OPTION_SPECS.GET('OPTION_NAME', ...)
        Pattern optionSpecPattern1 = Pattern.compile(
                "D_PKG_OPTION_SPECS\\.GET\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для D_PKG_OPTION_SPECS.GET(psSO_CODE => 'OPTION_NAME', ...)
        Pattern optionSpecPattern2 = Pattern.compile(
                "D_PKG_OPTION_SPECS\\.GET\\s*\\(\\s*psSO_CODE\\s*=>\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Извлекаем из D_PKG_OPTIONS.GET - паттерн 1
        Matcher matcher1 = optionPattern1.matcher(sql);
        while (matcher1.find()) {
            String option = matcher1.group(1);
            if (option != null && !option.isEmpty()) {
                options.add(option);
                System.out.println("      Найдена системная опция (D_PKG_OPTIONS): " + option);
            }
        }

        // Извлекаем из D_PKG_OPTIONS.GET - паттерн 2
        Matcher matcher2 = optionPattern2.matcher(sql);
        while (matcher2.find()) {
            String option = matcher2.group(1);
            if (option != null && !option.isEmpty()) {
                options.add(option);
                System.out.println("      Найдена системная опция (D_PKG_OPTIONS named): " + option);
            }
        }

        // Извлекаем из D_PKG_OPTION_SPECS.GET - паттерн 1
        Matcher matcher3 = optionSpecPattern1.matcher(sql);
        while (matcher3.find()) {
            String option = matcher3.group(1);
            if (option != null && !option.isEmpty()) {
                options.add(option);
                System.out.println("      Найдена системная опция (D_PKG_OPTION_SPECS): " + option);
            }
        }

        // Извлекаем из D_PKG_OPTION_SPECS.GET - паттерн 2
        Matcher matcher4 = optionSpecPattern2.matcher(sql);
        while (matcher4.find()) {
            String option = matcher4.group(1);
            if (option != null && !option.isEmpty()) {
                options.add(option);
                System.out.println("      Найдена системная опция (D_PKG_OPTION_SPECS named): " + option);
            }
        }

        // Добавляем найденные опции в SqlInfo
        for (String option : options) {
            sqlInfo.addSystemOption(option);
        }

        if (!options.isEmpty()) {
            System.out.println("    Всего найдено системных опций: " + options.size());
        }
    }

    private void processSqlComponent(String componentType, String componentName, String sqlContent,
                                     String fullXml, String sourcePath, String baseFormPath,
                                     List<SqlInfo> queries) {
        if (sqlContent == null || sqlContent.trim().isEmpty()) {
            return;
        }

        // Удаляем комментарии для анализа
        String cleanedSql = removeAllComments(sqlContent);

        SqlInfo sqlInfo = new SqlInfo();
        sqlInfo.setSourceType(componentType);
        sqlInfo.setSourcePath(sourcePath);
        sqlInfo.setBaseFormPath(baseFormPath);
        sqlInfo.setComponentName(componentName);

        // Сохраняем полный XML с тегами и оригинальный SQL
        sqlInfo.setSqlContent(fullXml != null ? fullXml : sqlContent);
        sqlInfo.setCleanSql(cleanSql(cleanedSql));

        // ========== НОВЫЙ ФУНКЦИОНАЛ: извлекаем объекты из оригинального SQL ==========
        // Это помогает найти объекты, которые могли быть потеряны при очистке
        extractFromOriginalSql(sqlContent, sqlInfo);

        // ========== ОСНОВНЫЕ МЕТОДЫ ИЗВЛЕЧЕНИЯ (старый функционал) ==========
        // Извлекаем все объекты в правильном порядке
        extractAllObjectsDirectly(cleanedSql, sqlInfo);
        extractPackagesFunctions(sqlInfo);
        extractUserProcedures(sqlInfo);
        extractSystemOptions(sqlInfo);
        extractConstants(sqlInfo);
        extractTablesViews(sqlInfo);

        // ДОПОЛНИТЕЛЬНО: извлекаем вьюхи из оригинального SQL (неочищенного)
        extractTablesViewsFromOriginal(sqlInfo, sqlContent);

        // ========== НОВЫЙ ФУНКЦИОНАЛ: поиск пакетов в оригинальном SQL ==========
        extractPackagesFromOriginal(sqlContent, sqlInfo);

        // ========== НОВЫЙ ФУНКЦИОНАЛ: поиск вызовов функций ==========
        extractFunctionCallsFromOriginal(sqlContent, sqlInfo);

        // Список объектов для принудительного переноса в unknown
        Set<String> forcedToUnknown = new HashSet<>(Arrays.asList(
                "D_PKG_STD.FRM_D", "D_PKG_STD.FRM_T", "D_PKG_STR_TOOLS.FIO",
                "D_P_EXC", "D_C_ID", "D_STRAGG_EX", "D_PKG_STD.TREF",
                "D_PKG_OPTIONS.GET", "D_PKG_OPTION_SPECS.GET",
                "D_TP_STRAGG_REC", "D_STRAGG", "D_ID", "D_LPU", "D_CODE",
                "D_DEPARTURE", "D_P_HH_NUMB_RES_DEL", "D_CL_ID",
                "D_PKG_STD.FRM_DT", "D_PKG_CONSTANTS.SEARCH_DATE",
                "D_PKG_CONSTANTS.SEARCH_NUM", "D_PKG_CONSTANTS.SEARCH_STR",
                "D_V_URPRIVS"
        ));

        // Очищаем пакетные функции от объектов из forced списка
        cleanPackageFunctionsByForcedList(sqlInfo, forcedToUnknown);

        queries.add(sqlInfo);
        System.out.println("  Извлечен компонент: " + componentType + " - " + componentName);
        System.out.println("    Таблиц/вьюх: " + sqlInfo.getTablesViews().size());
        System.out.println("    Пакетов/функций: " + sqlInfo.getPackagesFunctions().size());
        System.out.println("    Констант: " + sqlInfo.getConstants().size());
        System.out.println("    Системных опций: " + sqlInfo.getSystemOptions().size());
    }

    /**
     * Извлечение объектов из оригинального SQL (до очистки комментариев)
     */
    private void extractFromOriginalSql(String originalSql, SqlInfo sqlInfo) {
        if (originalSql == null || originalSql.isEmpty()) return;

        String upperSql = originalSql.toUpperCase();

        // Поиск всех D_V_ вхождений
        Pattern viewPattern = Pattern.compile("\\bD_V_[A-Z0-9_]+\\b");
        Matcher viewMatcher = viewPattern.matcher(upperSql);
        while (viewMatcher.find()) {
            String viewName = viewMatcher.group();
            if (!viewName.startsWith("D_PKG_") && !sqlInfo.getTablesViews().contains(viewName)) {
                sqlInfo.addTableView(viewName);
                System.out.println("      [DEBUG] Добавлена вьюха из оригинального SQL: " + viewName);
            }
        }
    }

    /**
     * Извлечение пакетов из оригинального SQL
     */
    private void extractPackagesFromOriginal(String originalSql, SqlInfo sqlInfo) {
        if (originalSql == null || originalSql.isEmpty()) return;

        String upperSql = originalSql.toUpperCase();

        // Универсальный паттерн для поиска всех D_PKG_XXXX.YYYY
        Pattern packagePattern = Pattern.compile(
                "\\b(D_PKG_[A-Z0-9_]+\\.[A-Z0-9_]+)\\b",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher packageMatcher = packagePattern.matcher(upperSql);
        while (packageMatcher.find()) {
            String pkgFunc = packageMatcher.group(1);
            // Исключаем константы и опции, они обрабатываются отдельно
            if (!pkgFunc.startsWith("D_PKG_CONSTANTS") &&
                    !pkgFunc.startsWith("D_PKG_OPTIONS") &&
                    !pkgFunc.startsWith("D_PKG_OPTION_SPECS") &&
                    !sqlInfo.getPackagesFunctions().contains(pkgFunc)) {
                sqlInfo.addPackageFunction(pkgFunc);
                System.out.println("      [DEBUG] Добавлен пакет из оригинального SQL: " + pkgFunc);
            }
        }
    }

    /**
     * Извлечение вызовов функций из оригинального SQL
     */
    private void extractFunctionCallsFromOriginal(String originalSql, SqlInfo sqlInfo) {
        if (originalSql == null || originalSql.isEmpty()) return;

        String upperSql = originalSql.toUpperCase();

        // Паттерн для поиска любых вызовов функций D_PKG_XXXX.YYYY(
        Pattern functionCallPattern = Pattern.compile(
                "\\b(D_PKG_[A-Z0-9_]+\\.[A-Z0-9_]+)\\s*\\(",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher functionMatcher = functionCallPattern.matcher(upperSql);
        while (functionMatcher.find()) {
            String functionCall = functionMatcher.group(1);
            if (!functionCall.startsWith("D_PKG_CONSTANTS") &&
                    !functionCall.startsWith("D_PKG_OPTIONS") &&
                    !functionCall.startsWith("D_PKG_OPTION_SPECS") &&
                    !sqlInfo.getPackagesFunctions().contains(functionCall)) {
                sqlInfo.addPackageFunction(functionCall);
                System.out.println("      [DEBUG] Добавлен вызов функции из оригинального SQL: " + functionCall);
            }
        }
    }

    /**
     * Извлечь таблицы и вьюхи из оригинального SQL (неочищенного)
     * Это помогает найти вьюхи, которые могли быть потеряны при очистке
     */
    /**
     * Извлечь таблицы и вьюхи из оригинального SQL (неочищенного)
     * Это помогает найти вьюхи, которые могли быть потеряны при очистке
     */
    private void extractTablesViewsFromOriginal(SqlInfo sqlInfo, String originalSql) {
        if (originalSql == null || originalSql.isEmpty()) return;

        String upperSql = originalSql.toUpperCase();
        Set<String> foundViews = new LinkedHashSet<>();

        // Поиск всех D_V_ вхождений
        Pattern viewPattern = Pattern.compile("\\bD_V_[A-Z0-9_]+\\b");
        Matcher viewMatcher = viewPattern.matcher(upperSql);
        while (viewMatcher.find()) {
            String viewName = viewMatcher.group();
            if (!viewName.startsWith("D_PKG_")) {
                foundViews.add(viewName);
            }
        }

        // Поиск в вызовах функций
        Pattern functionPattern = Pattern.compile(
                "D_PKG_[A-Z_]+\\.[A-Z_]+\\([^)]*\\b(D_V_[A-Z0-9_]+)\\b",
                Pattern.DOTALL
        );
        Matcher funcMatcher = functionPattern.matcher(upperSql);
        while (funcMatcher.find()) {
            String viewName = funcMatcher.group(1);
            foundViews.add(viewName);
        }

        // Поиск после FROM и JOIN
        Pattern fromJoinPattern = Pattern.compile(
                "(?:FROM|JOIN)\\s+(D_V_[A-Z0-9_]+)",
                Pattern.DOTALL
        );
        Matcher fromJoinMatcher = fromJoinPattern.matcher(upperSql);
        while (fromJoinMatcher.find()) {
            String viewName = fromJoinMatcher.group(1);
            foundViews.add(viewName);
        }

        // Добавляем найденные вьюхи
        for (String view : foundViews) {
            if (!sqlInfo.getTablesViews().contains(view)) {
                sqlInfo.addTableView(view);
                System.out.println("      [DEBUG] Добавлена вьюха из оригинального SQL: " + view);
            }
        }
    }


    /**
     * Извлечь константы из D_PKG_CONSTANTS.SEARCH_STR, SEARCH_NUM, SEARCH_DATE
     * Поддерживает как позиционные, так и именованные параметры
     */
    private void extractConstants(SqlInfo sqlInfo) {
        String sql = sqlInfo.getCleanSql(); // Используем cleanSql, а не sqlContent
        if (sql == null || sql.isEmpty()) {
            sql = sqlInfo.getSqlContent();
            if (sql == null || sql.isEmpty()) {
                return;
            }
        }

        Set<String> constants = new LinkedHashSet<>();

        // Паттерн для SEARCH_STR с позиционными параметрами
        Pattern strPattern1 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_STR\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_STR с именованными параметрами
        Pattern strPattern2 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_STR\\s*\\(\\s*psCONST_CODE\\s*=>\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_NUM с позиционными параметрами
        Pattern numPattern1 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_NUM\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_NUM с именованными параметрами
        Pattern numPattern2 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_NUM\\s*\\(\\s*psCONST_CODE\\s*=>\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_DATE с позиционными параметрами
        Pattern datePattern1 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_DATE\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_DATE с именованными параметрами
        Pattern datePattern2 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_DATE\\s*\\(\\s*psCONST_CODE\\s*=>\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Извлекаем из SEARCH_STR
        Matcher strMatcher1 = strPattern1.matcher(sql);
        while (strMatcher1.find()) {
            String constant = strMatcher1.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [extractConstants] Найдена константа (STR позиционная): " + constant);
            }
        }

        Matcher strMatcher2 = strPattern2.matcher(sql);
        while (strMatcher2.find()) {
            String constant = strMatcher2.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [extractConstants] Найдена константа (STR именованная): " + constant);
            }
        }

        // Извлекаем из SEARCH_NUM
        Matcher numMatcher1 = numPattern1.matcher(sql);
        while (numMatcher1.find()) {
            String constant = numMatcher1.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [extractConstants] Найдена константа (NUM позиционная): " + constant);
            }
        }

        Matcher numMatcher2 = numPattern2.matcher(sql);
        while (numMatcher2.find()) {
            String constant = numMatcher2.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [extractConstants] Найдена константа (NUM именованная): " + constant);
            }
        }

        // Извлекаем из SEARCH_DATE
        Matcher dateMatcher1 = datePattern1.matcher(sql);
        while (dateMatcher1.find()) {
            String constant = dateMatcher1.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [extractConstants] Найдена константа (DATE позиционная): " + constant);
            }
        }

        Matcher dateMatcher2 = datePattern2.matcher(sql);
        while (dateMatcher2.find()) {
            String constant = dateMatcher2.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [extractConstants] Найдена константа (DATE именованная): " + constant);
            }
        }

        // Добавляем в SqlInfo
        for (String constant : constants) {
            sqlInfo.addConstant(constant);
        }

        if (!constants.isEmpty()) {
            System.out.println("    [extractConstants] Всего найдено констант: " + constants.size());
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

        System.out.println("  Поиск компонентов в файле...");
        System.out.println("  Размер файла: " + content.length() + " символов");

        // ========== 1. D3 DataSet (cmpDataSet) - УПРОЩЕННЫЙ ПАТТЕРН ==========
        // Ищем любые cmpDataSet теги, независимо от содержимого
        Pattern d3DataSetPattern = Pattern.compile(
                "<cmpDataSet\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</cmpDataSet>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher d3DataSetMatcher = d3DataSetPattern.matcher(content);
        int dataSetCount = 0;
        while (d3DataSetMatcher.find()) {
            dataSetCount++;
            String componentName = d3DataSetMatcher.group(1);
            String body = d3DataSetMatcher.group(2);
            System.out.println("    Найден D3 DataSet: " + componentName);

            String sqlContent = null;
            // Проверяем наличие CDATA
            if (body != null && body.contains("CDATA")) {
                Pattern cdataPattern = Pattern.compile("<!\\[CDATA\\[(.*?)\\]\\]>", Pattern.DOTALL);
                Matcher cdataMatcher = cdataPattern.matcher(body);
                if (cdataMatcher.find()) {
                    sqlContent = cdataMatcher.group(1);
                    System.out.println("      Извлечено из CDATA, длина: " + sqlContent.length());
                }
            } else {
                sqlContent = cleanSqlBody(body);
                System.out.println("      Извлечено без CDATA, длина: " + (sqlContent != null ? sqlContent.length() : 0));
            }

            if (sqlContent != null && !sqlContent.trim().isEmpty() && isSqlContent(sqlContent)) {
                String fullXml = "<cmpDataSet name=\"" + componentName + "\">" + body + "</cmpDataSet>";
                processSqlComponent("D3 DataSet", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
                System.out.println("  Извлечен D3 DataSet: " + componentName);
            }
        }
        System.out.println("  Всего найдено D3 DataSet: " + dataSetCount);

        // ========== 2. D3 Action (cmpAction) - УПРОЩЕННЫЙ ПАТТЕРН ==========
        Pattern d3ActionPattern = Pattern.compile(
                "<cmpAction\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</cmpAction>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher d3ActionMatcher = d3ActionPattern.matcher(content);
        int actionCount = 0;
        while (d3ActionMatcher.find()) {
            actionCount++;
            String componentName = d3ActionMatcher.group(1);
            String body = d3ActionMatcher.group(2);
            System.out.println("    Найден D3 Action: " + componentName);

            String sqlContent = null;
            if (body != null && body.contains("CDATA")) {
                Pattern cdataPattern = Pattern.compile("<!\\[CDATA\\[(.*?)\\]\\]>", Pattern.DOTALL);
                Matcher cdataMatcher = cdataPattern.matcher(body);
                if (cdataMatcher.find()) {
                    sqlContent = cdataMatcher.group(1);
                    System.out.println("      Извлечено из CDATA, длина: " + sqlContent.length());
                }
            } else {
                sqlContent = cleanSqlBody(body);
                System.out.println("      Извлечено без CDATA, длина: " + (sqlContent != null ? sqlContent.length() : 0));
            }

            if (sqlContent != null && !sqlContent.trim().isEmpty() && isSqlContent(sqlContent)) {
                String fullXml = "<cmpAction name=\"" + componentName + "\">" + body + "</cmpAction>";
                processSqlComponent("D3 Action", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
                System.out.println("  Извлечен D3 Action: " + componentName);
            }
        }
        System.out.println("  Всего найдено D3 Action: " + actionCount);

        // ========== 3. M2 DataSet (component cmptype="DataSet") ==========
        Pattern m2DataSetPattern = Pattern.compile(
                "<component\\s+cmptype\\s*=\\s*[\"']DataSet[\"'][^>]*name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</component>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher m2DataSetMatcher = m2DataSetPattern.matcher(content);
        int m2DataSetCount = 0;
        while (m2DataSetMatcher.find()) {
            m2DataSetCount++;
            String componentName = m2DataSetMatcher.group(1);
            String body = m2DataSetMatcher.group(2);
            System.out.println("    Найден M2 DataSet: " + componentName);

            String sqlContent = null;
            if (body != null && body.contains("CDATA")) {
                Pattern cdataPattern = Pattern.compile("<!\\[CDATA\\[(.*?)\\]\\]>", Pattern.DOTALL);
                Matcher cdataMatcher = cdataPattern.matcher(body);
                if (cdataMatcher.find()) {
                    sqlContent = cdataMatcher.group(1);
                }
            } else {
                // Удаляем вложенные компоненты Variable
                String cleanedBody = body.replaceAll("<component\\s+cmptype\\s*=\\s*[\"']Variable[\"'][^>]*/>", "");
                sqlContent = cleanSqlBody(cleanedBody);
            }

            if (sqlContent != null && !sqlContent.trim().isEmpty() && isSqlContent(sqlContent)) {
                String fullXml = "<component cmptype=\"DataSet\" name=\"" + componentName + "\">" + body + "</component>";
                processSqlComponent("M2 DataSet", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
                System.out.println("  Извлечен M2 DataSet: " + componentName);
            }
        }
        System.out.println("  Всего найдено M2 DataSet: " + m2DataSetCount);

        // ========== 4. M2 Action (component cmptype="Action") ==========
        Pattern m2ActionPattern = Pattern.compile(
                "<component\\s+cmptype\\s*=\\s*[\"']Action[\"'][^>]*name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</component>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher m2ActionMatcher = m2ActionPattern.matcher(content);
        int m2ActionCount = 0;
        while (m2ActionMatcher.find()) {
            m2ActionCount++;
            String componentName = m2ActionMatcher.group(1);
            String body = m2ActionMatcher.group(2);
            System.out.println("    Найден M2 Action: " + componentName);

            String sqlContent = null;
            if (body != null && body.contains("CDATA")) {
                Pattern cdataPattern = Pattern.compile("<!\\[CDATA\\[(.*?)\\]\\]>", Pattern.DOTALL);
                Matcher cdataMatcher = cdataPattern.matcher(body);
                if (cdataMatcher.find()) {
                    sqlContent = cdataMatcher.group(1);
                }
            } else {
                // Удаляем вложенные компоненты ActionVar
                String cleanedBody = body.replaceAll("<component\\s+cmptype\\s*=\\s*[\"']ActionVar[\"'][^>]*/>", "");
                cleanedBody = cleanedBody.replaceAll("<component\\s+cmptype\\s*=\\s*[\"']ActionVar[\"'][^>]*>.*?</component>", "");
                sqlContent = cleanActionBody(cleanedBody);
            }

            if (sqlContent != null && !sqlContent.trim().isEmpty() && isSqlContent(sqlContent)) {
                String fullXml = "<component cmptype=\"Action\" name=\"" + componentName + "\">" + body + "</component>";
                processSqlComponent("M2 Action", componentName, sqlContent, fullXml, sourcePath, baseFormPath, queries);
                System.out.println("  Извлечен M2 Action: " + componentName);
            }
        }
        System.out.println("  Всего найдено M2 Action: " + m2ActionCount);

        return queries;
    }



    /**
     * Очистить тело Action от вложенных компонентов ActionVar
     */
    private String cleanActionBody(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }

        // Удаляем все вложенные компоненты ActionVar
        String cleaned = body.replaceAll("<component\\s+cmptype\\s*=\\s*[\"']ActionVar[\"'][^>]*/>", "");
        cleaned = cleaned.replaceAll("<component\\s+cmptype\\s*=\\s*[\"']ActionVar[\"'][^>]*>.*?</component>", "");

        // Удаляем другие возможные теги
        cleaned = cleaned.replaceAll("<[^>]+>", "");

        // Удаляем комментарии
        cleaned = removeSqlComments(cleaned);

        // Очищаем пустые строки
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

        // Приводим к верхнему регистру для удобства поиска (но для констант используем оригинал)
        String upperText = cleanText.toUpperCase();

        // ========== 1. СПЕЦИАЛЬНАЯ ОБРАБОТКА ДЛЯ КОНСТАНТ (РАСШИРЕННЫЙ ШАБЛОН) ==========
        Set<String> constants = new LinkedHashSet<>();

        // Паттерн для SEARCH_STR с позиционными параметрами: D_PKG_CONSTANTS.SEARCH_STR('CONST_NAME', ...)
        Pattern constStrPattern1 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_STR\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_STR с именованными параметрами: D_PKG_CONSTANTS.SEARCH_STR(psCONST_CODE => 'CONST_NAME', ...)
        Pattern constStrPattern2 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_STR\\s*\\(\\s*psCONST_CODE\\s*=>\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_NUM с позиционными параметрами: D_PKG_CONSTANTS.SEARCH_NUM('CONST_NAME', ...)
        Pattern constNumPattern1 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_NUM\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_NUM с именованными параметрами: D_PKG_CONSTANTS.SEARCH_NUM(psCONST_CODE => 'CONST_NAME', ...)
        Pattern constNumPattern2 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_NUM\\s*\\(\\s*psCONST_CODE\\s*=>\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_DATE с позиционными параметрами: D_PKG_CONSTANTS.SEARCH_DATE('CONST_NAME', ...)
        Pattern constDatePattern1 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_DATE\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для SEARCH_DATE с именованными параметрами: D_PKG_CONSTANTS.SEARCH_DATE(psCONST_CODE => 'CONST_NAME', ...)
        Pattern constDatePattern2 = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_DATE\\s*\\(\\s*psCONST_CODE\\s*=>\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Универсальный паттерн для любых констант (для отладки)
        Pattern anyConstPattern = Pattern.compile(
                "D_PKG_CONSTANTS\\.SEARCH_(?:STR|NUM|DATE)\\s*\\(\\s*(?:psCONST_CODE\\s*=>\\s*)?'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Извлекаем из SEARCH_STR (позиционные)
        Matcher strMatcher1 = constStrPattern1.matcher(cleanText);
        while (strMatcher1.find()) {
            String constant = strMatcher1.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [DEBUG] Найдена константа (SEARCH_STR позиционная): " + constant);
            }
        }

        // Извлекаем из SEARCH_STR (именованные)
        Matcher strMatcher2 = constStrPattern2.matcher(cleanText);
        while (strMatcher2.find()) {
            String constant = strMatcher2.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [DEBUG] Найдена константа (SEARCH_STR именованная): " + constant);
            }
        }

        // Извлекаем из SEARCH_NUM (позиционные)
        Matcher numMatcher1 = constNumPattern1.matcher(cleanText);
        while (numMatcher1.find()) {
            String constant = numMatcher1.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [DEBUG] Найдена константа (SEARCH_NUM позиционная): " + constant);
            }
        }

        // Извлекаем из SEARCH_NUM (именованные)
        Matcher numMatcher2 = constNumPattern2.matcher(cleanText);
        while (numMatcher2.find()) {
            String constant = numMatcher2.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [DEBUG] Найдена константа (SEARCH_NUM именованная): " + constant);
            }
        }

        // Извлекаем из SEARCH_DATE (позиционные)
        Matcher dateMatcher1 = constDatePattern1.matcher(cleanText);
        while (dateMatcher1.find()) {
            String constant = dateMatcher1.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [DEBUG] Найдена константа (SEARCH_DATE позиционная): " + constant);
            }
        }

        // Извлекаем из SEARCH_DATE (именованные)
        Matcher dateMatcher2 = constDatePattern2.matcher(cleanText);
        while (dateMatcher2.find()) {
            String constant = dateMatcher2.group(1);
            if (constant != null && !constant.isEmpty()) {
                constants.add(constant);
                System.out.println("      [DEBUG] Найдена константа (SEARCH_DATE именованная): " + constant);
            }
        }

        // Добавляем все найденные константы в sqlInfo
        for (String constant : constants) {
            sqlInfo.addConstant(constant);
        }

        if (!constants.isEmpty()) {
            System.out.println("      [DEBUG] Всего найдено констант: " + constants.size());
        }

        // ========== 2. ИЗВЛЕЧЕНИЕ СИСТЕМНЫХ ОПЦИЙ ==========
        // D_PKG_OPTIONS.GET('OPTION_NAME', ...)
        Pattern optionPattern1 = Pattern.compile(
                "D_PKG_OPTIONS\\.GET\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // D_PKG_OPTIONS.GET(psSO_CODE => 'OPTION_NAME', ...)
        Pattern optionPattern2 = Pattern.compile(
                "D_PKG_OPTIONS\\.GET\\s*\\(\\s*psSO_CODE\\s*=>\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // D_PKG_OPTION_SPECS.GET('OPTION_NAME', ...)
        Pattern optionSpecPattern1 = Pattern.compile(
                "D_PKG_OPTION_SPECS\\.GET\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // D_PKG_OPTION_SPECS.GET(psSO_CODE => 'OPTION_NAME', ...)
        Pattern optionSpecPattern2 = Pattern.compile(
                "D_PKG_OPTION_SPECS\\.GET\\s*\\(\\s*psSO_CODE\\s*=>\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Set<String> systemOptions = new LinkedHashSet<>();

        Matcher optMatcher1 = optionPattern1.matcher(cleanText);
        while (optMatcher1.find()) {
            String option = optMatcher1.group(1);
            if (option != null && !option.isEmpty()) {
                systemOptions.add(option);
                System.out.println("      [DEBUG] Найдена системная опция (D_PKG_OPTIONS): " + option);
            }
        }

        Matcher optMatcher2 = optionPattern2.matcher(cleanText);
        while (optMatcher2.find()) {
            String option = optMatcher2.group(1);
            if (option != null && !option.isEmpty()) {
                systemOptions.add(option);
                System.out.println("      [DEBUG] Найдена системная опция (D_PKG_OPTIONS named): " + option);
            }
        }

        Matcher optMatcher3 = optionSpecPattern1.matcher(cleanText);
        while (optMatcher3.find()) {
            String option = optMatcher3.group(1);
            if (option != null && !option.isEmpty()) {
                systemOptions.add(option);
                System.out.println("      [DEBUG] Найдена системная опция (D_PKG_OPTION_SPECS): " + option);
            }
        }

        Matcher optMatcher4 = optionSpecPattern2.matcher(cleanText);
        while (optMatcher4.find()) {
            String option = optMatcher4.group(1);
            if (option != null && !option.isEmpty()) {
                systemOptions.add(option);
                System.out.println("      [DEBUG] Найдена системная опция (D_PKG_OPTION_SPECS named): " + option);
            }
        }

        for (String option : systemOptions) {
            sqlInfo.addSystemOption(option);
        }

        // ========== 2.5. ПОИСК ВЬЮХ В ВЫЗОВАХ ФУНКЦИЙ ==========
        Pattern functionViewPattern = Pattern.compile(
                "D_PKG_[A-Z_]+\\.[A-Z_]+\\([^)]*\\b(D_V_[A-Z0-9_]+)\\b",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher funcViewMatcher = functionViewPattern.matcher(upperText);
        while (funcViewMatcher.find()) {
            String viewName = funcViewMatcher.group(1).toUpperCase();
            if (!viewName.startsWith("D_PKG_")) {
                sqlInfo.addTableView(viewName);
                System.out.println("      [DEBUG] Найдена вьюха в вызове функции: " + viewName);
            }
        }

        // ========== 2.6. ПОИСК ВЬЮХ В ПОДЗАПРОСАХ ==========
        Pattern subqueryViewPattern = Pattern.compile(
                "(?:FROM|JOIN)\\s+(D_V_[A-Z0-9_]+)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher subqueryViewMatcher = subqueryViewPattern.matcher(upperText);
        while (subqueryViewMatcher.find()) {
            String viewName = subqueryViewMatcher.group(1).toUpperCase();
            sqlInfo.addTableView(viewName);
            System.out.println("      [DEBUG] Найдена вьюха в подзапросе: " + viewName);
        }
        // ========== ПОИСК ВСЕХ ПАКЕТОВ D_PKG_****.**** ==========
        Pattern allPackagesPattern = Pattern.compile(
                "\\b(D_PKG_[A-Z0-9_]+\\.[A-Z0-9_]+)\\b",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher allPackagesMatcher = allPackagesPattern.matcher(upperText);
        Set<String> foundPackages = new LinkedHashSet<>();
        while (allPackagesMatcher.find()) {
            String pkgFunc = allPackagesMatcher.group(1).toUpperCase();
            // Исключаем константы и опции, они обрабатываются отдельно
            if (!pkgFunc.startsWith("D_PKG_CONSTANTS") &&
                    !pkgFunc.startsWith("D_PKG_OPTIONS") &&
                    !pkgFunc.startsWith("D_PKG_OPTION_SPECS")) {
                foundPackages.add(pkgFunc);
                System.out.println("      [DEBUG] Найден пакет (allPackagesPattern): " + pkgFunc);
            }
        }

        for (String pkg : foundPackages) {
            sqlInfo.addPackageFunction(pkg);
        }

        // ========== 3. ПОЛЬЗОВАТЕЛЬСКИЕ ПРОЦЕДУРЫ ==========
        Pattern userProcPattern = Pattern.compile(
                "D_V_USERPROCS\\s+.*?PR_CODE\\s*=\\s*'([^']+)'",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher userProcMatcher = userProcPattern.matcher(cleanText);
        while (userProcMatcher.find()) {
            String prCode = userProcMatcher.group(1);
            if (prCode != null && !prCode.isEmpty()) {
                sqlInfo.addUserProcedure(prCode);
                System.out.println("      [DEBUG] Найдена пользовательская процедура: " + prCode);
            }
        }

        // ========== 4. СОБИРАЕМ ВСЕ D_V_* КАК ПОТЕНЦИАЛЬНЫЕ ВЬЮХИ ==========
        // Улучшенный паттерн для поиска вьюх, включая те, что после FROM
        Pattern viewPattern = Pattern.compile(
                "\\bFROM\\s+(D_V_[A-Za-z0-9_]+)\\b",
                Pattern.CASE_INSENSITIVE
        );
        Matcher viewMatcher = viewPattern.matcher(cleanText);
        Set<String> potentialViews = new LinkedHashSet<>();
        while (viewMatcher.find()) {
            String name = viewMatcher.group(1).toUpperCase();
            potentialViews.add(name);
            System.out.println("      [DEBUG] Найдена вьюха после FROM: " + name);
        }

        // Дополнительный паттерн для поиска вьюх в любом месте
        Pattern anyViewPattern = Pattern.compile("\\b(D_V_[A-Za-z0-9_]+)\\b");
        Matcher anyViewMatcher = anyViewPattern.matcher(upperText);
        while (anyViewMatcher.find()) {
            String name = anyViewMatcher.group(1).toUpperCase();
            if (!name.startsWith("D_PKG_")) {
                potentialViews.add(name);
                System.out.println("      [DEBUG] Найдена вьюха (общий поиск): " + name);
            }
        }

        // ========== 5. СОБИРАЕМ ВСЕ D_PKG_*.XXX КАК ПОТЕНЦИАЛЬНЫЕ ПАКЕТНЫЕ ФУНКЦИИ ==========
        // ИСКЛЮЧАЕМ D_PKG_CONSTANTS, D_PKG_OPTIONS, D_PKG_OPTION_SPECS (уже обработаны)
        Pattern pkgPattern = Pattern.compile("\\b[Dd]_[Pp][Kk][Gg]_(?!CONSTANTS|OPTIONS|OPTION_SPECS)[A-Za-z0-9_]+\\s*\\.\\s*[A-Za-z0-9_]+\\b");
        Matcher pkgMatcher = pkgPattern.matcher(upperText);
        Set<String> potentialPackageFunctions = new LinkedHashSet<>();
        while (pkgMatcher.find()) {
            String fullName = pkgMatcher.group().toUpperCase();
            potentialPackageFunctions.add(fullName);
        }
        Pattern packageInWherePattern = Pattern.compile(
                "\\b(D_PKG_[A-Z_]+\\.[A-Z_]+)\\s*\\(",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher packageMatcher = packageInWherePattern.matcher(upperText);
        while (packageMatcher.find()) {
            String packageFunc = packageMatcher.group(1).toUpperCase();
            if (!packageFunc.contains("D_PKG_CONSTANTS") &&
                    !packageFunc.contains("D_PKG_OPTIONS") &&
                    !packageFunc.contains("D_PKG_OPTION_SPECS")) {
                sqlInfo.addPackageFunction(packageFunc);
                System.out.println("      [DEBUG] Найден пакет в условии: " + packageFunc);
            }
        }
        // ========== 6. СОБИРАЕМ ВСЕ ОСТАЛЬНЫЕ D_* ОБЪЕКТЫ ==========
        Pattern otherPattern = Pattern.compile("\\b[Dd]_[A-Za-z0-9_]+\\b");
        Matcher otherMatcher = otherPattern.matcher(upperText);
        Set<String> potentialUnknown = new LinkedHashSet<>();
        while (otherMatcher.find()) {
            String name = otherMatcher.group().toUpperCase();
            // Пропускаем вьюхи, пакетные функции, системные опции и константы
            if (!name.startsWith("D_V_") &&
                    !name.startsWith("D_PKG_") &&
                    !name.startsWith("D_V_URPRIVS") &&
                    !isSystemAlias(name)) {
                potentialUnknown.add(name);
            }
        }

        // ========== 7. СПИСОК ОБЪЕКТОВ, КОТОРЫЕ ДОЛЖНЫ БЫТЬ В "РАЗОБРАТЬ АНАЛИТИКОМ" ==========
        Set<String> forcedToUnknown = new HashSet<>(Arrays.asList(
                "D_PKG_STD.FRM_D",
                "D_PKG_STD.FRM_T",
                "D_PKG_STR_TOOLS.FIO",
                "D_P_EXC",
                "D_C_ID",
                "D_STRAGG_EX",
                "D_PKG_STD.TREF",
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

        // ========== 8. РАСПРЕДЕЛЯЕМ ОБЪЕКТЫ ==========
        // Вьюхи
        for (String view : potentialViews) {
            if (!forcedToUnknown.contains(view)) {
                sqlInfo.addTableView(view);
                System.out.println("      [DEBUG] Добавлена вьюха: " + view);
            } else {
                sqlInfo.addUnknownObject(view);
                System.out.println("      [DEBUG] Добавлен в unknown (вьюха): " + view);
            }
        }

        // Пакетные функции - проверяем, не нужно ли переместить в unknown
        for (String pkgFunc : potentialPackageFunctions) {
            if (!forcedToUnknown.contains(pkgFunc)) {
                sqlInfo.addPackageFunction(pkgFunc);
                System.out.println("      [DEBUG] Добавлен пакет/функция: " + pkgFunc);
            } else {
                sqlInfo.addUnknownObject(pkgFunc);
                System.out.println("      [DEBUG] Добавлен в unknown (пакет): " + pkgFunc);
            }
        }

        // Остальные объекты
        for (String unknown : potentialUnknown) {
            if (!forcedToUnknown.contains(unknown)) {
                // Проверяем, не является ли это константой или системной опцией
                if (!systemOptions.contains(unknown) && !constants.contains(unknown)) {
                    sqlInfo.addUnknownObject(unknown);
                    System.out.println("      [DEBUG] Добавлен в unknown: " + unknown);
                }
            } else {
                sqlInfo.addUnknownObject(unknown);
                System.out.println("      [DEBUG] Добавлен в unknown (forced): " + unknown);
            }
        }




        // Выводим статистику
        System.out.println("      [DEBUG] ИТОГО: констант=" + constants.size() +
                ", системных опций=" + systemOptions.size() +
                ", вьюх=" + potentialViews.size() +
                ", пакетов=" + potentialPackageFunctions.size() +
                ", unknown=" + potentialUnknown.size());
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
                "IS_CANCELED", "CANC_REASON_CODE", "CANC_REASON_NAME", "VISIT_DATE",
                "HOSP_HISTORY_ID", "HOSP_HISTORY_DEP_ID", "AGENT_ID", "PERSMEDCARD_ID"
        ));
        return aliases.contains(name);
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