package se.crisp.codekvast.web.logback;

import ch.qos.logback.core.PropertyDefinerBase;

import java.io.File;

/**
 * Finds out where the app is located and calculates an absolute path for the Logback log files. See logback.xml
 *
 * @author olle.hallin@crisp.se
 */
public class LogPathDefiner extends PropertyDefinerBase {

    @Override
    public String getPropertyValue() {
        String path = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        String result = System.getProperty("logging.path");
        boolean makeResultWritable = false;
        if (result != null) {
            // use it as it is
        } else if (path.endsWith(".jar")) {
            // Running from application start script
            result = path.substring(0, path.lastIndexOf("/")).replace("/lib", "/log");
            makeResultWritable = true;
        } else if (path.contains("/build/libs/") && path.endsWith(".jar!/")) {
            // Running from Gradle workspace with java -jar build/libs/xxx.jar
            int p = path.lastIndexOf("/build/libs");
            result = path.substring(0, p) + "/build";
        } else if (path.endsWith(".jar!/")) {
            // Running from java -jar outside the Gradle workspace
            result = "/var/log/codekvast";
        } else if (path.endsWith("/build/classes/main/")) {
            // Running from gradle run
            result = path.replace("/build/classes/main/", "/build");
        } else if (path.endsWith("/build/classes/production/web/")) {
            // Running from IDEA at $MODULE_DIR
            result = path.replace("/build/classes/production/web/", "/web/build");
        } else {
            result = ".";
        }

        File resultDir = new File(result);
        if (!resultDir.isDirectory() || !resultDir.canWrite()) {
            if (makeResultWritable) {
                resultDir.mkdirs();
            } else {
                //noinspection UseOfSystemOutOrSystemErr
                System.err.println(resultDir.getAbsoluteFile() + " is not writable, will log to working directory");
                result = ".";
            }
        }

        return result;
    }
}
