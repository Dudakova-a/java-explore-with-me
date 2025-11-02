package ru.practicum;

import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;


@RequiredArgsConstructor
public class StatsClient {

    private final RestTemplate restTemplate;
    private final String serverUrl;

    public void hit(EndpointHit endpointHit) {
        try {
            restTemplate.postForEntity(serverUrl + "/hit", endpointHit, Object.class);
        } catch (Exception e) {
            // Логируем ошибку, но не падаем, чтобы основное приложение работало
            System.err.println("Error sending hit to stats service: " + e.getMessage());
        }
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end,
                                    List<String> uris, Boolean unique) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(serverUrl + "/stats")
                    .queryParam("start", start.format(formatter))
                    .queryParam("end", end.format(formatter))
                    .queryParam("unique", unique != null ? unique : false);

            if (uris != null && !uris.isEmpty()) {
                // Для каждого URI добавляем отдельный параметр uris
                for (String uri : uris) {
                    builder.queryParam("uris", uri);
                }
            }

            String url = builder.toUriString();

            ResponseEntity<ViewStats[]> response = restTemplate.getForEntity(url, ViewStats[].class);

            ViewStats[] body = response.getBody();
            return body != null ? Arrays.asList(body) : List.of();

        } catch (Exception e) {
            System.err.println("Error getting stats: " + e.getMessage());
            return List.of(); // Возвращаем пустой список вместо исключения
        }
    }
}