package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StringTranslate;

import org.junit.jupiter.api.Test;

class HostedHudLineTest {
    @Test
    void rendersInReceivingClientsLanguageAfterSerialization() {
        StringTranslate.inject(new ByteArrayInputStream(
            "test.host.line=Fuel: %s, %s%%\ntest.host.state=locked\n".getBytes(StandardCharsets.UTF_8)));
        HostedHudLine line = HostedHudLine.text("test.host.line", HostedHudLine.text("test.host.state"), 50);
        NBTTagCompound packet = HostedHudLine.write(Collections.singletonList(line)).getCompoundTagAt(0);
        assertEquals("Fuel: locked, 50%", HostedHudLine.read(packet).render());
        StringTranslate.inject(new ByteArrayInputStream(
            "test.host.line=燃料：%s，%s%%\ntest.host.state=未解锁\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("燃料：未解锁，50%", HostedHudLine.read(packet).render());
    }

    @Test
    void sendsKeysAndTypedArgumentsWithoutServerTranslation() {
        HostedHudLine line = HostedHudLine.text("matterblueprints.host.hud.effects", 0.75D, Long.MAX_VALUE,
            HostedHudLine.text("matterblueprints.host.progression.disabled"));
        NBTTagCompound tag = HostedHudLine.write(Collections.singletonList(line)).getCompoundTagAt(0);
        assertEquals("matterblueprints.host.hud.effects", tag.getString("key"));
        NBTTagList args = tag.getTagList("args", 10);
        assertEquals(0.75D, args.getCompoundTagAt(0).getDouble("decimal"));
        assertEquals(Long.MAX_VALUE, args.getCompoundTagAt(1).getLong("integer"));
        assertEquals("matterblueprints.host.progression.disabled",
            args.getCompoundTagAt(2).getCompoundTag("line").getString("key"));
        assertEquals(tag, HostedHudLine.write(Collections.singletonList(HostedHudLine.read(tag))).getCompoundTagAt(0));
    }
}
