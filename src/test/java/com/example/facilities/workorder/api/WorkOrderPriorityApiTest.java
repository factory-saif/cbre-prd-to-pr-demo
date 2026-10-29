package com.example.facilities.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.facilities.workorder.MutableClock;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "facilities.demo-data=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkOrderPriorityApiTest {

    /** A Monday. A Medium order created here is due Thursday 09:00. */
    private static final Instant START = Instant.parse("2026-03-02T09:00:00Z");

    private static final String REASON = "water near the server racks";

    @TestConfiguration
    static class AdvancingClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MutableClock clock;

    @Test
    void raisingMediumToUrgentReturnsTheNewDueTime() throws Exception {
        String id = create("MEDIUM");
        mvc.perform(get("/api/work-orders/{id}", id)).andExpect(jsonPath("$.dueAt").value("2026-03-05T09:00:00Z"));
        clock.advance(Duration.ofHours(2));

        mvc.perform(changePriority(id, "URGENT", REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.dueAt").value("2026-03-02T13:00:00Z"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.atRisk").value(false));
    }

    @Test
    void changedOrderAppearsInAtRiskFilterAndSummary() throws Exception {
        String id = create("MEDIUM");
        String untouched = create("MEDIUM");
        clock.advance(Duration.ofHours(2));
        mvc.perform(changePriority(id, "URGENT", REASON)).andExpect(status().isOk());

        mvc.perform(get("/api/work-orders/summary"))
                .andExpect(jsonPath("$.urgentActive").value(1))
                .andExpect(jsonPath("$.atRisk").value(0));
        mvc.perform(get("/api/work-orders").param("priority", "URGENT"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/work-orders").param("priority", "MEDIUM"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(untouched));

        clock.advance(Duration.ofHours(1));
        mvc.perform(get("/api/work-orders/{id}", id))
                .andExpect(jsonPath("$.atRisk").value(true))
                .andExpect(jsonPath("$.overdue").value(false));
        mvc.perform(get("/api/work-orders").param("atRisk", "true"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/work-orders/summary"))
                .andExpect(jsonPath("$.atRisk").value(1))
                .andExpect(jsonPath("$.urgentActive").value(1));
    }

    @Test
    void raisingAfterTheNewTargetIsOverdueAtOnce() throws Exception {
        String id = create("MEDIUM");
        clock.advance(Duration.ofHours(5));

        mvc.perform(changePriority(id, "URGENT", REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueAt").value("2026-03-02T13:00:00Z"))
                .andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.atRisk").value(false));
        mvc.perform(get("/api/work-orders/summary")).andExpect(jsonPath("$.overdue").value(1));
    }

    @Test
    void loweringPriorityRecalculatesOverdue() throws Exception {
        String id = create("URGENT");
        clock.advance(Duration.ofHours(5));
        mvc.perform(get("/api/work-orders/{id}", id)).andExpect(jsonPath("$.overdue").value(true));

        mvc.perform(changePriority(id, "LOW", "cosmetic only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.dueAt").value("2026-03-09T09:00:00Z"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.atRisk").value(false));
        mvc.perform(get("/api/work-orders/summary"))
                .andExpect(jsonPath("$.overdue").value(0))
                .andExpect(jsonPath("$.urgentActive").value(0));
    }

    @Test
    void priorityChangeAfterReopenUsesTheReopenTime() throws Exception {
        String id = createAndStart("HIGH");
        clock.advance(Duration.ofHours(1));
        mvc.perform(statusChange(id, "COMPLETED")).andExpect(status().isOk());
        clock.advance(Duration.ofHours(2));
        mvc.perform(reopen(id, "Leak is dripping again"))
                .andExpect(jsonPath("$.reopenedAt").value("2026-03-02T12:00:00Z"));
        clock.advance(Duration.ofHours(1));

        mvc.perform(changePriority(id, "URGENT", REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueAt").value("2026-03-02T16:00:00Z"))
                .andExpect(jsonPath("$.reopenedAt").value("2026-03-02T12:00:00Z"))
                .andExpect(jsonPath("$.reopenCount").value(1))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void priorityChangeAddsActivityEntry() throws Exception {
        String id = create("MEDIUM");
        mvc.perform(assign(id)).andExpect(status().isOk());
        List<Map<String, Object>> before = activity(id);
        clock.advance(Duration.ofHours(2));

        mvc.perform(changePriority(id, "URGENT", REASON)).andExpect(status().isOk());

        mvc.perform(get("/api/work-orders/{id}", id))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignee").value("Priya Shah"))
                .andExpect(jsonPath("$.updatedAt").value("2026-03-02T11:00:00Z"));
        List<Map<String, Object>> after = activity(id);
        assertThat(after).hasSize(before.size() + 1);
        assertThat(after.subList(0, before.size())).isEqualTo(before);
        Map<String, Object> last = after.get(after.size() - 1);
        assertThat(last.get("at")).isEqualTo("2026-03-02T11:00:00Z");
        assertThat(last.get("message")).isEqualTo("Priority changed from Medium to Urgent: " + REASON);
    }

    @Test
    void priorityChangeRequiresAValidReason() throws Exception {
        String id = create("MEDIUM");
        int before = activity(id).size();

        for (String body : List.of(
                "{\"priority\":\"URGENT\"}",
                "{\"priority\":\"URGENT\",\"reason\":\"   \"}",
                "{\"priority\":\"URGENT\",\"reason\":\"" + "x".repeat(501) + "\"}")) {
            mvc.perform(post("/api/work-orders/{id}/priority", id).contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.reason").exists());
        }

        mvc.perform(get("/api/work-orders/{id}", id))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.dueAt").value("2026-03-05T09:00:00Z"));
        assertThat(activity(id)).hasSize(before);
    }

    @Test
    void priorityChangeRequiresAKnownPriority() throws Exception {
        String id = create("MEDIUM");

        mvc.perform(post("/api/work-orders/{id}/priority", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.priority").exists());
        mvc.perform(changePriority(id, "CRITICAL", REASON))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/work-orders/{id}", id)).andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    void priorityChangeOfAnUnknownIdReturns404() throws Exception {
        mvc.perform(changePriority("WO-9999", "URGENT", REASON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Work order not found"));
    }

    @Test
    void priorityChangeIsRejectedForClosedOrdersAndTheSamePriority() throws Exception {
        String completed = createAndStart("MEDIUM");
        mvc.perform(statusChange(completed, "COMPLETED")).andExpect(status().isOk());
        String cancelled = create("MEDIUM");
        mvc.perform(statusChange(cancelled, "CANCELLED")).andExpect(status().isOk());
        String active = create("MEDIUM");
        clock.advance(Duration.ofHours(1));

        for (String id : List.of(completed, cancelled, active)) {
            String next = id.equals(active) ? "MEDIUM" : "URGENT";
            int before = activity(id).size();

            mvc.perform(changePriority(id, next, REASON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Invalid status change"));

            mvc.perform(get("/api/work-orders/{id}", id))
                    .andExpect(jsonPath("$.priority").value("MEDIUM"))
                    .andExpect(jsonPath("$.dueAt").value("2026-03-05T09:00:00Z"));
            assertThat(activity(id)).hasSize(before);
        }
    }

    private String createAndStart(String priority) throws Exception {
        String id = create(priority);
        mvc.perform(assign(id)).andExpect(status().isOk());
        mvc.perform(statusChange(id, "IN_PROGRESS")).andExpect(status().isOk());
        return id;
    }

    private String create(String priority) throws Exception {
        String response = mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(priority)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private List<Map<String, Object>> activity(String id) throws Exception {
        ResultActions result = mvc.perform(get("/api/work-orders/{id}", id)).andExpect(status().isOk());
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.activity");
    }

    private static RequestBuilder changePriority(String id, String priority, String reason) {
        return post("/api/work-orders/{id}/priority", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\":\"" + priority + "\",\"reason\":\"" + reason + "\"}");
    }

    private static RequestBuilder assign(String id) {
        return post("/api/work-orders/{id}/assign", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee\":\"Priya Shah\"}");
    }

    private static RequestBuilder statusChange(String id, String status) {
        return post("/api/work-orders/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}");
    }

    private static RequestBuilder reopen(String id, String reason) {
        return post("/api/work-orders/{id}/reopen", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}");
    }

    private static String body(String priority) {
        return """
                {"title":"Leak near server racks","location":"Tower A · Floor 3",
                 "category":"PLUMBING","priority":"%s","requester":"Casey Nguyen"}
                """.formatted(priority);
    }
}
