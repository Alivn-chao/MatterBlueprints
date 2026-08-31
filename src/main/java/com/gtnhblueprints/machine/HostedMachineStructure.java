package com.gtnhblueprints.machine;

final class HostedMachineStructure {

    static final int WIDTH = 9;
    static final int DEPTH = 7;
    static final int HEIGHT = 6;
    static final int CONTROLLER_X = 4;
    static final int CONTROLLER_Y = 4;
    static final int CONTROLLER_Z = 0;

    /**
     * StructureLib consumes layers from top to bottom. Keep the receiver first and the concrete foundation last.
     */
    static final String[][] LAYERS = {
        {
            "         ",
            "         ",
            "         ",
            "    R    ",
            "         ",
            "         ",
            "         " },
        {
            "LLLLLLLLL",
            "L-------L",
            "L--PPP--L",
            "L--PPP--L",
            "L--PPP--L",
            "L-------L",
            "LLLLLLLLL" },
        {
            "LCCCCCCCL",
            "C-------C",
            "C-------C",
            "C-------C",
            "C-------C",
            "C-------C",
            "LCCCCCCCL" },
        {
            "LCCCCCCCL",
            "C-------C",
            "C-------C",
            "C-------C",
            "C-------C",
            "C-------C",
            "LCCCCCCCL" },
        {
            "PCCC~CCCP",
            "C-------C",
            "C-------C",
            "C-------C",
            "C-------C",
            "C-------C",
            "PCCCCCCCP" },
        {
            "FFFFFFFFF",
            "FFFFFFFFF",
            "FFFFFFFFF",
            "FFFFFFFFF",
            "FFFFFFFFF",
            "FFFFFFFFF",
            "FFFFFFFFF" } };

    private HostedMachineStructure() {}
}
