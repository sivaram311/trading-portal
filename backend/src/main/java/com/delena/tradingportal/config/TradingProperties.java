package com.delena.tradingportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code trading.*} configuration namespace. */
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    private final Exec exec = new Exec();
    private final Confluence confluence = new Confluence();
    private final Seed seed = new Seed();
    private final Security security = new Security();

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
}
