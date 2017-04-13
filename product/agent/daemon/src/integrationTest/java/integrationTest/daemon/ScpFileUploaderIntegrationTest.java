package integrationTest.daemon;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import se.crisp.codekvast.agent.daemon.beans.DaemonConfig;
import se.crisp.codekvast.agent.daemon.worker.FileUploadException;
import se.crisp.codekvast.agent.daemon.worker.impl.ScpFileUploaderImpl;
import se.crisp.codekvast.testsupport.docker.DockerContainer;

import java.io.File;
import java.net.URISyntaxException;

import static org.junit.Assume.assumeTrue;

/**
 * @author olle.hallin@crisp.se
 */
public class ScpFileUploaderIntegrationTest {

    @ClassRule
    public static DockerContainer sshd = DockerContainer.builder()
                                                        .imageName("rastasheep/ubuntu-sshd:14.04")
                                                        .port("22")
                                                        .build();

    private ScpFileUploaderImpl scpUploader;

    @Before
    public void beforeTest() throws Exception {
        assumeTrue(sshd.isRunning());

        DaemonConfig config = DaemonConfig.createSampleDaemonConfig().toBuilder()
                                          .uploadToHost("localhost:" + sshd.getExternalPort(22))
                                          .uploadToUsername("root")
                                          .uploadToPassword("root")
                                          .uploadToPath("/tmp/codekvast")
                                          .verifyUploadToHostKey(false)
                                          .build();

        scpUploader = new ScpFileUploaderImpl(config);
    }

    @Test
    public void should_validate_ssh() throws FileUploadException {
        assumeTrue(sshd.isRunning());

        scpUploader.validateUploadConfig();
    }

    @Test
    public void should_upload_file() throws FileUploadException, URISyntaxException {
        assumeTrue(sshd.isRunning());

        scpUploader.uploadFile(new File(getClass().getClassLoader().getResource("scpUploadTest.txt").toURI()));
    }
}
