package se.crisp.codekvast.server.codekvast_server.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import se.crisp.codekvast.server.agent_api.model.v1.SignatureData;
import se.crisp.codekvast.server.agent_api.model.v1.SignatureEntry;
import se.crisp.codekvast.server.codekvast_server.dao.AgentDAO;
import se.crisp.codekvast.server.codekvast_server.dao.ReportDAO;
import se.crisp.codekvast.server.codekvast_server.dao.ReportDAO.ReportParameters;
import se.crisp.codekvast.server.codekvast_server.exception.CodekvastException;
import se.crisp.codekvast.server.codekvast_server.model.AppId;
import se.crisp.codekvast.server.codekvast_server.model.event.rest.MethodUsageScope;
import se.crisp.codekvast.server.codekvast_server.service.AgentService;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * @author olle.hallin@crisp.se
 */
@ContextConfiguration(classes = {AgentServiceImpl.class})
public class ReportDAOIntegTest extends AbstractServiceIntegTest {

    @Inject
    private AgentService agentService;

    @Inject
    private AgentDAO agentDAO;

    @Inject
    private ReportDAO reportDAO;

    private final long bootstrapMillis = 10_000L;
    private final long usageCycleMillis = 600_000L;

    private final long hour = 3600_000L;
    private final long t0 = now - 10 * hour;
    private final long t1 = now - 9 * hour;
    private final long t2 = now - 8 * hour;
    private final long t3 = now - 7 * hour;

    private final List<SignatureEntry> deadSignatures = asList(
            new SignatureEntry("dead1", 0L, 0L, null),
            new SignatureEntry("dead2", 0L, 0L, null),
            new SignatureEntry("dead3", 0L, 0L, null),
            new SignatureEntry("dead4", 0L, 0L, null));

    @Before
    public void beforeTest() throws CodekvastException {
        // given
        agentService.storeJvmData("agent", createJvmData(t0, t1, "app1", "1.0", "jvm1", "hostName1"));
        agentService.storeJvmData("agent", createJvmData(t0, t1, "app1", "1.1", "jvm2", "hostName1"));
        agentService.storeJvmData("agent", createJvmData(t0, t2, "app1", "1.1", "jvm2", "hostName1"));
        agentService.storeJvmData("agent", createJvmData(t0, t3, "app1", "1.1", "jvm2", "hostName1"));
        agentService.storeJvmData("agent", createJvmData(t2, t3, "app2", "2.0", "jvm3", "hostName1"));
        agentService.storeJvmData("agent", createJvmData(t2, t3, "app2", "2.1", "jvm4", "hostName2"));

        agentService.storeSignatureData(SignatureData.builder().jvmUuid("jvm1").signatures(deadSignatures).build());
        agentService.storeSignatureData(SignatureData.builder().jvmUuid("jvm2").signatures(deadSignatures).build());
        agentService.storeSignatureData(SignatureData.builder().jvmUuid("jvm3").signatures(deadSignatures).build());
        agentService.storeSignatureData(SignatureData.builder().jvmUuid("jvm4").signatures(deadSignatures.subList(0, 2)).build());
    }

    @Test
    public void testGetJvmIds_invalid_organisation_id() throws Exception {
        assertThat(reportDAO.getJvmIdsByAppVersions(0, asList("1.0", "2.0")), empty());
    }

    @Test
    public void testGetJvmIds_v1_0() throws Exception {
        assertThat(reportDAO.getJvmIdsByAppVersions(1, asList("1.0")), contains(1L));
    }

    @Test
    public void testGetJvmIds_v1_0_twice() throws Exception {
        assertThat(reportDAO.getJvmIdsByAppVersions(1, asList("1.0", "1.0")), contains(1L));
    }

    @Test
    public void testGetJvmIds_subset1() throws Exception {
        assertThat(reportDAO.getJvmIdsByAppVersions(1, asList("2.0", "1.0")), contains(1L, 3L));
    }

    @Test
    public void testGetJvmIds_subset2() throws Exception {
        assertThat(reportDAO.getJvmIdsByAppVersions(1, asList("2.1", "1.1")), contains(2L, 4L));
    }

    @Test
    public void testGetJvmIds_all_plus_non_existing() throws Exception {
        assertThat(reportDAO.getJvmIdsByAppVersions(1, asList("2.1", "2.0", "foobar", "1.1", "1.0")), contains(1L, 2L, 3L, 4L));
    }

    @Test
    public void testGetJvmIds_only_non_existing() throws Exception {
        assertThat(reportDAO.getJvmIdsByAppVersions(1, asList("foobar")), empty());
    }

    @Test
    public void testGetDeadMethods_app1_v1_0() {
        ReportParameters params = getReportParameters(asList("app1"), asList("1.0"));

        assertThat(reportDAO.getMethodsForScope(MethodUsageScope.DEAD, params), hasSize(4));
    }

    @Test
    public void testGetDeadMethods_app1_v1_0_twice() {
        ReportParameters params = getReportParameters(asList("app1", "app1"), asList("1.0"));

        assertThat(reportDAO.getMethodsForScope(MethodUsageScope.DEAD, params), hasSize(4));
    }

    @Test
    public void testGetDeadMethods_app2_v2_1() {
        ReportParameters params = getReportParameters(asList("app2"), asList("2.1"));

        assertThat(reportDAO.getMethodsForScope(MethodUsageScope.DEAD, params), hasSize(2));
    }

    @Test
    public void testGetDeadMethods_non_existing_app() {
        ReportParameters params = getReportParameters(asList("foobar"), asList("1.0"));

        assertThat(reportDAO.getMethodsForScope(MethodUsageScope.DEAD, params), empty());
    }

    @Test
    public void testGet_PossiblyDead_Bootstrap_Live_Methods_app1_v1_1() throws CodekvastException {

        List<SignatureEntry> signatures = asList(
                new SignatureEntry("bootstrap1", t0 + bootstrapMillis / 2, bootstrapMillis / 2, null),
                new SignatureEntry("possiblyDead1", t2 - usageCycleMillis * 2, bootstrapMillis * 2, null),
                new SignatureEntry("live1", t3 - usageCycleMillis / 2, bootstrapMillis * 2, null));

        agentService.storeSignatureData(SignatureData.builder().jvmUuid("jvm2").signatures(signatures).build());

        long appId = agentDAO.getApplicationIds(1, asList("app1")).iterator().next().getAppId();
        long jvmId = reportDAO.getJvmIdsByAppVersions(1, asList("1.1")).iterator().next();

        String sql = "SELECT * FROM signatures s, application_statistics stats, jvm_info jvm " +
                "WHERE s.organisation_id = ? " +
                "AND s.application_id = ? " +
                "AND s.jvm_id = ? " +
                "AND s.jvm_id = jvm.id " +
                "AND stats.application_id = s.application_id " +
                "AND stats.application_version = jvm.application_version " +
                "AND s.invoked_at_millis >= stats.max_started_at_millis " +
                "AND s.millis_since_jvm_start <= ? ";

        Map<String, Object> map = jdbcTemplate.queryForMap(sql, 1, appId, jvmId, bootstrapMillis);

        ReportParameters params = getReportParameters(asList("app1"), asList("1.1"));
        assertThat(reportDAO.getMethodsForScope(MethodUsageScope.POSSIBLY_DEAD, params), hasSize(1));
        assertThat(reportDAO.getMethodsForScope(MethodUsageScope.BOOTSTRAP, params), hasSize(1));
        assertThat(reportDAO.getMethodsForScope(MethodUsageScope.LIVE, params), hasSize(1));
    }

    private ReportParameters getReportParameters(List<String> applicationNames, List<String> applicationVersions) {
        int organisationId = 1;

        return ReportParameters.builder()
                               .usageCycleSeconds((int) (usageCycleMillis / 1000L))
                               .bootstrapSeconds((int) (bootstrapMillis / 1000L))
                               .organisationId(organisationId)
                               .applicationIds(agentDAO.getApplicationIds(organisationId, applicationNames).stream().map(AppId::getAppId)
                                                       .collect(Collectors.toList()))
                               .jvmIds(reportDAO.getJvmIdsByAppVersions(organisationId, applicationVersions))
                               .build();
    }

}
