package ru.miacomsoft.model;

import java.io.*;
import java.util.Properties;

public class SettingsModel {
    private static final String SETTINGS_FILE = "analyzer_settings.properties";

    private String projectPath;
    private String outputDir;
    private String oracleUrl;
    private String oracleUser;
    private String oraclePassword;
    private String postgresUrl;
    private String postgresUser;
    private String postgresPassword;

    public SettingsModel() {
        loadSettings();
    }

    public void loadSettings() {
        Properties props = new Properties();
        File file = new File(SETTINGS_FILE);

        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);

                projectPath = props.getProperty("project.path", "/var/www/t-mis/mis");
                outputDir = props.getProperty("output.dir", "SQL_info");
                oracleUrl = props.getProperty("oracle.url", "jdbc:oracle:thin:@192.168.241.141:1521/med2dev");
                oracleUser = props.getProperty("oracle.user", "dev");
                oraclePassword = props.getProperty("oracle.password", "def");
                postgresUrl = props.getProperty("postgres.url", "jdbc:postgresql://192.168.241.137:5432/med2dev");
                postgresUser = props.getProperty("postgres.user", "postgres");
                postgresPassword = props.getProperty("postgres.password", "postgres");

            } catch (IOException e) {
                setDefaultValues();
            }
        } else {
            setDefaultValues();
        }
    }

    private void setDefaultValues() {
        projectPath = "/var/www/t-mis/mis";
        outputDir = "SQL_info";
        oracleUrl = "jdbc:oracle:thin:@192.168.241.141:1521/med2dev";
        oracleUser = "dev";
        oraclePassword = "def";
        postgresUrl = "jdbc:postgresql://192.168.241.137:5432/med2dev";
        postgresUser = "postgres";
        postgresPassword = "postgres";
    }

    public void saveSettings() {
        Properties props = new Properties();
        props.setProperty("project.path", projectPath);
        props.setProperty("output.dir", outputDir);
        props.setProperty("oracle.url", oracleUrl);
        props.setProperty("oracle.user", oracleUser);
        props.setProperty("oracle.password", oraclePassword);
        props.setProperty("postgres.url", postgresUrl);
        props.setProperty("postgres.user", postgresUser);
        props.setProperty("postgres.password", postgresPassword);

        try (FileOutputStream fos = new FileOutputStream(SETTINGS_FILE)) {
            props.store(fos, "TMIS Form Analyzer Settings");
        } catch (IOException e) {
            System.err.println("Ошибка сохранения настроек: " + e.getMessage());
        }
    }

    // Getters and Setters
    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    public String getOracleUrl() { return oracleUrl; }
    public void setOracleUrl(String oracleUrl) { this.oracleUrl = oracleUrl; }

    public String getOracleUser() { return oracleUser; }
    public void setOracleUser(String oracleUser) { this.oracleUser = oracleUser; }

    public String getOraclePassword() { return oraclePassword; }
    public void setOraclePassword(String oraclePassword) { this.oraclePassword = oraclePassword; }

    public String getPostgresUrl() { return postgresUrl; }
    public void setPostgresUrl(String postgresUrl) { this.postgresUrl = postgresUrl; }

    public String getPostgresUser() { return postgresUser; }
    public void setPostgresUser(String postgresUser) { this.postgresUser = postgresUser; }

    public String getPostgresPassword() { return postgresPassword; }
    public void setPostgresPassword(String postgresPassword) { this.postgresPassword = postgresPassword; }
}