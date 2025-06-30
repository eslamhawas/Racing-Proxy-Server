package com.hawas.proxyserver;

import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RequestRaceGroup {
    private final List<CompletableFuture<ResponseEntity<byte[]>>> clients = new ArrayList<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Map<Integer, Throwable> failures = new ConcurrentHashMap<>();
    private volatile int requestCounter = 0;
    private volatile int totalRequestsSent = 0;

    public synchronized void addClient(CompletableFuture<ResponseEntity<byte[]>> client) {
        if (!completed.get()) {
            clients.add(client);
        } else {
            //this shouldn't happen but handle gracefully
            client.completeExceptionally(new RuntimeException("Request group already completed"));
        }
    }

    public synchronized int getNextRequestNumber() {
        totalRequestsSent++;
        return ++requestCounter;
    }

    public synchronized int getClientCount() {
        return clients.size();
    }

    public boolean tryComplete() {
        return completed.compareAndSet(false, true);
    }

    public synchronized void completeAllClients(ResponseEntity<byte[]> response) {
        for (CompletableFuture<ResponseEntity<byte[]>> client : clients) {
            if (!client.isDone()) {
                client.complete(response);
            }
        }
    }

    public void recordFailure(int requestNumber, Throwable error) {
        failures.put(requestNumber, error);
    }

    public boolean allRequestsFailed() {
        return failures.size() >= totalRequestsSent && !completed.get();
    }
}
