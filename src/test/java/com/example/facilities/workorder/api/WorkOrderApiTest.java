package com.example.facilities.workorder.api;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@SpringBootTest(properties = "facilities.demo-data=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkOrderApiTest {

    private static final Instant NOW = Instant.parse("2026-03-02T09:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void createReturnsTheNewWorkOrderWithItsLocationAndDueDate() throws Exception {
        mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON).content(body("URGENT")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/work-orders/WO-1001"))
                .andExpect(jsonPath("$.id").value("WO-1001"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.dueAt").value("2026-03-02T13:00:00Z"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.atRisk").value(false))
                .andExpect(jsonPath("$.nextStatuses[0]").value("ASSIGNED"));
    }

    @Test
    void createRejectsMissingFieldsWithFieldErrors() throws Exception {
        mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"category\":\"HVAC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.location").exists())
                .andExpect(jsonPath("$.errors.priority").exists())
                .andExpect(jsonPath("$.errors.requester").exists())
                .andExpect(jsonPath("$.errors.category").doesNotExist());
    }

    @Test
    void createRejectsUnknownPriority() throws Exception {
        mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON).content(body("CRITICAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void unknownWorkOrderReturnsNotFound() throws Exception {
        mvc.perform(get("/api/work-orders/WO-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No work order exists with ID WO-9999"));
    }

    @Test
    void workOrderMovesThroughAssignStartAndComplete() throws Exception {
        String id = create("HIGH");

        mvc.perform(post("/api/work-orders/{id}/assign", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"Priya Shah\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignee").value("Priya Shah"));

        mvc.perform(statusChange(id, "IN_PROGRESS")).andExpect(status().isOk());

        mvc.perform(statusChange(id, "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value("2026-03-02T09:00:00Z"))
                .andExpect(jsonPath("$.nextStatuses", hasSize(0)))
                .andExpect(jsonPath("$.activity", hasSize(4)));
    }

    @Test
    void invalidStatusChangeReturnsConflict() throws Exception {
        String id = create("LOW");

        mvc.perform(statusChange(id, "COMPLETED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot move WO-1001 from Open to Completed"));
    }

    @Test
    void assignRejectsBlankAssignee() throws Exception {
        String id = create("LOW");

        mvc.perform(post("/api/work-orders/{id}/assign", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.assignee").exists());
    }

    @Test
    void listFiltersByPriorityAndRejectsUnknownValues() throws Exception {
        create("URGENT");
        create("LOW");

        mvc.perform(get("/api/work-orders").param("priority", "URGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].priority").value("URGENT"));

        mvc.perform(get("/api/work-orders").param("priority", "SOON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unsupported value for parameter 'priority'"));
    }

    @Test
    void summaryReportsCountsByStatus() throws Exception {
        create("URGENT");
        create("MEDIUM");

        mvc.perform(get("/api/work-orders/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.active").value(2))
                .andExpect(jsonPath("$.urgentActive").value(1))
                .andExpect(jsonPath("$.byStatus.OPEN").value(2));
    }

    @Test
    void webUiAndHealthAreServed() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(allOf(containsString("Facilities Desk"),
                                containsString("data-testid=\"summary-at-risk\""),
                                containsString("data-testid=\"filter-at-risk\""))));
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String create(String priority) throws Exception {
        String response = mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(priority)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static org.springframework.test.web.servlet.RequestBuilder statusChange(String id, String status) {
        return post("/api/work-orders/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}");
    }

    private static String body(String priority) {
        return """
                {"title":"HVAC not cooling","description":"Too warm","location":"Tower A · Floor 12",
                 "category":"HVAC","priority":"%s","requester":"Morgan Ellis"}
                """.formatted(priority);
    }
}
