package com.caseware.templateupdate.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PendingUpdateController.class)
@DisplayName("PendingUpdateController")
class PendingUpdateControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PendingUpdateQueryService queryService;

    @Test
    @DisplayName("returns the pending updates as the dashboard expects them")
    void returnsPendingUpdates() throws Exception {
        given(queryService.findPendingUpdates(any(), anyInt()))
                .willReturn(
                        List.of(
                                PendingUpdateView.builder()
                                        .engagementId("eng-1")
                                        .templateId("audit-ca")
                                        .currentVersion(3)
                                        .latestVersion(5)
                                        .versionsBehind(2)
                                        .previouslyDeclined(4)
                                        .summaryHeadline("2 changes across 1 area, v3 to v5")
                                        .summaryPoint("Audit procedures: changed Title")
                                        .build()));

        mockMvc
                .perform(
                        get("/api/firms/{firmId}/engagements/pending-updates", "firm-7")
                                .param("templateId", "audit-ca")
                                .param("latestVersion", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].engagementId").value("eng-1"))
                .andExpect(jsonPath("$[0].versionsBehind").value(2))
                .andExpect(jsonPath("$[0].previouslyDeclined[0]").value(4))
                .andExpect(jsonPath("$[0].summaryPoints[0]").value("Audit procedures: changed Title"));
    }

    @Test
    @DisplayName("returns an empty list when nothing is pending")
    void returnsEmptyList() throws Exception {
        given(queryService.findPendingUpdates(any(), anyInt())).willReturn(List.of());

        mockMvc
                .perform(
                        get("/api/firms/{firmId}/engagements/pending-updates", "firm-7")
                                .param("templateId", "audit-ca")
                                .param("latestVersion", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
