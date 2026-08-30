package com.gtnhblueprints.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlueprintIOTest {

    @Test
    void acceptsUnicodeAndRemovesExtension() throws IOException {
        assertEquals("测试 蓝图-01", BlueprintIO.safeName(" 测试 蓝图-01.gtbp "));
    }

    @Test
    void rejectsTraversalAndSeparators() {
        assertThrows(IOException.class, () -> BlueprintIO.safeName("../secret"));
        assertThrows(IOException.class, () -> BlueprintIO.safeName("folder\\secret"));
        assertThrows(IOException.class, () -> BlueprintIO.safeName("C:secret"));
    }

    @Test
    void writesAndReadsAtomically(@TempDir Path temporaryDirectory) throws IOException {
        File directory = temporaryDirectory.toFile();
        byte[] expected = new byte[] { 0x1f, (byte) 0x8b, 1, 2, 3 };
        BlueprintIO.write(directory, "roundtrip", expected);
        assertArrayEquals(expected, BlueprintIO.read(directory, "roundtrip"));
    }

    @Test
    void readsEmptyNbtStringsWrittenByMatterManipulator() {
        NBTTagCompound restored = BlueprintJson.GSON.fromJson("{\"filter\":\"s\"}", NBTTagCompound.class);

        assertEquals("", restored.getString("filter"));
    }
}
