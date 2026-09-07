package com.caseware.templateupdate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PendingUpdateController.class)
@Import(ApiExceptionHandler.class)
@DisplayName("ApiExceptionHandler")
class ApiExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PendingUpdateQueryService queryService;

    @Test
    @DisplayName("reports a rejected parameter as a client error, not a server fault")
    void rejectedParameterIsAClientError() throws Exception {
        mockMvc
                .perform(
                        get("/api/firms/{firmId}/engagements/pending-updates", "firm-7")
                                .param("templateId", " ")
                                .param("latestVersion", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameter"))
                .andExpect(jsonPath("$.detail").value("templateId must not be blank"));
    }

    @Test
    @DisplayName("passes the domain's own wording through to the caller")
    void carriesTheDomainMessage() {
        ProblemDetail problem =
                new ApiExceptionHandler()
                        .onInvalidArgument(
                                new IllegalArgumentException("template version must be positive, was 0"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("template version must be positive, was 0");
    }
}
