package com.caseware.templateupdate.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NarrationException")
class NarrationExceptionTest {

    @Test
    @DisplayName("keeps the underlying failure so an outage can be diagnosed")
    void retainsCause() {
        Exception cause = new IllegalStateException("connection reset");

        NarrationException failure = new NarrationException("model unavailable", cause);

        assertThat(failure).hasMessage("model unavailable").hasCause(cause);
    }
}
