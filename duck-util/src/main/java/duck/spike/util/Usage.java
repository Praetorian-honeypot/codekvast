package duck.spike.util;

import lombok.SneakyThrows;
import lombok.Value;

import java.io.*;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* @author Olle Hallin
*/
@Value
public class Usage {
    private final static Pattern PATTERN = Pattern.compile("^\\s*([\\d]+):(.*)");

    private final String signature;
    private final long usedAtMillis;

    public Usage(String signature, long usedAtMillis) {
        this.signature = signature;
        this.usedAtMillis = usedAtMillis;
    }

    @Override
    public String toString() {
        return String.format("%14d:%s", usedAtMillis, signature);
    }

    @SneakyThrows(IOException.class)
    public static void initializeUsagesFrom(Map<String, Usage> usages, File file) {
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        String line;
        while ((line = in.readLine()) != null) {
            Usage usage = parse(line);
            if (usage != null) {
                usages.put(usage.getSignature(), usage);
            }
        }
    }

    private static Usage parse(String line) {
        Matcher m = PATTERN.matcher(line);
        return m.matches() ? new Usage(m.group(2), Long.parseLong(m.group(1))) : null;
    }
}
