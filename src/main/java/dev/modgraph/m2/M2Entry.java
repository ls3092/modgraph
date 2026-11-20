package dev.modgraph.m2;

/**
 * M2 first cut: pre-wrapper entry point.
 *
 * This class is the project's own entry point. It prints one recognizable
 * marker line (project name + build hash) to stdout, then hands control over
 * to the official Fabric loader by reflective invocation of its string-named
 * main method.
 *
 * Compile-time dependency policy: no MC / Fabric / Mixin / ASM class is
 * referenced. The Fabric entry class is named only as a String literal, so
 * javac needs no Fabric classes on the compile classpath.
 */
public final class M2Entry {

    /** Recognizable marker: project name + build hash. Grep for MODGRAPH_M2_ENTRY in the log. */
    private static final String MARKER =
            "MODGRAPH_M2_ENTRY project=modgraph build=3be75d9c34fab056ffcc15afa0ec61962a4872b5";

    /** Fabric loader entry class, referenced by name only (never imported). */
    private static final String FABRIC_MAIN = "net.fabricmc.loader.impl.launch.knot.KnotClient";

    private M2Entry() {
    }

    public static void main(String[] args) {
        // 1) Print the recognizable marker first, flush so it survives a later crash.
        System.out.println(MARKER);
        System.out.flush();

        // 2) Reflectively hand control to the official Fabric loader.
        try {
            Class<?> knot = Class.forName(FABRIC_MAIN);
            java.lang.reflect.Method main = knot.getMethod("main", String[].class);
            main.invoke(null, (Object) args);
        } catch (Throwable t) {
            // Never swallow: print the original throwable to stderr, then exit non-zero.
            System.err.println("MODGRAPH_M2_ENTRY_HANDOFF_FAILED");
            t.printStackTrace(System.err);
            System.err.flush();
            System.exit(1);
        }
    }
}
