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
        extractD3ApiShowForms(baseContent, formInfo);
        extractUnitCompositions(baseContent, formInfo);

        List<SqlInfo> sqlQueries = sqlExtractor.extractAllSqlQueries(
                baseContent, baseFormPathObj.toString(), null
        );

// После добавления констант в formInfo
        for (SqlInfo sql : sqlQueries) {
            formInfo.addSqlQuery(sql);
            for (String tv : sql.getTablesViews()) formInfo.addTableView(tv);
            for (String pf : sql.getPackagesFunctions()) formInfo.addPackageFunction(pf);
            for (String proc : sql.getUserProcedures()) formInfo.addUserProcedure(proc);
            for (String opt : sql.getSystemOptions()) formInfo.addSystemOption(opt);
            for (String unknown : sql.getUnknownObjects()) formInfo.addUnknownObject(unknown);
            for (String constant : sql.getConstants()) {
                formInfo.addConstant(constant);
                System.out.println("[DEBUG] Добавлена константа в FormInfo: " + constant + " для формы " + formPath);
            }
        }
        // Если есть ПОЛНОЕ переопределение, можно также проанализировать и его
        if (formInfo.isFullyReplaced() && formInfo.getReplacementPath() != null) {
            String userContent = scannerService.readFileContent(Path.of(formInfo.getReplacementPath()));
            if (userContent != null) {
                // Анализируем пользовательскую форму (опционально)
                // Можно добавить в отдельный список или сравнить
            }
        }

        // После извлечения SQL запросов, добавьте принудительный поиск констант
        Set<String> forceConstants = new LinkedHashSet<>();
        Pattern constPattern = Pattern.compile("D_PKG_CONSTANTS\\.SEARCH_(?:STR|NUM|DATE)\\s*\\(\\s*'([^']+)'", Pattern.DOTALL);
        Matcher constMatcher = constPattern.matcher(baseContent);
        while (constMatcher.find()) {
            String constant = constMatcher.group(1);
            forceConstants.add(constant);
            System.out.println("Принудительно найдена константа: " + constant);
        }

        for (String constant : forceConstants) {
            formInfo.addConstant(constant);
        }

        System.out.println("[DEBUG] Всего констант в FormInfo для формы " + formPath + ": " + formInfo.getConstants().size());

        // ПРЯМОЙ ПОИСК КОНСТАНТ В СОДЕРЖИМОМ ФОРМЫ
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
                System.out.println("[DEBUG DIRECT] Найдена константа в форме: " + constant);
            }
        }

        for (String constant : directConstants) {
            formInfo.addConstant(constant);
        }

        System.out.println("[DEBUG DIRECT] Всего констант найдено прямым поиском: " + directConstants.size());


        return formInfo;
    }

    /**
     * Извлечь формы из D3Api.showForm вызовов
     * Форматы:
     * - D3Api.showForm('Reports/sign_data', ...)
     * - D3Api.showForm('help_view', null, ...)
     * - D3Api.showForm('System/change_password')
     */
    private void extractD3ApiShowForms(String content, FormInfo formInfo) {
        if (content == null || content.isEmpty()) return;

        Set<String> foundForms = new LinkedHashSet<>();

        // Универсальный паттерн для D3Api.showForm
        // Ищет: D3Api.showForm('путь/к/форме', ...)
        Pattern d3ApiPattern = Pattern.compile(
                "D3Api\\.showForm\\s*\\(\\s*['\"]([^'\"]+(?:\\.frm)?)['\"]",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = d3ApiPattern.matcher(content);
        while (matcher.find()) {
            String formPath = matcher.group(1);
            // Добавляем .frm если нет расширения
            // if (!formPath.endsWith(".frm") && !formPath.endsWith(".dfrm")) {
            //     formPath = formPath + ".frm";
            // }
            formPath = "(D3Api.showForm) "+formPath ;

            String normalized = normalizeFormPathFromJs(formPath);
            if (normalized != null && isValidFormPath(normalized)) {
                foundForms.add(normalized);
            }
        }

        // Добавляем найденные формы в subForm (или можно создать отдельный блок)
        for (String form : foundForms) {
            formInfo.addSubForm(form);
        }

        if (!foundForms.isEmpty()) {
            System.out.println("  Найдено D3Api.showForm: " + foundForms.size());
        }
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
     * Ищет везде: в CDATA, атрибутах и тексте
     */
    private void extractJsForms(String content, FormInfo formInfo) {
        if (content == null || content.isEmpty()) return;

        Set<String> foundForms = new LinkedHashSet<>();

        // Паттерн для поиска в любом контексте (атрибуты, CDATA, текст)
        Pattern anyContextPattern = Pattern.compile(
                "(?:openWindow|openD3Form)\\s*\\([^)]*['\"]([^'\"]+\\.frm)['\"][^)]*\\)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = anyContextPattern.matcher(content);
        while (matcher.find()) {
            String formPath = matcher.group(1);
            String normalized = normalizeFormPathFromJs(formPath);
            if (normalized != null && isValidFormPath(normalized)) {
                foundForms.add(normalized);
            }
        }

        // Дополнительный паттерн для поиска без .frm расширения в вызове
        Pattern noExtensionPattern = Pattern.compile(
                "(?:openWindow|openD3Form)\\s*\\([^)]*['\"]([^'\"]+/(?:[^'\"]+))['\"][^)]*\\)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        Matcher matcher2 = noExtensionPattern.matcher(content);
        while (matcher2.find()) {
            String formPath = matcher2.group(1);
            if (!formPath.endsWith(".frm") && !formPath.endsWith(".dfrm")) {
                formPath = formPath + ".frm";
            }
            String normalized = normalizeFormPathFromJs(formPath);
            if (normalized != null && isValidFormPath(normalized)) {
                foundForms.add(normalized);
            }
        }

        // Добавляем найденные формы в FormInfo
        for (String form : foundForms) {
            formInfo.addJsForm(form);
        }
    }

    /**
     * Извлечь значение атрибута из строки вида attr="value" или attr='value'
     */
    private String extractAttributeValue(String attrString) {
        // Ищем кавычки и берем содержимое между ними
        Pattern quotePattern = Pattern.compile("=['\"]([^'\"]*)['\"]");
        Matcher matcher = quotePattern.matcher(attrString);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
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
     * Извлечь композиции из UnitEdit компонентов
     * Форматы:
     * - M2: <component cmptype="UnitEdit" name="..." unit="INJURE_KINDS" composition="DEFAULT" .../>
     * - D3: <cmpUnitEdit name="..." unit="INJURE_KINDS" composition="DEFAULT" .../>
     */
    private void extractUnitCompositions(String content, FormInfo formInfo) {
        if (content == null || content.isEmpty()) return;

        Set<String> compositions = new LinkedHashSet<>();

        // Паттерн для M2 синтаксиса
        Pattern m2UnitEditPattern = Pattern.compile(
                "<component\\s+cmptype\\s*=\\s*[\"']UnitEdit[\"'][^>]*?\\s+unit\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?\\s+composition\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Паттерн для D3 синтаксиса
        Pattern d3UnitEditPattern = Pattern.compile(
                "<cmpUnitEdit[^>]*?\\s+unit\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?\\s+composition\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );

        // Обработка M2
        Matcher m2Matcher = m2UnitEditPattern.matcher(content);
        while (m2Matcher.find()) {
            String unit = m2Matcher.group(1);
            String composition = m2Matcher.group(2);
            compositions.add(String.format("        unit=\"%s\"  composition=\"%s\"", unit, composition));
        }

        // Обработка D3
        Matcher d3Matcher = d3UnitEditPattern.matcher(content);
        while (d3Matcher.find()) {
            String unit = d3Matcher.group(1);
            String composition = d3Matcher.group(2);
            compositions.add(String.format("        unit=\"%s\"  composition=\"%s\"", unit, composition));
        }

        // Добавляем в FormInfo
        for (String comp : compositions) {
            formInfo.addUnitComposition(comp);
        }

        if (!compositions.isEmpty()) {
            System.out.println("  Найдено композиций UnitEdit: " + compositions.size());
        }
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