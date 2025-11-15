package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.model.EndpointHitEntity;
import ru.practicum.repository.StatsRepository;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final StatsRepository statsRepository;

    @Override
    public void saveHit(EndpointHit endpointHit) {
        EndpointHitEntity entity = EndpointHitEntity.builder()
                .app(endpointHit.getApp())
                .uri(endpointHit.getUri())
                .ip(endpointHit.getIp())
                .timestamp(endpointHit.getTimestamp() != null ?
                        endpointHit.getTimestamp() : LocalDateTime.now())
                .build();
        statsRepository.save(entity);
    }

    @Override
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end,
                                    List<String> uris, Boolean unique) {


        if (uris == null || uris.isEmpty()) {
            if (Boolean.TRUE.equals(unique)) {
                return statsRepository.getUniqueStatsWithoutUris(start, end);
            } else {
                return statsRepository.getStatsWithoutUris(start, end);
            }
        } else {
            if (Boolean.TRUE.equals(unique)) {
                return statsRepository.getUniqueStats(start, end, uris);
            } else {
                return statsRepository.getStats(start, end, uris);
            }
        }
    }
}