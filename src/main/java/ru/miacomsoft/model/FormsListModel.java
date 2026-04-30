package ru.miacomsoft.model;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class FormsListModel {
    private static final String FORMS_LIST_FILE = "user_forms_list.txt";
    private List<String> forms;

    public FormsListModel() {
        forms = new ArrayList<>();
        loadFormsList();
    }

    public void loadFormsList() {
        File file = new File(FORMS_LIST_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                forms.clear();
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        String normalized = normalizeFormPath(line);
                        if (normalized != null) {
                            forms.add(normalized);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Ошибка загрузки списка форм: " + e.getMessage());
            }
        }
    }

    public void saveFormsList() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FORMS_LIST_FILE))) {
            for (String form : forms) {
                writer.write(form);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Ошибка сохранения списка форм: " + e.getMessage());
        }
    }

    public void addForm(String formPath) {
        String normalized = normalizeFormPath(formPath);
        if (normalized != null && !forms.contains(normalized)) {
            forms.add(normalized);
        }
    }

    public void addAllForms(List<String> formPaths) {
        for (String path : formPaths) {
            addForm(path);
        }
    }

    public void removeForm(int index) {
        if (index >= 0 && index < forms.size()) {
            forms.remove(index);
        }
    }

    public void clearForms() {
        forms.clear();
    }

    public List<String> getForms() {
        return new ArrayList<>(forms);
    }

    public void setForms(List<String> newForms) {
        forms.clear();
        for (String form : newForms) {
            String normalized = normalizeFormPath(form);
            if (normalized != null) {
                forms.add(normalized);
            }
        }
    }

    /**
     * Нормализация пути формы
     * Поддерживаемые форматы:
     * - /Forms/#examples/barcode.frm -> /Forms/#examples/barcode.frm
     * - Forms/ARMMainDoc/arm_director.frm -> /Forms/ARMMainDoc/arm_director.frm
     * - #examples/barcode -> /Forms/#examples/barcode.frm
     * - ARMMainDoc/arm_director -> /Forms/ARMMainDoc/arm_director.frm
     * - UserFormsAmber/Reports/Reception/rout_list.d/form.frm -> /UserFormsAmber/Reports/Reception/rout_list.d/form.frm
     */
    private String normalizeFormPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        String normalized = path.trim();

        // Убираем ведущий слеш если есть для обработки
        boolean addLeadingSlash = false;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
            addLeadingSlash = true;
        }

        // Проверяем, является ли путь UserForms
        if (normalized.startsWith("UserForms")) {
            String result = "/" + normalized;
            if (!result.endsWith(".frm") && !result.endsWith(".dfrm")) {
                result = result + ".frm";
            }
            return result;
        }

        // Обработка путей Forms
        if (normalized.startsWith("Forms/")) {
            normalized = normalized.substring(6);
        } else if (normalized.startsWith("Forms")) {
            normalized = normalized.substring(5);
        }

        // Добавляем .frm если нет расширения
        if (!normalized.endsWith(".frm") && !normalized.endsWith(".dfrm")) {
            normalized = normalized + ".frm";
        }

        // Добавляем префикс Forms/ если его нет
        if (!normalized.startsWith("Forms/") && !normalized.startsWith("/Forms/")) {
            normalized = "Forms/" + normalized;
        }

        return "/" + normalized;
    }
}