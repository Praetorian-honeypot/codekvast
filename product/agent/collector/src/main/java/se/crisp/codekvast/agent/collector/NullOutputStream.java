package se.crisp.codekvast.agent.collector;

import java.io.IOException;

/**
 * A no-op output stream to use if verbose=false
 *
 * @author olle.hallin@crisp.se
 */
class NullOutputStream extends java.io.OutputStream {

    @Override
    public void write(int b) throws IOException {
        // no-op
    }
}
