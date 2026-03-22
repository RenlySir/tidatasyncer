package com.example.sync.connectors.util;

public final class LightningCsvNaming {

    private static final int CSV_SEQUENCE_WIDTH = 8;

    private LightningCsvNaming() {
    }

    public static String singleFileName(String databaseName, String tableName) {
        return chunkFileName(databaseName, tableName, 1);
    }

    public static String chunkFileName(String databaseName, String tableName, int index) {
        return String.format("%s.%s.%0" + CSV_SEQUENCE_WIDTH + "d.csv", databaseName, tableName, index);
    }
}
