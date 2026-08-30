package com.gtnhblueprints.registry;

import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.MatterBlueprints;
import com.gtnhblueprints.machine.MTEHostedMachineController;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.util.GTModHandler;

public final class ModMachines {

    public static MTEHostedMachineController HOSTED_MACHINE_CONTROLLER;

    private ModMachines() {}

    public static void preInit() {
        int id = BlueprintConfig.hostedMachineControllerId;
        for (MetaTileEntityIDs officialId : MetaTileEntityIDs.values()) {
            if (officialId.ID == id) {
                throw new IllegalStateException(
                    "Matter Blueprints hosted machine controller MetaTileEntity ID " + id
                        + " is reserved by GregTech as "
                        + officialId.name()
                        + ". Choose a free machines.hostedMachineControllerId in matterblueprints.cfg before loading a world.");
            }
        }
        if (GregTechAPI.METATILEENTITIES[id] != null) {
            throw new IllegalStateException(
                "Matter Blueprints hosted machine controller MetaTileEntity ID " + id + " is already occupied by "
                    + GregTechAPI.METATILEENTITIES[id].getClass().getName()
                    + ". Choose a free machines.hostedMachineControllerId in matterblueprints.cfg before loading a world.");
        }
        HOSTED_MACHINE_CONTROLLER = new MTEHostedMachineController(
            id,
            "matterblueprints.multimachine.host",
            "Multiblock Machine Host");
        MatterBlueprints.LOG.info("Registered Multiblock Machine Host at GregTech MetaTileEntity ID {}", id);
    }

    public static void init() {
        GTModHandler.addCraftingRecipe(
            HOSTED_MACHINE_CONTROLLER.getStackForm(1),
            new Object[] {
                "SDS",
                "RHR",
                "FCF",
                'S',
                ItemList.Sensor_ZPM.get(1),
                'D',
                ItemList.Tool_DataOrb.get(1),
                'R',
                ItemList.Robot_Arm_ZPM.get(1),
                'H',
                ItemList.Hull_ZPM.get(1),
                'F',
                ItemList.Field_Generator_ZPM.get(1),
                'C',
                ItemList.Circuit_Master.get(1) });
    }
}
