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
class WorkOrderReopenApiTest {

    /** A Monday. Orders created here and completed an hour later are completed Monday 10:00. */
    private static final Instant START = Instant.parse("2026-03-02T09:00:00Z");

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
    void reopeningACompletedOrderReturnsItToInProgressWithTheSameTechnician() throws Exception {
        String id = completedHighOrder();
        clock.advance(Duration.ofHours(23));

        mvc.perform(reopen(id, "Leak is dripping again"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignee").value("Priya Shah"))
                .andExpect(jsonPath("$.reopened").value(true))
                .andExpect(jsonPath("$.reopenCount").value(1))
                .andExpect(jsonPath("$.reopenedAt").value("2026-03-03T09:00:00Z"));
    }

    @Test
    void reopenRestartsTheResponseTargetFromTheReopenTime() throws Exception {
        String id = completedHighOrder();
        clock.advance(Duration.ofHours(23));

        mvc.perform(reopen(id, "Light still flickers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueAt").value("2026-03-04T09:00:00Z"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.atRisk").value(false));
    }

    @Test
    void reopenIsAllowedAtExactlySevenDaysAndRejectedOneSecondLater() throws Exception {
        String onBoundary = createAndStart("HIGH");
        String pastBoundary = createAndStart("HIGH");
        clock.advance(Duration.ofHours(1));
        mvc.perform(statusChange(onBoundary, "COMPLETED")).andExpect(status().isOk());
        mvc.perform(statusChange(pastBoundary, "COMPLETED")).andExpect(status().isOk());

        clock.advance(Duration.ofDays(7));
        mvc.perform(reopen(onBoundary, "Still broken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reopenedAt").value("2026-03-09T10:00:00Z"));

        clock.advance(Duration.ofSeconds(1));
        mvc.perform(reopen(pastBoundary, "Still broken"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid status change"));
        mvc.perform(get("/api/work-orders/{id}", pastBoundary))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reopenCount").value(0));
    }

    @Test
    void reopenAddsAnActivityEntryWithTheReason() throws Exception {
        String id = completedHighOrder();
        clock.advance(Duration.ofHours(23));
        int before = activity(id).size();

        mvc.perform(reopen(id, "Leak is dripping again")).andExpect(status().isOk());

        List<Map<String, Object>> activity = activity(id);
        assertThat(activity).hasSize(before + 1);
        Map<String, Object> last = activity.get(activity.size() - 1);
        assertThat(last.get("at")).isEqualTo("2026-03-03T09:00:00Z");
        assertThat((String) last.get("message")).contains("Leak is dripping again");
    }

    @Test
    void reopenRejectsOrdersThatAreNotCompleted() throws Exception {
        String cancelled = create("HIGH");
        mvc.perform(statusChange(cancelled, "CANCELLED")).andExpect(status().isOk());
        String open = create("HIGH");
        String assigned = create("HIGH");
        mvc.perform(assign(assigned)).andExpect(status().isOk());
        String inProgress = createAndStart("HIGH");
        String onHold = createAndStart("HIGH");
        mvc.perform(statusChange(onHold, "ON_HOLD")).andExpect(status().isOk());

        for (String id : List.of(cancelled, open, assigned, inProgress, onHold)) {
            mvc.perform(reopen(id, "Try again"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Invalid status change"));
        }
    }

    @Test
    void reopenRequiresAReason() throws Exception {
        String id = completedHighOrder();
        clock.advance(Duration.ofHours(1));

        for (String body : List.of("{}", "{\"reason\":\"   \"}", "{\"reason\":\"" + "x".repeat(501) + "\"}")) {
            mvc.perform(post("/api/work-orders/{id}/reopen", id).contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.reason").exists());
        }

        mvc.perform(get("/api/work-orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reopened").value(false))
                .andExpect(jsonPath("$.reopenCount").value(0));
    }

    @Test
    void reopenOfAnUnknownIdReturns404() throws Exception {
        mvc.perform(reopen("WO-9999", "Still broken"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Work order not found"));
    }

    @Test
    void reopenedOrderCountsAsActiveAndUsesItsNewWindowInTheSummary() throws Exception {
        String id = completedHighOrder();
        clock.advance(Duration.ofHours(23));
        mvc.perform(reopen(id, "Leak is dripping again")).andExpect(status().isOk());

        mvc.perform(get("/api/work-orders/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.byStatus.COMPLETED").value(0))
                .andExpect(jsonPath("$.atRisk").value(0))
                .andExpect(jsonPath("$.overdue").value(0));

        clock.advance(Duration.ofHours(18));
        mvc.perform(get("/api/work-orders/summary"))
                .andExpect(jsonPath("$.atRisk").value(1))
                .andExpect(jsonPath("$.overdue").value(0));

        clock.advance(Duration.ofHours(6).plusSeconds(1));
        mvc.perform(get("/api/work-orders/summary"))
                .andExpect(jsonPath("$.atRisk").value(0))
                .andExpect(jsonPath("$.overdue").value(1));
    }

    @Test
    void aReopenedOrderCanBeCompletedAndReopenedAgain() throws Exception {
        String id = completedHighOrder();
        clock.advance(Duration.ofDays(1));
        mvc.perform(reopen(id, "Leak is dripping again")).andExpect(status().isOk());
        clock.advance(Duration.ofDays(4));
        mvc.perform(statusChange(id, "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reopened").value(true));

        clock.advance(Duration.ofDays(3));
        mvc.perform(reopen(id, "Leaking for a third time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.reopenCount").value(2))
                .andExpect(jsonPath("$.dueAt").value("2026-03-11T10:00:00Z"));
    }

    @Test
    void statusEndpointStillRejectsCompletedToInProgress() throws Exception {
        String id = completedHighOrder();

        mvc.perform(statusChange(id, "IN_PROGRESS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot move " + id + " from Completed to In progress"));
    }

    /** Creates a High order at the current time and completes it one hour later, with Priya Shah assigned. */
    private String completedHighOrder() throws Exception {
        String id = createAndStart("HIGH");
        clock.advance(Duration.ofHours(1));
        mvc.perform(statusChange(id, "COMPLETED")).andExpect(status().isOk());
        return id;
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
                {"title":"Restroom sink leaking","location":"Tower A · Floor 3",
                 "category":"PLUMBING","priority":"%s","requester":"Casey Nguyen"}
                """.formatted(priority);
    }
}
