/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.core.sim.persona;

/**
 * Describes Sim's tone and proactivity.
 */
public final class SimPersonality {
    public enum Type { GENTLE, NEUTRAL, PROACTIVE }

    private final Type type;

    public SimPersonality(String name){
        Type t;
        try { t = Type.valueOf(name.toUpperCase()); } catch (Exception e) { t = Type.GENTLE; }
        this.type = t;
    }

    public Type getType(){ return type; }
}
