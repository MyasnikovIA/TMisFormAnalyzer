package ru.miacomsoft.model;

public class AnalysisConfig {
    private static boolean includeViewTableDependencies = false;
    private static String formsListFile = "forms_list.txt";
    private static boolean scanAllFormsIfListEmpty = true;

    public static boolean isIncludeViewTableDependencies() {
        return includeViewTableDependencies;
    }

    public static void setIncludeViewTableDependencies(boolean value) {
        includeViewTableDependencies = value;
    }

    public static String getFormsListFile() {
        return formsListFile;
    }

    public static void setFormsListFile(String formsListFile) {
        AnalysisConfig.formsListFile = formsListFile;
    }

    public static boolean isScanAllFormsIfListEmpty() {
        return scanAllFormsIfListEmpty;
    }

    public static void setScanAllFormsIfListEmpty(boolean scanAllFormsIfListEmpty) {
        AnalysisConfig.scanAllFormsIfListEmpty = scanAllFormsIfListEmpty;
    }
}