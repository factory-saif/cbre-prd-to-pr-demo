package com.example.facilities.workorder.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.facilities.workorder.MutableClock;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "facilities.demo-data=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkOrderAtRiskApiTest {

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
    void atRiskFieldCoversTheLastQuarterOfTheWindowUntilDue() throws Exception {
        String id = create("URGENT");

        clock.advance(Duration.ofHours(2).plusMinutes(59));
        expectFlags(id, false, false);

        clock.advance(Duration.ofMinutes(1));
        expectFlags(id, true, false);

        clock.advance(Duration.ofHours(1));
        expectFlags(id, true, false);

        clock.advance(Duration.ofMinutes(1));
        expectFlags(id, false, true);
    }

    @Test
    void listFiltersByAtRiskAndCombinesWithOtherFilters() throws Exception {
        seedMix();

        mvc.perform(get("/api/work-orders").param("atRisk", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder("WO-1001", "WO-1003")))
                .andExpect(jsonPath("$[*].atRisk", containsInAnyOrder(true, true)));

        mvc.perform(get("/api/work-orders").param("atRisk", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder("WO-1002", "WO-1004", "WO-1005")));

        mvc.perform(get("/api/work-orders").param("atRisk", "true").param("priority", "URGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("WO-1003"));
    }

    @Test
    void listRejectsAnUnsupportedAtRiskValue() throws Exception {
        mvc.perform(get("/api/work-orders").param("atRisk", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.detail").value("Unsupported value for parameter 'atRisk'"));
    }

    @Test
    void summaryCountsTheSameOrdersAsTheAtRiskFilter() throws Exception {
        seedMix();

        String atRiskList = mvc.perform(get("/api/work-orders").param("atRisk", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int listed = JsonPath.<Integer>read(atRiskList, "$.length()");

        mvc.perform(get("/api/work-orders/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atRisk").value(listed))
                .andExpect(jsonPath("$.atRisk").value(2))
                .andExpect(jsonPath("$.overdue").value(1))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.active").value(4))
                .andExpect(jsonPath("$.byStatus.COMPLETED").value(1));
    }

    /**
     * Leaves the clock at 05:00 the next day with WO-1001 (High) and WO-1003 (Urgent) at risk,
     * WO-1002 overdue, WO-1004 completed inside its at-risk window, and WO-1005 just created.
     */
    private void seedMix() throws Exception {
        create("HIGH");
        create("URGENT");
        clock.advance(Duration.ofHours(17));
        create("URGENT");
        String completed = create("URGENT");
        clock.advance(Duration.ofHours(3));
        mvc.perform(post("/api/work-orders/{id}/assign", completed).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"Priya Shah\"}"))
                .andExpect(status().isOk());
        mvc.perform(statusChange(completed, "IN_PROGRESS")).andExpect(status().isOk());
        mvc.perform(statusChange(completed, "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atRisk").value(false));
        create("URGENT");
    }

    private ResultActions expectFlags(String id, boolean atRisk, boolean overdue) throws Exception {
        return mvc.perform(get("/api/work-orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atRisk").value(atRisk))
                .andExpect(jsonPath("$.overdue").value(overdue));
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
                {"title":"Freight elevator door sticking","location":"Tower B · Freight elevator",
                 "category":"GENERAL","priority":"%s","requester":"Casey Nguyen"}
                """.formatted(priority);
    }
}
