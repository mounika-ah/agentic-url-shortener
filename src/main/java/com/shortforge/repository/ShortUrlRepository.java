package com.shortforge.repository;

import com.shortforge.domain.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Modifying
    @Query("""
            update ShortUrl s
               set s.clickCount = s.clickCount + 1
             where s.shortCode = :shortCode
               and s.active = true
            """)
    int incrementClickCount(
            @Param("shortCode") String shortCode
    );
}
