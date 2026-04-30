package ru.miacomsoft.initBd;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseUtils {

    private static final String DB_PATH = "forms_analysis.db";

    public static Connection connect() throws SQLException {
        String url = "jdbc:sqlite:" + DB_PATH;
        return DriverManager.getConnection(url);
    }

    // Получение всех комментариев к колонкам
    public static Map<String, String> getColumnComments() throws SQLException {
        Map<String, String> comments = new HashMap<>();
        String sql = "SELECT column_name, comment FROM column_comments WHERE table_name = 'forms_data'";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                comments.put(rs.getString("column_name"), rs.getString("comment"));
            }
        }
        return comments;
    }

    // Поиск форм по подсистеме
    public static List<Map<String, String>> findFormsBySubsystem(String subsystem) throws SQLException {
        List<Map<String, String>> results = new ArrayList<>();
        String sql = "SELECT id, form_name, owner_tmis, owner_nmis, comment FROM forms_data WHERE subsystem LIKE ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + subsystem + "%");
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, String> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getInt("id")));
                row.put("form_name", rs.getString("form_name"));
                row.put("owner_tmis", rs.getString("owner_tmis"));
                row.put("owner_nmis", rs.getString("owner_nmis"));
                row.put("comment", rs.getString("comment"));
                results.add(row);
            }
        }
        return results;
    }

    // Получение статистики по владельцам
    public static Map<String, Integer> getOwnersStatistics() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        String sql = "SELECT owner_tmis, COUNT(*) as count FROM forms_data WHERE owner_tmis IS NOT NULL AND owner_tmis != '' GROUP BY owner_tmis";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                stats.put(rs.getString("owner_tmis"), rs.getInt("count"));
            }
        }
        return stats;
    }

    // Экспорт данных в CSV
    public static void exportToCSV(String outputPath) throws SQLException {
        String sql = "SELECT * FROM forms_data";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             java.io.PrintWriter writer = new java.io.PrintWriter(outputPath)) {

            // Получаем метаданные колонок
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Записываем заголовки
            for (int i = 1; i <= columnCount; i++) {
                writer.print(metaData.getColumnName(i));
                if (i < columnCount) writer.print(",");
            }
            writer.println();

            // Записываем данные
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    if (value != null && value.contains(",")) {
                        value = "\"" + value + "\"";
                    }
                    writer.print(value != null ? value : "");
                    if (i < columnCount) writer.print(",");
                }
                writer.println();
            }

            System.out.println("Данные экспортированы в: " + outputPath);
        } catch (java.io.IOException e) {
            System.err.println("Ошибка при экспорте: " + e.getMessage());
        }
    }
}