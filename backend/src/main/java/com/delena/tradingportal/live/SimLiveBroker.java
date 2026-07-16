package com.delena.tradingportal.live;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * DEV-safe broker: records a simulated micro fill without MT5 {@code order_send}.
 * Use {@code trading.exec.broker=sim} only on allowed profiles.
 */
@Component
public class SimLiveBroker implements LiveBroker {

    @Override
    public String name() {
        return "sim";
    }

    @Override
    public SubmitResult submit(LiveOrder order) {
        if (order == null || order.lots() <= 0) {
            return SubmitResult.fail("INVALID_LOTS");
        }
        String id = "SIM-" + UUID.randomUUID().toString().substring(0, 8);
        return SubmitResult.ok(id, order.entryRef(), "sim_fill");
    }
}
