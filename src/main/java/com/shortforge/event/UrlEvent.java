package com.shortforge.event;

import java.time.Instant;

public record UrlEvent(

        EventType type,

        String shortCode,

        String originalUrl,

        Instant timestamp

) {
}

