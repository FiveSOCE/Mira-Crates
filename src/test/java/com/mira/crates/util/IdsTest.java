package com.mira.crates.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdsTest {
    @Test
    void normalizesFriendlyIds() {
        assertEquals("vote_crate", Ids.normalize(" Vote Crate "));
        assertTrue(Ids.valid("vote_crate"));
        assertFalse(Ids.valid("no/slashes"));
    }
}
