package io.getbit.gim.webrtc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * LiveKitSfuAdapter.java
 *
 * SfuAdapter 的 LiveKit 参考实现
 * - 媒体房间管理：调用 LiveKit Server API（Twirp 协议 /twirp/livekit.RoomService/*）
 * - 接入凭证：签发 LiveKit JWT access token（HS256）
 *
 * 仅依赖 Gson 与 JDK HttpClient，不引入额外第三方库。
 * 配置示例（gim.rtc-group-call.sfu.*）：
 *   provider=livekit, host=http://127.0.0.1:7880,
 *   api-key=devkey, api-secret=secret, ws-url=wss://127.0.0.1:7880
 *
 * @author gogym
 */
@Slf4j
public class LiveKitSfuAdapter implements SfuAdapter {

    private static final String CREATE_ROOM_PATH = "/twirp/livekit.RoomService/CreateRoom";
    private static final String DELETE_ROOM_PATH = "/twirp/livekit.RoomService/DeleteRoom";

    private static final Gson GSON = new Gson();

    private final String host;
    private final String wsUrl;
    private final String apiKey;
    private final String apiSecret;
    private final int tokenTtlSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public LiveKitSfuAdapter(LiveKitConfig config) {
        this(config.getHost(), config.getApiKey(), config.getApiSecret(),
                config.getWsUrl(), config.getTokenTtlSeconds());
    }

    public LiveKitSfuAdapter(String host, String apiKey, String apiSecret, String wsUrl, int tokenTtlSeconds) {
        this.host = host != null ? host.replaceAll("/$", "") : "http://127.0.0.1:7880";
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.wsUrl = wsUrl != null && !wsUrl.isEmpty() ? wsUrl : this.host.replace("http", "ws");
        this.tokenTtlSeconds = tokenTtlSeconds > 0 ? tokenTtlSeconds : 3600;
    }

    @Override
    public void createRoom(String roomId) {
        JsonObject body = new JsonObject();
        body.addProperty("room", roomId);
        callServerApi(CREATE_ROOM_PATH, body.toString());
        log.info("LiveKit 媒体房间已创建: roomId={}", roomId);
    }

    @Override
    public void destroyRoom(String roomId) {
        JsonObject body = new JsonObject();
        body.addProperty("room", roomId);
        callServerApi(DELETE_ROOM_PATH, body.toString());
        log.info("LiveKit 媒体房间已销毁: roomId={}", roomId);
    }

    @Override
    public SfuToken issueToken(String roomId, String userId) {
        JsonObject video = new JsonObject();
        video.addProperty("roomJoin", true);
        video.addProperty("room", roomId);
        video.addProperty("canPublish", true);
        video.addProperty("canSubscribe", true);
        video.addProperty("canPublishData", true);

        String token = buildJwt(userId, video);
        return new SfuToken(token, wsUrl);
    }

    // ====================== 内部实现 ======================

    /**
     * 调用 LiveKit Server API（服务端管理 token：roomCreate/roomDelete 权限）
     */
    private void callServerApi(String path, String jsonBody) {
        try {
            JsonObject video = new JsonObject();
            video.addProperty("roomCreate", true);
            video.addProperty("roomDelete", true);
            video.addProperty("roomList", true);
            String serviceToken = buildJwt(apiKey, video);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(host + path))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + serviceToken)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.error("LiveKit Server API 调用失败: path={}, status={}, body={}", path, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            // LiveKit 首次入会时会自动建房，房间管理调用失败不阻断信令流程
            log.error("LiveKit Server API 调用异常: path={}", path, e);
        }
    }

    /**
     * 签发 LiveKit JWT access token（HS256）
     * claims: iss=apiKey, sub=identity, jti, nbf, exp, video grants
     */
    private String buildJwt(String identity, JsonObject videoGrants) {
        long now = System.currentTimeMillis() / 1000;

        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        header.addProperty("typ", "JWT");

        JsonObject payload = new JsonObject();
        payload.addProperty("iss", apiKey);
        payload.addProperty("sub", identity);
        payload.addProperty("jti", UUID.randomUUID().toString());
        payload.addProperty("nbf", now - 5);
        payload.addProperty("exp", now + tokenTtlSeconds);
        payload.add("video", videoGrants);

        String headerPart = base64Url(GSON.toJson(header).getBytes(StandardCharsets.UTF_8));
        String payloadPart = base64Url(GSON.toJson(payload).getBytes(StandardCharsets.UTF_8));
        String signingInput = headerPart + "." + payloadPart;
        String signature = base64Url(hmacSha256(signingInput));

        return signingInput + "." + signature;
    }

    private byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ==================== 配置类 ====================

    /**
     * LiveKit 连接配置
     */
    @Getter
    @Setter
    public static class LiveKitConfig {
        /**
         * LiveKit Server HTTP 地址（服务端 API 调用），如 http://127.0.0.1:7880
         */
        private String host = "http://127.0.0.1:7880";

        private String apiKey = "devkey";

        private String apiSecret = "secret";

        /**
         * 下发给客户端的 SFU 连接地址，如 wss://im.example.com:7880
         */
        private String wsUrl = "ws://127.0.0.1:7880";

        private int tokenTtlSeconds = 3600;
    }
}
