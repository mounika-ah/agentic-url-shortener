package com.shortforge.controller;

import com.shortforge.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Tag(
        name = "Redirect API",
        description = "Resolve short codes and redirect to original URLs"
)
public class RedirectController {

    private final ShortUrlService shortUrlService;

    public RedirectController(
            ShortUrlService shortUrlService
    ) {
        this.shortUrlService = shortUrlService;
    }

    @GetMapping("/{shortCode}")
    @Operation(
            summary = "Redirect to the original URL",
            description = """
                    Resolves a short code, records a click,
                    and returns an HTTP 302 redirect.
                    """
    )
    public ResponseEntity<Void> redirect(
            @PathVariable("shortCode")
            String shortCode
    ) {
        String originalUrl =
                shortUrlService.resolveAndRecordClick(
                        shortCode
                );

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}