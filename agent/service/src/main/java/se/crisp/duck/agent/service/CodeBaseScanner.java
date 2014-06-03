package se.crisp.duck.agent.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.runtime.reflect.Factory;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.xml.sax.helpers.DefaultHandler;
import se.crisp.duck.agent.util.Configuration;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Arrays.asList;

/**
 * @author Olle Hallin
 */
@Slf4j
public class CodeBaseScanner {

    /**
     * Uses AspectJ for creating the same signature as AbstractDuckAspect.
     *
     * @return The same signature object as an AspectJ execution pointcut will provide in JoinPoint.getSignature().
     * Returns null unless the method is public.
     */
    private String makeSignature(Class clazz, Method method) {
        return clazz != null && Modifier.isPublic(method.getModifiers()) ? new Factory(null, clazz)
                .makeMethodSig(method.getModifiers(), method.getName(), clazz, method.getParameterTypes(),
                               null, method.getExceptionTypes(), method.getReturnType()).toLongString() : null;
    }

    public static class Result {
        public final Set<String> signatures = new TreeSet<>();
        public final Map<String, String> overriddenSignatures = new HashMap<>();
    }

    @SneakyThrows(MalformedURLException.class)
    Result getPublicMethodSignatures(Configuration config) {
        File codeBase = new File(config.getCodeBaseUri());
        checkState(codeBase.exists(), "Code base at " + codeBase + " does not exist");

        long startedAt = System.currentTimeMillis();
        log.info("Scanning code base at {}", config.getCodeBaseUri());

        String packagePrefix = config.getPackagePrefix();
        URLClassLoader appClassLoader = new URLClassLoader(getUrlsForCodeBase(codeBase), System.class.getClassLoader());

        Reflections reflections = new Reflections(packagePrefix, appClassLoader, new SubTypesScanner(false));

        Result result = new Result();
        for (Class<?> rootClass : asList(Object.class, Enum.class, Thread.class, DefaultHandler.class, Exception.class)) {
            for (Class<?> clazz : reflections.getSubTypesOf(rootClass)) {
                findPublicMethods(result, packagePrefix, clazz);
            }
        }

        checkState(!result.signatures.isEmpty(),
                   "Code base at " + codeBase + " does not contain any classes with package prefix " + packagePrefix);

        log.debug("Code base at {} with package prefix '{}' scanned in {} ms, found {} public methods.",
                  config.getCodeBaseUri(), packagePrefix, System.currentTimeMillis() - startedAt, result.signatures.size());
        return result;
    }

    void findPublicMethods(Result result, String packagePrefix, Class<?> clazz) {
        log.debug("Analyzing {}", clazz);
        Method[] methods = clazz.getMethods();

        for (Method method : methods) {
            if (Modifier.isPublic(method.getModifiers())) {

                // Some AOP frameworks (e.g., Guice) push methods from a base class down to a subclass created in runtime.
                // We need to map those back to the original declaring signature, or else the original, declared method will look unused.

                String thisSignature = makeSignature(clazz, method);
                String declaringSignature = makeSignature(findDeclaringClass(method.getDeclaringClass(), method, packagePrefix), method);

                if (declaringSignature != null) {
                    if (!declaringSignature.equals(thisSignature)) {
                        log.trace("  Adding {} -> {} to overridden signatures", thisSignature, declaringSignature);
                        result.overriddenSignatures.put(thisSignature, declaringSignature);
                    }

                    if (result.signatures.add(declaringSignature)) {
                        log.trace("  Found {}", declaringSignature);
                    }
                }
            }
        }

        for (Class<?> innerClass : clazz.getDeclaredClasses()) {
            findPublicMethods(result, packagePrefix, innerClass);
        }
    }

    private Class findDeclaringClass(Class<?> clazz, Method method, String packagePrefix) {
        if (clazz == null) {
            return null;
        }
        String pkg = clazz.getPackage().getName();
        if (!pkg.startsWith(packagePrefix)) {
            return null;
        }
        try {
            clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
            return clazz;
        } catch (NoSuchMethodException ignore) {
        }
        return findDeclaringClass(clazz.getSuperclass(), method, packagePrefix);
    }

    URL[] getUrlsForCodeBase(File codeBase) throws MalformedURLException {
        checkArgument(codeBase.exists(), "Code base at " + codeBase + " does not exist");

        List<URL> result = new ArrayList<>();
        if (codeBase.isDirectory()) {
            scanExplodedDirectory(codeBase, result);
        } else if (codeBase.getName().endsWith(".war")) {
            throw new UnsupportedOperationException("Scanning WAR not yet supported");
        } else if (codeBase.getName().endsWith(".ear")) {
            throw new UnsupportedOperationException("Scanning EAR not yet supported");
        } else if (codeBase.getName().endsWith(".jar")) {
            result.add(codeBase.toURI().toURL());
        }

        return result.toArray(new URL[result.size()]);
    }

    private void scanExplodedDirectory(File directory, List<URL> result) throws MalformedURLException {
        log.debug("Scanning directory {}...", directory);

        result.add(directory.toURI().toURL());

        // Look for jars in that directory
        File[] jarFiles = directory.listFiles(new JarNameFilter());

        for (File jarFile : jarFiles) {
            if (jarFile.canRead()) {
                log.debug("  Found {}", jarFile);
                result.add(jarFile.toURI().toURL());
            } else {
                log.warn("Ignoring {} since it cannot be read", jarFile);
            }
        }
    }

    private static class JarNameFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            boolean isJar = file.isFile() && file.getName().endsWith(".jar");
            if (!isJar) {
                log.debug("  Ignoring {}, not a jar file", file);
            }
            return isJar;
        }
    }
}
