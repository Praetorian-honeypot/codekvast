/*
 * Copyright (c) 2015-2017 Crisp AB
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
package io.codekvast.javaagent.codebase;

import com.google.common.reflect.ClassPath;
import io.codekvast.javaagent.config.MethodAnalyzer;
import io.codekvast.javaagent.model.v1.MethodSignature;
import io.codekvast.javaagent.model.v1.SignatureStatus;
import io.codekvast.javaagent.util.SignatureUtils;
import lombok.extern.java.Log;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Analyzes a code base and detects methods to be tracked. Uses the org.reflections for retrieving method signature data.
 * <p>
 * It also contains support for mapping synthetic methods generated by runtime byte code manipulation frameworks back to the declared method
 * as it appears in the source code.
 *
 * @author olle.hallin@crisp.se
 */
@Log
public class CodeBaseScanner {

    /**
     * Scans the code base for public methods in the correct packages. The result is stored in the code base.
     *
     * @param codeBase The code base to scan.
     * @return The number of classes containing at least one included method.
     */
    public int scanSignatures(CodeBase codeBase) {
        long startedAt = System.currentTimeMillis();
        log.fine("Scanning " + codeBase);
        int result = 0;

        URLClassLoader appClassLoader = new URLClassLoader(codeBase.getUrls(), ClassLoader.getSystemClassLoader());
        Set<String> packages = new TreeSet<>(codeBase.getConfig().getNormalizedPackages());
        Set<String> excludePackages = new TreeSet<>(codeBase.getConfig().getNormalizedExcludePackages());

        Set<String> recognizedTypes = getRecognizedTypes(packages, appClassLoader);

        for (String type : recognizedTypes) {
            try {
                Class<?> clazz = Class.forName(type, false, appClassLoader);
                findTrackedConstructors(codeBase, clazz);
                findTrackedMethods(codeBase, packages, excludePackages, clazz);
                result += 1;
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                log.warning("Cannot analyze " + type + ": " + e);
            }
        }

        if (codeBase.isEmpty()) {
            log.warning(String.format("%s does not contain any classes within packages %s.'", codeBase,
                                      codeBase.getConfig().getNormalizedPackages()));
        }

        log.info(String.format("Scanned %s with package prefix %s in %d ms, found %d methods in %d classes.",
                               codeBase.getFingerprint(),
                               codeBase.getConfig().getNormalizedPackages(),
                               System.currentTimeMillis() - startedAt,
                               codeBase.size(),
                               result));

        return result;
    }

    private Set<String> getRecognizedTypes(Set<String> packages, URLClassLoader appClassLoader) {
        Set<String> result = new HashSet<>();
        try {
            ClassPath classPath = ClassPath.from(Thread.currentThread().getContextClassLoader());
            for (ClassPath.ClassInfo classInfo : classPath.getAllClasses()) {
                String name = classInfo.getPackageName();
                if (name.contains("sample.")) {
                    int i = 17;
                }
                for (String aPackage : packages) {
                    if (name.startsWith(aPackage)) {
                        result.add(classInfo.getName());
                    }
                }
            }
        } catch (IOException e) {
            log.severe("Cannot create ClassPath: " + e);
        }
        return result;
    }

    void findTrackedConstructors(CodeBase codeBase, Class<?> clazz) {
        if (clazz.isInterface()) {
            log.finest("Ignoring interface " + clazz);
            return;
        }

        log.finest("Analyzing " + clazz);
        MethodAnalyzer methodAnalyzer = codeBase.getConfig().getMethodAnalyzer();
        try {
            Constructor[] declaredConstructors = clazz.getDeclaredConstructors();

            for (Constructor constructor : declaredConstructors) {
                SignatureStatus status = methodAnalyzer.apply(constructor);
                MethodSignature thisSignature = SignatureUtils.makeConstructorSignature(clazz, constructor);
                codeBase.addSignature(thisSignature, thisSignature, status);
            }

            for (Class<?> innerClass : clazz.getDeclaredClasses()) {
                findTrackedConstructors(codeBase, innerClass);
            }
        } catch (NoClassDefFoundError e) {
            log.warning(String.format("Cannot analyze %s: %s", clazz, e.toString()));
        }
    }

    void findTrackedMethods(CodeBase codeBase, Set<String> packages, Set<String> excludePackages, Class<?> clazz) {
        if (clazz.isInterface()) {
            log.finest("Ignoring interface " + clazz);
            return;
        }

        log.finest("Analyzing " + clazz);
        MethodAnalyzer methodAnalyzer = codeBase.getConfig().getMethodAnalyzer();
        try {
            Method[] declaredMethods = clazz.getDeclaredMethods();
            Method[] methods = clazz.getMethods();
            Method[] allMethods = new Method[declaredMethods.length + methods.length];
            System.arraycopy(declaredMethods, 0, allMethods, 0, declaredMethods.length);
            System.arraycopy(methods, 0, allMethods, declaredMethods.length, methods.length);

            for (Method method : allMethods) {
                SignatureStatus status = methodAnalyzer.apply(method);

                // Some AOP frameworks (e.g., Guice) push methods from a base class down to subclasses created in runtime.
                // We need to map those back to the original declaring signature, or else the original declared method will look unused.

                MethodSignature thisSignature = SignatureUtils.makeMethodSignature(clazz, method);

                MethodSignature declaringSignature = SignatureUtils
                    .makeMethodSignature(findDeclaringClass(method.getDeclaringClass(), method, packages), method);

                if (shouldExcludeSignature(declaringSignature, excludePackages)) {
                    status = SignatureStatus.EXCLUDED_BY_PACKAGE_NAME;
                }
                codeBase.addSignature(thisSignature, declaringSignature, status);
            }

            for (Class<?> innerClass : clazz.getDeclaredClasses()) {
                findTrackedMethods(codeBase, packages, excludePackages, innerClass);
            }
        } catch (NoClassDefFoundError e) {
            log.warning(String.format("Cannot analyze %s: %s", clazz, e.toString()));
        }
    }

    private boolean shouldExcludeSignature(MethodSignature signature, Set<String> excludePackages) {
        if (signature != null) {
            String pkg = signature.getPackageName();
            for (String excludePackage : excludePackages) {
                if (pkg.startsWith(excludePackage)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Class findDeclaringClass(Class<?> clazz, Method method, Set<String> packages) {
        if (clazz == null) {
            return null;
        }
        String pkg = clazz.getPackage().getName();

        boolean found = false;
        for (String prefix : packages) {
            if (pkg.startsWith(prefix)) {
                found = true;
                break;
            }
        }

        if (!found) {
            return null;
        }

        try {
            //noinspection ConfusingArgumentToVarargsMethod
            clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
            return clazz;
        } catch (NoSuchMethodException ignore) {
        }
        return findDeclaringClass(clazz.getSuperclass(), method, packages);
    }

}
