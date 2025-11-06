package ru.practicum;

import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class StatsClient {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;
    private final String serverUrl;

    public void hit(EndpointHit endpointHit) {
        log.info("Sending hit to stats service: app={}, uri={}, ip={}, timestamp={}",
                endpointHit.getApp(), endpointHit.getUri(), endpointHit.getIp(), endpointHit.getTimestamp());

        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(serverUrl + "/hit", endpointHit, Object.class);
            log.info("Hit successfully sent to stats service. Response status: {}", response.getStatusCode());
        } catch (Exception e) {
            log.error("Error sending hit to stats service: {}", e.getMessage(), e);
        }
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end,
                                    List<String> uris, Boolean unique) {
        log.info("Requesting stats from stats service: start={}, end={}, uris={}, unique={}",
                start, end, uris, unique);

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(serverUrl + "/stats")
                    .queryParam("start", start.format(FORMATTER))
                    .queryParam("end", end.format(FORMATTER))
                    .queryParam("unique", unique != null ? unique : false);

            if (uris != null && !uris.isEmpty()) {
                for (String uri : uris) {
                    builder.queryParam("uris", uri);
                }
            }

            String url = builder.toUriString();
            log.debug("Built stats request URL: {}", url);

            ResponseEntity<ViewStats[]> response = restTemplate.getForEntity(url, ViewStats[].class);
            log.info("Stats request completed. Response status: {}, found {} records",
                    response.getStatusCode(), response.getBody() != null ? response.getBody().length : 0);

            ViewStats[] body = response.getBody();
            return body != null ? Arrays.asList(body) : List.of();

        } catch (Exception e) {
            log.error("Error getting stats from service: {}", e.getMessage(), e);
            return List.of();
        }
    }
}