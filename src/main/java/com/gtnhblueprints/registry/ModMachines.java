package com.gtnhblueprints.registry;

import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.MatterBlueprints;
import com.gtnhblueprints.machine.MTEHostedMachineController;
import com.gtnhblueprints.machine.MTEQuantizedMEInputBus;
import com.gtnhblueprints.machine.MTEQuantizedMEInputHatch;
import com.gtnhblueprints.machine.MTEMEInputAssembly;
import com.gtnhblueprints.machine.MTEMEOutputAssembly;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;

public final class ModMachines {

    public static MTEHostedMachineController HOSTED_MACHINE_CONTROLLER;

    public static MTEQuantizedMEInputBus QUANTIZED_ME_INPUT_BUS;
    public static MTEQuantizedMEInputHatch QUANTIZED_ME_INPUT_HATCH;
    public static MTEMEInputAssembly ME_INPUT_ASSEMBLY;
    public static MTEMEOutputAssembly ME_OUTPUT_ASSEMBLY;

    private ModMachines() {}

    private static int requireFreeId(int id) {
        if (id < 1 || id >= GregTechAPI.METATILEENTITIES.length)
            throw new IllegalStateException("Matter Blueprints invalid MetaTileEntity ID " + id);
        for (MetaTileEntityIDs official : MetaTileEntityIDs.values())
            if (official.ID == id) throw new IllegalStateException("Matter Blueprints ID " + id + " reserved by GT: " + official);
        if (GregTechAPI.METATILEENTITIES[id] != null)
            throw new IllegalStateException("Matter Blueprints ID " + id + " already occupied; choose a free ID in matterblueprints.cfg");
        return id;
    }

    public static void preInit() {
        com.gtnhblueprints.machine.MEPortTextures.init();
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
        QUANTIZED_ME_INPUT_BUS = new MTEQuantizedMEInputBus(requireFreeId(BlueprintConfig.quantizedMEInputBusId),
            "matterblueprints.hatch.quantized_me_input_bus", "Quantified ME Input Bus");
        QUANTIZED_ME_INPUT_HATCH = new MTEQuantizedMEInputHatch(requireFreeId(BlueprintConfig.quantizedMEInputHatchId),
            "matterblueprints.hatch.quantized_me_input", "Quantified ME Input Hatch");
        ME_INPUT_ASSEMBLY = new MTEMEInputAssembly(requireFreeId(BlueprintConfig.meInputAssemblyId),
            "matterblueprints.hatch.me_input_assembly", "ME Input Assembly");
        ME_OUTPUT_ASSEMBLY = new MTEMEOutputAssembly(requireFreeId(BlueprintConfig.meOutputAssemblyId),
            "matterblueprints.hatch.me_output_assembly", "ME Output Assembly");
        MatterBlueprints.LOG.info("Registered Multiblock Machine Host at GregTech MetaTileEntity ID {}", id);
    }

    public static void init() {
        GTModHandler.addShapelessCraftingRecipe(QUANTIZED_ME_INPUT_BUS.getStackForm(1),
            new Object[] { ItemList.Hatch_Input_Bus_ME_Advanced.get(1) });
        GTModHandler.addShapelessCraftingRecipe(QUANTIZED_ME_INPUT_HATCH.getStackForm(1),
            new Object[] { ItemList.Hatch_Input_ME_Advanced.get(1) });
        GTModHandler.addShapelessCraftingRecipe(ME_INPUT_ASSEMBLY.getStackForm(1),
            new Object[] { ItemList.Hatch_Input_Bus_ME_Advanced.get(1), ItemList.Hatch_Input_ME_Advanced.get(1) });
        GTModHandler.addShapelessCraftingRecipe(ME_OUTPUT_ASSEMBLY.getStackForm(1),
            new Object[] { ItemList.Hatch_Output_Bus_ME.get(1), ItemList.Hatch_Output_ME.get(1) });
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
                OrePrefixes.circuit.get(Materials.LuV) });
    }
}
