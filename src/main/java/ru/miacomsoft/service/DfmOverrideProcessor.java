package ru.miacomsoft.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.regex.*;

/**
 * Обработчик файлов .dfrm (частичное переопределение форм)
 * Поддерживает операции:
 * - node target="..." pos="after/before/replace/delete"
 * - attr target="..." pos="add/replace" name="..." value="..."
 */
public class DfmOverrideProcessor {

    // Регулярное выражение для парсинга node тэгов в .dfrm
    private static final Pattern NODE_PATTERN = Pattern.compile(
            "<node\\s+target=[\"']([^\"']+)[\"']\\s+pos=[\"']([^\"']+)[\"']\\s*>(.*?)</node>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Регулярное выражение для парсинга attr тэгов в .dfrm
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "<attr\\s+target=[\"']([^\"']+)[\"']\\s+pos=[\"']([^\"']+)[\"']\\s+name=[\"']([^\"']+)[\"']\\s+value=[\"']([^\"']*)[\"']\\s*/?>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );


    // Константы для настройки логирования
    private static final boolean SUPPRESS_TARGET_NOT_FOUND = true;  // Подавлять сообщения "Target not found"
    private static final boolean SUPPRESS_UNKNOWN_POSITION = true;  // Подавлять сообщения "Unknown position"
    private static final int MAX_TARGET_NOT_FOUND_LOG = 5;          // Максимальное количество логов для одного target
    private static final int MAX_UNKNOWN_POSITION_LOG = 3;          // Максимальное количество логов для одной позиции

    // Статистика для ограничения логов
    private static final Map<String, Integer> targetNotFoundCount = new HashMap<>();
    private static final Map<String, Integer> unknownPositionCount = new HashMap<>();


    /**
     * Операция переопределения
     */
    public static class OverrideOperation {
        private String type;      // "node" или "attr"
        private String target;    // target атрибут
        private String position;  // after, before, replace, delete, add
        private String name;      // для attr - имя атрибута
        private String value;     // для attr - значение атрибута
        private String content;   // для node - содержимое для вставки

        // Getters
        public String getType() { return type; }
        public String getTarget() { return target; }
        public String getPosition() { return position; }
        public String getName() { return name; }
        public String getValue() { return value; }
        public String getContent() { return content; }

        public static OverrideOperation createNode(String target, String position, String content) {
            OverrideOperation op = new OverrideOperation();
            op.type = "node";
            op.target = target;
            op.position = position;
            op.content = content;
            return op;
        }

        public static OverrideOperation createAttr(String target, String position, String name, String value) {
            OverrideOperation op = new OverrideOperation();
            op.type = "attr";
            op.target = target;
            op.position = position;
            op.name = name;
            op.value = value;
            return op;
        }

        @Override
        public String toString() {
            if ("node".equals(type)) {
                return String.format("node target='%s' pos='%s'", target, position);
            } else {
                return String.format("attr target='%s' pos='%s' name='%s' value='%s'",
                        target, position, name, value);
            }
        }
    }

    /**
     * Парсинг .dfrm файла в список операций переопределения
     */
    public List<OverrideOperation> parseDfrmFile(String dfrmContent) {
        List<OverrideOperation> operations = new ArrayList<>();

        if (dfrmContent == null || dfrmContent.isEmpty()) {
            return operations;
        }

        // ОБЕРТЫВАЕМ ВЕСЬ ФАЙЛ В DIV ДЛЯ КОРРЕКТНОГО ПАРСИНГА
        String wrappedContent = wrapFullDfrmContent(dfrmContent);

        // Парсим node тэги
        Matcher nodeMatcher = NODE_PATTERN.matcher(wrappedContent);
        while (nodeMatcher.find()) {
            String target = nodeMatcher.group(1);
            String position = nodeMatcher.group(2).toLowerCase();
            String content = nodeMatcher.group(3).trim();

            operations.add(OverrideOperation.createNode(target, position, content));
        }

        // Парсим attr тэги
        Matcher attrMatcher = ATTR_PATTERN.matcher(wrappedContent);
        while (attrMatcher.find()) {
            String target = attrMatcher.group(1);
            String position = attrMatcher.group(2).toLowerCase();
            String name = attrMatcher.group(3);
            String value = attrMatcher.group(4);

            operations.add(OverrideOperation.createAttr(target, position, name, value));
        }

        return operations;
    }

    /**
     * Обернуть весь .dfrm файл в корневой DIV для корректного парсинга
     */
    private String wrapFullDfrmContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "<div></div>";
        }

        String trimmed = content.trim();

        // Если уже обернуто в один корневой элемент, возвращаем как есть
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            // Проверяем, есть ли только один корневой элемент
            int openCount = trimmed.length() - trimmed.replace("<", "").length();
            int closeCount = trimmed.length() - trimmed.replace(">", "").length();
            if (openCount <= closeCount + 2) {
                return trimmed;
            }
        }

        // Обертываем в div для парсинга
        return "<div class=\"dfrm-root\">" + trimmed + "</div>";
    }

    /**
     * Применить операции переопределения к базовому XML
     * @param baseContent Базовое содержимое формы
     * @param dfrmContent Содержимое .dfrm файла
     * @return Измененное содержимое
     */
    public String applyOverrides(String baseContent, String dfrmContent) {
        if (baseContent == null || dfrmContent == null) {
            return baseContent;
        }

        List<OverrideOperation> operations = parseDfrmFile(dfrmContent);
        if (operations.isEmpty()) {
            return baseContent;
        }

        try {
            // Парсим базовый XML в Jsoup документ
            Document doc = Jsoup.parse(baseContent, "", org.jsoup.parser.Parser.xmlParser());

            // Применяем каждую операцию
            for (OverrideOperation op : operations) {
                try {
                    applyOperation(doc, op);
                } catch (Exception e) {
                    System.err.println("Error applying operation: " + op + " - " + e.getMessage());
                }
            }

            // Возвращаем измененный XML
            return doc.html();

        } catch (Exception e) {
            System.err.println("Error applying overrides: " + e.getMessage());
            return baseContent; // Возвращаем базовое содержимое в случае ошибки
        }
    }

    /**
     * Применить одну операцию к документу
     */
    private void applyOperation(Document doc, OverrideOperation op) {
        if ("node".equals(op.getType())) {
            applyNodeOperation(doc, op);
        } else if ("attr".equals(op.getType())) {
            applyAttrOperation(doc, op);
        }
    }

    /**
     * Применить операцию над узлом (node)
     */
    private void applyNodeOperation(Document doc, OverrideOperation op) {
        String target = op.getTarget();
        String position = op.getPosition();
        String content = op.getContent();

        // Ищем целевой элемент по атрибуту name
        Elements targets = doc.select("[name=" + target + "]");
        if (targets.isEmpty()) {
            // Пробуем искать по другим атрибутам
            targets = doc.select("[id=" + target + "]");
        }
        if (targets.isEmpty()) {
            // Пробуем искать как тэг
            targets = doc.select(target);
        }

        if (targets.isEmpty()) {
            // Фильтруем сообщения "Target not found"
            if (!SUPPRESS_TARGET_NOT_FOUND) {
                int count = targetNotFoundCount.getOrDefault(target, 0);
                if (count < MAX_TARGET_NOT_FOUND_LOG) {
                    System.err.println("Target not found: " + target);
                    targetNotFoundCount.put(target, count + 1);
                } else if (count == MAX_TARGET_NOT_FOUND_LOG) {
                    System.err.println("Target not found: " + target + " (дальнейшие сообщения подавлены)");
                    targetNotFoundCount.put(target, count + 1);
                }
            }
            return;
        }

        Element targetElement = targets.first();
        Element parent = targetElement.parent();

        if (parent == null) {
            System.err.println("Parent element is null for target: " + target);
            return;
        }

        //*************************************************************************************************
        //*************************************************************************************************
        //*************************************************************************************************
        //*************************************************************************************************
        //*************************************************************************************************
        if (1==1) return; // отключить замену ЮЗЕР форм
        switch (position) {
            case "after":
                String afterHtml = wrapContent(content);
                Elements afterChildren = Jsoup.parse(afterHtml, "", org.jsoup.parser.Parser.xmlParser()).body().children();
                if (!afterChildren.isEmpty()) {
                    parent.insertChildren(targetElement.siblingIndex() + 1, afterChildren);
                }
                break;

            case "before":
                String beforeHtml = wrapContent(content);
                Elements beforeChildren = Jsoup.parse(beforeHtml, "", org.jsoup.parser.Parser.xmlParser()).body().children();
                if (!beforeChildren.isEmpty()) {
                    parent.insertChildren(targetElement.siblingIndex(), beforeChildren);
                }
                break;

            case "replace":
                String replaceHtml = wrapContent(content);
                Element newElement = Jsoup.parse(replaceHtml, "", org.jsoup.parser.Parser.xmlParser()).body().children().first();

                if (newElement != null) {
                    targetElement.replaceWith(newElement);
                } else {
                    System.err.println("New element is null for replace operation, removing target: " + target);
                    targetElement.remove();
                }
                break;

            case "delete":
                // временно заменить
                //targetElement.remove();
                break;

            case "end":
                String endHtml = wrapContent(content);
                Elements endChildren = Jsoup.parse(endHtml, "", org.jsoup.parser.Parser.xmlParser()).body().children();
                if (!endChildren.isEmpty()) {
                    for (Element child : endChildren) {
                        parent.appendChild(child);
                    }
                }
                break;

            case "start":
            case "begin":
                String startHtml = wrapContent(content);
                Elements startChildren = Jsoup.parse(startHtml, "", org.jsoup.parser.Parser.xmlParser()).body().children();
                if (!startChildren.isEmpty()) {
                    if (parent.children().isEmpty()) {
                        for (Element child : startChildren) {
                            parent.appendChild(child);
                        }
                    } else {
                        parent.insertChildren(0, startChildren);
                    }
                }
                break;

            default:
                // Фильтруем сообщения "Unknown position"
                if (!SUPPRESS_UNKNOWN_POSITION) {
                    int count = unknownPositionCount.getOrDefault(position, 0);
                    if (count < MAX_UNKNOWN_POSITION_LOG) {
                        System.err.println("Unknown position: " + position);
                        unknownPositionCount.put(position, count + 1);
                    } else if (count == MAX_UNKNOWN_POSITION_LOG) {
                        System.err.println("Unknown position: " + position + " (дальнейшие сообщения подавлены)");
                        unknownPositionCount.put(position, count + 1);
                    }
                }
        }
        //*************************************************************************************************
        //*************************************************************************************************
        //*************************************************************************************************
        //*************************************************************************************************
        //*************************************************************************************************

    }

    /**
     * Применить операцию над атрибутом (attr)
     */
    /**
     * Применить операцию над атрибутом (attr)
     */
    private void applyAttrOperation(Document doc, OverrideOperation op) {
        String target = op.getTarget();
        String position = op.getPosition();
        String attrName = op.getName();
        String attrValue = op.getValue();

        // Ищем целевой элемент
        Elements targets = doc.select("[name=" + target + "]");
        if (targets.isEmpty()) {
            targets = doc.select("[id=" + target + "]");
        }
        if (targets.isEmpty()) {
            targets = doc.select(target);
        }

        if (targets.isEmpty()) {
            // Фильтруем сообщения "Target not found for attr"
            if (!SUPPRESS_TARGET_NOT_FOUND) {
                int count = targetNotFoundCount.getOrDefault("attr:" + target, 0);
                if (count < MAX_TARGET_NOT_FOUND_LOG) {
                    System.err.println("Target not found for attr: " + target);
                    targetNotFoundCount.put("attr:" + target, count + 1);
                } else if (count == MAX_TARGET_NOT_FOUND_LOG) {
                    System.err.println("Target not found for attr: " + target + " (дальнейшие сообщения подавлены)");
                    targetNotFoundCount.put("attr:" + target, count + 1);
                }
            }
            return;
        }

        Element targetElement = targets.first();

        switch (position) {
            case "add":
            case "replace":
                targetElement.attr(attrName, attrValue);
                break;

            case "delete":
                targetElement.removeAttr(attrName);
                break;

            case "append":
                String currentValue = targetElement.attr(attrName);
                if (currentValue != null && !currentValue.isEmpty()) {
                    targetElement.attr(attrName, currentValue + attrValue);
                } else {
                    targetElement.attr(attrName, attrValue);
                }
                break;

            default:
                if (!SUPPRESS_UNKNOWN_POSITION) {
                    int count = unknownPositionCount.getOrDefault("attr:" + position, 0);
                    if (count < MAX_UNKNOWN_POSITION_LOG) {
                        System.err.println("Unknown attr position: " + position);
                        unknownPositionCount.put("attr:" + position, count + 1);
                    } else if (count == MAX_UNKNOWN_POSITION_LOG) {
                        System.err.println("Unknown attr position: " + position + " (дальнейшие сообщения подавлены)");
                        unknownPositionCount.put("attr:" + position, count + 1);
                    }
                }
        }
    }

    /**
     * Обертка содержимого для корректного парсинга в Jsoup
     */
    private String wrapContent(String content) {
        // Если содержимое пустое, возвращаем пустой div
        if (content == null || content.trim().isEmpty()) {
            return "<div></div>";
        }

        String trimmed = content.trim();

        // Если содержимое уже является валидным XML с одним корневым элементом
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            // Проверяем, есть ли закрывающий тэг для первого открывающего
            int firstClose = trimmed.indexOf('>');
            int lastOpen = trimmed.lastIndexOf('<');

            // Если есть закрывающий тэг в конце и это XML
            if (firstClose > 0 && lastOpen > firstClose) {
                String firstTag = trimmed.substring(1, firstClose).split("\\s")[0];
                String lastTag = trimmed.substring(lastOpen + 1, trimmed.length() - 1);
                if (firstTag.equals(lastTag)) {
                    return trimmed; // Уже корректный XML
                }
            }

            // Если несколько корневых элементов, обертываем
            if (trimmed.indexOf('<', 1) < trimmed.lastIndexOf('<')) {
                return "<root>" + trimmed + "</root>";
            }
            return trimmed;
        }

        // Иначе обертываем в div
        return "<div>" + trimmed + "</div>";
    }
}