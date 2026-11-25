package com.auditlog.starter.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;

import com.auditlog.starter.model.CapturedApiLogEvent;
import com.auditlog.starter.dto.CapturedApiLogRecord;
import com.auditlog.starter.service.ApiExceptionCaptureManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Owns the private in-memory H2 database used by one capture session. */
public final class TemporaryH2CaptureStore implements AutoCloseable {
    private final JdbcDataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String databaseName;

    public TemporaryH2CaptureStore() {
        databaseName = "audit_capture_" + UUID.randomUUID().toString().replace("-", "");
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE");
        dataSource.setUser("sa");
        initializeSchema();
    }

    public boolean insert(CapturedApiLogEvent event) {
        String json = parseJsonObjectOrArray(event.message());
        long recordBytes = estimatedBytes(event, json);
        String sql = "INSERT INTO CAPTURED_API_LOG(CAPTURED_AT,LOGGED_AT,EVENT_TYPE,LEVEL,LOGGER_NAME,THREAD_NAME,MESSAGE,EXCEPTION_TYPE,EXCEPTION_MESSAGE,STACK_TRACE,REQUEST_METHOD,REQUEST_PATH,REQUEST_ID,HANDLER,SOURCE_CLASS,SOURCE_METHOD,EVENT_JSON,RECORD_BYTES) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long currentBytes;
            try (var sizeQuery = connection.createStatement();
                 ResultSet sizeResult = sizeQuery.executeQuery("SELECT COALESCE(SUM(RECORD_BYTES),0) FROM CAPTURED_API_LOG")) {
                currentBytes = sizeResult.next() ? sizeResult.getLong(1) : 0;
            }
            if (recordBytes > ApiExceptionCaptureManager.MAX_TOTAL_STORAGE_BYTES) {
                connection.rollback();
                return false;
            }
            while (currentBytes + recordBytes > ApiExceptionCaptureManager.MAX_TOTAL_STORAGE_BYTES) {
                long oldestId;
                long oldestSize;
                try (var oldest = connection.createStatement();
                     ResultSet row = oldest.executeQuery("SELECT ID,RECORD_BYTES FROM CAPTURED_API_LOG ORDER BY ID ASC LIMIT 1")) {
                    if (!row.next()) break;
                    oldestId = row.getLong("ID");
                    oldestSize = row.getLong("RECORD_BYTES");
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM CAPTURED_API_LOG WHERE ID=?")) {
                    delete.setLong(1, oldestId);
                    delete.executeUpdate();
                }
                currentBytes -= oldestSize;
            }
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = 1;
            ps.setTimestamp(i++, java.sql.Timestamp.from(event.capturedAt()));
            ps.setTimestamp(i++, event.loggedAt() == null ? null : java.sql.Timestamp.from(event.loggedAt()));
            ps.setString(i++, event.eventType());
            ps.setString(i++, event.level());
            ps.setString(i++, event.loggerName());
            ps.setString(i++, event.threadName());
            ps.setString(i++, event.message());
            ps.setString(i++, event.exceptionType());
            ps.setString(i++, event.exceptionMessage());
            ps.setString(i++, event.stackTrace());
            ps.setString(i++, event.requestMethod());
            ps.setString(i++, event.requestPath());
            ps.setString(i++, event.requestId());
            ps.setString(i++, event.handler());
            ps.setString(i++, event.sourceClass());
            ps.setString(i++, event.sourceMethod());
            ps.setString(i++, json);
            ps.setLong(i, recordBytes);
            ps.executeUpdate();
            }
            try (var trim = connection.createStatement()) {
                trim.executeUpdate("DELETE FROM CAPTURED_API_LOG WHERE ID NOT IN (SELECT ID FROM CAPTURED_API_LOG ORDER BY ID DESC LIMIT " + ApiExceptionCaptureManager.MAX_RECORDS + ")");
            }
            connection.commit();
            return true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not write captured event to temporary H2 store.", ex);
        }
    }

    public List<CapturedApiLogRecord> findRecent(int limit) {
        String sql = "SELECT ID,CAPTURED_AT,EVENT_TYPE,LEVEL,LOGGER_NAME,THREAD_NAME,MESSAGE,EXCEPTION_TYPE,EXCEPTION_MESSAGE,STACK_TRACE,REQUEST_METHOD,REQUEST_PATH,REQUEST_ID,HANDLER,SOURCE_CLASS,SOURCE_METHOD,EVENT_JSON FROM CAPTURED_API_LOG ORDER BY ID DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<CapturedApiLogRecord> records = new ArrayList<>();
                while (rs.next()) {
                    String json = rs.getString("EVENT_JSON");
                    records.add(new CapturedApiLogRecord(rs.getLong("ID"), rs.getTimestamp("CAPTURED_AT").toInstant(),
                            rs.getString("EVENT_TYPE"), rs.getString("LEVEL"), rs.getString("LOGGER_NAME"),
                            rs.getString("THREAD_NAME"), rs.getString("MESSAGE"), rs.getString("EXCEPTION_TYPE"),
                            rs.getString("EXCEPTION_MESSAGE"), rs.getString("STACK_TRACE"), rs.getString("REQUEST_METHOD"),
                            rs.getString("REQUEST_PATH"), rs.getString("REQUEST_ID"), rs.getString("HANDLER"),
                            rs.getString("SOURCE_CLASS"), rs.getString("SOURCE_METHOD"),
                            json == null ? null : objectMapper.readTree(json)));
                }
                return records;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read captured events from temporary H2 store.", ex);
        }
    }

    public long count() {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM CAPTURED_API_LOG")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not count captured events in temporary H2 store.", ex);
        }
    }

    public long storedBytes() {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COALESCE(SUM(RECORD_BYTES),0) FROM CAPTURED_API_LOG")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not inspect temporary H2 store size.", ex);
        }
    }

    @Override 
    public void close() {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException ignored) {
            // The database may already have shut down after an interrupted write.
        }
    }

    private void initializeSchema() {
        String ddl = "CREATE TABLE CAPTURED_API_LOG(ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,CAPTURED_AT TIMESTAMP WITH TIME ZONE NOT NULL,LOGGED_AT TIMESTAMP WITH TIME ZONE,EVENT_TYPE VARCHAR(80),LEVEL VARCHAR(16),LOGGER_NAME VARCHAR(512),THREAD_NAME VARCHAR(256),MESSAGE CLOB,EXCEPTION_TYPE VARCHAR(512),EXCEPTION_MESSAGE CLOB,STACK_TRACE CLOB,REQUEST_METHOD VARCHAR(32),REQUEST_PATH VARCHAR(2048),REQUEST_ID VARCHAR(256),HANDLER VARCHAR(1024),SOURCE_CLASS VARCHAR(512),SOURCE_METHOD VARCHAR(512),EVENT_JSON CLOB,RECORD_BYTES BIGINT NOT NULL)";
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(ddl);
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not initialize temporary H2 capture store.", ex);
        }
    }

    private String parseJsonObjectOrArray(String message) {
        if (message == null) return null;
        try {
            JsonNode node = objectMapper.readTree(message);
            return node != null && (node.isObject() || node.isArray()) ? node.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long estimatedBytes(CapturedApiLogEvent event, String json) {
        return 128L + utf8(event.eventType()) + utf8(event.level()) + utf8(event.loggerName())
                + utf8(event.threadName()) + utf8(event.message()) + utf8(event.exceptionType())
                + utf8(event.exceptionMessage()) + utf8(event.stackTrace()) + utf8(event.requestMethod())
                + utf8(event.requestPath()) + utf8(event.requestId()) + utf8(event.handler())
                + utf8(event.sourceClass()) + utf8(event.sourceMethod()) + utf8(json);
    }

    private static int utf8(String value) {
        return value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
