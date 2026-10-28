package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

final class TestJwt {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int PARTS_IN_COMPACT_JWS = 3;
    private static final int HEADER_INDEX = 0;
    private static final int PAYLOAD_INDEX = 1;
    private static final int SIGNATURE_INDEX = 2;

    private TestJwt() {}

    static Map<String, Object> header(String token) {
        return decodeSection(split(token)[HEADER_INDEX]);
    }

    static Map<String, Object> payload(String token) {
        return decodeSection(split(token)[PAYLOAD_INDEX]);
    }

    static boolean hasValidHmacSignature(String token, String secret) {
        String[] parts = split(token);
        byte[] expected = hmacSha256(signingInput(parts), secret);
        byte[] actual = Base64.getUrlDecoder().decode(parts[SIGNATURE_INDEX]);
        return MessageDigest.isEqual(expected, actual);
    }

    static String withPayload(String token, Map<String, Object> newPayload) {
        String[] parts = split(token);
        return parts[HEADER_INDEX] + "." + encodeSection(newPayload) + "." + parts[SIGNATURE_INDEX];
    }

    private static String[] split(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != PARTS_IN_COMPACT_JWS) {
            throw new IllegalArgumentException("Token must have three parts");
        }
        return parts;
    }

    private static String signingInput(String[] parts) {
        return parts[HEADER_INDEX] + "." + parts[PAYLOAD_INDEX];
    }

    private static Map<String, Object> decodeSection(String section) {
        try {
            return MAPPER.readValue(Base64.getUrlDecoder().decode(section), MAP_TYPE);
        } catch (IOException e) {
            throw new IllegalArgumentException("Token section is not valid JSON", e);
        }
    }

    private static String encodeSection(Map<String, Object> section) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MAPPER.writeValueAsBytes(section));
        } catch (IOException e) {
            throw new IllegalArgumentException("Token section cannot be serialized", e);
        }
    }

    private static byte[] hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
