package com.example.facilities.workorder.api;

import com.example.facilities.workorder.InvalidTransitionException;
import com.example.facilities.workorder.WorkOrderNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(WorkOrderNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(WorkOrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Work order not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ResponseEntity<ProblemDetail> invalidTransition(InvalidTransitionException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid status change", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ResponseEntity<ProblemDetail> response =
                problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
        response.getBody().setProperty("errors", errors);
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "The request body is missing or contains an unsupported value");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Unsupported value for parameter '" + ex.getName() + "'");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return ResponseEntity.status(status).body(body);
    }
}
