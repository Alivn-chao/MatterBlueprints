package com.gtnhblueprints.machine;

final class HostedMachineStructure {

    static final int WIDTH = 17;
    static final int DEPTH = 5;
    static final int HEIGHT = 5;
    static final int CONTROLLER_X = 14;
    static final int CONTROLLER_Y = 2;
    // The controller sits on the outward-facing depth plane. Keeping it at z=0
    // makes StructureLib build the machine room behind the controller instead
    // of placing the controller face toward the room interior.
    static final int CONTROLLER_Z = 0;

    /**
     * Exact geometry captured in the local {@code jiegou.gtbp} reference. StructureLib consumes layers from top to
     * bottom. L replaces the electronic-computer edge/foundation blocks, V replaces the heat vents and H replaces
     * advanced-computer blocks, where machine hatches are allowed.
     */
    static final String[][] LAYERS = {
        {
            "LLLLLLLLLLLLLLLLL",
            "LHHHHHHHHHHHHHHHL",
            "LHHHHHHHHHHHHHHHL",
            "LHHHHHHHHHHHHHHHL",
            "LLLLLLLLLLLLLLLLL" },
        {
            "LVVVVVVVVVVVVHHHL",
            "H---------------H",
            "H---------------H",
            "H---------------H",
            "LVVVVVVVVVVVVVVVL" },
        {
            "LHHHHHHHHHHHHH~HL",
            "H---------------H",
            "H---------------H",
            "H---------------H",
            "LHHHHHHHHHHHHHHHL" },
        {
            "LHHHHHHHHHHHHHHHL",
            "H---------------H",
            "H---------------H",
            "H---------------H",
            "LHHHHHHHHHHHHHHHL" },
        {
            "LLLLLLLLLLLLLLLLL",
            "LLLLLLLLLLLLLLLLL",
            "LLLLLLLLLLLLLLLLL",
            "LLLLLLLLLLLLLLLLL",
            "LLLLLLLLLLLLLLLLL" } };

    private HostedMachineStructure() {}
}
