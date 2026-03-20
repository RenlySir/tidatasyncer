package com.example.sync.connectors.util;

public final class LightningCsvNaming {

    private LightningCsvNaming() {
    }

    public static String singleFileName(String databaseName, String tableName) {
        return databaseName + "." + tableName + ".csv";
    }

    public static String chunkFileName(String databaseName, String tableName, int index) {
        return "%s.%s.%03d.csv".formatted(databaseName, tableName, index);
    }
}
