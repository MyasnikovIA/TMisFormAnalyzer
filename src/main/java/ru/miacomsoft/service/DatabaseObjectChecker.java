package ru.miacomsoft.service;

import ru.miacomsoft.model.SettingsModel;

import java.sql.*;
import java.util.*;

public class DatabaseObjectChecker {

    private String oracleUrl;
    private String oracleUser;
    private String oraclePassword;
    private String postgresUrl;
    private String postgresUser;
    private String postgresPassword;

    public DatabaseObjectChecker(SettingsModel settings) {
        this.oracleUrl = settings.getOracleUrl();
        this.oracleUser = settings.getOracleUser();
        this.oraclePassword = settings.getOraclePassword();
        this.postgresUrl = settings.getPostgresUrl();
        this.postgresUser = settings.getPostgresUser();
        this.postgresPassword = settings.getPostgresPassword();
    }

    /**
     * Получение подключения к Oracle
     */
    private Connection getOracleConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", oracleUser);
        props.setProperty("password", oraclePassword);
        props.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
        props.setProperty("oracle.jdbc.ReadTimeout", "30000");

        return DriverManager.getConnection(oracleUrl, props);
    }

    /**
     * Получение подключения к PostgreSQL
     */
    private Connection getPostgresConnection() throws SQLException {
        DriverManager.setLoginTimeout(10);
        return DriverManager.getConnection(postgresUrl, postgresUser, postgresPassword);
    }

    /**
     * Проверка существования таблицы/вьюхи в Oracle
     */
    public boolean checkObjectExistsInOracle(String objectName, String objectType) {
        String sql = "SELECT COUNT(*) FROM ALL_OBJECTS WHERE OBJECT_NAME = ? AND OBJECT_TYPE = ?";

        try (Connection conn = getOracleConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, objectName.toUpperCase());
            pstmt.setString(2, objectType.toUpperCase());
            pstmt.setQueryTimeout(10);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Oracle ошибка при проверке " + objectName + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Проверка существования таблицы/вьюхи в PostgreSQL
     */
    public boolean checkObjectExistsInPostgres(String objectName, String objectType) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ? AND table_type = ?";

        try (Connection conn = getPostgresConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, objectName.toLowerCase());
            pstmt.setString(2, objectType.toUpperCase());
            pstmt.setQueryTimeout(10);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("PostgreSQL ошибка при проверке " + objectName + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Проверка наличия данных в таблице/вьюхе (Oracle)
     */
    public long checkDataCountInOracle(String objectName) {
        String sql = "SELECT COUNT(*) FROM " + objectName;

        try (Connection conn = getOracleConnection();
             Statement stmt = conn.createStatement()) {

            stmt.setQueryTimeout(30);
            ResultSet rs = stmt.executeQuery(sql);

            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("Oracle ошибка при подсчете данных в " + objectName + ": " + e.getMessage());
            return -1;
        }
        return 0;
    }

    /**
     * Проверка наличия данных в таблице/вьюхе (PostgreSQL)
     */
    public long checkDataCountInPostgres(String objectName) {
        String sql = "SELECT COUNT(*) FROM " + objectName;

        try (Connection conn = getPostgresConnection();
             Statement stmt = conn.createStatement()) {

            stmt.setQueryTimeout(30);
            ResultSet rs = stmt.executeQuery(sql);

            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("PostgreSQL ошибка при подсчете данных в " + objectName + ": " + e.getMessage());
            return -1;
        }
        return 0;
    }

    /**
     * Полная проверка объекта в обеих БД
     */
    public Map<String, Object> checkObject(String objectName) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Определяем тип объекта (TABLE или VIEW)
        String objectType = objectName.startsWith("D_V_") ? "VIEW" : "TABLE";

        // Проверка в Oracle
        boolean existsInOracle = checkObjectExistsInOracle(objectName, objectType);
        result.put("oracle_exists", existsInOracle);

        if (existsInOracle) {
            long oracleCount = checkDataCountInOracle(objectName);
            result.put("oracle_count", oracleCount);
        } else {
            result.put("oracle_count", -1);
        }

        // Проверка в PostgreSQL
        boolean existsInPostgres = checkObjectExistsInPostgres(objectName, objectType);
        result.put("postgres_exists", existsInPostgres);

        if (existsInPostgres) {
            long postgresCount = checkDataCountInPostgres(objectName);
            result.put("postgres_count", postgresCount);
        } else {
            result.put("postgres_count", -1);
        }

        return result;
    }

    /**
     * Проверка списка объектов
     */
    public Map<String, Map<String, Object>> checkObjects(Set<String> objectNames) {
        Map<String, Map<String, Object>> results = new LinkedHashMap<>();

        for (String objectName : objectNames) {
            results.put(objectName, checkObject(objectName));
        }

        return results;
    }

    /**
     * Проверка существования пакета/функции в Oracle
     */
    public boolean checkPackageExistsInOracle(String packageName) {
        String sql = "SELECT COUNT(*) FROM ALL_OBJECTS WHERE OBJECT_NAME = ? AND OBJECT_TYPE IN ('PACKAGE', 'PACKAGE BODY', 'FUNCTION', 'PROCEDURE')";

        String objectName = packageName;
        if (packageName.contains(".")) {
            objectName = packageName.split("\\.")[0];
        }

        try (Connection conn = getOracleConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, objectName.toUpperCase());
            pstmt.setQueryTimeout(10);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Oracle ошибка при проверке пакета " + packageName + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Проверка существования функции в PostgreSQL
     */
    public boolean checkPackageExistsInPostgres(String packageName) {
        String sql = "SELECT COUNT(*) FROM pg_proc WHERE proname = ?";

        // Извлекаем имя функции из полного имени пакета
        String functionName = packageName;
        if (packageName.contains(".")) {
            functionName = packageName.substring(packageName.lastIndexOf(".") + 1);
        }

        try (Connection conn = getPostgresConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, functionName.toLowerCase());
            pstmt.setQueryTimeout(10);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("PostgreSQL ошибка при проверке функции " + packageName + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Проверка списка пакетов/функций
     */
    public Map<String, Map<String, Object>> checkPackages(Set<String> packageNames) {
        Map<String, Map<String, Object>> results = new LinkedHashMap<>();

        for (String packageName : packageNames) {
            Map<String, Object> result = new LinkedHashMap<>();

            // Проверка в Oracle
            boolean existsInOracle = checkPackageExistsInOracle(packageName);
            result.put("oracle_exists", existsInOracle);

            // Проверка в PostgreSQL
            boolean existsInPostgres = checkPackageExistsInPostgres(packageName);
            result.put("postgres_exists", existsInPostgres);

            results.put(packageName, result);
        }

        return results;
    }
}