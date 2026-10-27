package dev.cxtrends.trends;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * {@code GET /trends?category=&region=&from=&to=} — weekly ticket volumes for a slice
 * with an anomaly flag per week. All parameters are optional; dates are ISO
 * (YYYY-MM-DD); category and region match case-insensitively.
 */
@RestController
public class TrendController {

    private final TrendService service;

    public TrendController(TrendService service) {
        this.service = service;
    }

    @GetMapping("/trends")
    public TrendResponse trends(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.trends(new TrendService.TrendQuery(category, region, from, to));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail badParameter(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "invalid value '%s' for '%s'; dates must be YYYY-MM-DD".formatted(e.getValue(), e.getName()));
    }
}
