package com.example.facilities.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkOrderCsvTest {

    @Test
    void plainValuesAreWrittenAsIs() {
        assertThat(WorkOrderCsv.field("HVAC not cooling")).isEqualTo("HVAC not cooling");
        assertThat(WorkOrderCsv.field("")).isEmpty();
    }

    @Test
    void nullBecomesAnEmptyField() {
        assertThat(WorkOrderCsv.field(null)).isEmpty();
    }

    @Test
    void commaAndQuotesAreQuotedAndEmbeddedQuotesDoubled() {
        assertThat(WorkOrderCsv.field("Fix \"east\" door, lobby")).isEqualTo("\"Fix \"\"east\"\" door, lobby\"");
        assertThat(WorkOrderCsv.field("Lobby, east")).isEqualTo("\"Lobby, east\"");
        assertThat(WorkOrderCsv.field("\"")).isEqualTo("\"\"\"\"");
    }

    @Test
    void lineBreaksAreQuoted() {
        assertThat(WorkOrderCsv.field("Leak\nunder sink")).isEqualTo("\"Leak\nunder sink\"");
        assertThat(WorkOrderCsv.field("Leak\r\nunder sink")).isEqualTo("\"Leak\r\nunder sink\"");
        assertThat(WorkOrderCsv.field("Leak\runder sink")).isEqualTo("\"Leak\runder sink\"");
    }

    @Test
    void noOrdersWritesOnlyTheHeaderRow() {
        assertThat(WorkOrderCsv.write(List.of()))
                .isEqualTo("id,title,category,priority,status,assignee,created at,due at\r\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Fix \"east\" door, lobby",
            "Leak\nunder sink",
            "Leak\r\nunder sink",
            "\"",
            ",,,",
            "Café door · Tower A",
            " padded "})
    void escapedFieldsParseBackToTheOriginalValue(String value) {
        String line = WorkOrderCsv.field(value) + "," + WorkOrderCsv.field("next") + "\r\n";

        assertThat(Rfc4180Reader.read(line)).containsExactly(List.of(value, "next"));
    }

    @Test
    void testReaderRejectsMalformedCsv() {
        assertThatThrownBy(() -> Rfc4180Reader.read("Fix \"east\" door\r\n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Rfc4180Reader.read("a,b\nc,d\r\n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Rfc4180Reader.read("a,b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Rfc4180Reader.read("\"a\"b,c\r\n")).isInstanceOf(IllegalArgumentException.class);
    }
}
