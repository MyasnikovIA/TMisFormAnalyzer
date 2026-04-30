package ru.miacomsoft.initBd;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;

import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.List;

public class CSVToSQLiteImporter {

    private static final String DB_PATH = "forms_analysis.db";
    private static final String CSV_FILE_PATH = "data.csv"; // Укажите путь к вашему CSV файлу

    // Определение колонок таблицы (английские названия и русские комментарии)
    private static final Column[] COLUMNS = {
            new Column("form_name", "Форма", "TEXT"),
            new Column("overridden_by_forms", "Переопределяется формами", "TEXT"),
            new Column("overrides_forms", "Переопределяет формы", "TEXT"),
            new Column("included_as_subform", "Включается как сабформа", "TEXT"),
            new Column("includes_subform", "Включает сабформу", "TEXT"),
            new Column("called_on_forms", "Вызывается на формах", "TEXT"),
            new Column("calls_forms", "Вызывает формы", "TEXT"),
            new Column("comment", "Комментарий", "TEXT"),
            new Column("subsystem", "Подсистема", "TEXT"),
            new Column("component", "Компонент", "TEXT"),
            new Column("module_tmis", "Модуль тМИС", "TEXT"),
            new Column("used_as", "Используется как", "TEXT"),
            new Column("owner_tmis", "Владелец тМИС", "TEXT"),
            new Column("owner_nmis", "Владелец нМИС", "TEXT"),
            new Column("form_widget", "Форма/виджет", "TEXT"),
            new Column("git", "GIT", "TEXT")
    };

    static class Column {
        String name;
        String comment;
        String sqlType;

        Column(String name, String comment, String sqlType) {
            this.name = name;
            this.comment = comment;
            this.sqlType = sqlType;
        }
    }

   // public static void main(String[] args) {
   //     CSVToSQLiteImporter importer = new CSVToSQLiteImporter();
//
   //     try {
   //         // Создание таблицы
   //         importer.createTable();
   //         System.out.println("Таблица успешно создана");
//
   //         // Загрузка данных из CSV
   //         int rowsCount = importer.loadDataFromCSV(CSV_FILE_PATH);
   //         System.out.println("Загружено строк: " + rowsCount);
//
   //         // Вывод статистики
   //         importer.printStatistics();
//
   //     } catch (Exception e) {
   //         System.err.println("Ошибка: " + e.getMessage());
   //         e.printStackTrace();
   //     }
   // }

    private Connection connect() throws SQLException {
        String url = "jdbc:sqlite:" + DB_PATH;
        return DriverManager.getConnection(url);
    }

    public void createTable() throws SQLException {
        String createTableSQL = buildCreateTableSQL();

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            // Удаляем таблицу если существует (для чистой установки)
            stmt.execute("DROP TABLE IF EXISTS forms_data");

            // Создаем таблицу
            stmt.execute(createTableSQL);

            // Добавляем комментарии к колонкам (SQLite не поддерживает COMMENT, сохраняем в отдельной таблице)
            saveColumnComments(conn);

            System.out.println("Таблица 'forms_data' создана");
        }
    }

    private String buildCreateTableSQL() {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS forms_data (\n");
        sql.append("    id INTEGER PRIMARY KEY AUTOINCREMENT,\n");

        for (Column col : COLUMNS) {
            sql.append("    ").append(col.name).append(" ").append(col.sqlType).append(",\n");
        }

        // Удаляем последнюю запятую и добавляем закрывающую скобку
        sql.setLength(sql.length() - 2);
        sql.append("\n);");

        return sql.toString();
    }

    private void saveColumnComments(Connection conn) throws SQLException {
        String createCommentsTable = """
            CREATE TABLE IF NOT EXISTS column_comments (
                table_name TEXT,
                column_name TEXT,
                comment TEXT,
                PRIMARY KEY (table_name, column_name)
            )
        """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createCommentsTable);

            // Очищаем старые комментарии
            stmt.execute("DELETE FROM column_comments WHERE table_name = 'forms_data'");

            // Добавляем комментарии
            String insertComment = "INSERT INTO column_comments (table_name, column_name, comment) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertComment)) {
                for (Column col : COLUMNS) {
                    pstmt.setString(1, "forms_data");
                    pstmt.setString(2, col.name);
                    pstmt.setString(3, col.comment);
                    pstmt.executeUpdate();
                }
            }
        }
    }

    public int loadDataFromCSV(String csvFilePath) throws IOException, CsvException, SQLException {
        List<String[]> rows = readCSV(csvFilePath);

        if (rows.isEmpty()) {
            System.out.println("CSV файл пуст или не содержит данных");
            return 0;
        }

        // Первая строка - заголовки, пропускаем её
        String[] headers = rows.get(0);
        int insertedCount = 0;

        String insertSQL = buildInsertSQL();

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            // Начинаем транзакцию для ускорения вставки
            conn.setAutoCommit(false);

            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);

                // Пропускаем пустые строки и строки с #ERROR!
                if (isRowEmpty(row) || hasError(row)) {
                    continue;
                }

                // Заполняем параметры запроса
                for (int j = 0; j < COLUMNS.length; j++) {
                    String value = (j < row.length) ? cleanValue(row[j]) : "";
                    pstmt.setString(j + 1, value);
                }

                pstmt.executeUpdate();
                insertedCount++;

                // Периодически выводим прогресс
                if (insertedCount % 100 == 0) {
                    System.out.println("Обработано строк: " + insertedCount);
                }
            }

            // Фиксируем транзакцию
            conn.commit();

        } catch (SQLException e) {
            System.err.println("Ошибка при вставке данных: " + e.getMessage());
            throw e;
        }

        return insertedCount;
    }

    private List<String[]> readCSV(String filePath) throws IOException, CsvException {
        try (CSVReader reader = new CSVReaderBuilder(new FileReader(filePath))
                .withSkipLines(0)
                .build()) {

            List<String[]> allRows = reader.readAll();

            // Удаляем последние пустые строки
            while (!allRows.isEmpty() && allRows.get(allRows.size() - 1).length == 0) {
                allRows.remove(allRows.size() - 1);
            }

            // Проверяем, что у нас есть хотя бы одна строка с заголовками
            if (!allRows.isEmpty() && allRows.get(0).length > 0) {
                // Ищем строку с заголовками (она начинается с "Форма")
                int headerRowIndex = -1;
                for (int i = 0; i < allRows.size(); i++) {
                    String[] row = allRows.get(i);
                    if (row.length > 0 && "Форма".equals(row[0])) {
                        headerRowIndex = i;
                        break;
                    }
                }

                if (headerRowIndex > 0) {
                    // Удаляем строки до заголовков
                    allRows = allRows.subList(headerRowIndex, allRows.size());
                }
            }

            return allRows;
        }
    }

    private String buildInsertSQL() {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO forms_data (");

        for (Column col : COLUMNS) {
            sql.append(col.name).append(", ");
        }

        sql.setLength(sql.length() - 2);
        sql.append(") VALUES (");

        for (int i = 0; i < COLUMNS.length; i++) {
            sql.append("?, ");
        }

        sql.setLength(sql.length() - 2);
        sql.append(")");

        return sql.toString();
    }

    private String cleanValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String cleaned = value.trim();

        // Удаляем лишние кавычки, если они окружают всю строку
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        // Заменяем двойные кавычки внутри строки
        cleaned = cleaned.replace("\"\"", "\"");

        // Обработка многострочных значений - заменяем переносы строк на пробелы
        cleaned = cleaned.replace("\n", " ").replace("\r", " ");

        // Удаляем множественные пробелы
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned;
    }

    private boolean isRowEmpty(String[] row) {
        if (row == null || row.length == 0) {
            return true;
        }

        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasError(String[] row) {
        if (row.length > 0 && row[0] != null) {
            return row[0].contains("#ERROR!");
        }
        return false;
    }

    public void printStatistics() throws SQLException {
        String countSQL = "SELECT COUNT(*) as total FROM forms_data";
        String notEmptySQL = "SELECT \n" +
                "    SUM(CASE WHEN form_name IS NOT NULL AND form_name != '' THEN 1 ELSE 0 END) as forms_count,\n" +
                "    SUM(CASE WHEN owner_tmis IS NOT NULL AND owner_tmis != '' THEN 1 ELSE 0 END) as tmis_owners_count,\n" +
                "    SUM(CASE WHEN owner_nmis IS NOT NULL AND owner_nmis != '' THEN 1 ELSE 0 END) as nmis_owners_count\n" +
                "FROM forms_data";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            // Получаем общее количество записей
            ResultSet rs = stmt.executeQuery(countSQL);
            if (rs.next()) {
                System.out.println("\n=== Статистика базы данных ===");
                System.out.println("Всего записей в таблице: " + rs.getInt("total"));
            }

            // Получаем дополнительную статистику
            rs = stmt.executeQuery(notEmptySQL);
            if (rs.next()) {
                System.out.println("Записей с указанием формы: " + rs.getInt("forms_count"));
                System.out.println("Записей с указанием владельца тМИС: " + rs.getInt("tmis_owners_count"));
                System.out.println("Записей с указанием владельца нМИС: " + rs.getInt("nmis_owners_count"));
            }

            // Выводим список уникальных подсистем
            String subsystemsSQL = "SELECT DISTINCT subsystem FROM forms_data WHERE subsystem IS NOT NULL AND subsystem != '' LIMIT 10";
            rs = stmt.executeQuery(subsystemsSQL);
            System.out.println("\nУникальные подсистемы (первые 10):");
            int count = 0;
            while (rs.next() && count < 10) {
                System.out.println("  - " + rs.getString("subsystem"));
                count++;
            }

        } catch (SQLException e) {
            System.err.println("Ошибка при получении статистики: " + e.getMessage());
        }
    }

    // Дополнительный метод для просмотра данных в таблице
    public void previewData(int limit) throws SQLException {
        String sql = "SELECT id, form_name, subsystem, owner_tmis, owner_nmis FROM forms_data LIMIT ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            System.out.println("\n=== Предпросмотр данных (первые " + limit + " записей) ===");
            System.out.println("ID | Форма | Подсистема | Владелец тМИС | Владелец нМИС");
            System.out.println("---|-------|------------|---------------|---------------");

            while (rs.next()) {
                System.out.printf("%d | %s | %s | %s | %s%n",
                        rs.getInt("id"),
                        truncateString(rs.getString("form_name"), 30),
                        truncateString(rs.getString("subsystem"), 20),
                        truncateString(rs.getString("owner_tmis"), 15),
                        truncateString(rs.getString("owner_nmis"), 15)
                );
            }
        }
    }

    private String truncateString(String str, int maxLength) {
        if (str == null || str.isEmpty()) {
            return "-";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }


}