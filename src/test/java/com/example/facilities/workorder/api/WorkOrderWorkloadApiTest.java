package com.example.facilities.workorder.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkOrderWorkloadApiTest {

    private static final String HVAC = "WO-1001";
    private static final String LIGHTS = "WO-1002";
    private static final String LEAK = "WO-1003";
    private static final String BADGE_READER = "WO-1004";
    private static final String FREIGHT_ELEVATOR = "WO-1005";
    private static final String TILE = "WO-1007";

    @Autowired
    private MockMvc mvc;

    @Test
    void workloadListsBusiestTechniciansFirstAndTheUnassignedQueue() throws Exception {
        mvc.perform(get("/api/work-orders/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians", hasSize(3)))
                .andExpect(jsonPath("$.technicians[0].assignee").value("Jordan Lee"))
                .andExpect(jsonPath("$.technicians[0].active").value(1))
                .andExpect(jsonPath("$.technicians[0].overdue").value(1))
                .andExpect(jsonPath("$.technicians[1].assignee").value("Priya Shah"))
                .andExpect(jsonPath("$.technicians[1].active").value(1))
                .andExpect(jsonPath("$.technicians[1].overdue").value(0))
                .andExpect(jsonPath("$.technicians[2].assignee").value("Sam Rivera"))
                .andExpect(jsonPath("$.technicians[2].active").value(1))
                .andExpect(jsonPath("$.technicians[2].overdue").value(0))
                .andExpect(jsonPath("$.technicians[?(@.assignee == 'Alex Kim')]").isEmpty())
                .andExpect(jsonPath("$.unassigned.active").value(3))
                .andExpect(jsonPath("$.unassigned.overdue").value(1));
    }

    @Test
    void reassigningMovesWorkToTheNewTechnician() throws Exception {
        mvc.perform(post("/api/work-orders/{id}/assign", HVAC).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"Priya Shah\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/work-orders/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians", hasSize(2)))
                .andExpect(jsonPath("$.technicians[0].assignee").value("Priya Shah"))
                .andExpect(jsonPath("$.technicians[0].active").value(2))
                .andExpect(jsonPath("$.technicians[0].overdue").value(1))
                .andExpect(jsonPath("$.technicians[1].assignee").value("Sam Rivera"))
                .andExpect(jsonPath("$.technicians[?(@.assignee == 'Jordan Lee')]").isEmpty());
    }

    @Test
    void cancellingAndCreatingChangeTheUnassignedQueue() throws Exception {
        mvc.perform(post("/api/work-orders/{id}/status", BADGE_READER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/work-orders/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassigned.active").value(2));

        mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Door closer broken","location":"Tower A · Floor 2",
                         "category":"GENERAL","priority":"LOW","requester":"Morgan Ellis"}
                        """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/work-orders/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassigned.active").value(3));
    }

    @Test
    void listFiltersByAssignee() throws Exception {
        mvc.perform(get("/api/work-orders").param("assignee", "jordan lee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(HVAC)));
        mvc.perform(get("/api/work-orders").param("assignee", "Jordan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/work-orders").param("assignee", "Alex Kim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(TILE)));
        mvc.perform(get("/api/work-orders").param("assignee", "Nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listFiltersUnassignedAndCombinesWithOtherFilters() throws Exception {
        mvc.perform(get("/api/work-orders").param("unassigned", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(LIGHTS, BADGE_READER, FREIGHT_ELEVATOR)));
        mvc.perform(get("/api/work-orders").param("assignee", "Priya Shah").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(LEAK)));
        mvc.perform(get("/api/work-orders").param("unassigned", "true").param("overdue", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(LIGHTS)));
        mvc.perform(get("/api/work-orders").param("unassigned", "true").param("q", "elevator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(FREIGHT_ELEVATOR)));
    }

    @Test
    void listRejectsInvalidOrConflictingAssignmentFilters() throws Exception {
        mvc.perform(get("/api/work-orders").param("unassigned", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.detail").value("Unsupported value for parameter 'unassigned'"));
        mvc.perform(get("/api/work-orders").param("assignee", "Sam Rivera").param("unassigned", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.detail").value("Use either 'assignee' or 'unassigned', not both"));
    }

    @Test
    void workloadRouteIsNotTreatedAsAWorkOrderId() throws Exception {
        mvc.perform(get("/api/work-orders/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians").isArray())
                .andExpect(jsonPath("$.unassigned").exists());
    }
}
