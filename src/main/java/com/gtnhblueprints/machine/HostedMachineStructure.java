package com.gtnhblueprints.machine;

final class HostedMachineStructure {

    static final int WIDTH = 17;
    static final int DEPTH = 5;
    static final int HEIGHT = 5;
    static final int CONTROLLER_X = 14;
    static final int CONTROLLER_Y = 2;
    static final int CONTROLLER_Z = 4;

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
            "LVVVVVVVVVVVVVVVL",
            "H---------------H",
            "H---------------H",
            "H---------------H",
            "LVVVVVVVVVVVVHHHL" },
        {
            "LHHHHHHHHHHHHHHHL",
            "H---------------H",
            "H---------------H",
            "H---------------H",
            "LHHHHHHHHHHHHH~HL" },
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
