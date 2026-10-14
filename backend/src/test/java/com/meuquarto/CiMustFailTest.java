package com.meuquarto;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/** Throwaway: proves CI turns red on a failing test. Never merged. */
class CiMustFailTest {
    @Test
    void failsOnPurpose() {
        fail("intentional failure to check CI");
    }
}
