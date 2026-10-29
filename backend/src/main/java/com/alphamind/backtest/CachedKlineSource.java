package com.alphamind.backtest;

import com.alphamind.market.provider.DailyKline;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 本地文件缓存的日 K 来源：{@code <dir>/<code>.json}。命中缓存（且未要求刷新）时不访问网络，
 * 使回测可离线复现；取数失败不写缓存，也不编造数据。刷新失败时沿用已有缓存并记入 {@link #notes()}。
 */
public class CachedKlineSource implements KlineSource {
    private static final TypeReference<List<DailyKline>> BARS = new TypeReference<>() {};

    private final Path dir;
    private final Function<String, Optional<List<DailyKline>>> fetcher;
    private final boolean refresh;
    private final ObjectMapper mapper;
    private final List<String> notes = new ArrayList<>();

    public CachedKlineSource(Path dir, Function<String, Optional<List<DailyKline>>> fetcher,
                             boolean refresh, ObjectMapper mapper) {
        this.dir = dir;
        this.fetcher = fetcher;
        this.refresh = refresh;
        this.mapper = mapper.copy().registerModule(new JavaTimeModule());
    }

    @Override
    public List<String> notes() {
        return List.copyOf(notes);
    }

    @Override
    public Optional<List<DailyKline>> load(String code) {
        Path file = dir.resolve(code + ".json");
        try {
            if (!refresh && Files.exists(file)) {
                return Optional.of(mapper.readValue(file.toFile(), BARS));
            }
            Optional<List<DailyKline>> fetched = fetcher.apply(code);
            if (fetched.isPresent() && !fetched.get().isEmpty()) {
                Files.createDirectories(dir);
                mapper.writeValue(file.toFile(), fetched.get());
                return fetched;
            }
            if (refresh && Files.exists(file)) {
                notes.add(code + "：刷新失败，沿用旧缓存");
                return Optional.of(mapper.readValue(file.toFile(), BARS));
            }
            return Optional.empty();
        } catch (IOException exception) {
            throw new UncheckedIOException("读取或写入回测 K 线缓存失败: " + file, exception);
        }
    }
}
