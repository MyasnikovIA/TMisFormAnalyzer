package ru.miacomsoft.service;

import ru.miacomsoft.model.FormInfo;
import ru.miacomsoft.model.SqlInfo;

import java.nio.file.Path;
import java.util.*;
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

    public TmisFormAnalyzerService(String projectRoot) {
        this.projectRoot = projectRoot;
        this.scannerService = new FileScannerService(projectRoot);
        this.userFormsResolver = new UserFormsResolver(scannerService);
        this.sqlExtractor = new SqlExtractorService();
    }

    /**
     * Проанализировать форму по заданному пути
     * @param formPath Путь к форме (относительно Forms/ или полный)
     * @return FormInfo с результатами анализа, или null если форма не найдена
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

        List<SqlInfo> sqlQueries = sqlExtractor.extractAllSqlQueries(
                baseContent, baseFormPathObj.toString(), null
        );

        for (SqlInfo sql : sqlQueries) {
            formInfo.addSqlQuery(sql);
            for (String tv : sql.getTablesViews()) formInfo.addTableView(tv);
            for (String pf : sql.getPackagesFunctions()) formInfo.addPackageFunction(pf);
            for (String proc : sql.getUserProcedures()) formInfo.addUserProcedure(proc);  // ЭТО ДОЛЖНО БЫТЬ
            for (String opt : sql.getSystemOptions()) formInfo.addSystemOption(opt);
            for (String unknown : sql.getUnknownObjects()) formInfo.addUnknownObject(unknown);
        }

        // Если есть ПОЛНОЕ переопределение, можно также проанализировать и его
        if (formInfo.isFullyReplaced() && formInfo.getReplacementPath() != null) {
            String userContent = scannerService.readFileContent(Path.of(formInfo.getReplacementPath()));
            if (userContent != null) {
                // Анализируем пользовательскую форму (опционально)
                // Можно добавить в отдельный список или сравнить
            }
        }

        return formInfo;
    }

    /**
     * Нормализация пути формы
     */
    private String normalizeFormPath(String formPath) {
        String normalized = formPath;

        // Убираем префикс Forms/ если есть
        if (normalized.startsWith("Forms/")) {
            normalized = normalized.substring(6);
        }

        // Убираем префикс / если есть
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        // Добавляем .frm если нужно
        if (!normalized.endsWith(".frm") && !normalized.endsWith(".dfrm")) {
            normalized = normalized + ".frm";
        }

        return normalized;
    }

    /**
     * Извлечь SubForm из содержимого формы
     */
    private void extractSubForms(String content, FormInfo formInfo) {
        if (content == null || content.isEmpty()) return;

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
            String path = d3Matcher.group(1);
            if (isValidFormPath(path)) {
                formInfo.addSubForm(path);
            }
        }

        Matcher m2Matcher = m2SubFormPattern.matcher(content);
        while (m2Matcher.find()) {
            String path = m2Matcher.group(1);
            if (isValidFormPath(path)) {
                formInfo.addSubForm(path);
            }
        }
    }

    /**
     * Извлечь JS формы (openWindow, openD3Form) из содержимого формы
     * Поддерживает различные форматы вызова и ищет скрипты во всех CDATA секциях
     */
    private void extractJsForms(String content, FormInfo formInfo) {
        if (content == null || content.isEmpty()) return;

        Set<String> foundForms = new LinkedHashSet<>();

        // Ищем ВСЕ CDATA секции в любом месте формы
        Pattern allCDataPattern = Pattern.compile(
                "<!\\[CDATA\\[(.*?)\\]\\]>",
                Pattern.DOTALL
        );

        Matcher cdataMatcher = allCDataPattern.matcher(content);
        while (cdataMatcher.find()) {
            String scriptContent = cdataMatcher.group(1);
            foundForms.addAll(extractFormsFromScript(scriptContent));
        }

        // Дополнительно ищем в атрибутах onclick и других событиях
        Pattern onclickPattern = Pattern.compile(
                "onclick\\s*=\\s*['\"]([^'\"]*(?:openWindow|openD3Form)[^'\"]*)['\"]",
                Pattern.DOTALL
        );
        Matcher onclickMatcher = onclickPattern.matcher(content);
        while (onclickMatcher.find()) {
            String onclickContent = onclickMatcher.group(1);
            foundForms.addAll(extractFormsFromScript(onclickContent));
        }

        // Ищем прямые вызовы в XML атрибутах
        Pattern directCallPattern = Pattern.compile(
                "(?:openWindow|openD3Form)\\s*\\([^)]*\\)",
                Pattern.DOTALL
        );
        Matcher directMatcher = directCallPattern.matcher(content);
        while (directMatcher.find()) {
            String callContent = directMatcher.group(0);
            foundForms.addAll(extractFormsFromScript(callContent));
        }

        // Добавляем найденные формы в FormInfo
        for (String form : foundForms) {
            if (isValidFormPath(form)) {
                formInfo.addJsForm(form);
            }
        }
    }

    /**
     * Извлечь все пути к формам из JS скрипта
     * Поддерживает различные форматы вызова
     */
    private Set<String> extractFormsFromScript(String scriptContent) {
        Set<String> forms = new LinkedHashSet<>();

        if (scriptContent == null || scriptContent.isEmpty()) {
            return forms;
        }

        // Паттерн для openWindow со строковым параметром: openWindow('path/to/form.frm', ...)
        Pattern openWindowStringPattern = Pattern.compile(
                "openWindow\\s*\\(\\s*['\"]([^'\"]+(?:\\.frm)?)['\"]",
                Pattern.DOTALL
        );

        // Паттерн для openWindow с объектом: openWindow({name: 'path/to/form.frm', ...}, ...)
        Pattern openWindowObjectPattern = Pattern.compile(
                "openWindow\\s*\\(\\s*\\{\\s*name\\s*:\\s*['\"]([^'\"]+(?:\\.frm)?)['\"]",
                Pattern.DOTALL
        );

        // Паттерн для openD3Form со строковым параметром
        Pattern openD3StringPattern = Pattern.compile(
                "openD3Form\\s*\\(\\s*['\"]([^'\"]+(?:\\.frm)?)['\"]",
                Pattern.DOTALL
        );

        // Паттерн для openD3Form с объектом: openD3Form({name: 'path/to/form.frm', ...}, ...)
        Pattern openD3ObjectPattern = Pattern.compile(
                "openD3Form\\s*\\(\\s*\\{\\s*name\\s*:\\s*['\"]([^'\"]+(?:\\.frm)?)['\"]",
                Pattern.DOTALL
        );

        // Паттерн для openD3Form с тремя параметрами: openD3Form('path', true, {vars: {...}})
        Pattern openD3ThreeParamsPattern = Pattern.compile(
                "openD3Form\\s*\\(\\s*['\"]([^'\"]+(?:\\.frm)?)['\"]\\s*,\\s*true\\s*,\\s*\\{",
                Pattern.DOTALL
        );

        // Собираем все совпадения
        Matcher m1 = openWindowStringPattern.matcher(scriptContent);
        while (m1.find()) {
            String path = normalizeFormPathFromJs(m1.group(1));
            if (path != null) forms.add(path);
        }

        Matcher m2 = openWindowObjectPattern.matcher(scriptContent);
        while (m2.find()) {
            String path = normalizeFormPathFromJs(m2.group(1));
            if (path != null) forms.add(path);
        }

        Matcher m3 = openD3StringPattern.matcher(scriptContent);
        while (m3.find()) {
            String path = normalizeFormPathFromJs(m3.group(1));
            if (path != null) forms.add(path);
        }

        Matcher m4 = openD3ObjectPattern.matcher(scriptContent);
        while (m4.find()) {
            String path = normalizeFormPathFromJs(m4.group(1));
            if (path != null) forms.add(path);
        }

        Matcher m5 = openD3ThreeParamsPattern.matcher(scriptContent);
        while (m5.find()) {
            String path = normalizeFormPathFromJs(m5.group(1));
            if (path != null) forms.add(path);
        }

        return forms;
    }

    /**
     * Нормализация пути формы из JS вызова
     */
    private String normalizeFormPathFromJs(String formPath) {
        if (formPath == null || formPath.trim().isEmpty()) {
            return null;
        }

        String normalized = formPath.trim();

        // Убираем возможные кавычки
        normalized = normalized.replaceAll("^['\"]|['\"]$", "");

        // Если путь содержит параметры или vars - обрезаем
        if (normalized.contains(",") || normalized.contains("}")) {
            return null;
        }

        // Добавляем .frm если нет расширения и это не похоже на переменную
        if (!normalized.endsWith(".frm") && !normalized.endsWith(".dfrm") &&
                !normalized.contains("'") && !normalized.contains("\"") &&
                !normalized.contains("+") && !normalized.contains("getVar") &&
                normalized.contains("/")) {
            normalized = normalized + ".frm";
        }

        // Игнорируем вызовы с переменными
        if (normalized.contains("getVar") || normalized.contains("getValue") ||
                normalized.contains("$") || normalized.contains("' +") ||
                normalized.contains("\" +")) {
            return null;
        }

        return normalized;
    }
    /**
     * Нормализация пути формы из JS вызовов
     * Убирает лишние символы и приводит к стандартному виду
     */
    private String normalizeJsFormPath(String formPath) {
        if (formPath == null || formPath.trim().isEmpty()) {
            return "";
        }

        String normalized = formPath.trim();

        // Убираем возможные пробелы и кавычки
        normalized = normalized.replaceAll("['\"]", "");

        // Убираем .frm если есть (добавим позже, если нужно)
        // but keep original extension

        return normalized;
    }

    /**
     * Проверка валидности пути формы
     */
    private boolean isValidFormPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        // Игнорируем специальные значения
        if ("components_m2".equals(path) || "".equals(path.trim())) {
            return false;
        }
        // Игнорируем строки, которые выглядят как JavaScript код
        if (path.contains("function") || path.contains("setValue") ||
                path.contains("executeAction") || path.contains("getControl") ||
                path.contains("printReportByCode") || path.contains("SysDate") ||
                path.contains("refreshDataSet") || path.contains("getDataSet") ||
                path.contains("showAlert") || path.contains("confirm") ||
                path.contains("closeWindow") || path.contains("addListener") ||
                path.contains("getVar") || path.contains("setVar")) {
            return false;
        }
        return path.contains("/") || path.endsWith(".frm") || path.endsWith(".dfrm") ||
                path.matches("^[a-zA-Z_/]+$");
    }
}