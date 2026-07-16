package com.delena.tradingportal.live;

import com.delena.tradingportal.config.TradingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveExecutionGateTest {

    private TradingProperties props;
    private MockEnvironment env;
    private LiveExecutionGate gate;

    @BeforeEach
    void setUp() {
        props = new TradingProperties();
        env = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        env.setActiveProfiles("dev");
        gate = new LiveExecutionGate(props, env);
    }

    @Test
    void defaultsDenyLiveDisabled() {
        var v = gate.evaluate();
        assertFalse(v.ok());
        assertEquals("LIVE_DISABLED", v.denyReason());
    }

    @Test
    void killSwitchBlocksEvenWhenArmed() {
        props.getExec().setLiveEnabled(true);
        props.getExec().setBroker("sim");
        props.getExec().setAllowedProfiles("dev");
        assertTrue(gate.isKillSwitchEngaged());
        assertEquals("KILL_SWITCH", gate.evaluate().denyReason());
    }

    @Test
    void allowsWhenArmedOnDevWithSim() {
        props.getExec().setLiveEnabled(true);
        props.getExec().setBroker("sim");
        props.getExec().setAllowedProfiles("dev");
        gate.setKillSwitchEngaged(false);
        var v = gate.evaluate();
        assertTrue(v.ok());
        assertEquals("sim", v.broker());
    }

    @Test
    void rejectsNonAllowedProfile() {
        props.getExec().setLiveEnabled(true);
        props.getExec().setBroker("sim");
        props.getExec().setAllowedProfiles("dev");
        gate.setKillSwitchEngaged(false);
        env.setActiveProfiles("prod");
        assertEquals("PROFILE_NOT_ALLOWED", gate.evaluate().denyReason());
    }

    @Test
    void rejectsBrokerNone() {
        props.getExec().setLiveEnabled(true);
        props.getExec().setBroker("none");
        props.getExec().setAllowedProfiles("dev");
        gate.setKillSwitchEngaged(false);
        assertEquals("BROKER_NONE", gate.evaluate().denyReason());
    }
}
