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

    // Настройки отчета
    private boolean includeSqlContent;
    private boolean includeJsForms;
    private boolean includeTablesViews;
    private boolean includeViewTables;
    private boolean includeJsUnitCompositions;
    private boolean includeViewDetails;

    // Проверка объектов БД
    private boolean checkDbObjects;
    private boolean checkDbData;
    private boolean checkPostgresPackages;

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

                includeSqlContent = Boolean.parseBoolean(props.getProperty("report.includeSqlContent", "false"));
                includeJsForms = Boolean.parseBoolean(props.getProperty("report.includeJsForms", "true"));
                includeTablesViews = Boolean.parseBoolean(props.getProperty("report.includeTablesViews", "true"));
                includeViewTables = Boolean.parseBoolean(props.getProperty("report.includeViewTables", "true"));
                includeJsUnitCompositions = Boolean.parseBoolean(props.getProperty("report.includeJsUnitCompositions", "true"));
                includeViewDetails = Boolean.parseBoolean(props.getProperty("report.includeViewDetails", "false"));

                checkDbObjects = Boolean.parseBoolean(props.getProperty("report.checkDbObjects", "false"));
                checkDbData = Boolean.parseBoolean(props.getProperty("report.checkDbData", "false"));
                checkPostgresPackages = Boolean.parseBoolean(props.getProperty("report.checkPostgresPackages", "false"));

            } catch (IOException e) {
                setDefaultValues();
            }
        } else {
            setDefaultValues();
        }

        applyReportConfig();
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

        includeSqlContent = false;
        includeJsForms = true;
        includeTablesViews = true;
        includeViewTables = true;
        includeJsUnitCompositions = true;
        includeViewDetails = false;

        checkDbObjects = false;
        checkDbData = false;
        checkPostgresPackages = false;

        applyReportConfig();
    }

    private void applyReportConfig() {
        ReportConfig.setIncludeSqlContent(includeSqlContent);
        ReportConfig.setIncludeJsForms(includeJsForms);
        ReportConfig.setIncludeTablesViews(includeTablesViews);
        ReportConfig.setIncludeViewTables(includeViewTables);
        ReportConfig.setIncludeJsUnitCompositions(includeJsUnitCompositions);
        ReportConfig.setIncludeViewDetails(includeViewDetails);
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

        props.setProperty("report.includeSqlContent", String.valueOf(includeSqlContent));
        props.setProperty("report.includeJsForms", String.valueOf(includeJsForms));
        props.setProperty("report.includeTablesViews", String.valueOf(includeTablesViews));
        props.setProperty("report.includeViewTables", String.valueOf(includeViewTables));
        props.setProperty("report.includeJsUnitCompositions", String.valueOf(includeJsUnitCompositions));
        props.setProperty("report.includeViewDetails", String.valueOf(includeViewDetails));

        props.setProperty("report.checkDbObjects", String.valueOf(checkDbObjects));
        props.setProperty("report.checkDbData", String.valueOf(checkDbData));
        props.setProperty("report.checkPostgresPackages", String.valueOf(checkPostgresPackages));

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

    public boolean isIncludeSqlContent() { return includeSqlContent; }
    public void setIncludeSqlContent(boolean value) { this.includeSqlContent = value; ReportConfig.setIncludeSqlContent(value); }

    public boolean isIncludeJsForms() { return includeJsForms; }
    public void setIncludeJsForms(boolean value) { this.includeJsForms = value; ReportConfig.setIncludeJsForms(value); }

    public boolean isIncludeTablesViews() { return includeTablesViews; }
    public void setIncludeTablesViews(boolean value) { this.includeTablesViews = value; ReportConfig.setIncludeTablesViews(value); }

    public boolean isIncludeViewTables() { return includeViewTables; }
    public void setIncludeViewTables(boolean value) { this.includeViewTables = value; ReportConfig.setIncludeViewTables(value); }

    public boolean isIncludeJsUnitCompositions() { return includeJsUnitCompositions; }
    public void setIncludeJsUnitCompositions(boolean value) { this.includeJsUnitCompositions = value; ReportConfig.setIncludeJsUnitCompositions(value); }

    public boolean isIncludeViewDetails() { return includeViewDetails; }
    public void setIncludeViewDetails(boolean value) { this.includeViewDetails = value; ReportConfig.setIncludeViewDetails(value); }

    public boolean isCheckDbObjects() { return checkDbObjects; }
    public void setCheckDbObjects(boolean value) { this.checkDbObjects = value; }

    public boolean isCheckDbData() { return checkDbData; }
    public void setCheckDbData(boolean value) { this.checkDbData = value; }

    public boolean isCheckPostgresPackages() { return checkPostgresPackages; }
    public void setCheckPostgresPackages(boolean value) { this.checkPostgresPackages = value; }
}