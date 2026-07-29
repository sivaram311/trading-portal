package com.delena.tradingportal.web;

import com.delena.tradingportal.config.TradingProperties;
import com.delena.tradingportal.engine.style.StyleRegistry;
import com.delena.tradingportal.engine.style.TradingStyle;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleControllerTest {

    private final TradingProperties props = new TradingProperties();
    private final StyleRegistry registry = new StyleRegistry();
    private final StyleController controller = new StyleController(props, registry);

    @Test
    void getReflectsConfiguredDefaultStyle() {
        StyleController.StyleStatusResponse res = controller.get();

        assertEquals("DAY", res.style());
        assertEquals(2, res.maxLegs());
        assertEquals(0.625, res.riskPct());
        assertEquals(3, res.profiles().size());
        assertTrue(res.profiles().stream().anyMatch(p -> "SCALP".equals(p.style())));
        assertTrue(res.profiles().stream().anyMatch(p -> "POSITIONAL".equals(p.style())));
    }

    @Test
    void putUpdatesTradingPropertiesAndReturnsNewStyle() {
        StyleController.StyleStatusResponse res = controller.put(new StyleController.StyleUpdateRequest("scalp"));

        assertEquals("SCALP", res.style());
        assertEquals(1, res.maxLegs());
        assertEquals(TradingStyle.SCALP, props.getStyle());
    }

    @Test
    void putIsPickedUpByAnyReaderOfTradingPropertiesWithoutRestart() {
        controller.put(new StyleController.StyleUpdateRequest("POSITIONAL"));

        // Simulates a separate reader (e.g. PipelineService) that re-reads props.getStyle() per call.
        assertEquals(TradingStyle.POSITIONAL, props.getStyle());
        assertEquals(3, registry.get(props.getStyle()).maxLegs());
    }

    @Test
    void putRejectsUnknownStyle() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.put(new StyleController.StyleUpdateRequest("SWING")));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void putRejectsMissingBody() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.put(null));
        assertEquals(400, ex.getStatusCode().value());
    }
}
