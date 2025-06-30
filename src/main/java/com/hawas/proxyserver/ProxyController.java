package com.hawas.proxyserver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/proxy")
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping("/**")
    public Mono<ResponseEntity<byte[]>> handleProxy(
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) Mono<byte[]> bodyMono,
            HttpMethod method,
            ServerWebExchange exchange
    ) {
        String targetUrl = headers.getFirst("X-Target-Url");
        if (targetUrl == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body("Missing X-Target-Url header".getBytes()));
        }

        return bodyMono
                .defaultIfEmpty(new byte[0])
                .flatMap(bodyBytes -> {
                    CompletableFuture<ResponseEntity<byte[]>> future = proxyService.proxyRequest(
                            method,
                            targetUrl,
                            headers,
                            bodyBytes
                    );
                    return Mono.fromFuture(future);
                });
    }
}
