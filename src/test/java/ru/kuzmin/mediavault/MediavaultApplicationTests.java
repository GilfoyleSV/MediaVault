package ru.kuzmin.mediavault;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.kuzmin.mediavault.service.MediaCleanup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MediavaultApplicationTests {
    private static final String MINIO_USER = "integration-user";
    private static final String MINIO_PASSWORD = "integration-password";

    @Container
    static final PostgreSQLContainer db = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>("pgsty/silo:RELEASE.2026-10-26T00-00-00Z")
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("spring.datasource.username", db::getUsername);
        registry.add("spring.datasource.password", db::getPassword);
        registry.add("minio.endpoint", () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        registry.add("minio.access-key", () -> MINIO_USER);
        registry.add("minio.secret-key", () -> MINIO_PASSWORD);
        registry.add("security.jwt.secret", () -> "integration-test-signing-key-32-bytes");
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MediaCleanup cleanup;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void uploadsStreamsAndDeletesOnlyForOwner() throws Exception {
        String catalog = "/api/v1/media";
        assertThat(api("GET", catalog, null, null).statusCode()).isEqualTo(401);
        assertThat(api("GET", "/actuator/health/readiness", null, null).statusCode()).isEqualTo(200);
        assertThat(api("GET", "/actuator/prometheus", null, null).body()).contains("jvm_memory_used_bytes");
        assertThat(api("GET", "/v3/api-docs", null, null).body()).contains("bearerAuth");

        String owner = registerAndLogin("first_owner");
        String other = registerAndLogin("second_owner");
        byte[] content = "media-demo-bytes".getBytes(StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        String metadata = """
                {"filename":"demo.mp3","mimeType":"audio/mpeg","sizeBytes":%d,"checksumSha256":"%s"}
                """.formatted(content.length, checksum);

        var created = api("POST", catalog + "/uploads", owner, metadata);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode ticket = json.readTree(created.body());
        String id = ticket.path("mediaId").asString();
        String asset = catalog + "/" + id;
        String complete = asset + "/complete";
        String access = asset + "/access-url";

        assertThat(api("GET", asset, other, null).statusCode()).isEqualTo(404);
        assertThat(api("POST", complete, other, null).statusCode()).isEqualTo(404);
        assertThat(api("DELETE", asset, other, null).statusCode()).isEqualTo(404);
        assertThat(api("GET", catalog, other, null).body()).contains("\"items\":[]");
        assertThat(api("GET", catalog + "?size=0", owner, null).statusCode()).isEqualTo(400);
        assertThat(api("POST", complete, owner, null).statusCode()).isEqualTo(409);

        var put = HttpRequest.newBuilder(URI.create(ticket.path("uploadUrl").asString()))
                .timeout(Duration.ofSeconds(10)).PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        ticket.path("uploadHeaders").properties().forEach(header -> {
            if (!header.getKey().equalsIgnoreCase("content-length")) {
                put.header(header.getKey(), header.getValue().asString());
            }
        });
        var stored = send(put.build());
        assertThat(stored.statusCode()).as(stored.body()).isEqualTo(200);
        assertThat(send(put.build()).statusCode()).isEqualTo(412);

        var ready = api("POST", complete, owner, null);
        assertThat(ready.statusCode()).as(ready.body()).isEqualTo(200);
        assertThat(ready.body()).contains("\"status\":\"READY\"");
        assertThat(api("POST", complete, owner, null).body()).isEqualTo(ready.body());
        assertThat(api("GET", catalog, owner, null).body()).contains(id);
        assertThat(api("GET", access, other, null).statusCode()).isEqualTo(404);

        var authorized = api("GET", access, owner, null);
        assertThat(authorized.statusCode()).isEqualTo(200);
        String url = json.readTree(authorized.body()).path("downloadUrl").asString();
        var range = send(HttpRequest.newBuilder(URI.create(url)).header("Range", "bytes=0-4").build());
        assertThat(range.statusCode()).isEqualTo(206);
        assertThat(range.body()).isEqualTo("media");

        assertThat(api("DELETE", asset, owner, null).statusCode()).isEqualTo(204);
        assertThat(api("DELETE", asset, owner, null).statusCode()).isEqualTo(204);
        assertThat(api("GET", access, owner, null).statusCode()).isEqualTo(404);
        jdbc.sql("UPDATE media_asset SET cleanup_at = now() WHERE id = ?").param(UUID.fromString(id)).update();
        cleanup.runBatch();
        assertThat(send(HttpRequest.newBuilder(URI.create(url)).build()).statusCode()).isEqualTo(404);
    }

    private String registerAndLogin(String username) throws Exception {
        String credentials = """
                {"username":"%s","password":"test-password-2026"}
                """.formatted(username);
        var registration = api("POST", "/api/v1/auth/register", null, credentials);
        assertThat(registration.statusCode()).as(registration.body()).isEqualTo(201);
        var login = api("POST", "/api/v1/auth/token", null, credentials);
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("accessToken").asString();
    }

    private HttpResponse<String> api(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return send(request.build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
