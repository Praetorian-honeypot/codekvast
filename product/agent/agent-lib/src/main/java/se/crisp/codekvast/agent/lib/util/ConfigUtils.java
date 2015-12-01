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
package se.crisp.codekvast.agent.lib.util;

import lombok.experimental.UtilityClass;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for config stuff.
 *
 * @author olle.hallin@crisp.se
 */
@UtilityClass
public final class ConfigUtils {

    public static List<String> getNormalizedPackagePrefixes(String packagePrefixes) {
        List<String> result = new ArrayList<String>();
        if (packagePrefixes != null) {
            String[] prefixes = packagePrefixes.split("[:;,]");
            for (String prefix : prefixes) {
                result.add(getNormalizedPackagePrefix(prefix.trim()));
            }
        }
        return result;
    }

    public static String getNormalizedPackagePrefix(String packagePrefix) {
        int dot = packagePrefix.length() - 1;
        while (dot >= 0 && packagePrefix.charAt(dot) == '.') {
            dot -= 1;
        }
        return packagePrefix.substring(0, dot + 1);
    }


    public static String normalizePathName(String path) {
        return path.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase(Locale.ENGLISH);
    }

    public static String getOptionalStringValue(Properties props, String key, String defaultValue) {
        return expandVariables(props, props.getProperty(key, defaultValue));
    }

    public static String expandVariables(Properties props, String value) {
        if (value == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("\\$(\\{([a-zA-Z0-9._-]+)\\}|([a-zA-Z0-9._-]+))");
        Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key1 = matcher.group(2);
            String key2 = matcher.group(3);
            String key = key1 != null ? key1 : key2;
            String replacement = System.getProperty(key);
            if (replacement == null) {
                replacement = System.getenv(key);
            }
            if (replacement == null && props != null) {
                replacement = props.getProperty(key);
            }
            if (replacement == null) {
                String prefix = key1 != null ? "\\$\\{" : "\\$";
                String suffix = key1 != null ? "\\}" : "";
                replacement = String.format("%s%s%s", prefix, key, suffix);
                //noinspection UseOfSystemOutOrSystemErr
                System.err.printf("Warning: unrecognized variable: %s%n", replacement.replace("\\", ""));
            }

            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static boolean getOptionalBooleanValue(Properties props, String key, boolean defaultValue) {
        return Boolean.valueOf(getOptionalStringValue(props, key, Boolean.toString(defaultValue)));
    }

    public static int getOptionalIntValue(Properties props, String key, int defaultValue) {
        String value = expandVariables(props, props.getProperty(key));
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(String.format("Illegal integer value for %s: %s", key, value));
            }
        }
        return defaultValue;
    }

    public static String getMandatoryStringValue(Properties props, String key) {
        String value = expandVariables(props, props.getProperty(key));
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("Missing or empty property: " + key);
        }
        return value;
    }

    public static List<File> getCommaSeparatedFileValues(String uriValues, boolean removeTrailingSlashes) {
        List<File> result = new ArrayList<File>();
        String[] parts = uriValues.split("[;,]");
        for (String value : parts) {
            value = value.trim();
            if (removeTrailingSlashes && value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            result.add(new File(value));
        }
        return result;
    }

    public static File getDataPath(Properties props) {
        // Prefer /tmp over ${java.io.tmpdir}, since the latter is redefined when running inside Tomcat
        File tmpDir = new File("/tmp");
        if (!tmpDir.isDirectory()) {
            tmpDir = new File(System.getProperty("java.io.tmpdir"));
        }
        String defaultValue = new File(tmpDir, "codekvast").getAbsolutePath();

        return new File(getOptionalStringValue(props, "dataPath", defaultValue));
    }
}
