package com.delena.tradingportal.live;

import java.time.Instant;

/** Port to a micro-lot broker. Implementations must refuse when not fully configured. */
public interface LiveBroker {

    String name();

    SubmitResult submit(LiveOrder order);

    record LiveOrder(
            String decisionId,
            String symbol,
            String direction,
            double lots,
            double entryRef,
            double stop,
            String mode,
            String grade
    ) {
    }

    record SubmitResult(
            boolean ok,
            String brokerOrderId,
            double fillPrice,
            String message,
            Instant ts
    ) {
        static SubmitResult fail(String message) {
            return new SubmitResult(false, null, 0, message, Instant.now());
        }

        static SubmitResult ok(String id, double fill, String message) {
            return new SubmitResult(true, id, fill, message, Instant.now());
        }
    }
}
