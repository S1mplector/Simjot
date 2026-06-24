/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.io;

import main.infrastructure.ffi.MacNativeBridge;

/**
 * macOS no-idle-sleep assertion guard for long-running operations.
 */
public final class MacPowerAssertion implements AutoCloseable {
    private final long token;
    private boolean released;

    private MacPowerAssertion(long token) {
        this.token = token;
    }

    public static MacPowerAssertion acquire(String reason) {
        return new MacPowerAssertion(MacNativeBridge.beginPowerAssertion(reason));
    }

    public boolean isActive() {
        return token > 0 && !released;
    }

    @Override
    public synchronized void close() {
        if (released) return;
        released = true;
        if (token > 0) {
            MacNativeBridge.endPowerAssertion(token);
        }
    }
}
