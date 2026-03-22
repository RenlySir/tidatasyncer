package com.example.sync.connectors.util;

import com.example.sync.core.config.TableMapping;
import java.util.List;
import java.util.regex.Pattern;

public final class SqlIdentifierQuoter {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_$#]+");

    private SqlIdentifierQuoter() {
    }

    public static String mysqlTable(TableMapping mapping) {
        return quoteBackticks(mapping.sourceSchema()) + "." + quoteBackticks(mapping.sourceTable());
    }

    public static String postgresqlTable(TableMapping mapping) {
        return quoteDouble(mapping.sourceSchema()) + "." + quoteDouble(mapping.sourceTable());
    }

    public static String db2Table(TableMapping mapping) {
        return quoteDouble(mapping.sourceSchema()) + "." + quoteDouble(mapping.sourceTable());
    }

    public static String oracleTable(TableMapping mapping) {
        return quoteDouble(mapping.sourceSchema()) + "." + quoteDouble(mapping.sourceTable());
    }

    public static String hanaTable(TableMapping mapping) {
        return quoteDouble(mapping.sourceSchema()) + "." + quoteDouble(mapping.sourceTable());
    }

    public static String sqlServerTable(TableMapping mapping) {
        return quoteSqlServer(mapping.sourceSchema()) + "." + quoteSqlServer(mapping.sourceTable());
    }

    public static String mysqlSelectList(TableMapping mapping) {
        return selectList(mapping.includedColumns(), SqlIdentifierQuoter::quoteBackticks);
    }

    public static String postgresqlSelectList(TableMapping mapping) {
        return selectList(mapping.includedColumns(), SqlIdentifierQuoter::quoteDouble);
    }

    public static String db2SelectList(TableMapping mapping) {
        return selectList(mapping.includedColumns(), SqlIdentifierQuoter::quoteDouble);
    }

    public static String oracleSelectList(TableMapping mapping) {
        return selectList(mapping.includedColumns(), SqlIdentifierQuoter::quoteDouble);
    }

    public static String hanaSelectList(TableMapping mapping) {
        return selectList(mapping.includedColumns(), SqlIdentifierQuoter::quoteDouble);
    }

    public static String sqlServerSelectList(TableMapping mapping) {
        return selectList(mapping.includedColumns(), SqlIdentifierQuoter::quoteSqlServer);
    }

    private static String selectList(List<String> columns, java.util.function.Function<String, String> quoter) {
        if (columns == null || columns.isEmpty()) {
            return "*";
        }
        return columns.stream().map(quoter).reduce((a, b) -> a + ", " + b).orElse("*");
    }

    private static String quoteBackticks(String identifier) {
        return "`" + validate(identifier) + "`";
    }

    private static String quoteDouble(String identifier) {
        return "\"" + validate(identifier) + "\"";
    }

    private static String quoteSqlServer(String identifier) {
        return "[" + validate(identifier) + "]";
    }

    private static String validate(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("SQL identifier must not be blank");
        }
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + identifier);
        }
        return identifier;
    }
}
