/**
 * Copyright (c) 2015 Crisp AB
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
package se.crisp.codekvast.agent.daemon.codebase;

import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.springframework.stereotype.Component;
import se.crisp.codekvast.agent.lib.config.MethodFilter;
import se.crisp.codekvast.agent.lib.model.MethodSignature;
import se.crisp.codekvast.agent.lib.util.SignatureUtils;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.TreeSet;

/**
 * Analyzes a code base and detects public methods. Uses the org.reflections for retrieving method signature data.
 * <p>
 * It also contains support for mapping synthetic methods generated by runtime byte code manipulation frameworks back to the declared method
 * as it appears in the source code.
 *
 * @author olle.hallin@crisp.se
 */
@Slf4j
@Component
public class CodeBaseScanner {

    /**
     * Scans the code base for public methods in the correct packages. The result is stored in the code base.
     *
     * @param codeBase The code base to scan.
     * @return The number of scanned classes.
     */
    public int scanSignatures(CodeBase codeBase) {
        int result = 0;
        long startedAt = System.currentTimeMillis();
        log.debug("Scanning code base {}", codeBase);

        URLClassLoader appClassLoader = new URLClassLoader(codeBase.getUrls(), System.class.getClassLoader());
        Set<String> prefixes = new TreeSet<String>(codeBase.getConfig().getNormalizedPackagePrefixes());

        Set<String> recognizedTypes = getRecognizedTypes(prefixes, appClassLoader);

        for (String type : recognizedTypes) {
            try {
                Class<?> clazz = Class.forName(type, false, appClassLoader);
                result += findTrackedMethods(codeBase, prefixes, clazz);
            } catch (ClassNotFoundException e) {
                log.warn("Cannot analyze " + type + ": " + e);
            } catch (NoClassDefFoundError e) {
                log.warn("Cannot analyze " + type + ": " + e);
            }
        }

        if (codeBase.isEmpty()) {
            log.warn("{} does not contain any classes with package prefixes {}.'", codeBase,
                     codeBase.getConfig().getNormalizedPackagePrefixes());
        }

        codeBase.writeSignaturesToDisk();

        log.info("Scanned {} with package prefix {} in {} ms, found {} methods in {} classes.",
                 codeBase, codeBase.getConfig().getNormalizedPackagePrefixes(), System.currentTimeMillis() - startedAt,
                 codeBase.size(), result);

        return result;
    }

    private Set<String> getRecognizedTypes(Set<String> packagePrefixes, URLClassLoader appClassLoader) {
        // This is a weird way of using Reflections.
        // We're only interested in it's ability to enumerate everything inside a class loader.
        // The actual Reflections object is immediately discarded. Our data is collected by the filter.

        RecordingClassFileFilter recordingClassNameFilter = new RecordingClassFileFilter(packagePrefixes);

        new Reflections(appClassLoader, new SubTypesScanner(), recordingClassNameFilter);

        return recordingClassNameFilter.getMatchedClassNames();
    }

    int findTrackedMethods(CodeBase codeBase, Set<String> packagePrefixes, Class<?> clazz) {
        if (clazz.isInterface()) {
            log.debug("Ignoring interface {}", clazz);
            return 0;
        }

        log.debug("Analyzing {}", clazz);
        MethodFilter methodFilter = codeBase.getConfig().getMethodFilter();
        int result = 1;
        try {
            Method[] declaredMethods = clazz.getDeclaredMethods();
            Method[] methods = clazz.getMethods();
            Method[] allMethods = new Method[declaredMethods.length + methods.length];
            System.arraycopy(declaredMethods, 0, allMethods, 0, declaredMethods.length);
            System.arraycopy(methods, 0, allMethods, declaredMethods.length, methods.length);

            for (Method method : allMethods) {
                if (methodFilter.shouldInclude(method)) {

                    // Some AOP frameworks (e.g., Guice) push methods from a base class down to subclasses created in runtime.
                    // We need to map those back to the original declaring signature, or else the original declared method will look unused.

                    MethodSignature thisSignature = SignatureUtils.makeMethodSignature(methodFilter, clazz, method);

                    MethodSignature declaringSignature = SignatureUtils
                            .makeMethodSignature(methodFilter, findDeclaringClass(method.getDeclaringClass(), method, packagePrefixes),
                                                 method);

                    codeBase.addSignature(thisSignature, declaringSignature);
                }
            }

            for (Class<?> innerClass : clazz.getDeclaredClasses()) {
                result += findTrackedMethods(codeBase, packagePrefixes, innerClass);
            }
        } catch (NoClassDefFoundError e) {
            log.warn("Cannot analyze {}: {}", clazz, e.toString());
        }
        return result;
    }

    private Class findDeclaringClass(Class<?> clazz, Method method, Set<String> packagePrefixes) {
        if (clazz == null) {
            return null;
        }
        String pkg = clazz.getPackage().getName();

        boolean found = false;
        for (String prefix : packagePrefixes) {
            if (pkg.startsWith(prefix)) {
                found = true;
                break;
            }
        }

        if (!found) {
            return null;
        }

        try {
            clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
            return clazz;
        } catch (NoSuchMethodException ignore) {
        }
        return findDeclaringClass(clazz.getSuperclass(), method, packagePrefixes);
    }

}
