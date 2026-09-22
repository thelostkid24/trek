package com.sahyatri.payment.gateway;

import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps Razorpay JSON entities (API responses and webhook payloads share the shape). */
public final class RazorpayJson {

    private RazorpayJson() {
    }

    public static GatewayPayment payment(JsonNode p) {
        String method = text(p, "method");
        Map<String, String> detail = new LinkedHashMap<>();
        switch (method == null ? "" : method) {
            case "card" -> {
                JsonNode card = p.path("card");
                putIfPresent(detail, "card_network", text(card, "network"));
                putIfPresent(detail, "card_last4", text(card, "last4"));
            }
            case "netbanking" -> putIfPresent(detail, "bank", text(p, "bank"));
            case "wallet" -> putIfPresent(detail, "wallet", text(p, "wallet"));
            case "upi" -> {
                String vpa = text(p, "vpa");
                if (vpa == null) {
                    vpa = text(p.path("upi"), "vpa");
                }
                putIfPresent(detail, "vpa", maskVpa(vpa));
            }
            default -> {
            }
        }
        return new GatewayPayment(text(p, "id"), text(p, "order_id"), text(p, "status"),
                p.path("amount").asLong(), method, detail, text(p, "error_code"), text(p, "error_description"));
    }

    public static GatewayRefund refund(JsonNode r) {
        Map<String, String> notes = new LinkedHashMap<>();
        JsonNode notesNode = r.path("notes");
        if (notesNode.isObject()) {
            notesNode.properties().forEach(e -> notes.put(e.getKey(), e.getValue().asString()));
        }
        return new GatewayRefund(text(r, "id"), text(r, "payment_id"), r.path("amount").asLong(), text(r, "status"),
                notes);
    }

    /** "asha.rao@okhdfcbank" → "as***@okhdfcbank". */
    static String maskVpa(String vpa) {
        if (vpa == null) {
            return null;
        }
        int at = vpa.indexOf('@');
        if (at < 0) {
            return "***";
        }
        return vpa.substring(0, Math.min(2, at)) + "***" + vpa.substring(at);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
