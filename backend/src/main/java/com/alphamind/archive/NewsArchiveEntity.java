package com.alphamind.archive;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/** 新闻存档行：只增不改，{@code fetchedAt} 是我们实际看到这条新闻的时间。 */
@Entity
@Table(name = "news_archive")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NewsArchiveEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", length = 20, nullable = false, updatable = false)
    private String stockCode;

    @Column(name = "title", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String title;

    @Column(name = "published_at", updatable = false)
    private LocalDateTime publishedAt;

    @Column(name = "url", columnDefinition = "TEXT", updatable = false)
    private String url;

    @Column(name = "source", length = 40, nullable = false, updatable = false)
    private String source;

    @Column(name = "fetched_at", nullable = false, updatable = false)
    private OffsetDateTime fetchedAt;

    @Column(name = "dedupe_key", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String dedupeKey;
}
