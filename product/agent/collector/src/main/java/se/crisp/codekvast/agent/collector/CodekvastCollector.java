package se.crisp.codekvast.agent.collector;

import org.aspectj.bridge.Constants;
import se.crisp.codekvast.agent.collector.aspects.AbstractMethodExecutionAspect;
import se.crisp.codekvast.agent.config.CollectorConfig;
import se.crisp.codekvast.agent.config.CollectorConfigLocator;
import se.crisp.codekvast.agent.config.MethodFilter;
import se.crisp.codekvast.agent.util.FileUtils;

import java.io.File;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This is the Java agent that hooks up Codekvast to the app.
 *
 * It does <strong>NOT</strong> load aspectjweaver.
 *
 * Invocation: Add the following options to the Java command line:
 * <pre><code>
 *    -javaagent:/path/to/codekvast-collector-n.n.jar -javaagent:/path/to/aspectjweaver-n.n.jar
 * </code></pre>
 *
 * <em>NOTE: the ordering of the collector and the aspectjweaver is important!</em>
 *
 * @author olle.hallin@crisp.se
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
     * @param args The string after the equals sign in -javaagent:codekvast-collector.jar=args. Is used as overrides to the collector
     *             configuration file.
     * @param inst The standard instrumentation hook.
     */
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public static void premain(String args, Instrumentation inst) {
        CollectorConfig config = CollectorConfig.parseCollectorConfig(CollectorConfigLocator.locateConfig(System.out), args, true);
        if (config == null) {
            return;
        }

        CodekvastCollector.out = config.isVerbose() ? System.err : new PrintStream(new NullOutputStream());

        InvocationRegistry.initialize(config);

        defineAspectjWeaverConfig(config);

        int firstResultInSeconds = createTimerTask(config.getCollectorResolutionSeconds());

        CodekvastCollector.out.printf("%s is ready to detect used code within(%s..*).%n" +
                                              "First write to %s will be in %d seconds, thereafter every %d seconds.%n" +
                                              "-------------------------------------------------------------------------------%n",
                                      NAME, config.getNormalizedPackagePrefixes(), config.getInvocationsFile(),
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
     * Creates a concrete implementation of the AbstractMethodExecutionAspect, using the packagePrefixes for specifying the abstract
     * pointcut 'scope'.
     *
     * @return A file URI to a temporary aop-ajc.xml file.
     */
    private static String createAopXml(CollectorConfig config) {
        String aspectjOptions = config.getAspectjOptions();
        if (aspectjOptions == null) {
            aspectjOptions = "";
        }

        StringBuilder includeWithin = new StringBuilder();
        for (String prefix : config.getNormalizedPackagePrefixes()) {
            includeWithin.append(String.format("    <include within='%s..*' />\n", prefix));
        }

        String xml = String.format(
                "<aspectj>\n"
                        + "  <aspects>\n"
                        + "    <concrete-aspect name='se.crisp.codekvast.agent.collector.aspects.MethodExecutionAspect'\n"
                        + "                     extends='%1$s'>\n"
                        + "      <pointcut name='methodExecution' expression='%2$s'/>\n"
                        + "    </concrete-aspect>\n"
                        + "  </aspects>\n"
                        + "  <weaver options='%3$s'>\n"
                        + "%4$s"
                        + "    <exclude within='%5$s..*'/>\n"
                        + "  </weaver>\n"
                        + "</aspectj>\n",
                AbstractMethodExecutionAspect.class.getName(),
                toMethodExecutionPointcut(config.getMethodVisibility()),
                aspectjOptions,
                includeWithin.toString(),
                CodekvastCollector.class.getPackage().getName()
        );

        File file = config.getAspectFile();
        if (config.isClobberAopXml() || !file.canRead()) {
            FileUtils.writeToFile(xml, file);
        }
        return "file:" + file.getAbsolutePath();
    }

    private static String toMethodExecutionPointcut(MethodFilter filter) {
        if (filter.selectsPrivateMethods()) {
            return "execution(* *..*(..))";
        }
        if (filter.selectsPackagePrivateMethods()) {
            return "execution(!private * *..*(..))";
        }
        if (filter.selectsProtectedMethods()) {
            return "execution(public * *..*(..)) || execution(protected * *..*(..))";
        }
        return "execution(public * *..*(..))";
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
