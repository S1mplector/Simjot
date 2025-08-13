package main.core.sim;

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
