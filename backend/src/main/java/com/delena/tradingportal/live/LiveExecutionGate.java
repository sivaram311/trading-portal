package com.delena.tradingportal.live;

import com.delena.tradingportal.config.TradingProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hard gates for P5 micro-live. Fail closed unless every check passes.
 * Default config keeps live disabled; kill switch starts engaged for safety.
 */
@Component
public class LiveExecutionGate {

    private final TradingProperties props;
    private final Environment env;
    /** Starts engaged (blocked). Operator must clear via OPS before any live submit. */
    private final AtomicBoolean killSwitchEngaged = new AtomicBoolean(true);

    public LiveExecutionGate(TradingProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    public boolean isKillSwitchEngaged() {
        return killSwitchEngaged.get();
    }

    public void setKillSwitchEngaged(boolean engaged) {
        killSwitchEngaged.set(engaged);
    }

    public GateVerdict evaluate() {
        TradingProperties.Exec exec = props.getExec();
        if (!exec.isLiveEnabled()) {
            return GateVerdict.deny("LIVE_DISABLED");
        }
        if (killSwitchEngaged.get()) {
            return GateVerdict.deny("KILL_SWITCH");
        }
        if (!profileAllowed(exec.getAllowedProfiles())) {
            return GateVerdict.deny("PROFILE_NOT_ALLOWED");
        }
        String broker = exec.getBroker() == null ? "none" : exec.getBroker().trim().toLowerCase(Locale.ROOT);
        if ("none".equals(broker) || broker.isBlank()) {
            return GateVerdict.deny("BROKER_NONE");
        }
        return GateVerdict.allow(broker);
    }

    private boolean profileAllowed(String allowedCsv) {
        if (allowedCsv == null || allowedCsv.isBlank()) {
            return false;
        }
        var allowed = Arrays.stream(allowedCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        return Arrays.stream(env.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(allowed::contains);
    }

    public record GateVerdict(boolean ok, String denyReason, String broker) {
        static GateVerdict allow(String broker) {
            return new GateVerdict(true, null, broker);
        }

        static GateVerdict deny(String reason) {
            return new GateVerdict(false, reason, null);
        }
    }
}
