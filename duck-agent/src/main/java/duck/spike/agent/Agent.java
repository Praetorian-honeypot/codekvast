package duck.spike.agent;


import duck.spike.util.AspectjUtils;
import duck.spike.util.Configuration;
import duck.spike.util.Usage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aspectj.lang.reflect.MethodSignature;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Olle Hallin
 */
@RequiredArgsConstructor
public class Agent extends TimerTask {
    private final Configuration config;
    private final Timer timer = new Timer(getClass().getSimpleName(), false);
    private final Map<String, Usage> usages = new HashMap<>();

    private long dataFileModifiedAt;

    private void start() {
        long intervalMillis = config.getWarehouseUploadIntervalSeconds() * 1000L;
        scanCodeBase();
        timer.scheduleAtFixedRate(this, intervalMillis, intervalMillis);
        System.out.printf("Started, config=%s%n", config);
    }

    @Override
    public void run() {
        long modifiedAt = config.getDataFile().lastModified();
        if (modifiedAt != dataFileModifiedAt) {
            Usage.readUsagesFromFile(usages, config.getDataFile());

            int unused = 0;
            int used = 0;

            for (Usage usage : usages.values()) {
                if (usage.getUsedAtMillis() == 0L) {
                    unused += 1;
                }
                if (usage.getUsedAtMillis() != 0L) {
                    used += 1;
                }
            }

            System.out.printf("Posting usage data for %d unused and %d used methods in %s to %s%n", unused, used,
                              config.getAppName(), config.getWarehouseUri());
            dataFileModifiedAt = modifiedAt;
        }
    }

    @SneakyThrows(MalformedURLException.class)
    private void scanCodeBase() {

        File codeBase = new File(config.getCodeBaseUri());
        assert codeBase.exists();

        long startedAt = System.currentTimeMillis();

        URLClassLoader appClassLoader = new URLClassLoader(new URL[]{codeBase.toURI().toURL()}, System.class.getClassLoader());

        Reflections reflections = new Reflections(
                config.getPackagePrefix(),
                appClassLoader,
                new SubTypesScanner(false));

        int count = 0;
        for (Class<?> clazz : reflections.getSubTypesOf(Object.class)) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
                    MethodSignature signature = AspectjUtils.getMethodSignature(clazz, method);

                    Usage usage = new Usage(AspectjUtils.makeMethodKey(signature), 0L);
                    usages.put(usage.getSignature(), usage);
                    count += 1;
                    System.out.printf("  Found %s%n", usage.getSignature());
                }
            }
        }

        System.out.printf("Code base %s with package prefix '%s' scanned in %d ms, found %d methods.%n",
                          config.getCodeBaseUri(), config.getPackagePrefix(), System.currentTimeMillis() - startedAt, count);
    }

    public static void main(String[] args) {
        Configuration config = Configuration.parseConfigFile("/path/to/duck.properties");
        Agent agent = new Agent(config);
        agent.start();
    }

}
