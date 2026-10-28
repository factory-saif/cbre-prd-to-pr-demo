package com.example.facilities.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.facilities.workorder.MutableClock;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(properties = "facilities.demo-data=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkOrderCsvExportApiTest {

    private static final Instant START = Instant.parse("2026-03-02T09:00:00Z");
    private static final String EXPORT = "/api/work-orders/export.csv";
    private static final String HEADER = "id,title,category,priority,status,assignee,created at,due at";

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
    void exportIsAnAttachedUtf8CsvFile() throws Exception {
        mvc.perform(get(EXPORT))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"work-orders.csv\""));
    }

    @Test
    void emptyExportIsOnlyTheHeaderRow() throws Exception {
        assertThat(export(Map.of())).isEqualTo(HEADER + "\r\n");

        create("Lobby too cold", "HVAC", "LOW");
        assertThat(export(Map.of("status", "COMPLETED"))).isEqualTo(HEADER + "\r\n");
    }

    @Test
    void eachWorkOrderIsOneCrlfTerminatedRecordWithColumnsInOrder() throws Exception {
        String unassigned = create("HVAC not cooling", "HVAC", "URGENT");
        clock.advance(Duration.ofMinutes(30));
        String assigned = create("Spill in cafeteria", "CLEANING", "LOW");
        assign(assigned, "Priya Shah");

        String body = export(Map.of());

        assertThat(body).isEqualTo(HEADER + "\r\n"
                + unassigned + ",HVAC not cooling,HVAC,URGENT,OPEN,,2026-03-02T09:00:00Z,2026-03-02T13:00:00Z\r\n"
                + assigned + ",Spill in cafeteria,CLEANING,LOW,ASSIGNED,Priya Shah,"
                + "2026-03-02T09:30:00Z,2026-03-09T09:30:00Z\r\n");
        assertThat(body.replace("\r\n", "")).doesNotContain("\n", "\r");
    }

    @Test
    void specialCharactersAreEscapedAndParseBackToTheOriginalTitle() throws Exception {
        String quoted = "Fix \"east\" door, lobby";
        String multiline = "Leak\nunder sink";
        String unicode = "Café door · Tower A";
        create(quoted, "GENERAL", "URGENT");
        create(multiline, "PLUMBING", "URGENT");
        create(unicode, "SECURITY", "URGENT");

        String body = export(Map.of());

        assertThat(body).contains(",\"Fix \"\"east\"\" door, lobby\",GENERAL,");
        assertThat(body).contains(",\"Leak\nunder sink\",PLUMBING,");
        assertThat(body).contains("," + unicode + ",SECURITY,");
        assertThat(body).endsWith("\r\n");

        List<List<String>> rows = Rfc4180Reader.read(body);
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0)).containsExactly(HEADER.split(","));
        assertThat(rows.stream().skip(1).map(row -> row.get(1))).containsExactly(quoted, multiline, unicode);
        assertThat(rows.stream().skip(1).map(row -> row.get(5))).containsOnly("");
        assertThat(rows).allSatisfy(row -> assertThat(row).hasSize(8));
    }

    @Test
    void everyFilterReturnsTheSameIdsInTheSameOrderAsTheList() throws Exception {
        seedMix();

        List<Map<String, String>> filters = List.of(
                Map.of(),
                Map.of("status", "OPEN"),
                Map.of("status", "COMPLETED"),
                Map.of("priority", "URGENT"),
                Map.of("category", "HVAC"),
                Map.of("overdue", "true"),
                Map.of("overdue", "false"),
                Map.of("atRisk", "true"),
                Map.of("atRisk", "false"),
                Map.of("q", "elevator"),
                Map.of("q", "priya"),
                Map.of("priority", "URGENT", "category", "HVAC"),
                Map.of("atRisk", "true", "q", "elevator"));

        for (Map<String, String> filter : filters) {
            assertThat(exportIds(filter)).as("ids for %s", filter).isEqualTo(listIds(filter));
        }

        assertThat(exportIds(Map.of())).hasSize(6);
        assertThat(exportIds(Map.of("priority", "URGENT", "category", "HVAC")))
                .containsExactly("WO-1005", "WO-1004");
        assertThat(exportIds(Map.of("atRisk", "true", "q", "elevator")))
                .containsExactly("WO-1003", "WO-1001");
        assertThat(exportIds(Map.of("overdue", "true"))).containsExactly("WO-1002");
        assertThat(exportIds(Map.of("q", "priya"))).containsExactly("WO-1006");
    }

    @Test
    void invalidFilterValuesReturnBadRequestLikeTheList() throws Exception {
        for (String param : List.of("status", "priority", "category", "overdue", "atRisk")) {
            mvc.perform(get(EXPORT).param(param, "NOPE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Malformed request"))
                    .andExpect(jsonPath("$.detail").value("Unsupported value for parameter '" + param + "'"));
            mvc.perform(get("/api/work-orders").param(param, "NOPE"))
                    .andExpect(status().isBadRequest());
        }

        mvc.perform(get(EXPORT).param("status", "NOPE").accept(MediaType.parseMediaType("text/csv")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportPathIsNotTreatedAsAWorkOrderId() throws Exception {
        mvc.perform(get(EXPORT))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));

        mvc.perform(get("/api/work-orders/WO-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Work order not found"));
    }

    /**
     * Leaves the clock at 05:00 the next day with WO-1001 (High) and WO-1003 (Urgent) at risk, WO-1002 overdue,
     * WO-1004 completed, WO-1005 just created, and WO-1006 assigned to Priya Shah.
     */
    private void seedMix() throws Exception {
        create("Freight elevator door sticking", "GENERAL", "HIGH");
        create("Leak in restroom", "PLUMBING", "URGENT");
        clock.advance(Duration.ofHours(17));
        create("Passenger elevator stuck", "GENERAL", "URGENT");
        String completed = create("Server room too warm", "HVAC", "URGENT");
        clock.advance(Duration.ofHours(3));
        assign(completed, "Sam Ortiz");
        mvc.perform(statusChange(completed, "IN_PROGRESS")).andExpect(status().isOk());
        mvc.perform(statusChange(completed, "COMPLETED")).andExpect(status().isOk());
        create("Lobby too cold", "HVAC", "URGENT");
        String assigned = create("Spill in cafeteria", "CLEANING", "LOW");
        assign(assigned, "Priya Shah");
    }

    private List<String> exportIds(Map<String, String> filter) throws Exception {
        return Rfc4180Reader.read(export(filter)).stream().skip(1).map(row -> row.get(0)).toList();
    }

    private List<String> listIds(Map<String, String> filter) throws Exception {
        String json = mvc.perform(get("/api/work-orders").params(params(filter)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[*].id");
    }

    private String export(Map<String, String> filter) throws Exception {
        return mvc.perform(get(EXPORT).params(params(filter)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static MultiValueMap<String, String> params(Map<String, String> filter) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        filter.forEach(params::add);
        return params;
    }

    private void assign(String id, String assignee) throws Exception {
        mvc.perform(post("/api/work-orders/{id}/assign", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"" + assignee + "\"}"))
                .andExpect(status().isOk());
    }

    private String create(String title, String category, String priority) throws Exception {
        String json = """
                {"title":%s,"location":"Tower B · Floor 3","category":"%s","priority":"%s",
                 "requester":"Casey Nguyen"}
                """.formatted(jsonString(title), category, priority);
        String response = mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static RequestBuilder statusChange(String id, String status) {
        return post("/api/work-orders/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}");
    }
}
