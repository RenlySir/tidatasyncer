package com.example.sync.admin.service;

import com.example.sync.admin.domain.ConnectionProfileEntity;
import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.domain.DatabaseEndpointType;
import com.example.sync.admin.dto.ConnectionProfilePermissionCheckItemResponse;
import com.example.sync.admin.dto.ConnectionProfilePermissionCheckResponse;
import com.example.sync.connectors.util.JdbcConnectionSupport;
import com.example.sync.connectors.util.MongoConnectionSupport;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.TargetConnectionProperties;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ConnectionProfilePermissionCheckService {

    private final ConnectionProfileService connectionProfileService;
    private final ConnectionProfileBindingService connectionBindingService;

    public ConnectionProfilePermissionCheckService(
            ConnectionProfileService connectionProfileService,
            ConnectionProfileBindingService connectionBindingService
    ) {
        this.connectionProfileService = connectionProfileService;
        this.connectionBindingService = connectionBindingService;
    }

    public ConnectionProfilePermissionCheckResponse check(Long id) {
        ConnectionProfileEntity profile = connectionProfileService.findEntity(id);
        return switch (profile.getDatabaseType()) {
            case CSV -> checkCsv(profile);
            case MYSQL, MARIADB -> checkMySqlFamily(profile);
            case TIDB -> checkTidbTarget(profile);
            case POSTGRESQL -> checkPostgreSql(profile);
            case SQLSERVER -> checkSqlServer(profile);
            case ORACLE -> checkOracle(profile);
            case HANA -> checkHana(profile);
            case DB2 -> checkDb2(profile);
            case MONGODB -> checkMongoDb(profile);
        };
    }

    private ConnectionProfilePermissionCheckResponse checkCsv(ConnectionProfileEntity profile) {
        List<ConnectionProfilePermissionCheckItemResponse> checks = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        String directoryPath = connectionBindingService.firstNonBlank(profile.getCsvDirectory());
        if (directoryPath == null) {
            checks.add(check("directory", "CSV directory configured", false, "CSV directory is empty."));
            missing.add("CSV directory path");
            return build(profile, checks, missing, List.of("mkdir -p /data/csv-load && chmod -R u+rwX /data/csv-load"));
        }

        Path directory = Path.of(directoryPath);
        checks.add(check("exists", "Directory exists", Files.exists(directory), Files.exists(directory) ? "Directory exists." : "Directory does not exist."));
        checks.add(check("is-directory", "Path is a directory", Files.isDirectory(directory), Files.isDirectory(directory) ? "Path points to a directory." : "Path is not a directory."));
        checks.add(check("readable", "Directory is readable", Files.isReadable(directory), Files.isReadable(directory) ? "Current process can read the directory." : "Current process cannot read the directory."));
        checks.add(check("writable", "Directory is writable", Files.isWritable(directory), Files.isWritable(directory) ? "Current process can write split or converted files." : "Current process cannot write into the directory."));

        if (!Files.exists(directory)) {
            missing.add("Directory does not exist");
        }
        if (!Files.isDirectory(directory)) {
            missing.add("Path is not a directory");
        }
        if (!Files.isReadable(directory)) {
            missing.add("Directory read permission");
        }
        if (!Files.isWritable(directory)) {
            missing.add("Directory write permission");
        }
        return build(profile, checks, missing, List.of("mkdir -p " + directory.toAbsolutePath(), "chmod -R u+rwX " + directory.toAbsolutePath()));
    }

    private ConnectionProfilePermissionCheckResponse checkMySqlFamily(ConnectionProfileEntity profile) {
        List<ConnectionProfilePermissionCheckItemResponse> checks = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        try (Connection connection = openSourceConnection(profile)) {
            checks.add(check("connectivity", "Database login", true, "Successfully connected to the source database."));
            List<String> grants = querySingleColumn(connection, "SHOW GRANTS FOR CURRENT_USER()");
            String grantText = String.join("\n", grants).toUpperCase(Locale.ROOT);
            addMySqlPrivilegeCheck(checks, missing, grantText, "SELECT", "Read source tables", profile.getDatabaseName(), false);
            addMySqlPrivilegeCheck(checks, missing, grantText, "RELOAD", "Snapshot control", null, true);
            addMySqlPrivilegeCheck(checks, missing, grantText, "SHOW DATABASES", "Inspect database metadata", null, true);
            addMySqlPrivilegeCheck(checks, missing, grantText, "REPLICATION SLAVE", "Read binlog", null, true, "REPLICATION REPLICA");
            addMySqlPrivilegeCheck(checks, missing, grantText, "REPLICATION CLIENT", "Read binlog position", null, true);
            addMySqlPrivilegeCheck(checks, missing, grantText, "LOCK TABLES", "Acquire snapshot locks", null, true);
            return build(profile, checks, missing, mysqlSourceGrantStatements(profile));
        } catch (Exception ex) {
            checks.add(check("connectivity", "Database login", false, ex.getMessage()));
            missing.add("Database login or base connectivity");
            return build(profile, checks, missing, mysqlSourceGrantStatements(profile));
        }
    }

    private ConnectionProfilePermissionCheckResponse checkTidbTarget(ConnectionProfileEntity profile) {
        List<ConnectionProfilePermissionCheckItemResponse> checks = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        try (Connection connection = openTargetConnection(profile)) {
            checks.add(check("connectivity", "TiDB login", true, "Successfully connected to the TiDB target."));
            List<String> grants = querySingleColumn(connection, "SHOW GRANTS FOR CURRENT_USER()");
            String grantText = String.join("\n", grants).toUpperCase(Locale.ROOT);
            addMySqlPrivilegeCheck(checks, missing, grantText, "SELECT", "Read target tables", profile.getDatabaseName(), false);
            addMySqlPrivilegeCheck(checks, missing, grantText, "INSERT", "Insert incremental rows", profile.getDatabaseName(), false);
            addMySqlPrivilegeCheck(checks, missing, grantText, "UPDATE", "Update incremental rows", profile.getDatabaseName(), false);
            addMySqlPrivilegeCheck(checks, missing, grantText, "DELETE", "Delete incremental rows", profile.getDatabaseName(), false);
            addMySqlPrivilegeCheck(checks, missing, grantText, "CREATE", "Create tables for load", profile.getDatabaseName(), false);
            addMySqlPrivilegeCheck(checks, missing, grantText, "ALTER", "Adjust table structure", profile.getDatabaseName(), false);
            addMySqlPrivilegeCheck(checks, missing, grantText, "INDEX", "Create indexes", profile.getDatabaseName(), false);
            return build(profile, checks, missing, tidbGrantStatements(profile));
        } catch (Exception ex) {
            checks.add(check("connectivity", "TiDB login", false, ex.getMessage()));
            missing.add("TiDB login or base connectivity");
            return build(profile, checks, missing, tidbGrantStatements(profile));
        }
    }

    private ConnectionProfilePermissionCheckResponse checkPostgreSql(ConnectionProfileEntity profile) {
        List<ConnectionProfilePermissionCheckItemResponse> checks = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        String schema = connectionBindingService.firstNonBlank(profile.getSchemaName(), "public");
        try (Connection connection = openSourceConnection(profile)) {
            checks.add(check("connectivity", "Database login", true, "Successfully connected to PostgreSQL."));
            boolean canLogin = queryBoolean(connection, "select rolcanlogin from pg_roles where rolname = current_user");
            boolean replication = queryBoolean(connection, "select rolreplication from pg_roles where rolname = current_user");
            boolean connect = queryBoolean(connection, "select has_database_privilege(current_user, current_database(), 'CONNECT')");
            boolean schemaUsage = queryBoolean(connection, "select has_schema_privilege(current_user, '" + escapeSql(schema) + "', 'USAGE')");
            int missingTableSelectCount = queryInt(connection,
                    "select count(*) from information_schema.tables t where t.table_schema = '" + escapeSql(schema)
                            + "' and t.table_type = 'BASE TABLE' and not has_table_privilege(current_user, quote_ident(t.table_schema)||'.'||quote_ident(t.table_name), 'SELECT')");

            addBooleanCheck(checks, missing, "login", "Role can login", canLogin, "LOGIN privilege is required.");
            addBooleanCheck(checks, missing, "replication", "Logical replication privilege", replication, "REPLICATION privilege is required.");
            addBooleanCheck(checks, missing, "connect", "Connect to database", connect, "CONNECT ON DATABASE is required.");
            addBooleanCheck(checks, missing, "schema-usage", "Use schema", schemaUsage, "USAGE ON SCHEMA " + schema + " is required.");
            boolean selectAllTables = missingTableSelectCount == 0;
            checks.add(check("table-select", "Read tables in schema", selectAllTables,
                    selectAllTables ? "SELECT is available on all tables in the schema." : missingTableSelectCount + " table(s) are missing SELECT privilege."));
            if (!selectAllTables) {
                missing.add("SELECT ON ALL TABLES IN SCHEMA " + schema);
            }
            return build(profile, checks, missing, postgresqlGrantStatements(profile, schema));
        } catch (Exception ex) {
            checks.add(check("connectivity", "Database login", false, ex.getMessage()));
            missing.add("PostgreSQL login or base connectivity");
            return build(profile, checks, missing, postgresqlGrantStatements(profile, schema));
        }
    }

    private ConnectionProfilePermissionCheckResponse checkSqlServer(ConnectionProfileEntity profile) {
        List<ConnectionProfilePermissionCheckItemResponse> checks = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        String schema = connectionBindingService.firstNonBlank(profile.getSchemaName(), "dbo");
        try (Connection connection = openSourceConnection(profile)) {
            checks.add(check("connectivity", "Database login", true, "Successfully connected to SQL Server."));
            boolean cdcEnabled = queryBoolean(connection, "select cast(is_cdc_enabled as bit) from sys.databases where name = DB_NAME()");
            boolean databaseSelect = queryBoolean(connection, "select cast(HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'SELECT') as bit)");
            boolean canReadCdcCatalog = tryQuery(connection, "select top 1 1 from cdc.change_tables");

            addBooleanCheck(checks, missing, "cdc-enabled", "CDC enabled on database", cdcEnabled, "An administrator must run sp_cdc_enable_db first.");
            addBooleanCheck(checks, missing, "select", "Read source tables", databaseSelect, "SELECT on source tables is required.");
            addBooleanCheck(checks, missing, "cdc-catalog", "Read CDC change tables", canReadCdcCatalog, "A CDC gating role or equivalent access to cdc.change_tables is required.");
            return build(profile, checks, missing, sqlServerGrantStatements(profile, schema));
        } catch (Exception ex) {
            checks.add(check("connectivity", "Database login", false, ex.getMessage()));
            missing.add("SQL Server login or base connectivity");
            return build(profile, checks, missing, sqlServerGrantStatements(profile, schema));
        }
    }

    private ConnectionProfilePermissionCheckResponse checkOracle(ConnectionProfileEntity profile) {
        List<ConnectionProfilePermissionCheckItemResponse> checks = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        try (Connection connection = openSourceConnection(profile)) {
            checks.add(check("connectivity", "Database login", true, "Successfully connected to Oracle."));
            Set<String> privileges = new LinkedHashSet<>(querySingleColumn(connection, "select privilege from session_privs"));
            Set<String> roles = new LinkedHashSet<>(querySingleColumn(connection, "select granted_role from user_role_privs"));
            boolean cdb = tryQueryBoolean(connection, "select case when cdb = 'YES' then 1 else 0 end from V$DATABASE");

            addOraclePrivilegeCheck(checks, missing, privileges, "CREATE SESSION", "Create session");
            if (cdb) {
                addOraclePrivilegeCheck(checks, missing, privileges, "SET CONTAINER", "Switch PDB or CDB");
            }
            addOraclePrivilegeCheck(checks, missing, privileges, "FLASHBACK ANY TABLE", "Flashback any table");
            addOraclePrivilegeCheck(checks, missing, privileges, "SELECT ANY TABLE", "Read source tables");
            addOraclePrivilegeCheck(checks, missing, privileges, "SELECT ANY TRANSACTION", "Read transaction metadata");
            addOraclePrivilegeCheck(checks, missing, privileges, "LOGMINING", "Run LogMiner");
            addOraclePrivilegeCheck(checks, missing, privileges, "CREATE TABLE", "Create LogMiner helper tables");
            addOraclePrivilegeCheck(checks, missing, privileges, "LOCK ANY TABLE", "Acquire locks for export");
            addOraclePrivilegeCheck(checks, missing, privileges, "CREATE SEQUENCE", "Create LogMiner helper sequences");
            addOracleRoleCheck(checks, missing, roles, "SELECT_CATALOG_ROLE", "Read data dictionary");
            addOracleRoleCheck(checks, missing, roles, "EXECUTE_CATALOG_ROLE", "Execute catalog packages");
            addProbeCheck(connection, checks, missing, "V_$DATABASE", "Read V_$DATABASE", "select 1 from V_$DATABASE where 1 = 0");
            addProbeCheck(connection, checks, missing, "V_$LOGMNR_CONTENTS", "Read V_$LOGMNR_CONTENTS", "select 1 from V_$LOGMNR_CONTENTS where 1 = 0");
            return build(profile, checks, missing, oracleGrantStatements(profile));
        } catch (Exception ex) {
            checks.add(check("connectivity", "Database login", false, ex.getMessage()));
            missing.add("Oracle login or base connectivity");
            return build(profile, checks, missing, oracleGrantStatements(profile));
        }
    }

    private ConnectionProfilePermissionCheckResponse checkHana(ConnectionProfileEntity profile) {
        List<ConnectionProfilePermissionCheckItemResponse> checks = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        try (Connection connection = openSourceConnection(profile)) {
            checks.add(check("connectivity", "Database login", true, "Successfully connected to SAP HANA."));
            boolean metadataReadable = tryQuery(connection, "select top 1 schema_name from sys.tables");
            addBooleanCheck(checks, missing, "catalog-read", "Read system catalog", metadataReadable, "CATALOG READ is recommended.");
            addFirstTableReadCheck(connection, profile, checks, missing, "Read source tables");
            return build(profile, checks, missing, hanaGrantStatements(profile));
        } catch (Exception ex) {
            checks.add(check("connectivity", "Database login", false, ex.getMessage()));
            missing.add("HANA login or base connectivity");
            return build(profile, checks, missing, hanaGrantStatements(profile));
        }
    }

    private ConnectionProfilePermissionCheckResponse checkDb2(ConnectionProfileEntity profile) {
        List<ConnectionProfilePermissionCheckItemResponse> checks = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        try (Connection connection = openSourceConnection(profile)) {
            checks.add(check("connectivity", "Database login", true, "Successfully connected to Db2."));
            boolean metadataReadable = tryQuery(connection, "select count(*) from syscat.tables fetch first 1 rows only");
            addBooleanCheck(checks, missing, "catalog-read", "Read system catalog", metadataReadable, "Read access on SYSCAT views is recommended.");
            addFirstTableReadCheck(connection, profile, checks, missing, "Read source tables");
            return build(profile, checks, missing, db2GrantStatements(profile));
        } catch (Exception ex) {
            checks.add(check("connectivity", "Database login", false, ex.getMessage()));
            missing.add("Db2 login or base connectivity");
            return build(profile, checks, missing, db2GrantStatements(profile));
        }
    }

    private ConnectionProfilePermissionCheckResponse checkMongoDb(ConnectionProfileEntity profile) {
        List<ConnectionProfilePermissionCheckItemResponse> checks = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        try {
            SourceConnectionProperties source = connectionBindingService.toSourceProperties(profile);
            String uri = MongoConnectionSupport.resolveConnectionString(source, Map.of());
            try (MongoClient client = MongoClients.create(uri)) {
                var database = client.getDatabase(connectionBindingService.firstNonBlank(profile.getDatabaseName(), "admin"));
                database.runCommand(new org.bson.Document("ping", 1));
                checks.add(check("connectivity", "Database login", true, "Successfully connected to MongoDB."));
                var connectionStatus = database.runCommand(new org.bson.Document("connectionStatus", 1).append("showPrivileges", true));
                var authInfo = connectionStatus.get("authInfo", org.bson.Document.class);
                List<String> roleNames = new ArrayList<>();
                if (authInfo != null) {
                    var roles = authInfo.getList("authenticatedUserRoles", org.bson.Document.class, List.of());
                    for (org.bson.Document role : roles) {
                        roleNames.add(role.getString("role"));
                    }
                }
                boolean hasReadRole = roleNames.stream().anyMatch(role -> List.of("read", "readWrite", "dbOwner", "root").contains(role));
                boolean replicaSet = connectionStatus.containsKey("setName") || connectionStatus.getBoolean("isreplicaset", false);
                addBooleanCheck(checks, missing, "read-role", "Read role assigned", hasReadRole, "Grant read or a stronger role.");
                addBooleanCheck(checks, missing, "replica-set", "Change stream environment ready", replicaSet, "Change streams require a replica set or sharded cluster.");
                return build(profile, checks, missing, mongoGrantStatements(profile));
            }
        } catch (Exception ex) {
            checks.add(check("connectivity", "Database login", false, ex.getMessage()));
            missing.add("MongoDB login or base connectivity");
            return build(profile, checks, missing, mongoGrantStatements(profile));
        }
    }

    private ConnectionProfilePermissionCheckResponse build(
            ConnectionProfileEntity profile,
            List<ConnectionProfilePermissionCheckItemResponse> checks,
            Set<String> missingPermissions,
            List<String> suggestions
    ) {
        boolean passed = missingPermissions.isEmpty() && checks.stream().allMatch(ConnectionProfilePermissionCheckItemResponse::passed);
        String summary = passed
                ? "Permission check passed. You can continue with schema or data sync tasks."
                : "Permission check failed. Grant the missing privileges and run the check again.";
        return new ConnectionProfilePermissionCheckResponse(
                profile.getId(),
                profile.getName(),
                profile.getRole(),
                profile.getDatabaseType(),
                passed,
                summary,
                List.copyOf(missingPermissions),
                passed ? List.of() : suggestions,
                List.copyOf(checks),
                Instant.now()
        );
    }

    private void addMySqlPrivilegeCheck(
            List<ConnectionProfilePermissionCheckItemResponse> checks,
            Set<String> missing,
            String grantText,
            String privilege,
            String label,
            String databaseName,
            boolean globalOnly,
            String... aliases
    ) {
        boolean passed = hasMySqlPrivilege(grantText, privilege, databaseName, globalOnly);
        if (!passed) {
            for (String alias : aliases) {
                if (hasMySqlPrivilege(grantText, alias, databaseName, globalOnly)) {
                    passed = true;
                    break;
                }
            }
        }
        checks.add(check("mysql-" + privilege.toLowerCase(Locale.ROOT).replace(' ', '-'), label, passed,
                passed ? privilege + " privilege is available." : privilege + " privilege is missing."));
        if (!passed) {
            missing.add(privilege);
        }
    }

    private boolean hasMySqlPrivilege(String grantText, String privilege, String databaseName, boolean globalOnly) {
        String normalizedPrivilege = privilege.toUpperCase(Locale.ROOT);
        String databaseScope = databaseName == null || databaseName.isBlank() ? null : ("`" + databaseName + "`").toUpperCase(Locale.ROOT);
        for (String line : grantText.split("\n")) {
            String normalized = line.toUpperCase(Locale.ROOT);
            if (!normalized.startsWith("GRANT ")) {
                continue;
            }
            boolean scopeMatches = normalized.contains(" ON *.* ");
            if (!globalOnly && databaseScope != null) {
                scopeMatches = scopeMatches || normalized.contains(" ON " + databaseScope + ".* ");
            }
            if (!scopeMatches) {
                continue;
            }
            if (normalized.contains("ALL PRIVILEGES")) {
                return true;
            }
            if (normalized.contains(normalizedPrivilege)) {
                return true;
            }
        }
        return false;
    }

    private void addBooleanCheck(
            List<ConnectionProfilePermissionCheckItemResponse> checks,
            Set<String> missing,
            String key,
            String label,
            boolean passed,
            String failureDetail
    ) {
        checks.add(check(key, label, passed, passed ? "Check passed." : failureDetail));
        if (!passed) {
            missing.add(label);
        }
    }

    private void addOraclePrivilegeCheck(
            List<ConnectionProfilePermissionCheckItemResponse> checks,
            Set<String> missing,
            Set<String> privileges,
            String privilege,
            String label
    ) {
        boolean passed = privileges.stream().anyMatch(item -> item.equalsIgnoreCase(privilege));
        checks.add(check("oracle-" + privilege.toLowerCase(Locale.ROOT).replace(' ', '-'), label, passed,
                passed ? privilege + " is available." : privilege + " is missing."));
        if (!passed) {
            missing.add(privilege);
        }
    }

    private void addOracleRoleCheck(
            List<ConnectionProfilePermissionCheckItemResponse> checks,
            Set<String> missing,
            Set<String> roles,
            String role,
            String label
    ) {
        boolean passed = roles.stream().anyMatch(item -> item.equalsIgnoreCase(role));
        checks.add(check("oracle-role-" + role.toLowerCase(Locale.ROOT), label, passed,
                passed ? "Role " + role + " is available." : "Role " + role + " is missing."));
        if (!passed) {
            missing.add(role);
        }
    }

    private void addProbeCheck(
            Connection connection,
            List<ConnectionProfilePermissionCheckItemResponse> checks,
            Set<String> missing,
            String missingLabel,
            String label,
            String sql
    ) {
        boolean passed = tryQuery(connection, sql);
        checks.add(check("probe-" + missingLabel.toLowerCase(Locale.ROOT), label, passed,
                passed ? "Probe succeeded." : "Probe failed, which usually means the related object privilege is missing."));
        if (!passed) {
            missing.add(missingLabel);
        }
    }

    private void addFirstTableReadCheck(
            Connection connection,
            ConnectionProfileEntity profile,
            List<ConnectionProfilePermissionCheckItemResponse> checks,
            Set<String> missing,
            String label
    ) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connectionBindingService.tableCatalog(profile);
            String schema = connectionBindingService.tableSchema(profile);
            try (ResultSet tables = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                if (tables.next()) {
                    String tableSchema = connectionBindingService.firstNonBlank(tables.getString("TABLE_SCHEM"), schema);
                    String tableName = tables.getString("TABLE_NAME");
                    boolean passed = tryQuery(connection, "select * from " + qualifiedTable(tableSchema, tableName) + " where 1 = 0");
                    checks.add(check("table-read", label, passed,
                            passed ? "A sample source table can be read." : "Reading a sample source table failed, which usually means SELECT is missing."));
                    if (!passed) {
                        missing.add("SELECT ON SOURCE TABLES");
                    }
                    return;
                }
            }
            checks.add(check("table-read", label, true, "No table was found in the current schema. Sample table read probe was skipped."));
        } catch (Exception ex) {
            checks.add(check("table-read", label, false, "Sample table read probe failed: " + ex.getMessage()));
            missing.add("SELECT ON SOURCE TABLES");
        }
    }

    private String qualifiedTable(String schema, String table) {
        if (schema == null || schema.isBlank()) {
            return table;
        }
        return schema + "." + table;
    }

    private ConnectionProfilePermissionCheckItemResponse check(String key, String label, boolean passed, String detail) {
        return new ConnectionProfilePermissionCheckItemResponse(key, label, passed, detail);
    }

    private Connection openSourceConnection(ConnectionProfileEntity profile) throws Exception {
        SourceConnectionProperties source = connectionBindingService.toSourceProperties(profile);
        String url = JdbcConnectionSupport.resolveSourceJdbcUrl(source);
        return DriverManager.getConnection(url, source.username(), source.password());
    }

    private Connection openTargetConnection(ConnectionProfileEntity profile) throws Exception {
        TargetConnectionProperties target = connectionBindingService.toTargetProperties(profile, null);
        String url = JdbcConnectionSupport.resolveTargetJdbcUrl(target);
        return DriverManager.getConnection(url, target.username(), target.password());
    }

    private List<String> querySingleColumn(Connection connection, String sql) throws Exception {
        List<String> results = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String value = resultSet.getString(1);
                if (value != null && !value.isBlank()) {
                    results.add(value.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return results;
    }

    private boolean queryBoolean(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getBoolean(1);
            }
        }
        return false;
    }

    private boolean tryQueryBoolean(Connection connection, String sql) {
        try {
            return queryBoolean(connection, sql);
        } catch (Exception ignored) {
            return false;
        }
    }

    private int queryInt(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        }
        return 0;
    }

    private boolean tryQuery(Connection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String escapeSql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private List<String> mysqlSourceGrantStatements(ConnectionProfileEntity profile) {
        String username = connectionBindingService.firstNonBlank(profile.getUsername(), "sync_user");
        return List.of(
                "CREATE USER '" + username + "'@'%' IDENTIFIED BY 'StrongPassword';",
                "GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO '" + username + "'@'%';",
                "GRANT LOCK TABLES ON *.* TO '" + username + "'@'%';",
                "FLUSH PRIVILEGES;"
        );
    }

    private List<String> tidbGrantStatements(ConnectionProfileEntity profile) {
        String username = connectionBindingService.firstNonBlank(profile.getUsername(), "sync_user");
        String databaseName = connectionBindingService.firstNonBlank(profile.getDatabaseName(), "target_db");
        return List.of(
                "CREATE USER '" + username + "'@'%' IDENTIFIED BY 'StrongPassword';",
                "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX ON `" + databaseName + "`.* TO '" + username + "'@'%';"
        );
    }

    private List<String> postgresqlGrantStatements(ConnectionProfileEntity profile, String schema) {
        String username = connectionBindingService.firstNonBlank(profile.getUsername(), "sync_user");
        String databaseName = connectionBindingService.firstNonBlank(profile.getDatabaseName(), "appdb");
        return List.of(
                "CREATE ROLE " + username + " REPLICATION LOGIN PASSWORD 'StrongPassword';",
                "GRANT CONNECT ON DATABASE " + databaseName + " TO " + username + ";",
                "GRANT USAGE ON SCHEMA " + schema + " TO " + username + ";",
                "GRANT SELECT ON ALL TABLES IN SCHEMA " + schema + " TO " + username + ";",
                "ALTER DEFAULT PRIVILEGES IN SCHEMA " + schema + " GRANT SELECT ON TABLES TO " + username + ";"
        );
    }

    private List<String> sqlServerGrantStatements(ConnectionProfileEntity profile, String schema) {
        String username = connectionBindingService.firstNonBlank(profile.getUsername(), "sync_user");
        String databaseName = connectionBindingService.firstNonBlank(profile.getDatabaseName(), "MyDB");
        return List.of(
                "USE " + databaseName + ";",
                "EXEC sys.sp_cdc_enable_db;",
                "EXEC sys.sp_cdc_enable_table @source_schema = N'" + schema + "', @source_name = N'<TABLE_NAME>', @role_name = N'cdc_reader', @supports_net_changes = 0;",
                "CREATE USER " + username + " FOR LOGIN " + username + ";",
                "GRANT SELECT ON SCHEMA::" + schema + " TO " + username + ";",
                "EXEC sp_addrolemember N'cdc_reader', N'" + username + "';"
        );
    }

    private List<String> oracleGrantStatements(ConnectionProfileEntity profile) {
        String username = connectionBindingService.firstNonBlank(profile.getUsername(), "c##dbzuser");
        return List.of(
                "GRANT CREATE SESSION TO " + username + " CONTAINER=ALL;",
                "GRANT SET CONTAINER TO " + username + " CONTAINER=ALL;",
                "GRANT FLASHBACK ANY TABLE TO " + username + " CONTAINER=ALL;",
                "GRANT SELECT ANY TABLE TO " + username + " CONTAINER=ALL;",
                "GRANT SELECT_CATALOG_ROLE TO " + username + " CONTAINER=ALL;",
                "GRANT EXECUTE_CATALOG_ROLE TO " + username + " CONTAINER=ALL;",
                "GRANT SELECT ANY TRANSACTION TO " + username + " CONTAINER=ALL;",
                "GRANT LOGMINING TO " + username + " CONTAINER=ALL;",
                "GRANT CREATE TABLE TO " + username + " CONTAINER=ALL;",
                "GRANT LOCK ANY TABLE TO " + username + " CONTAINER=ALL;",
                "GRANT CREATE SEQUENCE TO " + username + " CONTAINER=ALL;",
                "GRANT EXECUTE ON DBMS_LOGMNR TO " + username + " CONTAINER=ALL;",
                "GRANT EXECUTE ON DBMS_LOGMNR_D TO " + username + " CONTAINER=ALL;",
                "GRANT SELECT ON V_$DATABASE TO " + username + " CONTAINER=ALL;",
                "GRANT SELECT ON V_$LOGMNR_CONTENTS TO " + username + " CONTAINER=ALL;"
        );
    }

    private List<String> hanaGrantStatements(ConnectionProfileEntity profile) {
        String username = connectionBindingService.firstNonBlank(profile.getUsername(), "SYNC_USER");
        String schema = connectionBindingService.firstNonBlank(profile.getSchemaName(), "APP");
        return List.of(
                "GRANT CATALOG READ TO " + username + ";",
                "GRANT SELECT ON SCHEMA " + schema + " TO " + username + ";"
        );
    }

    private List<String> db2GrantStatements(ConnectionProfileEntity profile) {
        String username = connectionBindingService.firstNonBlank(profile.getUsername(), "SYNC_USER");
        String schema = connectionBindingService.firstNonBlank(profile.getSchemaName(), username);
        return List.of(
                "GRANT CONNECT ON DATABASE TO USER " + username + ";",
                "GRANT SELECT ON ALL TABLES IN SCHEMA " + schema + " TO USER " + username + ";"
        );
    }

    private List<String> mongoGrantStatements(ConnectionProfileEntity profile) {
        String username = connectionBindingService.firstNonBlank(profile.getUsername(), "sync_user");
        String databaseName = connectionBindingService.firstNonBlank(profile.getDatabaseName(), "source_db");
        return List.of(
                "use " + databaseName,
                "db.createUser({ user: \"" + username + "\", pwd: \"StrongPassword\", roles: [ { role: \"read\", db: \"" + databaseName + "\" } ] })"
        );
    }
}
