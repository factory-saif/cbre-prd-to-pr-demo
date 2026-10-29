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
class WorkOrderNotesApiTest {

    /** A Monday. A Medium order created here is due Thursday 09:00 and at risk from Wednesday 15:00. */
    private static final Instant START = Instant.parse("2026-03-02T09:00:00Z");

    private static final String TEXT = "Parts ordered, back Thursday";
    private static final String AUTHOR = "Sam Rivera";

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
    void addingANoteToAnInProgressOrderReturnsTheNewEntry() throws Exception {
        String id = createAndStart();
        List<Map<String, Object>> before = activity(id);
        clock.advance(Duration.ofHours(2));

        mvc.perform(addNote(id, TEXT, AUTHOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignee").value("Priya Shah"))
                .andExpect(jsonPath("$.noteCount").value(1))
                .andExpect(jsonPath("$.updatedAt").value("2026-03-02T11:00:00Z"))
                .andExpect(jsonPath("$.activity.length()").value(before.size() + 1))
                .andExpect(jsonPath("$.activity[-1].message").value("Note from Sam Rivera: " + TEXT))
                .andExpect(jsonPath("$.activity[-1].at").value("2026-03-02T11:00:00Z"));

        List<Map<String, Object>> after = activity(id);
        assertThat(after.subList(0, before.size())).isEqualTo(before);
    }

    @Test
    void addingANoteKeepsPriorityDueTimeOverdueAndAtRisk() throws Exception {
        String id = createAndStart();
        clock.advance(Duration.ofHours(60));
        String before = mvc.perform(get("/api/work-orders/{id}", id))
                .andExpect(jsonPath("$.atRisk").value(true))
                .andReturn().getResponse().getContentAsString();

        String after = mvc.perform(addNote(id, TEXT, AUTHOR))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String field : List.of("$.priority", "$.dueAt", "$.overdue", "$.atRisk")) {
            Object expected = JsonPath.read(before, field);
            Object actual = JsonPath.read(after, field);
            assertThat(actual).as(field).isEqualTo(expected);
        }
        mvc.perform(get("/api/work-orders/{id}", id))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.dueAt").value("2026-03-05T09:00:00Z"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.atRisk").value(true));
    }

    @Test
    void notesAreAcceptedInEveryActiveStatus() throws Exception {
        String open = create();
        String assigned = create();
        mvc.perform(assign(assigned)).andExpect(status().isOk());
        String inProgress = createAndStart();
        String onHold = createAndStart();
        mvc.perform(statusChange(onHold, "ON_HOLD")).andExpect(status().isOk());

        Map<String, String> expected = Map.of(
                open, "OPEN", assigned, "ASSIGNED", inProgress, "IN_PROGRESS", onHold, "ON_HOLD");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            mvc.perform(addNote(entry.getKey(), TEXT, AUTHOR))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(entry.getValue()))
                    .andExpect(jsonPath("$.noteCount").value(1))
                    .andExpect(jsonPath("$.activity[-1].message").value("Note from Sam Rivera: " + TEXT));
        }
    }

    @Test
    void notesOnClosedOrdersReturn409() throws Exception {
        String completed = createAndStart();
        mvc.perform(statusChange(completed, "COMPLETED")).andExpect(status().isOk());
        String cancelled = create();
        mvc.perform(statusChange(cancelled, "CANCELLED")).andExpect(status().isOk());
        clock.advance(Duration.ofHours(1));

        for (String id : List.of(completed, cancelled)) {
            int before = activity(id).size();

            mvc.perform(addNote(id, TEXT, AUTHOR))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Invalid status change"));

            mvc.perform(get("/api/work-orders/{id}", id))
                    .andExpect(jsonPath("$.noteCount").value(0))
                    .andExpect(jsonPath("$.activity.length()").value(before));
        }
    }

    @Test
    void noteTextIsValidated() throws Exception {
        String id = createAndStart();
        int before = activity(id).size();

        for (String body : List.of(
                "{\"author\":\"Sam Rivera\"}",
                "{\"text\":\"   \",\"author\":\"Sam Rivera\"}",
                "{\"text\":\"" + "x".repeat(501) + "\",\"author\":\"Sam Rivera\"}")) {
            mvc.perform(post("/api/work-orders/{id}/notes", id).contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.text").exists());
        }

        mvc.perform(get("/api/work-orders/{id}", id))
                .andExpect(jsonPath("$.noteCount").value(0))
                .andExpect(jsonPath("$.activity.length()").value(before));
    }

    @Test
    void noteAuthorIsValidated() throws Exception {
        String id = createAndStart();
        int before = activity(id).size();

        for (String body : List.of(
                "{\"text\":\"" + TEXT + "\"}",
                "{\"text\":\"" + TEXT + "\",\"author\":\"   \"}",
                "{\"text\":\"" + TEXT + "\",\"author\":\"" + "a".repeat(101) + "\"}")) {
            mvc.perform(post("/api/work-orders/{id}/notes", id).contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.author").exists());
        }

        mvc.perform(get("/api/work-orders/{id}", id))
                .andExpect(jsonPath("$.noteCount").value(0))
                .andExpect(jsonPath("$.activity.length()").value(before));
    }

    @Test
    void noteTextOfExactly500CharactersIsAccepted() throws Exception {
        String id = createAndStart();
        String text = "x".repeat(500);

        mvc.perform(addNote(id, text, AUTHOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteCount").value(1))
                .andExpect(jsonPath("$.activity[-1].message").value("Note from Sam Rivera: " + text));
    }

    @Test
    void noteOnAnUnknownIdReturns404() throws Exception {
        mvc.perform(addNote("WO-9999", TEXT, AUTHOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Work order not found"));
    }

    @Test
    void notesStayInTimeOrderWithStatusChanges() throws Exception {
        String id = createAndStart();
        clock.advance(Duration.ofHours(1));
        mvc.perform(statusChange(id, "ON_HOLD")).andExpect(status().isOk());
        clock.advance(Duration.ofHours(1));
        mvc.perform(addNote(id, TEXT, AUTHOR)).andExpect(status().isOk());
        clock.advance(Duration.ofHours(1));
        mvc.perform(statusChange(id, "IN_PROGRESS")).andExpect(status().isOk());

        List<Map<String, Object>> activity = activity(id);
        List<Map<String, Object>> lastThree = activity.subList(activity.size() - 3, activity.size());
        assertThat(lastThree).extracting(entry -> entry.get("message")).containsExactly(
                "Status changed from In progress to On hold",
                "Note from Sam Rivera: " + TEXT,
                "Status changed from On hold to In progress");
        assertThat(lastThree).extracting(entry -> entry.get("at")).containsExactly(
                "2026-03-02T10:00:00Z", "2026-03-02T11:00:00Z", "2026-03-02T12:00:00Z");
    }

    @Test
    void listIncludesNoteCountForEveryOrder() throws Exception {
        String withNotes = createAndStart();
        String withoutNotes = createAndStart();
        mvc.perform(addNote(withNotes, TEXT, AUTHOR)).andExpect(status().isOk());
        mvc.perform(addNote(withNotes, "Tenant not home, rescheduled", AUTHOR)).andExpect(status().isOk());

        String list = mvc.perform(get("/api/work-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<Integer>>read(list, "$[?(@.id == '" + withNotes + "')].noteCount"))
                .containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(list, "$[?(@.id == '" + withoutNotes + "')].noteCount"))
                .containsExactly(0);
    }

    private String createAndStart() throws Exception {
        String id = create();
        mvc.perform(assign(id)).andExpect(status().isOk());
        mvc.perform(statusChange(id, "IN_PROGRESS")).andExpect(status().isOk());
        return id;
    }

    private String create() throws Exception {
        String response = mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Leak near server racks","location":"Tower A · Floor 3",
                                 "category":"PLUMBING","priority":"MEDIUM","requester":"Casey Nguyen"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private List<Map<String, Object>> activity(String id) throws Exception {
        ResultActions result = mvc.perform(get("/api/work-orders/{id}", id)).andExpect(status().isOk());
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.activity");
    }

    private static RequestBuilder addNote(String id, String text, String author) {
        return post("/api/work-orders/{id}/notes", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\",\"author\":\"" + author + "\"}");
    }

    private static RequestBuilder assign(String id) {
        return post("/api/work-orders/{id}/assign", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee\":\"Priya Shah\"}");
    }

    private static RequestBuilder statusChange(String id, String status) {
        return post("/api/work-orders/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}");
    }
}
