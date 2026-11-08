import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Static compatibility inventory, not a gameplay/render test. Reads Java 8 root entries only. */
public class HostedVisualAudit {
    public static void main(String[] args) throws Exception {
        Map<String, ClassNode> classes = new TreeMap<>();
        try (ZipFile zip = new ZipFile(args[0])) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) continue;
                ClassNode node = new ClassNode();
                new ClassReader(zip.getInputStream(entry)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                classes.put(node.name, node);
            }
        }
        int count = 0;
        for (ClassNode node : classes.values()) {
            ClassNode parent = node;
            while (parent != null && !parent.name.equals("gregtech/api/metatileentity/implementations/MTEMultiBlockBase")) parent = classes.get(parent.superName);
            if (parent == null) continue;
            count++;
            Set<String> coils = new TreeSet<>(), active = new TreeSet<>(), rendering = new TreeSet<>(), custom = new TreeSet<>();
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        if (call.name.equals("activeCoils")) coils.add(method.name);
                        if (call.name.equals("isActive")) active.add(method.name);
                        if (call.name.toLowerCase().contains("render") && !method.name.equals("<clinit>")) rendering.add(method.name);
                    }
                    if (instruction instanceof FieldInsnNode field && field.name.equals("mMaxProgresstime")
                        && (method.name.toLowerCase().contains("render") || method.name.equals("onPostTick"))) custom.add(method.name);
                }
            }
            if (!coils.isEmpty() || !rendering.isEmpty() || !custom.isEmpty()) {
                System.out.println(node.name + " | coils=" + coils + " | active=" + active + " | render=" + rendering + " | progress=" + custom);
            }
        }
        System.out.println("MULTIBLOCK_CLASSES=" + count);
    }
}
