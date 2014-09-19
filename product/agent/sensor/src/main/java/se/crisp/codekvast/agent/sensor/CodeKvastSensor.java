package se.crisp.codekvast.agent.sensor;

import org.aspectj.bridge.Constants;
import se.crisp.codekvast.agent.sensor.aspects.AbstractMethodExecutionAspect;
import se.crisp.codekvast.agent.sensor.aspects.JasperExecutionAspect;
import se.crisp.codekvast.agent.util.AgentConfig;
import se.crisp.codekvast.agent.util.FileUtils;

import java.io.File;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This is the Java agent that hooks up CodeKvast to the app. It also loads aspectjweaver.
 * <p/>
 * Usage: Add the following option to the Java command line:
 * <pre><code>
 *    -javaagent:/path/to/codekvast-sensor-n.n-shadow.jar=path/to/codekvast-agent.properties
 * </code></pre>
 *
 * @author Olle Hallin
 */
public class CodeKvastSensor {

    public static final String NAME = "CodeKvast";

    public static PrintStream out;

    private CodeKvastSensor() {
        // Not possible to instantiate a javaagent
    }

    /**
     * This method is invoked by the JVM as part of bootstrapping the -javaagent
     */
    public static void premain(String args, Instrumentation inst) {
        AgentConfig config = AgentConfig.parseConfigFile(args);

        //noinspection UseOfSystemOutOrSystemErr
        CodeKvastSensor.out = config.isVerbose() ? System.err : new PrintStream(new NullOutputStream());

        UsageRegistry.initialize(config);

        CodeKvastSensor.out.printf("%s is loading aspectjweaver%n", NAME);
        loadAspectjWeaver(args, inst, config);

        int firstResultInSeconds = createTimerTask(config.getSensorResolutionIntervalSeconds());

        CodeKvastSensor.out.printf("%s is ready to detect used code within(%s..*).%n" +
                                           "First write to %s will be in %d seconds, thereafter every %d seconds.%n" +
                                           "-------------------------------------------------------------------------------%n",
                                   NAME, config.getPackagePrefix(), config.getUsageFile(), firstResultInSeconds,
                                   config.getSensorResolutionIntervalSeconds()
        );
    }

    private static int createTimerTask(int dumpIntervalSeconds) {
        UsageDumpingTimerTask timerTask = new UsageDumpingTimerTask();

        Timer timer = new Timer(NAME, true);

        int initialDelaySeconds = 5;
        timer.scheduleAtFixedRate(timerTask, initialDelaySeconds * 1000L, dumpIntervalSeconds * 1000L);

        Runtime.getRuntime().addShutdownHook(new Thread(timerTask, NAME + " shutdown hook"));

        return initialDelaySeconds;
    }

    private static void loadAspectjWeaver(String args, Instrumentation inst, AgentConfig config) {
        System.setProperty("org.aspectj.weaver.loadtime.configuration", join(createAopXml(config),
                                                                             Constants.AOP_USER_XML,
                                                                             Constants.AOP_AJC_XML,
                                                                             Constants.AOP_OSGI_XML));
        org.aspectj.weaver.loadtime.Agent.premain(args, inst);
    }

    private static String join(String... args) {
        StringBuilder sb = new StringBuilder();
        String delimiter = "";
        for (String arg : args) {
            sb.append(delimiter).append(arg);
            delimiter = ";";
        }
        return sb.toString();
    }

    /**
     * Creates a concrete implementation of the AbstractMethodExecutionAspect, using the packagePrefix for specifying the abstract pointcut
     * 'scope'.
     *
     * @return A file URI to a temporary aop-ajc.xml file. The file is deleted on JVM exit.
     */
    private static String createAopXml(AgentConfig config) {
        String xml = String.format(
                "<aspectj>\n"
                        + "  <aspects>\n"
                        + "    <aspect name='%1$s'/>\n"
                        + "    <concrete-aspect name='se.crisp.codekvast.agent.sensor.aspects.PublicMethodExecutionAspect'\n"
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
                config.getPackagePrefix(),
                config.getAspectjOptions() + " -XmessageHandlerClass:" + AspectjMessageHandler.class.getName(),
                JasperExecutionAspect.JASPER_BASE_PACKAGE
        );

        File file = config.getAspectFile();
        if (config.isClobberAopXml() || !file.canRead()) {
            FileUtils.writeToFile(xml, file);
        }
        return "file:" + file.getAbsolutePath();
    }

    private static class UsageDumpingTimerTask extends TimerTask {

        private int dumpCount;

        @Override
        public void run() {
            dumpCount += 1;
            UsageRegistry.instance.dumpDataToDisk(dumpCount);
        }
    }
}
