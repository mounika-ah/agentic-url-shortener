package com.shortforge.unit.controller;

import com.shortforge.controller.RedirectController;
import com.shortforge.service.ShortUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedirectControllerTest {

    private static final String SHORT_CODE = "AbCd1234";
    private static final String ORIGINAL_URL =
            "https://www.google.com";

    @Mock
    private ShortUrlService shortUrlService;

    private RedirectController redirectController;

    @BeforeEach
    void setUp() {
        redirectController =
                new RedirectController(shortUrlService);
    }

    @Test
    void redirectShouldReturnFoundWithLocationHeader() {
        when(shortUrlService.resolveAndRecordClick(SHORT_CODE))
                .thenReturn(ORIGINAL_URL);

        ResponseEntity<Void> response =
                redirectController.redirect(SHORT_CODE);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.FOUND);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create(ORIGINAL_URL));

        assertThat(response.getBody())
                .isNull();

        verify(shortUrlService)
                .resolveAndRecordClick(SHORT_CODE);
    }
}