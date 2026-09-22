package com.sahyatri.auth.sms;

import com.sahyatri.common.config.SmsProperties;
import com.sahyatri.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OTP over MSG91's Flow API (`app.sms.provider=msg91`). Indian SMS needs TRAI DLT registration: the sender id and
 * the OTP template must be approved before MSG91 will deliver.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "msg91")
public class Msg91SmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(Msg91SmsSender.class);

    private final RestClient http;
    private final String templateId;

    public Msg91SmsSender(SmsProperties props) {
        if (isBlank(props.msg91AuthKey()) || isBlank(props.msg91OtpTemplateId())) {
            throw new IllegalStateException("MSG91_AUTH_KEY and MSG91_OTP_TEMPLATE_ID must be set when SMS_PROVIDER=msg91");
        }
        this.templateId = props.msg91OtpTemplateId();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder()
                .baseUrl("https://control.msg91.com/api/v5")
                .requestFactory(factory)
                .defaultHeader("authkey", props.msg91AuthKey())
                .build();
    }

    @Override
    public void sendOtp(String phone, String code) {
        // MSG91 wants the number without "+": 91XXXXXXXXXX.
        Map<String, Object> body = Map.of(
                "template_id", templateId,
                "short_url", "0",
                "recipients", List.of(Map.of("mobiles", phone.replace("+", ""), "otp", code)));
        try {
            http.post().uri("/flow").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.error("MSG91 could not send an OTP", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_UNAVAILABLE",
                    "We couldn't send the code right now. Try again, or sign in another way.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
