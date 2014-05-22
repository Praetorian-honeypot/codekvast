package duck.spike.agent;

import duck.spike.util.Configuration;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class CodeBaseScannerTest {

    public static final String SAMPLE_APP_LIB = "src/test/resources/sample-app/lib";
    public static final String SAMPLE_APP_JAR = SAMPLE_APP_LIB + "/sample-app.jar";

    private final CodeBaseScanner scanner = new CodeBaseScanner();

    @Test(expected = NullPointerException.class)
    public void testGetUrlsForNullFile() throws Exception {
        scanner.getUrlsForCodeBase(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetUrlsForMissingFile() throws Exception {
        scanner.getUrlsForCodeBase(new File("   "));
    }

    @Test
    public void testGetUrlsForDirectoryWithoutJars() throws MalformedURLException {
        URL[] urls = scanner.getUrlsForCodeBase(new File("build/classes/main"));
        assertThat(urls, notNullValue());
        assertThat(urls.length, is(1));
    }

    @Test
    public void testGetUrlsForDirectoryContainingJars() throws MalformedURLException {
        URL[] urls = scanner.getUrlsForCodeBase(new File(SAMPLE_APP_LIB));
        assertThat(urls, notNullValue());
        assertThat(urls.length, is(3));
    }

    @Test
    public void testGetUrlsForSingleJar() throws MalformedURLException {
        URL[] urls = scanner.getUrlsForCodeBase(new File(SAMPLE_APP_JAR));
        assertThat(urls, notNullValue());
        assertThat(urls.length, is(1));
    }

    @Test
    public void testScanCodeBaseForSingleJar() throws URISyntaxException {
        Configuration config = Configuration.builder()
                                            .packagePrefix("se.crisp")
                                            .codeBaseUri(new File(SAMPLE_APP_JAR).toURI())
                                            .build();
        Set<String> signatures = scanner.getPublicMethodSignatures(config);
        assertThat(signatures, notNullValue());
        assertThat(signatures.size(), is(8));
    }

    @Test
    public void testScanCodeBaseForDirectoryContainingMultipleJars() throws URISyntaxException {
        Configuration config = Configuration.builder()
                                            .packagePrefix("se.crisp")
                                            .codeBaseUri(new File(SAMPLE_APP_LIB).toURI())
                                            .build();
        Set<String> signatures = scanner.getPublicMethodSignatures(config);
        assertThat(signatures, notNullValue());
        assertThat(signatures.size(), is(8));
    }
}
