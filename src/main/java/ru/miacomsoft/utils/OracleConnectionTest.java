package ru.miacomsoft.utils;

import java.sql.*;

public class OracleConnectionTest {

    public static boolean testConnection(String url, String user, String password) {
        // Для Windows может потребоваться указать путь к Oracle клиенту
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Путь к Instant Client (если установлен)
            System.setProperty("oracle.jdbc.defaultNChar", "true");
            // Можно указать путь к библиотекам
            // System.setProperty("java.library.path", "C:\\oracle\\instantclient_21_13");
        }

        try {
            // Загружаем драйвер
            Class.forName("oracle.jdbc.OracleDriver");

            // Используем расширенные свойства для подключения
            java.util.Properties props = new java.util.Properties();
            props.setProperty("user", user);
            props.setProperty("password", password);
            props.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
            props.setProperty("oracle.jdbc.ReadTimeout", "30000");

            // Для Windows может потребоваться указать кодировку
            props.setProperty("oracle.jdbc.defaultNChar", "true");

            System.out.println("Подключение к Oracle: " + url);
            try (Connection conn = DriverManager.getConnection(url, props)) {
                System.out.println("Успешное подключение к Oracle!");
                return true;
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Oracle JDBC драйвер не найден: " + e.getMessage());
            System.err.println("Добавьте ojdbc11.jar в classpath");
            return false;
        } catch (SQLException e) {
            System.err.println("Ошибка подключения к Oracle: " + e.getMessage());
            if (e.getMessage().contains("OCI")) {
                System.err.println("Возможно, требуется установка Oracle Instant Client");
            }
            return false;
        }
    }
}