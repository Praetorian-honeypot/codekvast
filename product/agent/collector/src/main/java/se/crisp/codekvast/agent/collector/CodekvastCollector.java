package se.crisp.codekvast.agent.collector;

import org.aspectj.bridge.Constants;
import se.crisp.codekvast.agent.collector.aspects.AbstractMethodExecutionAspect;
import se.crisp.codekvast.agent.collector.aspects.JasperExecutionAspect;
import se.crisp.codekvast.agent.config.CollectorConfig;
import se.crisp.codekvast.agent.util.FileUtils;

import java.io.File;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This is the Java agent that hooks up Codekvast to the app.
 *
 * It does <strong>NOT</strong> load aspectjweaver.
 *
 * Invocation: Add the following options to the Java command line:
 * <pre><code>
 *    -javaagent:/path/to/codekvast-collector-n.n.jar=path/to/codekvast.conf -javaagent:/path/to/aspectjweaver-n.n.jar
 * </code></pre>
 *
 * <em>NOTE: the ordering of the collector and the aspectjweaver is important!</em>
 *
 * @author Olle Hallin
 */
public class CodekvastCollector {

    public static final String NAME = "Codekvast";

    // AspectJ uses this system property for defining the list of names of AOP config files to locate...
    private static final String ASPECTJ_WEAVER_CONFIGURATION = "org.aspectj.weaver.loadtime.configuration";

    public static PrintStream out;

    private CodekvastCollector() {
        // Not possible to instantiate a javaagent
    }

    /**
     * This method is invoked by the JVM as part of bootstrapping the -javaagent
     * @param args The string after the equals sign in -javaagent:codekvast-collector.jar=args. Is used as URL to the collector
     *             configuration file.
     * @param inst The standard instrumentation hook.
     *             @throws URISyntaxException if args is not a valid URL
     */
    public static void premain(String args, Instrumentation inst) throws URISyntaxException {
        CollectorConfig config = CollectorConfig.parseCollectorConfig(args);

        //noinspection UseOfSystemOutOrSystemErr
        CodekvastCollector.out = config.isVerbose() ? System.err : new PrintStream(new NullOutputStream());

        InvocationRegistry.initialize(config);

        defineAspectjWeaverConfig(config);

        int firstResultInSeconds = createTimerTask(config.getCollectorResolutionSeconds());

        CodekvastCollector.out.printf("%s is ready to detect used code within(%s..*).%n" +
                                              "First write to %s will be in %d seconds, thereafter every %d seconds.%n" +
                                              "-------------------------------------------------------------------------------%n",
                                      NAME, config.getSharedConfig().getNormalizedPackagePrefix(), config.getInvocationsFile(),
                                      firstResultInSeconds, config.getCollectorResolutionSeconds()
        );
    }

    private static int createTimerTask(int dumpIntervalSeconds) {
        InvocationDumpingTimerTask timerTask = new InvocationDumpingTimerTask();

        Timer timer = new Timer(NAME, true);

        int initialDelaySeconds = 5;
        timer.scheduleAtFixedRate(timerTask, initialDelaySeconds * 1000L, dumpIntervalSeconds * 1000L);

        Runtime.getRuntime().addShutdownHook(new Thread(timerTask, NAME + " shutdown hook"));

        return initialDelaySeconds;
    }

    private static void defineAspectjWeaverConfig(CollectorConfig config) {
        System.setProperty(ASPECTJ_WEAVER_CONFIGURATION, createAopXml(config) + ";" +
                Constants.AOP_USER_XML + ";" +
                Constants.AOP_AJC_XML + ";" +
                Constants.AOP_OSGI_XML);

        CodekvastCollector.out.printf("%s=%s%n", ASPECTJ_WEAVER_CONFIGURATION, System.getProperty(ASPECTJ_WEAVER_CONFIGURATION));
    }

    /**
     * Creates a concrete implementation of the AbstractMethodExecutionAspect, using the packagePrefix for specifying the abstract pointcut
     * 'scope'.
     *
     * @return A file URI to a temporary aop-ajc.xml file.
     */
    private static String createAopXml(CollectorConfig config) {
        String xml = String.format(
                "<aspectj>\n"
                        + "  <aspects>\n"
                        + "    <aspect name='%1$s'/>\n"
                        + "    <concrete-aspect name='se.crisp.codekvast.agent.collector.aspects.PublicMethodExecutionAspect'\n"
                        + "                     extends='%2$s'>\n"
                        + "      <pointcut name='scope' expression='within(%3$s..*)'/>\n"
                        + "    </concrete-aspect>\n"
                        + "  </aspects>\n"
                        + "  <weaver options='%4$s'>\n"
                        + "    <include within='%3$s..*' />\n"
                        + "    <include within='%5$s..*' />\n"
                        + "  </weaver>\n"
                        + "</aspectj>\n",
                JasperExecutionAspect.class.getName(),
                AbstractMethodExecutionAspect.class.getName(),
                config.getSharedConfig().getNormalizedPackagePrefix(),
                config.getAspectjOptions(),
                JasperExecutionAspect.JASPER_BASE_PACKAGE
        );

        File file = config.getAspectFile();
        if (config.isClobberAopXml() || !file.canRead()) {
            FileUtils.writeToFile(xml, file);
        }
        return "file:" + file.getAbsolutePath();
    }

    private static class InvocationDumpingTimerTask extends TimerTask {

        private int dumpCount;

        @Override
        public void run() {
            dumpCount += 1;
            InvocationRegistry.instance.dumpDataToDisk(dumpCount);
        }
    }
}
