package io.codekvast.agent.lib.model;

import io.codekvast.agent.lib.model.v1.MethodSignature;
import org.junit.Test;

/**
 * @author olle.hallin@crisp.se
 */
public class MethodSignatureTest {

    @Test
    public void should_create_sample() throws Exception {
        // given

        // when
        MethodSignature signature = MethodSignature.createSampleMethodSignature();

        // then
        // should not crash on missing non-null fields
    }
}
