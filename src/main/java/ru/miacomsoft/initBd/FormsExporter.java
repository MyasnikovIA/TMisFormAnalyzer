package ru.miacomsoft.initBd;


import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FormsExporter {

    private static final String DB_PATH = "forms_analysis.db";

    /**
     * Метод для выгрузки списка имен форм для указанных владельцев тМИС
     * @param outputFilePath путь к выходному текстовому файлу
     * @param owners список владельцев тМИС (например, "HM1", "HM2", "HM3")
     * @return количество выгруженных записей
     */
    public static int exportFormsByOwners(String outputFilePath, List<String> owners) throws SQLException, IOException {

        if (owners == null || owners.isEmpty()) {
            throw new IllegalArgumentException("Список владельцев не может быть пустым");
        }

        // Формируем SQL запрос с параметрами
        String sql = buildQuery(owners);

        List<String> formNames = new ArrayList<>();

        // Подключаемся к БД и выполняем запрос
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Устанавливаем параметры для владельцев
            for (int i = 0; i < owners.size(); i++) {
                //pstmt.setString(i + 1, owners.get(i));
                pstmt.setString(i + 1, owners.get(i));
            }

            // Выполняем запрос
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String formName = rs.getString("form_name");
                    if (formName != null && !formName.trim().isEmpty()) {
                        formNames.add(formName);
                    }
                }
            }
        }

        // Записываем результаты в файл
        writeToFile(outputFilePath, formNames, owners);

        return formNames.size();
    }

    /**
     * Формирует SQL запрос с плейсхолдерами для владельцев
     */
    private static String buildQuery(List<String> owners) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT form_name FROM forms_data WHERE owner_tmis IN (");

        // Добавляем плейсхолдеры для каждого владельца
        for (int i = 0; i < owners.size(); i++) {
            sql.append("?");
            if (i < owners.size() - 1) {
                sql.append(", ");
            }
        }
        sql.append(")");
        sql.append(" AND form_name IS NOT NULL");
        sql.append(" AND form_name != ''");
        sql.append(" ORDER BY form_name");

        return sql.toString();
    }

    /**
     * Записывает список имен форм в текстовый файл
     */
    private static void writeToFile(String filePath, List<String> formNames, List<String> owners) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), "UTF-8"))) {
            // Записываем каждое имя формы с нумерацией
            for (int i = 0; i < formNames.size(); i++) {
                //writer.write((i + 1) + ". " + formNames.get(i) + "\n");
                writer.write(formNames.get(i) + "\n");
            }
        }

        System.out.println("Файл успешно создан: " + filePath);
        System.out.println("Всего выгружено форм: " + formNames.size());
    }

    /**
     * Перегруженный метод для выгрузки с указанием одного владельца
     */
    public static int exportFormsByOwner(String outputFilePath, String owner) throws SQLException, IOException {
        List<String> owners = new ArrayList<>();
        owners.add(owner);
        return exportFormsByOwners(outputFilePath, owners);
    }

    /**
     * Метод для выгрузки с дополнительной информацией (включая комментарии)
     */
    public static int exportFormsWithDetails(String outputFilePath, List<String> owners) throws SQLException, IOException {

        if (owners == null || owners.isEmpty()) {
            throw new IllegalArgumentException("Список владельцев не может быть пустым");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT form_name, subsystem, component, comment FROM forms_data WHERE owner_tmis IN (");

        for (int i = 0; i < owners.size(); i++) {
            sql.append("?");
            if (i < owners.size() - 1) {
                sql.append(", ");
            }
        }
        sql.append(") AND form_name IS NOT NULL AND form_name != '' ORDER BY subsystem, form_name");

        List<String[]> details = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < owners.size(); i++) {
                pstmt.setString(i + 1, owners.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String[] row = new String[4];
                    row[0] = rs.getString("form_name");
                    row[1] = rs.getString("subsystem");
                    row[2] = rs.getString("component");
                    row[3] = rs.getString("comment");
                    details.add(row);
                }
            }
        }

        // Записываем детальную информацию в файл
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilePath), "UTF-8"))) {

            writer.write("=== Детальный список форм для владельцев тМИС: " + String.join(", ", owners) + " ===\n");
            writer.write("Всего найдено форм: " + details.size() + "\n");
            writer.write("========================================================\n\n");

            // Заголовки таблицы
            writer.write(String.format("%-60s | %-30s | %-20s | %s\n",
                    "Имя формы", "Подсистема", "Компонент", "Комментарий"));
            writer.write(String.format("%s\n", "=".repeat(140)));

            // Записываем данные
            for (String[] row : details) {
                writer.write(String.format("%-60s | %-30s | %-20s | %s\n",
                        truncateString(row[0], 58),
                        truncateString(row[1], 28),
                        truncateString(row[2], 18),
                        row[3] != null ? row[3] : ""));
            }

            writer.write("\n=== Конец списка ===\n");
        }

        return details.size();
    }

    /**
     * Вспомогательный метод для обрезания длинных строк
     */
    private static String truncateString(String str, int maxLength) {
        if (str == null || str.isEmpty()) {
            return "-";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

   // /**
   //  * Пример использования метода в main
   //  */
   ////public static void main(String[] args) {
   //    try {
   //        // Пример 1: Выгрузка для списка владельцев
   //        List<String> owners = List.of("HM1", "HM2", "HM3");
   //        int count = exportFormsByOwners("forms_hm_owners.txt", owners);
   //        System.out.println("Выгружено форм: " + count);

   //        // Пример 2: Выгрузка с детальной информацией
   //        int detailedCount = exportFormsWithDetails("forms_hm_detailed.txt", owners);
   //        System.out.println("Выгружено форм с деталями: " + detailedCount);

   //        // Пример 3: Выгрузка для одного владельца
   //        int singleCount = exportFormsByOwner("forms_hm1_only.txt", "HM1");
   //        System.out.println("Выгружено форм для HM1: " + singleCount);

   //    } catch (SQLException | IOException e) {
   //        System.err.println("Ошибка: " + e.getMessage());
   //        e.printStackTrace();
   //    }
   //}
}