package ru.miacomsoft.initBd;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleFormsLoader {

    private static final List<String> FORMS_TO_PROCESS;

    static {
        FORMS_TO_PROCESS = loadSimpleFormsList("forms_list.txt");
    }

    public static List<String> loadSimpleFormsList(String filePath) {
        List<String> forms = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Пропускаем пустые строки и комментарии
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("===")) {
                    // Если строка начинается с номера (например, "1. /path"), извлекаем путь
                    if (line.matches("^\\d+\\..*")) {
                        int dotIndex = line.indexOf('.');
                        if (dotIndex > 0 && dotIndex + 1 < line.length()) {
                            String formPath = line.substring(dotIndex + 1).trim();
                            forms.add(formPath);
                        }
                    } else {
                        forms.add(line);
                    }
                }
            }
            System.out.println("Успешно загружено " + forms.size() + " форм из " + filePath);
        } catch (IOException e) {
            System.err.println("Ошибка загрузки файла " + filePath + ": " + e.getMessage());
        }

        return forms;
    }

    public static List<String> getFormsToProcess() {
        return new ArrayList<>(FORMS_TO_PROCESS);
    }
}