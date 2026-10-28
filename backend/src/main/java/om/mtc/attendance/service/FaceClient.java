package om.mtc.attendance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import om.mtc.attendance.config.AppProperties;
import om.mtc.attendance.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** HTTP client for the stateless face service (POST /embed). */
@Component
public class FaceClient {
    private static final Logger log = LoggerFactory.getLogger(FaceClient.class);

    private static final Map<String, String> MESSAGES = Map.of(
            "UNSUPPORTED_FORMAT", "صيغة الصورة غير مدعومة",
            "CORRUPT_IMAGE", "الصورة تالفة أو لا يمكن قراءتها",
            "IMAGE_TOO_LARGE", "دقة الصورة كبيرة جداً",
            "IMAGE_TOO_SMALL", "الصورة صغيرة جداً",
            "FILE_TOO_LARGE", "حجم الصورة أكبر من المسموح",
            "EMPTY_FILE", "الملف فارغ");

    public record DetectedFace(int[] bbox, float detScore, int width, int height, float[] embedding) {
    }

    public record EmbedResponse(String model, int imageWidth, int imageHeight, long elapsedMs,
                                List<DetectedFace> faces) {
    }

    private final RestClient client;
    private final ObjectMapper om;

    public FaceClient(AppProperties props, ObjectMapper om, RestClient.Builder builder) {
        this.om = om;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(5, props.faceService().timeoutSeconds()));
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(timeout);
        this.client = builder.clone()
                .baseUrl(props.faceService().url())
                .defaultHeader("X-API-Key", props.faceService().apiKey())
                .requestFactory(rf)
                .build();
    }

    public EmbedResponse embed(byte[] image, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String name = filename == null || filename.isBlank() ? "image.jpg" : filename;
        body.add("image", new HttpEntity<>(new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return name;
            }
        }, partHeaders));

        try {
            EmbedResponse r = client.post()
                    .uri("/embed")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(EmbedResponse.class);
            if (r == null || r.faces() == null) {
                throw unavailable();
            }
            return r;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 422) {
                String code = extractCode(e.getResponseBodyAsString());
                throw ApiException.unprocessable(code, MESSAGES.getOrDefault(code, "الصورة غير صالحة"));
            }
            log.error("Face service returned HTTP {}", e.getStatusCode().value());
            throw unavailable();
        } catch (ResourceAccessException e) {
            log.error("Face service unreachable: {}", e.getMessage());
            throw unavailable();
        }
    }

    private String extractCode(String json) {
        try {
            JsonNode n = om.readTree(json);
            JsonNode code = n.path("detail").path("code");
            return code.isTextual() ? code.asText() : "INVALID_IMAGE";
        } catch (Exception ex) {
            return "INVALID_IMAGE";
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FACE_SERVICE_UNAVAILABLE",
                "خدمة التعرف على الوجوه غير متاحة حالياً، يمكنك إكمال التحضير يدوياً");
    }
}
