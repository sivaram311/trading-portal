package com.delena.tradingportal.config;

import com.delena.tradingportal.engine.style.TradingStyle;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Typed view of the {@code trading.*} configuration namespace. */
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    private final Exec exec = new Exec();
    private final Confluence confluence = new Confluence();
    private final Seed seed = new Seed();
    private final Security security = new Security();
    private final Paper paper = new Paper();
    private final News news = new News();
    private final Ops ops = new Ops();
    /** Active trading style preset (SCALP / DAY / POSITIONAL). Default DAY. */
    private TradingStyle style = TradingStyle.DAY;

    public Exec getExec() {
        return exec;
    }

    public Confluence getConfluence() {
        return confluence;
    }

    public Seed getSeed() {
        return seed;
    }

    public Security getSecurity() {
        return security;
    }

    public Paper getPaper() {
        return paper;
    }

    public News getNews() {
        return news;
    }

    public Ops getOps() {
        return ops;
    }

    public TradingStyle getStyle() {
        return style;
    }

    public void setStyle(TradingStyle style) {
        this.style = style != null ? style : TradingStyle.DAY;
    }

    /** Live-execution guard. There is no live adapter in this slice; this must stay false. */
    public static class Exec {
        private boolean liveEnabled = false;

        public boolean isLiveEnabled() {
            return liveEnabled;
        }

        public void setLiveEnabled(boolean liveEnabled) {
            this.liveEnabled = liveEnabled;
        }
    }

    public static class Confluence {
        private String weightsVersion = "v1";

        public String getWeightsVersion() {
            return weightsVersion;
        }

        public void setWeightsVersion(String weightsVersion) {
            this.weightsVersion = weightsVersion;
        }
    }

    public static class Seed {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Security wiring. Default path is real CSS JWKS validation. dev-bypass is an explicit,
     * default-off escape hatch for local engine testing when CSS is unreachable.
     */
    public static class Security {
        private boolean devBypass = false;
        private String devToken = "dev-operator-token";
        private String clientId = "trading-portal";
        private String jwkSetUri = "http://127.0.0.1:9000/.well-known/jwks.json";

        public boolean isDevBypass() {
            return devBypass;
        }

        public void setDevBypass(boolean devBypass) {
            this.devBypass = devBypass;
        }

        public String getDevToken() {
            return devToken;
        }

        public void setDevToken(String devToken) {
            this.devToken = devToken;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }
    }

    /** Paper-trading operator policy flags. */
    public static class Paper {
        /** When true, ALERTED A+ decisions auto-confirm into PAPER_OPEN (default OFF). */
        private boolean autoConfirmAPlus = false;

        public boolean isAutoConfirmAPlus() {
            return autoConfirmAPlus;
        }

        public void setAutoConfirmAPlus(boolean autoConfirmAPlus) {
            this.autoConfirmAPlus = autoConfirmAPlus;
        }
    }

    /**
     * Configured news blackout windows. When {@code asof} falls inside any window,
     * confluence receives a NEWS_VETO (fail-closed).
     */
    public static class News {
        private List<BlackoutWindow> blackouts = new ArrayList<>();

        public List<BlackoutWindow> getBlackouts() {
            return blackouts;
        }

        public void setBlackouts(List<BlackoutWindow> blackouts) {
            this.blackouts = blackouts != null ? blackouts : new ArrayList<>();
        }
    }

    /** Observability / fleet probes (paper-only; no broker). */
    public static class Ops {
        /** Optional Python ingest health URL (e.g. http://127.0.0.1:3342/health). Empty = skip probe. */
        private String ingestHealthUrl = "";
        /** OHLC freshness warn threshold in seconds (default 2h). */
        private long ohlcStaleAfterSeconds = 7200;

        public String getIngestHealthUrl() {
            return ingestHealthUrl;
        }

        public void setIngestHealthUrl(String ingestHealthUrl) {
            this.ingestHealthUrl = ingestHealthUrl != null ? ingestHealthUrl : "";
        }

        public long getOhlcStaleAfterSeconds() {
            return ohlcStaleAfterSeconds;
        }

        public void setOhlcStaleAfterSeconds(long ohlcStaleAfterSeconds) {
            this.ohlcStaleAfterSeconds = ohlcStaleAfterSeconds;
        }
    }

    /**
     * A blackout window: either absolute UTC instants ({@code start}/{@code end}) or a recurring
     * NY-local time band on an optional weekday (null weekday = every day).
     */
    public static class BlackoutWindow {
        private Instant start;
        private Instant end;
        private DayOfWeek weekday;
        private LocalTime nyStart;
        private LocalTime nyEnd;

        public Instant getStart() {
            return start;
        }

        public void setStart(Instant start) {
            this.start = start;
        }

        public Instant getEnd() {
            return end;
        }

        public void setEnd(Instant end) {
            this.end = end;
        }

        public DayOfWeek getWeekday() {
            return weekday;
        }

        public void setWeekday(DayOfWeek weekday) {
            this.weekday = weekday;
        }

        public LocalTime getNyStart() {
            return nyStart;
        }

        public void setNyStart(LocalTime nyStart) {
            this.nyStart = nyStart;
        }

        public LocalTime getNyEnd() {
            return nyEnd;
        }

        public void setNyEnd(LocalTime nyEnd) {
            this.nyEnd = nyEnd;
        }
    }
}
