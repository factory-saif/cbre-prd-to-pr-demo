package com.example.facilities.workorder.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Strict RFC 4180 reader for tests: records must end with CRLF, a bare CR or LF is only allowed inside a quoted
 * field, and a double quote is only allowed as a field delimiter or doubled inside a quoted field. Any other input
 * throws, so a passing parse proves the text is well-formed CSV.
 */
final class Rfc4180Reader {

    private Rfc4180Reader() {
    }

    static List<List<String>> read(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        int i = 0;
        while (i < csv.length()) {
            if (csv.charAt(i) == '"') {
                i++;
                while (true) {
                    if (i >= csv.length()) {
                        throw new IllegalArgumentException("Unterminated quoted field");
                    }
                    char c = csv.charAt(i);
                    if (c == '"') {
                        if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                            field.append('"');
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    field.append(c);
                    i++;
                }
            } else {
                while (i < csv.length() && csv.charAt(i) != ',' && csv.charAt(i) != '\r') {
                    char c = csv.charAt(i);
                    if (c == '"' || c == '\n') {
                        throw new IllegalArgumentException("Unexpected " + describe(c) + " at offset " + i);
                    }
                    field.append(c);
                    i++;
                }
            }
            record.add(field.toString());
            field.setLength(0);
            if (i >= csv.length()) {
                throw new IllegalArgumentException("Last record does not end with CRLF");
            }
            char separator = csv.charAt(i);
            if (separator == ',') {
                i++;
            } else if (separator == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                records.add(record);
                record = new ArrayList<>();
                i += 2;
            } else {
                throw new IllegalArgumentException("Unexpected " + describe(separator) + " at offset " + i);
            }
        }
        return records;
    }

    private static String describe(char c) {
        return switch (c) {
            case '\r' -> "CR";
            case '\n' -> "LF";
            default -> "'" + c + "'";
        };
    }
}
