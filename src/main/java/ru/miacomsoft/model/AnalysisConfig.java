package ru.miacomsoft.model;

public class AnalysisConfig {
    private static boolean includeViewTableDependencies = false;

    public static boolean isIncludeViewTableDependencies() {
        return includeViewTableDependencies;
    }

    public static void setIncludeViewTableDependencies(boolean value) {
        includeViewTableDependencies = value;
    }
}