package duck.spike;

import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Olle Hallin
 */
public class UsageRegistry {

    private static final Long BEGINNING_OF_TIME = 0L;

    private UsageRegistry() {
        // Utility class with only static methods
    }

    private static String packagePrefix;
    private static Map<String, Long> invokedTypes;
    private static Map<String, Long> invokedMethods;

    public static void registerMethodExecution(String declaringType, String methodSignature) {
        if (invokedTypes == null) {
            scanClassPathForPublicMethods();
        }
        Long now = System.currentTimeMillis();
        invokedTypes.put(declaringType, now);
        invokedMethods.put(methodSignature, now);
    }

    private static synchronized void scanClassPathForPublicMethods() {
        if (invokedTypes == null) {
            long startedAt = System.currentTimeMillis();
            invokedTypes = new ConcurrentHashMap<String, Long>();
            invokedMethods = new ConcurrentHashMap<String, Long>();

            Reflections reflections = new Reflections(ClasspathHelper.forPackage(packagePrefix), new SubTypesScanner(false));

            for (Class<?> clazz : reflections.getSubTypesOf(Object.class)) {
                invokedTypes.put(clazz.getName(), BEGINNING_OF_TIME);

                for (Method method : clazz.getDeclaredMethods()) {
                    if (Modifier.isPublic(method.getModifiers())) {
                        MethodSignature signature = new Factory(null, clazz).makeMethodSig(
                                method.getModifiers(),
                                method.getName(),
                                method.getDeclaringClass(),
                                method.getParameterTypes(),
                                null,
                                method.getExceptionTypes(),
                                method.getReturnType());
                        invokedMethods.put(signature.toLongString(), BEGINNING_OF_TIME);
                    }
                }
            }

            System.out.printf("Classpath with package prefix %s scanned in %d ms, found %d public methods.%n",
                    packagePrefix, System.currentTimeMillis() - startedAt, invokedMethods.size());
        }
    }

    public static void setPackagePrefix(String packagePrefix) {
        UsageRegistry.packagePrefix = packagePrefix;
        // Cannot scan classpath here, it breaks the aspect :(
        // scanClassPathForPublicMethods();
    }

    public static void dumpUnusedCode(boolean dumpTypes, boolean dumpMethods) {
        if (invokedTypes != null) {
            System.out.printf("%nDumping code usage:%n");
            if (dumpTypes) {
                dumpUnused("types", invokedTypes);
            }
            if (dumpMethods) {
                dumpUnused("methods", invokedMethods);
            }
        }
    }

    private static void dumpUnused(String what, Map<String, Long> invoked) {
        Set<String> unused = new TreeSet<String>();
        for (Map.Entry<String, Long> entry : invoked.entrySet()) {
            if (entry.getValue() == BEGINNING_OF_TIME) {
                unused.add(entry.getKey());
            }
        }

        if (!unused.isEmpty()) {
            System.out.printf("  Unused %s:%n", what);
            for (String name : unused) {
                System.out.printf("    %s%n", name);
            }
        }
    }

}
