package com.hawas.proxyserver;


import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProxyService {

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();

    private final ConcurrentHashMap<String, RequestRaceGroup> racingRequests = new ConcurrentHashMap<>();

    public CompletableFuture<ResponseEntity<byte[]>> proxyRequest(
            HttpMethod method, String url, HttpHeaders headers, byte[] body) {

        String key = generateRequestKey(method, url, headers, body);
        CompletableFuture<ResponseEntity<byte[]>> clientFuture = new CompletableFuture<>();

        final RequestRaceGroup[] groupRef = new RequestRaceGroup[1];
        final int[] requestNumber = new int[1];

        racingRequests.compute(key, (k, existingGroup) -> {
            if (existingGroup == null) {
                RequestRaceGroup newGroup = new RequestRaceGroup();
                newGroup.addClient(clientFuture);
                requestNumber[0] = newGroup.getNextRequestNumber();
                groupRef[0] = newGroup;
                return newGroup;
            } else {
                existingGroup.addClient(clientFuture);
                requestNumber[0] = existingGroup.getNextRequestNumber();
                groupRef[0] = existingGroup;
                return existingGroup;
            }
        });

        sendUpstreamRequest(method, url, headers, body, key, groupRef[0], requestNumber[0]);

        return clientFuture;
    }

    private void sendUpstreamRequest(HttpMethod method, String url, HttpHeaders headers,
                                     byte[] body, String key, RequestRaceGroup group, int requestNumber) {

        System.out.println("Sending upstream request #" + requestNumber + " for key: " + key);

        WebClient.RequestBodySpec reqSpec = webClient
                .method(method)
                .uri(url)
                .headers(h -> {
                    h.addAll(headers);
                    h.remove(HttpHeaders.HOST);
                    h.remove("X-Target-Url");
                });

        Mono<ResponseEntity<byte[]>> responseMono;

        if (method == HttpMethod.GET || method == HttpMethod.DELETE || body == null || body.length == 0) {
            responseMono = reqSpec.retrieve().toEntity(byte[].class);
        } else {
            MediaType contentType = headers.getContentType();
            responseMono = reqSpec
                    .contentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM)
                    .body(BodyInserters.fromValue(body))
                    .retrieve()
                    .toEntity(byte[].class);
        }

        responseMono.subscribe(
                response -> {
                    System.out.println("Request #" + requestNumber + " completed");
                    completeAllClients(key, response, requestNumber);
                },
                error -> {
                    System.out.println("Request #" + requestNumber + " failed: " + error.getMessage());
                    handleRequestError(key, error, requestNumber);
                }
        );
    }

    private void completeAllClients(String key, ResponseEntity<byte[]> response, int winningRequestNumber) {
        RequestRaceGroup group = racingRequests.remove(key);
        if (group != null && group.tryComplete()) {
            System.out.println("Request #" + winningRequestNumber + " WON the race! Using this response for all " + group.getClientCount() + " waiting clients");
            group.completeAllClients(response);
        }
    }

    private void handleRequestError(String key, Throwable error, int requestNumber) {
        RequestRaceGroup group = racingRequests.get(key);
        if (group != null) {
            group.recordFailure(requestNumber, error);

            if (group.allRequestsFailed()) {
                racingRequests.remove(key);
                if (group.tryComplete()) {
                    ResponseEntity<byte[]> errorResponse = ResponseEntity
                            .status(HttpStatus.BAD_GATEWAY)
                            .body(("All upstream requests failed. Last error: " + error.getMessage()).getBytes());
                    group.completeAllClients(errorResponse);
                }
            }
        }
    }

    private String generateRequestKey(HttpMethod method, String url, HttpHeaders headers, byte[] body) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(method.name()).append(":");
        keyBuilder.append(url).append(":");

        keyBuilder.append(Arrays.hashCode(body)).append(":");

        String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null) {
            keyBuilder.append("AUTH:").append(auth.hashCode()).append(":");
        }

        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType != null) {
            keyBuilder.append("CT:").append(contentType).append(":");
        }

        return keyBuilder.toString();
    }

}