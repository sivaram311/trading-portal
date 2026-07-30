package com.delena.tradingportal.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperDecisionPolicyTest {

    @Test
    void modeRConfirmableWhenAlignedGradeA() {
        assertTrue(PaperDecisionPolicy.isConfirmable("confirm", "A", "R", true));
        assertTrue(PaperDecisionPolicy.isConfirmable("confirm", "A+", "C", true));
    }

    @Test
    void modeTNeverConfirmableEvenIfAutomationConfirm() {
        // Theory: Mode T is watch-only; even a mis-labeled confirm must not paper-open.
        assertFalse(PaperDecisionPolicy.isConfirmable("confirm", "A", "T", true));
    }

    @Test
    void denyNoneAndFailClosed() {
        assertFalse(PaperDecisionPolicy.isConfirmable("deny", "A", "R", true));
        assertFalse(PaperDecisionPolicy.isConfirmable("confirm", "A", "NONE", true));
        assertFalse(PaperDecisionPolicy.isConfirmable("confirm", "F", "R", true));
        assertFalse(PaperDecisionPolicy.isConfirmable("confirm", "A", "R", false));
    }
}
