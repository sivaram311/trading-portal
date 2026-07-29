package com.delena.tradingportal.web;

import com.delena.tradingportal.config.TradingProperties;
import com.delena.tradingportal.engine.style.StyleProfile;
import com.delena.tradingportal.engine.style.StyleRegistry;
import com.delena.tradingportal.engine.style.TradingStyle;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Runtime trading-style selector (paper-only; §8 style profiles). Reading/writing
 * {@link TradingProperties#getStyle()} takes effect on the very next
 * {@code PipelineService.recompute()} tick — the pipeline re-reads {@code props.getStyle()} on
 * every run rather than caching it at startup, so no restart is required. See
 * {@link TradingProperties} for the thread-safety notes on the underlying field.
 */
@RestController
@RequestMapping("/api/style")
public class StyleController {

    private final TradingProperties props;
    private final StyleRegistry styleRegistry;

    public StyleController(TradingProperties props, StyleRegistry styleRegistry) {
        this.props = props;
        this.styleRegistry = styleRegistry;
    }

    @GetMapping
    public StyleStatusResponse get() {
        return statusOf(props.getStyle());
    }

    @PutMapping
    public StyleStatusResponse put(@RequestBody StyleUpdateRequest body) {
        TradingStyle next = parse(body == null ? null : body.style());
        props.setStyle(next);
        return statusOf(next);
    }

    private StyleStatusResponse statusOf(TradingStyle active) {
        StyleProfile profile = styleRegistry.get(active);
        return new StyleStatusResponse(active.name(), profile.maxLegs(), profile.riskPct(), profiles());
    }

    private List<ProfileSummary> profiles() {
        return List.of(summarize(TradingStyle.SCALP), summarize(TradingStyle.DAY), summarize(TradingStyle.POSITIONAL));
    }

    private ProfileSummary summarize(TradingStyle style) {
        StyleProfile p = styleRegistry.get(style);
        return new ProfileSummary(style.name(), p.riskPct(), p.maxLegs(), p.maxSpreadPts(),
                p.maxHold().toString(), p.requireKillzone(), p.beTriggerR(), p.scaleOutPct());
    }

    private TradingStyle parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "style is required (SCALP|DAY|POSITIONAL)");
        }
        try {
            return TradingStyle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "style must be SCALP|DAY|POSITIONAL");
        }
    }

    public record StyleUpdateRequest(String style) {
    }

    /** Key fields per style preset (full ICT/Gann thresholds stay internal to the engines). */
    public record ProfileSummary(
            String style,
            double riskPct,
            int maxLegs,
            double maxSpreadPts,
            String maxHold,
            boolean requireKillzone,
            double beTriggerR,
            double scaleOutPct) {
    }

    public record StyleStatusResponse(
            String style,
            int maxLegs,
            double riskPct,
            List<ProfileSummary> profiles) {
    }
}
