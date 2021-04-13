/*
 * Copyright <2021> Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package software.amazon.documentdb.jdbc;

import software.amazon.documentdb.jdbc.common.DatabaseMetaData;
import software.amazon.documentdb.jdbc.common.utilities.JdbcType;
import software.amazon.documentdb.jdbc.metadata.DocumentDbCollectionMetadata;
import software.amazon.documentdb.jdbc.metadata.DocumentDbDatabaseSchemaMetadata;
import software.amazon.documentdb.jdbc.metadata.DocumentDbMetadataColumn;
import software.amazon.documentdb.jdbc.metadata.DocumentDbMetadataTable;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static software.amazon.documentdb.jdbc.DocumentDbConnectionProperties.isNullOrWhitespace;
import static software.amazon.documentdb.jdbc.DocumentDbDatabaseMetaDataResultSets.buildAttributesColumnMetaData;
import static software.amazon.documentdb.jdbc.DocumentDbDatabaseMetaDataResultSets.buildCatalogsColumnMetaData;
import static software.amazon.documentdb.jdbc.DocumentDbDatabaseMetaDataResultSets.buildColumnPrivilegesColumnMetaData;
import static software.amazon.documentdb.jdbc.DocumentDbDatabaseMetaDataResultSets.buildColumnsColumnMetaData;
import static software.amazon.documentdb.jdbc.DocumentDbDatabaseMetaDataResultSets.buildImportedKeysColumnMetaData;
import static software.amazon.documentdb.jdbc.DocumentDbDatabaseMetaDataResultSets.buildPrimaryKeysColumnMetaData;
import static software.amazon.documentdb.jdbc.DocumentDbDatabaseMetaDataResultSets.buildProceduresColumnMetaData;
import static software.amazon.documentdb.jdbc.DocumentDbDatabaseMetaDataResultSets.buildSchemasColumnMetaData;
import static software.amazon.documentdb.jdbc.DocumentDbDatabaseMetaDataResultSets.buildTableTypesColumnMetaData;
import static software.amazon.documentdb.jdbc.DocumentDbDatabaseMetaDataResultSets.buildTablesColumnMetaData;

/**
 * DocumentDb implementation of DatabaseMetaData.
 */
public class DocumentDbDatabaseMetaData extends DatabaseMetaData implements java.sql.DatabaseMetaData {
    private static final Map<Integer, Integer> TYPE_COLUMN_SIZE_MAP;
    private final DocumentDbDatabaseSchemaMetadata databaseMetadata;
    private final DocumentDbConnectionProperties properties;

    static {
        TYPE_COLUMN_SIZE_MAP = new HashMap<>();
        for (JdbcType jdbcType : JdbcType.values()) {
            switch (jdbcType.getJdbcType()) {
                case Types.DECIMAL:
                case Types.NUMERIC:
                    TYPE_COLUMN_SIZE_MAP.put(jdbcType.getJdbcType(), 646456995); // precision + "-.".length()}
                    break;
                case Types.FLOAT:
                case Types.REAL:
                case Types.DOUBLE:
                    TYPE_COLUMN_SIZE_MAP.put(jdbcType.getJdbcType(), 23); // String.valueOf(-Double.MAX_VALUE).length();
                    break;
                case Types.BIGINT:
                    TYPE_COLUMN_SIZE_MAP.put(jdbcType.getJdbcType(), 20); // decimal precision + "-".length();
                    break;
                case Types.INTEGER:
                    TYPE_COLUMN_SIZE_MAP.put(jdbcType.getJdbcType(), 11); // decimal precision + "-".length();
                    break;
                case Types.SMALLINT :
                    TYPE_COLUMN_SIZE_MAP.put(jdbcType.getJdbcType(),  6); // decimal precision + "-".length();
                    break;
                case Types.TINYINT :
                    TYPE_COLUMN_SIZE_MAP.put(jdbcType.getJdbcType(), 4);
                    break;
                case Types.VARBINARY:
                case Types.VARCHAR:
                    TYPE_COLUMN_SIZE_MAP.put(jdbcType.getJdbcType(), 65536);
                    break;
                default:
                    TYPE_COLUMN_SIZE_MAP.put(jdbcType.getJdbcType(), 0);
                    break;
            }
        }
    }

    /**
     * DocumentDbDatabaseMetaData constructor, initializes super class.
     *
     * @param connection the connection.
     * @param databaseMetadata the underlying database metadata.
     * @param properties the connection properties.
     */
    DocumentDbDatabaseMetaData(
            final DocumentDbConnection connection,
            final DocumentDbDatabaseSchemaMetadata databaseMetadata,
            final DocumentDbConnectionProperties properties) {
        super(connection);
        this.databaseMetadata = databaseMetadata;
        this.properties = properties;
    }

    // TODO: Go through and implement these functions
    @Override
    public String getURL() {
        return DocumentDbDriver.DOCUMENT_DB_SCHEME + properties.buildSanitizedConnectionString();
    }

    @Override
    public String getUserName() {
        return properties.getUser();
    }

    @Override
    public String getDatabaseProductName() {
        return "DocumentDB";
    }

    @Override
    public String getDatabaseProductVersion() {
        // TODO: Get this from underlying server.
        return "4.0";
    }

    @Override
    public String getDriverName() {
        return "DocumentDB JDBC Driver";
    }

    @Override
    public String getSQLKeywords() {
        return "";
    }

    @Override
    public String getNumericFunctions() {
        return "";
    }

    @Override
    public String getStringFunctions() {
        return "";
    }

    @Override
    public String getSystemFunctions() {
        return "";
    }

    @Override
    public String getTimeDateFunctions() {
        return "";
    }

    @Override
    public String getSearchStringEscape() {
        return "\\";
    }

    @Override
    public String getExtraNameCharacters() {
        return "";
    }

    @Override
    public String getCatalogTerm() {
        return "catalog";
    }

    @Override
    public String getCatalogSeparator() {
        return ".";
    }

    @Override
    public int getMaxRowSize() {
        return 0; // Indicate either no limit or unknown.
    }

    @Override
    public ResultSet getProcedures(final String catalog, final String schemaPattern,
            final String procedureNamePattern) {
        final List<List<Object>> metaData = new ArrayList<>();
        return new DocumentDbListResultSet(
                null,
                buildProceduresColumnMetaData(properties.getDatabase()),
                metaData);
    }

    @Override
    public ResultSet getTables(final String catalog, final String schemaPattern,
            final String tableNamePattern, final String[] types) {
        final List<List<Object>> metaData = new ArrayList<>();

        // ASSUMPTION: We're only supporting tables.
        if (isNullOrWhitespace(catalog)
                && (types == null || Arrays.stream(types)
                        .anyMatch(s -> isNullOrWhitespace(s) || s.equals("TABLE")))) {
            if (isNullOrWhitespace(schemaPattern)
                    || properties.getDatabase().matches(convertPatternToRegex(schemaPattern))) {
                addTablesForSchema(tableNamePattern, metaData);
            }
        }

        return new DocumentDbListResultSet(
                null,
                buildTablesColumnMetaData(properties.getDatabase()),
                metaData);
    }

    private void addTablesForSchema(final String tableNamePattern,
            final List<List<Object>> metaData) {
        for (Entry<String, DocumentDbCollectionMetadata> collection : databaseMetadata
                .getCollectionMetadataMap().entrySet()) {
            for (DocumentDbMetadataTable table : collection.getValue().getTables()
                    .values()) {
                if (isNullOrWhitespace(tableNamePattern)
                        || table.getName().matches(convertPatternToRegex(tableNamePattern))) {
                    addTableEntry(metaData, table);
                }
            }
        }
    }

    private void addTableEntry(final List<List<Object>> metaData,
            final DocumentDbMetadataTable table) {
        // 1. TABLE_CAT String => table catalog (may be null)
        // 2. TABLE_SCHEM String => table schema (may be null)
        // 3. TABLE_NAME String => table name
        // 4. TABLE_TYPE String => table type. Typical types are "TABLE", "VIEW", "SYSTEM TABLE", "GLOBAL TEMPORARY", "LOCAL TEMPORARY", "ALIAS", "SYNONYM".
        // 5. REMARKS String => explanatory comment on the table
        // 6. TYPE_CAT String => the types catalog (may be null)
        // 7. TYPE_SCHEM String => the types schema (may be null)
        // 8. TYPE_NAME String => type name (may be null)
        // 9. SELF_REFERENCING_COL_NAME String => name of the designated "identifier" column of a typed table (may be null)
        // 10. REF_GENERATION String => specifies how values in SELF_REFERENCING_COL_NAME are created. Values are "SYSTEM", "USER", "DERIVED". (may be null)
        final List<Object> row = new ArrayList<>(Arrays.asList(
                null,
                properties.getDatabase(),
                table.getName(),
                "TABLE",
                null,
                null,
                null,
                null,
                null,
                null));
        metaData.add(row);
    }

    @Override
    public ResultSet getSchemas() {
        return getSchemas(null, null);
    }

    @Override
    public ResultSet getCatalogs() {
        final List<List<Object>> metaData = new ArrayList<>();
        // 1. TABLE_CAT String => catalog name
        // Note: return NO records to indicate no catalogs.
        return new DocumentDbListResultSet(
                null,
                buildCatalogsColumnMetaData(properties.getDatabase()),
                metaData);
    }

    @Override
    public ResultSet getTableTypes() {
        final List<List<Object>> metaData = new ArrayList<>();
        // ASSUMPTION: We're only supporting TABLE types.
        for (String tableType : Arrays.asList("TABLE")) {
            // 1. TABLE_TYPE String => table type. Typical types are "TABLE", "VIEW", "SYSTEM TABLE", "GLOBAL TEMPORARY", "LOCAL TEMPORARY", "ALIAS", "SYNONYM".
            metaData.add(Collections.singletonList(tableType));
        }
        return new DocumentDbListResultSet(
                null,
                buildTableTypesColumnMetaData(properties.getDatabase()),
                metaData);
    }

    @Override
    public ResultSet getColumns(final String catalog, final String schemaPattern,
            final String tableNamePattern, final String columnNamePattern) {
        final List<List<Object>> metaData = new ArrayList<>();
        if (isNullOrWhitespace(catalog)) {
            if (isNullOrWhitespace(schemaPattern)
                    || properties.getDatabase().matches(convertPatternToRegex(schemaPattern))) {
                addColumnsForSchema(tableNamePattern, columnNamePattern, metaData);
            }
        }

        return new DocumentDbListResultSet(
                null,
                buildColumnsColumnMetaData(properties.getDatabase()),
                metaData);
    }

    private void addColumnsForSchema(final String tableNamePattern, final String columnNamePattern,
            final List<List<Object>> metaData) {
        for (Entry<String, DocumentDbCollectionMetadata> collection : databaseMetadata
                .getCollectionMetadataMap().entrySet()) {
            for (DocumentDbMetadataTable table : collection.getValue().getTables()
                    .values()) {
                if (isNullOrWhitespace(tableNamePattern)
                        || table.getName().matches(convertPatternToRegex(tableNamePattern))) {
                    addColumnsForTable(columnNamePattern, metaData, table);
                }
            }
        }
    }

    private void addColumnsForTable(final String columnNamePattern,
            final List<List<Object>> metaData,
            final DocumentDbMetadataTable table) {
        for (DocumentDbMetadataColumn column : table.getColumns().values()) {
            if (isNullOrWhitespace(columnNamePattern)
                    || column.getName().matches(convertPatternToRegex(columnNamePattern))) {
                addColumnEntry(metaData, table, column);
            }
        }
    }

    private void addColumnEntry(final List<List<Object>> metaData,
            final DocumentDbMetadataTable table,
            final DocumentDbMetadataColumn column) {
        //  1. TABLE_CAT String => table catalog (may be null)
        //  2. TABLE_SCHEM String => table schema (may be null)
        //  3. TABLE_NAME String => table name
        //  4. COLUMN_NAME String => column name
        //  5. DATA_TYPE int => SQL type from java.sql.Types
        //  6. TYPE_NAME String => Data source dependent type name, for a UDT the type name is fully qualified
        //  7. COLUMN_SIZE int => column size.
        //  8. BUFFER_LENGTH is not used.
        //  9. DECIMAL_DIGITS int => the number of fractional digits. Null is returned for data types where DECIMAL_DIGITS is not applicable.
        // 10. NUM_PREC_RADIX int => Radix (typically either 10 or 2)
        // 11. NULLABLE int => is NULL allowed.
        //        columnNoNulls - might not allow NULL values
        //        columnNullable - definitely allows NULL values
        //        columnNullableUnknown - nullability unknown
        // 12. REMARKS String => comment describing column (may be null)
        // 13. COLUMN_DEF String => default value for the column, which should be interpreted as a string when the value is enclosed in single quotes (may be null)
        // 14. SQL_DATA_TYPE int => unused
        // 15. SQL_DATETIME_SUB int => unused
        // 16. CHAR_OCTET_LENGTH int => for char types the maximum number of bytes in the column
        // 17. ORDINAL_POSITION int => index of column in table (starting at 1)
        // 18. IS_NULLABLE String => ISO rules are used to determine the nullability for a column.
        //        YES --- if the column can include NULLs
        //        NO --- if the column cannot include NULLs
        //        empty string --- if the nullability for the column is unknown
        // 19. SCOPE_CATALOG String => catalog of table that is the scope of a reference attribute (null if DATA_TYPE isn't REF)
        // 20. SCOPE_SCHEMA String => schema of table that is the scope of a reference attribute (null if the DATA_TYPE isn't REF)
        // 21. SCOPE_TABLE String => table name that this the scope of a reference attribute (null if the DATA_TYPE isn't REF)
        // 22. SOURCE_DATA_TYPE short => source type of a distinct type or user-generated Ref type, SQL type from java.sql.Types (null if DATA_TYPE isn't DISTINCT or user-generated REF)
        // 23. IS_AUTOINCREMENT String => Indicates whether this column is auto incremented
        //        YES --- if the column is auto incremented
        //        NO --- if the column is not auto incremented
        //        empty string --- if it cannot be determined whether the column is auto incremented
        // 24. IS_GENERATEDCOLUMN String => Indicates whether this is a generated column
        //        YES --- if this a generated column
        //        NO --- if this not a generated column
        //        empty string --- if it cannot be determined whether this is a generated column
        if (column.getSqlType() == Types.JAVA_OBJECT || column.getSqlType() == Types.ARRAY) {
            return;
        }
        final JdbcType jdbcType = JdbcType.fromType(column.getSqlType());
        final List<Object> row = new ArrayList<>(Arrays.asList(
                null, // TABLE_CAT
                properties.getDatabase(), // TABLE_SCHEM
                table.getName(), // TABLE_NAME
                column.getName(), // COLUMN_NAME
                column.getSqlType(), //DATA_TYPE
                jdbcType.name(), // TYPE_NAME
                TYPE_COLUMN_SIZE_MAP.get(column.getSqlType()),
                // COLUMN_SIZE
                null, // DECIMAL_DIGITS
                null, // NUM_PREC_RADIX
                null, // BUFFER_LENGTH
                column.getPrimaryKey() > 0 // NULLABLE
                        ? ResultSetMetaData.columnNoNulls
                        : ResultSetMetaData.columnNullable,
                null, // REMARKS
                null, // COLUMN_DEF
                null, // SQL_DATA_TYPE
                null, // SQL_DATETIME_SUB
                getCharOctetLength(column), // CHAR_OCTET_LENGTH
                column.getIndex() + 1, // ORDINAL_POSITION (one-based)
                column.getPrimaryKey() > 0 ? "NO" : "YES",
                // IS_NULLABLE
                null, // SCOPE_CATALOG
                null, // SCOPE_SCHEMA
                null, // SCOPE_TABLE
                null, // SOURCE_DATA_TYPE
                "NO", // IS_AUTOINCREMENT
                column.isGenerated() ? "YES" : "NO" // IS_GENERATEDCOLUMN
        ));
        metaData.add(row);
    }

    private static Integer getCharOctetLength(final DocumentDbMetadataColumn column) {
        switch (column.getSqlType()) {
            case Types.CHAR:
            case Types.NCHAR:
            case Types.VARCHAR:
            case Types.NVARCHAR:
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
                return TYPE_COLUMN_SIZE_MAP.get(column.getSqlType()) * 4;
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return TYPE_COLUMN_SIZE_MAP.get(column.getSqlType());
            default:
                return null;
        }
    }

    @Override
    public ResultSet getColumnPrivileges(final String catalog, final String schema,
            final String table, final String columnNamePattern) {
        // 1. TABLE_CAT String => table catalog (may be null)
        // 2. TABLE_SCHEM String => table schema (may be null)
        // 3. TABLE_NAME String => table name
        // 4. COLUMN_NAME String => column name
        // 5. GRANTOR String => grantor of access (may be null)
        // 6. GRANTEE String => grantee of access
        // 7. PRIVILEGE String => name of access (SELECT, INSERT, UPDATE, REFRENCES, ...)
        // 8. IS_GRANTABLE String => "YES" if grantee is permitted to grant to others; "NO" if not; null if unknown
        final List<List<Object>> metaData = new ArrayList<>();
        return new DocumentDbListResultSet(
                null,
                buildColumnPrivilegesColumnMetaData(properties.getDatabase()),
                metaData);
    }

    @Override
    public ResultSet getBestRowIdentifier(final String catalog, final String schema,
            final String table, final int scope, final boolean nullable)
            throws SQLException {
        // TODO: Implement
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public ResultSet getPrimaryKeys(final String catalog, final String schema,
            final String table) {
        // 1. TABLE_CAT String => table catalog (may be null)
        // 2. TABLE_SCHEM String => table schema (may be null)
        // 3. TABLE_NAME String => table name
        // 4. COLUMN_NAME String => column name
        // 5. KEY_SEQ short => sequence number within primary key( a value of 1 represents the first column of the primary key, a value of 2 would represent the second column within the primary key).
        // 6. PK_NAME String => primary key name (may be null)
        final List<List<Object>> metaData = new ArrayList<>();
        if (schema == null || properties.getDatabase().matches(convertPatternToRegex(schema))) {
            for (Entry<String, DocumentDbCollectionMetadata> collection : databaseMetadata
                    .getCollectionMetadataMap().entrySet()) {
                for (DocumentDbMetadataTable metadataTable : collection.getValue().getTables().values()) {
                    if (table == null || metadataTable.getName().matches(convertPatternToRegex(table))) {
                        for (DocumentDbMetadataColumn column : metadataTable.getColumns()
                                .values()) {
                            // 1. TABLE_CAT String => table catalog (may be null)
                            // 2. TABLE_SCHEM String => table schema (may be null)
                            // 3. TABLE_NAME String => table name
                            // 4. COLUMN_NAME String => column name
                            // 5. KEY_SEQ short => sequence number within primary key
                            //    (a value of 1 represents the first column of the primary key, a
                            //    value of 2 would represent the second column within the primary key).
                            // 6. PK_NAME String => primary key name (may be null)
                            if (column.getPrimaryKey() > 0) {
                                final List<Object> row = new ArrayList<>(Arrays.asList(
                                        null, // TABLE_CAT
                                        properties.getDatabase(), // TABLE_SCHEM
                                        metadataTable.getName(), // TABLE_NAME
                                        column.getName(), // COLUMN_NAME
                                        column.getPrimaryKey(), // KEY_SEQ
                                        null // PK_NAME
                                ));
                                metaData.add(row);
                            }
                        }
                    }
                }
            }
        }

        return new DocumentDbListResultSet(
                null,
                buildPrimaryKeysColumnMetaData(properties.getDatabase()),
                metaData);
    }

    @Override
    public ResultSet getImportedKeys(final String catalog, final String schema,
            final String table) {
        final List<List<Object>> metaData = new ArrayList<>();
        if (isNullOrWhitespace(catalog)) {
            if (schema == null || properties.getDatabase().matches(convertPatternToRegex(schema))) {
                addImportedKeysForSchema(table, metaData);
            }
        }

        return new DocumentDbListResultSet(
                null,
                buildImportedKeysColumnMetaData(properties.getDatabase()),
                metaData);
    }

    private void addImportedKeysForSchema(final String table,
            final List<List<Object>> metaData) {
        for (DocumentDbCollectionMetadata collection : databaseMetadata
                .getCollectionMetadataMap().values()) {
            // Get the base table by matching the collection path to the name of table.
            final DocumentDbMetadataTable baseMetadataTable = collection.getTables()
                    .get(collection.getPath());
            for (DocumentDbMetadataTable metadataTable : collection.getTables().values()) {
                if (table == null || metadataTable.getName().matches(convertPatternToRegex(table))) {
                    addImportedKeysForTable(metaData, baseMetadataTable, metadataTable);
                }
            }
        }
    }

    private void addImportedKeysForTable(final List<List<Object>> metaData,
            final DocumentDbMetadataTable baseMetadataTable,
            final DocumentDbMetadataTable metadataTable) {
        for (DocumentDbMetadataColumn column : metadataTable.getColumns()
                .values()) {
            addImportedKey(metaData, baseMetadataTable, metadataTable, column);
        }
    }

    private void addImportedKey(final List<List<Object>> metaData,
            final DocumentDbMetadataTable baseMetadataTable,
            final DocumentDbMetadataTable metadataTable,
            final DocumentDbMetadataColumn column) {
        //  1. PKTABLE_CAT String => primary key table catalog being imported (may be null)
        //  2. PKTABLE_SCHEM String => primary key table schema being imported (may be null)
        //  3. PKTABLE_NAME String => primary key table name being imported
        //  4. PKCOLUMN_NAME String => primary key column name being imported
        //  5. FKTABLE_CAT String => foreign key table catalog (may be null)
        //  6. FKTABLE_SCHEM String => foreign key table schema (may be null)
        //  7. FKTABLE_NAME String => foreign key table name
        //  8. FKCOLUMN_NAME String => foreign key column name
        //  9. KEY_SEQ short => sequence number within a foreign key
        //        (a value of 1 represents the first column of the foreign key, a value of 2 would represent
        //        the second column within the foreign key).
        // 10. UPDATE_RULE short => What happens to a foreign key when the primary key is updated:
        //        importedNoAction - do not allow update of primary key if it has been imported
        //        importedKeyCascade - change imported key to agree with primary key update
        //        importedKeySetNull - change imported key to NULL if its primary key has been updated
        //        importedKeySetDefault - change imported key to default values if its primary key has been updated
        //        importedKeyRestrict - same as importedKeyNoAction (for ODBC 2.x compatibility)
        // 11. DELETE_RULE short => What happens to the foreign key when primary is deleted.
        //        importedKeyNoAction - do not allow delete of primary key if it has been imported
        //        importedKeyCascade - delete rows that import a deleted key
        //        importedKeySetNull - change imported key to NULL if its primary key has been deleted
        //        importedKeyRestrict - same as importedKeyNoAction (for ODBC 2.x compatibility)
        //        importedKeySetDefault - change imported key to default if its primary key has been deleted
        // 12. FK_NAME String => foreign key name (may be null)
        // 13. PK_NAME String => primary key name (may be null)
        // 14. DEFERRABILITY short => can the evaluation of foreign key constraints be deferred until commit
        //        importedKeyInitiallyDeferred - see SQL92 for definition
        //        importedKeyInitiallyImmediate - see SQL92 for definition
        //        importedKeyNotDeferrable - see SQL92 for definition
        if (column.getForeignKey() > 0 && baseMetadataTable != null) {
            // ASSUMPTION: This can only be done because we only reference
            // the base table in a foreign key relationship.
            final List<Object> row = new ArrayList<>(Arrays.asList(
                    null, // PKTABLE_CAT
                    properties.getDatabase(), // PKTABLE_SCHEM
                    baseMetadataTable.getName(), // PKTABLE_NAME
                    column.getName(), // PKCOLUMN_NAME
                    null, // FKTABLE_CAT
                    properties.getDatabase(), // FKTABLE_SCHEM
                    metadataTable.getName(), // FKTABLE_NAME
                    column.getName(), // FKCOLUMN_NAME
                    column.getForeignKey(), // KEY_SEQ
                    importedKeyNoAction, // UPDATE_RULE
                    importedKeyNoAction, // DELETE_RULE
                    null, // FK_NAME
                    null, // PK_NAME
                    importedKeyInitiallyDeferred // DEFERRABILITY
            ));
            metaData.add(row);
        }
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        // TODO: Implement
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public ResultSet getIndexInfo(final String catalog, final String schema, final String table,
            final boolean unique, final boolean approximate) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public ResultSet getAttributes(final String catalog, final String schemaPattern,
            final String typeNamePattern, final String attributeNamePattern) {
        //  1. TYPE_CAT String => type catalog (may be null)
        //  2. TYPE_SCHEM String => type schema (may be null)
        //  3. TYPE_NAME String => type name
        //  4. ATTR_NAME String => attribute name
        //  5. DATA_TYPE int => attribute type SQL type from java.sql.Types
        //  6. ATTR_TYPE_NAME String => Data source dependent type name. For a UDT, the type name is fully qualified. For a REF, the type name is fully qualified and represents the target type of the reference type.
        //  7. ATTR_SIZE int => column size. For char or date types this is the maximum number of characters; for numeric or decimal types this is precision.
        //  8. DECIMAL_DIGITS int => the number of fractional digits. Null is returned for data types where DECIMAL_DIGITS is not applicable.
        //  9. NUM_PREC_RADIX int => Radix (typically either 10 or 2)
        // 10. NULLABLE int => whether NULL is allowed
        //        attributeNoNulls - might not allow NULL values
        //        attributeNullable - definitely allows NULL values
        //        attributeNullableUnknown - nullability unknown
        // 11. REMARKS String => comment describing column (may be null)
        // 12. ATTR_DEF String => default value (may be null)
        // 13. SQL_DATA_TYPE int => unused
        // 14. SQL_DATETIME_SUB int => unused
        // 15. CHAR_OCTET_LENGTH int => for char types the maximum number of bytes in the column
        // 16. ORDINAL_POSITION int => index of the attribute in the UDT (starting at 1)
        // 17. IS_NULLABLE String => ISO rules are used to determine the nullability for a attribute.
        //        YES --- if the attribute can include NULLs
        //        NO --- if the attribute cannot include NULLs
        //        empty string --- if the nullability for the attribute is unknown
        // 18. SCOPE_CATALOG String => catalog of table that is the scope of a reference attribute (null if DATA_TYPE isn't REF)
        // 19. SCOPE_SCHEMA String => schema of table that is the scope of a reference attribute (null if DATA_TYPE isn't REF)
        // 20. SCOPE_TABLE String => table name that is the scope of a reference attribute (null if the DATA_TYPE isn't REF)
        // 21. SOURCE_DATA_TYPE short => source type of a distinct type or user-generated Ref type,SQL type from java.sql.Types (null if DATA_TYPE isn't DISTINCT or user-generated REF)
        final List<List<Object>> metaData = new ArrayList<>();
        return new DocumentDbListResultSet(
                null,
                buildAttributesColumnMetaData(properties.getDatabase()),
                metaData);
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        // TODO: Implement
        return 4;
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        // TODO: Implement
        return 0;
    }

    @Override
    public int getJDBCMajorVersion() {
        return 4;
    }

    @Override
    public int getJDBCMinorVersion() {
        return 2;
    }

    @Override
    public ResultSet getSchemas(final String catalog, final String schemaPattern) {
        final List<List<Object>> metaData = new ArrayList<>();
        // 1. TABLE_SCHEM String => schema name
        // 2. TABLE_CATALOG String => catalog name (may be null)
        if (isNullOrWhitespace(catalog)) {
            if (isNullOrWhitespace(schemaPattern)
                    || properties.getDatabase().matches(convertPatternToRegex(schemaPattern))) {
                final List<Object> row = new ArrayList<>(
                        Arrays.asList(properties.getDatabase(), null));
                metaData.add(row);
            }
        }
        return new DocumentDbListResultSet(
                null,
                buildSchemasColumnMetaData(properties.getDatabase()),
                metaData);
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        // TODO: Implement
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * Expects a string with zero-or more occurrences of '%' and '_' in the pattern.
     * Here we're converting the SQL-type pattern to a Regex pattern.
     *
     * @param pattern the SQL-type pattern to convert.
     *
     * @return the pattern converted to a Regex pattern
     */
    private String convertPatternToRegex(final String pattern) {
        final StringBuilder converted = new StringBuilder();
        int start = 0;
        for (int i = 0; i < pattern.length(); i++) {
            if (pattern.charAt(i) == '%') {
                if (i - start > 0) {
                    converted.append(Pattern.quote(pattern.substring(start, i)));
                }
                converted.append(".*");
                start = i + 1;
            } else if (pattern.charAt(i) == '_') {
                if (i - start > 0) {
                    converted.append(Pattern.quote(pattern.substring(start, i)));
                }
                converted.append(".");
                start = i + 1;
            }
        }
        // Handle the trailing string.
        if (pattern.length() - start > 0) {
            converted.append(Pattern.quote(pattern.substring(start)));
        }
        return converted.toString();
    }
}