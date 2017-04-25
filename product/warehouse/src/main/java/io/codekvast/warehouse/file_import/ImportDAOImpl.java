/*
 * Copyright (c) 2015-2017 Crisp AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.codekvast.warehouse.file_import;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import io.codekvast.agent.lib.model.v1.legacy.ExportFileMetaInfo;
import io.codekvast.agent.lib.model.v1.legacy.JvmData;

import javax.inject.Inject;
import java.sql.*;
import java.util.List;

/**
 * @author Olle Hallin (qolha), olle.hallin@crisp.se
 */
@Repository
@Slf4j
public class ImportDAOImpl implements ImportDAO {

    @Value
    private static class InsertResult {
        long id;
        boolean newRow;
    }

    private final JdbcTemplate jdbcTemplate;

    @Inject
    public ImportDAOImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isFileImported(ExportFileMetaInfo metaInfo) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM import_file_info WHERE uuid = ? ", Integer.class, metaInfo.getUuid()) > 0;
    }

    @Override
    public void recordFileAsImported(ExportFileMetaInfo metaInfo, ImportStatistics importStatistics) {
        if (isFileImported(metaInfo)) {
            log.warn("Rejecting import of {}", metaInfo);
        } else {
            jdbcTemplate
                    .update("INSERT INTO import_file_info(uuid, fileSchemaVersion, fileName, fileLengthBytes, importedAt, " +
                                    "importTimeMillis, " +

                                    "daemonHostname, daemonVersion, daemonVcsId, environment) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            metaInfo.getUuid(), metaInfo.getSchemaVersion(), importStatistics.getImportFile().getPath(),
                            importStatistics.getImportFile().length(), new Timestamp(System.currentTimeMillis()),
                            importStatistics.getProcessingTime().toMillis(), metaInfo.getDaemonHostname(), metaInfo.getDaemonVersion(),
                            metaInfo.getDaemonVcsId(), metaInfo.getEnvironment());
            log.info("Imported {} {}", metaInfo, importStatistics);
        }
    }

    @Override
    public boolean saveApplication(Application application, ImportContext context) {
        InsertResult insertResult = getCentralApplicationId(application);
        context.putApplication(insertResult.getId(), application);
        return insertResult.isNewRow();
    }

    @Override
    public boolean saveMethod(Method method, ImportContext context) {
        InsertResult insertResult = getCentralMethodId(method);
        context.putMethod(insertResult.getId(), method);
        return insertResult.isNewRow();
    }

    @Override
    public boolean saveJvm(Jvm jvm, JvmData jvmData, ImportContext context) {
        InsertResult insertResult = getCentralJvmId(jvm, jvmData);
        context.putJvm(insertResult.getId(), jvm);
        return insertResult.isNewRow();
    }

    @Override
    public boolean saveInvocation(Invocation invocation, ImportContext context) {
        long applicationId = context.getApplicationId(invocation.getLocalApplicationId());
        long methodId = context.getMethodId(invocation.getLocalMethodId());
        long jvmId = context.getJvmId(invocation.getLocalJvmId());
        Timestamp invokedAt = new Timestamp(invocation.getInvokedAtMillis());

        int rows =
                jdbcTemplate.update("INSERT INTO invocations(applicationId, methodId, jvmId, invokedAtMillis, invocationCount, status) " +
                                            "VALUES(?, ?, ?, ?, ?, ?) " +
                                            "ON DUPLICATE KEY UPDATE " +
                                            "invokedAtMillis = GREATEST(invokedAtMillis, VALUES(invokedAtMillis)), " +
                                            "invocationCount = invocationCount + VALUES(invocationCount)," +
                                            "status = VALUES(status)",
                                    applicationId, methodId, jvmId, invokedAt.getTime(),
                                    invocation.getInvocationCount(), invocation.getStatus().name());
        log.trace("{} invocation {}:{}:{} {}", rows == 1 ? "Inserted" : "Updated", applicationId, methodId, jvmId, invokedAt);
        return true;
    }

    private Long doInsertRow(PreparedStatementCreator psc) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(psc, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private InsertResult getCentralApplicationId(Application app) {
        Long appId = queryForLong("SELECT id FROM applications WHERE name = ? AND version = ? ",
                                  app.getName(), app.getVersion());
        if (appId == null) {
            appId = doInsertRow(new InsertApplicationStatement(app));
            log.debug("Stored application {}:{}", appId, app);
            return new InsertResult(appId, true);
        }
        return new InsertResult(appId, false);
    }

    private InsertResult getCentralMethodId(Method method) {
        Long methodId = queryForLong("SELECT id FROM methods WHERE signature = ? ", method.getSignature());

        if (methodId == null) {
            methodId = doInsertRow(new InsertMethodStatement(method));
            log.trace("Stored method {}:{}", methodId, method.getSignature());
            return new InsertResult(methodId, true);
        }
        return new InsertResult(methodId, false);
    }

    private InsertResult getCentralJvmId(Jvm jvm, JvmData jvmData) {
        Long jvmId = queryForLong("SELECT id FROM jvms WHERE uuid = ? ", jvm.getUuid());

        if (jvmId == null) {
            jvmId = doInsertRow(new InsertJvmStatement(jvm, jvmData));
            log.trace("Stored JVM {}:{}", jvmId, jvm);
            return new InsertResult(jvmId, true);
        }

        jdbcTemplate.update("UPDATE jvms SET dumpedAt=? WHERE uuid=?", new Timestamp(jvm.getDumpedAtMillis()), jvm.getUuid());
        log.trace("Updated JVM {}:{}", jvmId, jvm);
        return new InsertResult(jvmId, false);
    }

    @RequiredArgsConstructor
    private static class InsertApplicationStatement implements PreparedStatementCreator {
        private final Application app;

        @SuppressWarnings("ValueOfIncrementOrDecrementUsed")
        @Override
        public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
            PreparedStatement ps = con.prepareStatement("INSERT INTO applications(name, version, createdAt) " +
                                                                "VALUES(?, ?, ?)",
                                                        Statement.RETURN_GENERATED_KEYS);
            int column = 0;
            ps.setString(++column, app.getName());
            ps.setString(++column, app.getVersion());
            ps.setTimestamp(++column, new Timestamp(app.getCreatedAtMillis()));
            return ps;
        }
    }

    @RequiredArgsConstructor
    private static class InsertMethodStatement implements PreparedStatementCreator {
        private final Method method;

        @SuppressWarnings({"ValueOfIncrementOrDecrementUsed", "Duplicates"})
        @Override
        public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
            PreparedStatement ps = con.prepareStatement("INSERT INTO methods(visibility, signature, createdAt, declaringType, " +
                                                                "exceptionTypes, methodName, modifiers, packageName, parameterTypes, " +
                                                                "returnType) " +
                                                                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                                        Statement.RETURN_GENERATED_KEYS);
            int column = 0;
            ps.setString(++column, method.getVisibility());
            ps.setString(++column, method.getSignature());
            ps.setTimestamp(++column, new Timestamp(method.getCreatedAtMillis()));
            ps.setString(++column, method.getDeclaringType());
            ps.setString(++column, method.getExceptionTypes());
            ps.setString(++column, method.getMethodName());
            ps.setString(++column, method.getModifiers());
            ps.setString(++column, method.getPackageName());
            ps.setString(++column, method.getParameterTypes());
            ps.setString(++column, method.getReturnType());
            return ps;
        }
    }

    @RequiredArgsConstructor
    public static class InsertJvmStatement implements PreparedStatementCreator {
        private final Jvm jvm;
        private final JvmData jvmData;

        @SuppressWarnings("ValueOfIncrementOrDecrementUsed")
        @Override
        public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
            PreparedStatement ps = con.prepareStatement("INSERT INTO jvms(uuid, " +
                                                                "startedAt, " +
                                                                "dumpedAt, " +
                                                                "collectorResolutionSeconds, " +
                                                                "methodVisibility, " +
                                                                "packages, " +
                                                                "excludePackages, " +
                                                                "environment, " +
                                                                "collectorComputerId, " +
                                                                "collectorHostname, " +
                                                                "collectorVersion, " +
                                                                "collectorVcsId, " +
                                                                "tags) " +
                                                                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                                        Statement.RETURN_GENERATED_KEYS);
            int column = 0;
            ps.setString(++column, jvm.getUuid());
            ps.setTimestamp(++column, new Timestamp(jvm.getStartedAtMillis()));
            ps.setTimestamp(++column, new Timestamp(jvm.getDumpedAtMillis()));
            ps.setInt(++column, jvmData.getCollectorResolutionSeconds());
            ps.setString(++column, jvmData.getMethodVisibility());
            ps.setString(++column, jvmData.getPackages());
            ps.setString(++column, jvmData.getExcludePackages());
            ps.setString(++column, jvmData.getEnvironment());
            ps.setString(++column, jvmData.getCollectorComputerId());
            ps.setString(++column, jvmData.getCollectorHostName());
            ps.setString(++column, jvmData.getCollectorVersion());
            ps.setString(++column, jvmData.getCollectorVcsId());
            ps.setString(++column, jvmData.getTags());
            return ps;
        }
    }

    private Long queryForLong(String sql, Object... args) {
        List<Long> list = jdbcTemplate.queryForList(sql, Long.class, args);
        return list.isEmpty() ? null : list.get(0);
    }

    private Timestamp queryForTimestamp(String sql, Object... args) {
        List<Long> list = jdbcTemplate.queryForList(sql, Long.class, args);
        return list.isEmpty() ? null : new Timestamp(list.get(0));
    }
}
