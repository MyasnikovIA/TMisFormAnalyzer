package ru.miacomsoft.initBd;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class loadBD {
    public static void main(String[] args) {
        CSVToSQLiteImporter importer = new CSVToSQLiteImporter();

        try {
            // Сначала загружаем данные из CSV в БД
            System.out.println("=== НАЧАЛО ЗАГРУЗКИ ДАННЫХ ===");

            // Создание таблицы
            importer.createTable();
            System.out.println("Таблица успешно создана");

            // Загрузка данных из CSV (укажите правильный путь к вашему CSV файлу)
            String csvPath = "/home/myasnikov/Wor/T-Mis/Формы тМИС - Список форм 04.2026.csv";
            int rowsCount = importer.loadDataFromCSV(csvPath);
            System.out.println("Загружено строк: " + rowsCount);

            // Вывод статистики
            importer.printStatistics();

            // Предпросмотр данных
            importer.previewData(10);

            // Статистика по владельцам
            System.out.println("\n=== Статистика по владельцам тМИС ===");
            var ownersStats = DatabaseUtils.getOwnersStatistics();
            if (ownersStats.isEmpty()) {
                System.out.println("Нет данных по владельцам тМИС!");
            } else {
                for (var entry : ownersStats.entrySet()) {
                    System.out.println(entry.getKey() + ": " + entry.getValue() + " форм");
                }
            }

            // Теперь экспортируем формы для HM1, HM2, HM3
            System.out.println("\n=== ЭКСПОРТ ФОРМ ДЛЯ HM1, HM2, HM3 ===");
            exportFormsForOwners();

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void exportFormsForOwners() {
        try {
            List<String> owners = List.of("HM1", "HM2", "HM3");

            // Проверяем, есть ли данные в БД
            var stats = DatabaseUtils.getOwnersStatistics();
            boolean hasData = false;
            for (String owner : owners) {
                if (stats.containsKey(owner)) {
                    hasData = true;
                    System.out.println("Найдены данные для " + owner + ": " + stats.get(owner) + " форм");
                } else {
                    System.out.println("Данные для " + owner + " не найдены в БД");
                }
            }

            if (!hasData) {
                System.out.println("Внимание: В базе данных нет форм для владельцев HM1, HM2, HM3!");
                System.out.println("Проверьте, что в CSV файле есть строки с этими владельцами в колонке 'Владелец тМИС'");
                return;
            }

            // Простой список имен форм
            int count = FormsExporter.exportFormsByOwners("forms_list.txt", owners);
            System.out.println("Экспортировано " + count + " форм");

            // С детальной информацией
            int detailedCount = FormsExporter.exportFormsWithDetails("forms_detailed.txt", owners);
            System.out.println("Экспортировано " + detailedCount + " форм с деталями");

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }
}