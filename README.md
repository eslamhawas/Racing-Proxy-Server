# Racing Proxy Service 🏁

A Spring Boot proxy service that implements a unique **racing strategy** for handling identical concurrent requests. When multiple identical requests arrive, it sends ALL of them to the upstream API simultaneously and uses the fastest response to complete all waiting clients.

## 🎯 How It Works

Instead of traditional request deduplication (which sends only one upstream request), this service:

1. **Receives multiple identical requests** from clients
2. **Sends ALL requests to upstream** simultaneously
3. **Uses the fastest response** to complete all waiting clients
4. **Ignores slower responses** (they're discarded)

This approach can significantly reduce response times when upstream services have variable latency.

## 🚀 Features

- **Request Racing**: Multiple upstream requests race each other
- **Response Sharing**: Fastest response is shared with all waiting clients
- **Binary Data Support**: Handles images, files, and all content types
- **Thread-Safe**: Concurrent request handling with proper synchronization
- **Error Handling**: Graceful failure handling when all upstream requests fail
- **Debug Logging**: Detailed console output for monitoring races

## 📊 Performance Benefits

**Traditional Approach:**

```
10 identical requests → 1 upstream request → All clients wait for that single response
```

**Racing Approach:**

```
10 identical requests → 10 upstream requests → All clients get the fastest response
```

**Result**: If upstream latency varies between 1-5 seconds, racing approach will consistently deliver ~1 second responses instead of random 1-5 second responses.

## 🛠️ Setup

### Prerequisites

- Java 17+
- Spring Boot 3.x
- Maven or Gradle

### Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

### Installation

```bash
git clone https://github.com/eslamhawas/racing-proxy-service.git
cd racing-proxy-service
mvn spring-boot:run
```

The service will start on `http://localhost:8080`

## 📖 Usage

### Basic Request

```bash
curl -H "X-Target-Url: https://api.example.com/data" \
     http://localhost:8080/proxy/anything
```

### Racing Example

Send multiple identical requests to see the racing in action:

```bash
# Send 10 identical requests simultaneously
for i in {1..10}; do
  curl -H "X-Target-Url: https://httpbin.org/delay/3" \
       http://localhost:8080/proxy/test &
done
wait
```

### Console Output

```
Sending upstream request #1 for key: GET:https://httpbin.org/delay/3:1:
Sending upstream request #2 for key: GET:https://httpbin.org/delay/3:1:
...
Sending upstream request #10 for key: GET:https://httpbin.org/delay/3:1:
Request #7 completed
Request #3 completed
Request #7 WON the race! Using this response for all 10 waiting clients
Request #1 completed
Request #5 completed
...
```

## 🔧 Configuration

### Request Matching

Requests are considered identical based on:

- HTTP Method (GET, POST, etc.)
- Target URL
- Request body content
- Authorization header
- Content-Type header

### Customizing Key Generation

Modify the `generateRequestKey()` method in `ProxyService.java` to include/exclude headers:

```java
private String generateRequestKey(HttpMethod method, String url, HttpHeaders headers, byte[] body) {
    StringBuilder keyBuilder = new StringBuilder();
    keyBuilder.append(method.name()).append(":");
    keyBuilder.append(url).append(":");
    keyBuilder.append(Arrays.hashCode(body)).append(":");

    // Add custom headers that affect response
    String customHeader = headers.getFirst("X-Custom-Header");
    if (customHeader != null) {
        keyBuilder.append("CUSTOM:").append(customHeader).append(":");
    }

    return keyBuilder.toString();
}
```

## 📡 API Reference

### Proxy Endpoint

- **URL**: `/proxy/**`
- **Methods**: All HTTP methods (GET, POST, PUT, DELETE, etc.)
- **Headers**:
  - `X-Target-Url` (required): The upstream URL to proxy to
  - All other headers are forwarded to upstream
- **Body**: Request body is forwarded as-is (supports binary data)

### Response

- **Status**: Same as upstream response
- **Headers**: Same as upstream response
- **Body**: Same as upstream response (binary data preserved)

### Error Responses

- **400 Bad Request**: Missing `X-Target-Url` header
- **502 Bad Gateway**: All upstream requests failed

## 🔍 Monitoring

### Debug Information

The service logs detailed information about request racing:

- When each upstream request is sent
- When each upstream request completes
- Which request wins the race
- How many clients are served by the winning response

### Metrics to Track

- **Race win distribution**: Which request number typically wins
- **Response time improvement**: Compare to single-request approach
- **Upstream load**: Monitor if racing causes issues for upstream services

## ⚠️ Important Considerations

### Upstream Load

This service multiplies upstream requests by the number of concurrent identical requests. Consider:

- **Rate limiting** on upstream services
- **Cost implications** for paid APIs
- **Upstream server capacity**

### Use Cases

Best suited for:

- **High-latency upstream services** with variable response times
- **Read-only operations** (GET requests)
- **Scenarios where speed > resource efficiency**

Not recommended for:

- **Write operations** (POST/PUT/DELETE) - can cause data duplication
- **Rate-limited APIs** - will exhaust limits faster
- **Cost-sensitive integrations** - increases API usage costs

### Memory Usage

The service stores request groups in memory. For high-volume scenarios, consider:

- Adding request group TTL/cleanup
- Monitoring memory usage
- Implementing request group limits

## 🧪 Testing

### Unit Tests

```bash
mvn test
```

### Load Testing

Use tools like Apache Bench or wrk to test concurrent request handling:

```bash
# Test with Apache Bench
ab -n 100 -c 10 -H "X-Target-Url: https://httpbin.org/delay/2" \
   http://localhost:8080/proxy/loadtest
```

### Racing Verification

Monitor console output to verify that:

1. Multiple upstream requests are sent
2. Only one "WON the race" message appears per request group
3. All clients receive the same response

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the Apache License 2.0 License - see the [LICENSE](LICENSE) file for details.

## 🔗 Related Projects

- [Spring WebFlux](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [WebClient Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/web.html#web.reactive.webclient)

## 📈 Roadmap

- [ ] Add configuration for maximum racing requests per group
- [ ] Implement upstream request timeout configuration
- [ ] Add metrics endpoint for monitoring race statistics
- [ ] Support for request group TTL and cleanup
- [ ] Circuit breaker pattern for failing upstreams
- [ ] WebSocket proxy support
- [ ] Request/response transformation hooks

---

**⚡ Built for speed, designed for scale!**
