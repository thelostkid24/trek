package com.sahyatri.payment.gateway;

import com.sahyatri.common.config.PaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Razorpay REST API over basic auth (key_id:key_secret). Short timeouts; every failure is a GatewayException. */
@Component
public class RazorpayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

    private final PaymentProperties props;
    private final ObjectMapper json;
    private final RestClient http;

    public RazorpayGateway(PaymentProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestClient.Builder builder = RestClient.builder().baseUrl(props.apiBaseUrl()).requestFactory(factory);
        if (props.configured()) {
            builder.defaultHeaders(h -> h.setBasicAuth(props.keyId(), props.keySecret()));
        }
        this.http = builder.build();
    }

    @Override
    public String keyId() {
        requireConfigured();
        return props.keyId();
    }

    @Override
    public String checkoutSigningKey() {
        requireConfigured();
        return props.keySecret();
    }

    @Override
    public String createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        return post("/orders", body).path("id").asString();
    }

    @Override
    public Optional<GatewayPayment> fetchPayment(String paymentId) {
        return Optional.of(RazorpayJson.payment(get("/payments/" + paymentId + "?expand[]=card")));
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(String orderId) {
        return items(get("/orders/" + orderId + "/payments"), RazorpayJson::payment);
    }

    @Override
    public GatewayRefund createRefund(String paymentId, long amountPaise, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("speed", "normal");
        body.put("notes", notes);
        return RazorpayJson.refund(post("/payments/" + paymentId + "/refund", body));
    }

    @Override
    public List<GatewayRefund> fetchPaymentRefunds(String paymentId) {
        return items(get("/payments/" + paymentId + "/refunds"), RazorpayJson::refund);
    }

    private JsonNode get(String path) {
        requireConfigured();
        try {
            return json.readTree(http.get().uri(path).accept(MediaType.APPLICATION_JSON).retrieve().body(String.class));
        } catch (RestClientException | tools.jackson.core.JacksonException e) {
            log.warn("Razorpay GET {} failed: {}", path, e.getMessage());
            throw new GatewayException("GET " + path + ": " + e.getMessage());
        }
    }

    private JsonNode post(String path, Map<String, Object> body) {
        requireConfigured();
        try {
            String response = http.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return json.readTree(response);
        } catch (RestClientException | tools.jackson.core.JacksonException e) {
            log.warn("Razorpay POST {} failed: {}", path, e.getMessage());
            throw new GatewayException("POST " + path + ": " + e.getMessage());
        }
    }

    private static <T> List<T> items(JsonNode collection, Function<JsonNode, T> mapper) {
        List<T> result = new ArrayList<>();
        collection.path("items").forEach(item -> result.add(mapper.apply(item)));
        return result;
    }

    private void requireConfigured() {
        if (!props.configured()) {
            throw new GatewayException("RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET are not set");
        }
    }
}
