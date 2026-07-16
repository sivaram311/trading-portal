package com.delena.tradingportal.live;

import com.delena.tradingportal.config.TradingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional MT5 bridge via HTTP. Refuses unless {@code trading.exec.mt5-bridge-url} is set
 * and {@code trading.exec.mt5-allow-send=true}. Never invents a local order_send.
 */
@Component
public class Mt5BridgeLiveBroker implements LiveBroker {

    private final TradingProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public Mt5BridgeLiveBroker(TradingProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "mt5";
    }

    @Override
    public SubmitResult submit(LiveOrder order) {
        TradingProperties.Exec exec = props.getExec();
        String url = exec.getMt5BridgeUrl();
        if (url == null || url.isBlank()) {
            return SubmitResult.fail("MT5_BRIDGE_URL_MISSING");
        }
        if (!exec.isMt5AllowSend()) {
            return SubmitResult.fail("MT5_ALLOW_SEND_FALSE");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("symbol", order.symbol());
            body.put("direction", order.direction());
            body.put("lots", order.lots());
            body.put("entry_ref", order.entryRef());
            body.put("stop", order.stop());
            body.put("decision_id", order.decisionId());
            String json = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return SubmitResult.fail("MT5_BRIDGE_HTTP_" + res.statusCode());
            }
            JsonNode node = mapper.readTree(res.body() == null ? "{}" : res.body());
            boolean ok = node.path("ok").asBoolean(false);
            if (!ok) {
                return SubmitResult.fail(node.path("message").asText("MT5_BRIDGE_REJECT"));
            }
            String id = node.path("broker_order_id").asText("MT5-UNKNOWN");
            double fill = node.path("fill_price").asDouble(order.entryRef());
            return SubmitResult.ok(id, fill, node.path("message").asText("mt5_bridge_ok"));
        } catch (Exception e) {
            return SubmitResult.fail("MT5_BRIDGE_ERROR:" + e.getClass().getSimpleName());
        }
    }
}
