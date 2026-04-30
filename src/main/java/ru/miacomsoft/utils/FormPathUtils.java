package ru.miacomsoft.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Утилиты для работы с путями форм T-MIS
 */
public class FormPathUtils {

    private static final Pattern FORM_PATH_PATTERN = Pattern.compile(
            "^([A-Za-z0-9_/]+)\\.(frm|dfrm)$"
    );

    /**
     * Нормализовать путь формы
     * @param path Исходный путь (может быть с Forms/ или без, с расширением или без)
     * @return Нормализованный путь относительно Forms/
     */
    public static String normalizeFormPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }

        String normalized = path.trim();

        // Убираем префикс Forms/ если есть
        if (normalized.startsWith("Forms/")) {
            normalized = normalized.substring(6);
        }

        // Убираем префикс / если есть
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        // Добавляем .frm если нет расширения
        if (!normalized.endsWith(".frm") && !normalized.endsWith(".dfrm")) {
            normalized = normalized + ".frm";
        }

        return normalized;
    }

    /**
     * Получить имя формы без расширения
     */
    public static String getFormNameWithoutExtension(String formPath) {
        String normalized = normalizeFormPath(formPath);
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex > 0) {
            return normalized.substring(0, dotIndex);
        }
        return normalized;
    }

    /**
     * Получить расширение формы
     */
    public static String getFormExtension(String formPath) {
        String normalized = normalizeFormPath(formPath);
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < normalized.length() - 1) {
            return normalized.substring(dotIndex + 1);
        }
        return "frm";
    }

    /**
     * Получить путь к каталогу .d для формы
     */
    public static String getDotDPath(String formPath) {
        String nameWithoutExt = getFormNameWithoutExtension(formPath);
        return nameWithoutExt + ".d";
    }

    /**
     * Проверка валидности пути формы
     */
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
        String lowerPath = path.toLowerCase();
        if (lowerPath.contains("function") || lowerPath.contains("setvalue") ||
                lowerPath.contains("executeaction") || lowerPath.contains("getcontrol") ||
                lowerPath.contains("printreportbycode") || lowerPath.contains("sysdate") ||
                lowerPath.contains("refreshdataset") || lowerPath.contains("getdataset") ||
                lowerPath.contains("showalert") || lowerPath.contains("confirm") ||
                lowerPath.contains("closewindow") || lowerPath.contains("addlistener") ||
                lowerPath.contains("getvar") || lowerPath.contains("setvar") ||
                lowerPath.contains("modalresult") || lowerPath.contains("getproperty") ||
                lowerPath.contains("sunit") || lowerPath.contains("composition") ||
                lowerPath.contains("show_buttons") || lowerPath.contains("height") ||
                lowerPath.contains("width") || lowerPath.contains("onclose") ||
                lowerPath.contains("onafterclose") || lowerPath.contains("addlistener") ||
                lowerPath.contains("then") || lowerPath.contains("else") ||
                lowerPath.contains("return") || lowerPath.contains("typeof") ||
                lowerPath.contains("undefined") || lowerPath.contains("null") ||
                lowerPath.contains("true") || lowerPath.contains("false") ||
                lowerPath.contains("buttonedit_getcontrol") || lowerPath.contains("getproperty") ||
                lowerPath.contains("setcontrolvalue") || lowerPath.contains("setcontrolcaption") ||
                lowerPath.contains("getpage") || lowerPath.contains("getdom") ||
                lowerPath.contains("this") || lowerPath.contains("_dom")) {
            return false;
        }

        // Валидные пути содержат слеши или имеют структуру папок и заканчиваются на .frm или .dfrm
        return (path.contains("/") && (path.endsWith(".frm") || path.endsWith(".dfrm"))) ||
                path.matches("^[A-Za-z0-9_/]+\\.(frm|dfrm)$");
    }

    /**
     * Получить путь относительно корня проекта
     * @param projectRoot Корень проекта
     * @param absolutePath Абсолютный путь
     * @return Относительный путь
     */
    public static String getRelativePath(String projectRoot, String absolutePath) {
        Path root = Paths.get(projectRoot);
        Path abs = Paths.get(absolutePath);

        try {
            return root.relativize(abs).toString().replace("\\", "/");
        } catch (IllegalArgumentException e) {
            return absolutePath;
        }
    }

    /**
     * Получить путь формы относительно каталога Forms
     * @param projectRoot Корень проекта
     * @param absolutePath Абсолютный путь к файлу формы
     * @return Относительный путь от Forms/
     */
    public static String getFormPathFromAbsolute(String projectRoot, String absolutePath) {
        String relative = getRelativePath(projectRoot, absolutePath);

        if (relative.startsWith("Forms/")) {
            return relative.substring(6);
        }

        return relative;
    }
}