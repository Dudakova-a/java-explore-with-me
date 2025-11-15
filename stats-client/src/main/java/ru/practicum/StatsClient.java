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
import java.util.Collections;

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
            String url = serverUrl + "/hit";
            ResponseEntity<Object> response = restTemplate.postForEntity(url, endpointHit, Object.class);
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

            LocalDateTime cleanStart = start.withNano(0);
            LocalDateTime cleanEnd = end.withNano(0);


            String startStr = cleanStart.format(FORMATTER);
            String endStr = cleanEnd.format(FORMATTER);


            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(serverUrl + "/stats")
                    .queryParam("start", startStr)
                    .queryParam("end", endStr);

            if (unique != null) {
                builder.queryParam("unique", unique);
            }

            if (uris != null && !uris.isEmpty()) {
                for (String uri : uris) {
                    builder.queryParam("uris", uri);
                }
            }


            String url = builder.build().toUriString();
            log.info("Final stats URL: {}", url);

            ResponseEntity<ViewStats[]> response = restTemplate.getForEntity(url, ViewStats[].class);

            ViewStats[] body = response.getBody();
            List<ViewStats> result = body != null ? Arrays.asList(body) : Collections.emptyList();

            log.info("Stats request successful. Found {} records", result.size());
            return result;

        } catch (Exception e) {
            log.error("Error getting stats from service: {} : \"{}\"",
                    e.getMessage(), e.toString(), e);
            return Collections.emptyList();
        }
    }
}