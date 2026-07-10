package com.dreamyao.nhtranslationupdate.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.dreamyao.nhtranslationupdate.NHTranslationUpdate;

/**
 * ASM transformer that inserts the NHTranslationResourcePack at the highest
 * priority position in Minecraft's resource-pack chain.
 *
 * <p>
 * Uses {@code @SortingIndex(2000)} so it runs <em>after</em>
 * TX Loader (1001).  The hook is inserted just before
 * {@code Minecraft.reloadResources(List)}, which guarantees the translation
 * pack always sits at the end of the list — overriding everything including
 * TX Loader's {@code forceload} pack.
 * </p>
 */
public final class MinecraftClassTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!"net.minecraft.client.Minecraft".equals(transformedName)) {
            return basicClass;
        }
        return transformMinecraft(basicClass);
    }

    private static byte[] transformMinecraft(byte[] basicClass) {
        final boolean devEnv = (boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
        final String targetMethod = devEnv ? "refreshResources" : "func_110436_a";
        final String targetCall   = devEnv ? "reloadResources"  : "func_110541_a";

        final ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean patched = false;
        for (MethodNode mn : classNode.methods) {
            if (mn.name.equals(targetMethod) && mn.desc.equals("()V")) {
                for (AbstractInsnNode insn : mn.instructions.toArray()) {
                    if (isReloadCall(insn, targetCall)) {
                        mn.instructions.insertBefore(
                            insn,
                            new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/dreamyao/nhtranslationupdate/core/MinecraftHook",
                                "insertPack",
                                "(Ljava/util/List;)Ljava/util/List;",
                                false));
                        patched = true;
                        break;
                    }
                }
                break;
            }
        }
        if (!patched) {
            NHTranslationUpdate.LOG.error("NHTranslationUpdate could not patch Minecraft.refreshResources");
            return basicClass;
        }
        final ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        NHTranslationUpdate.LOG.info("Injected resource-pack hook into Minecraft.refreshResources");
        return writer.toByteArray();
    }

    private static boolean isReloadCall(AbstractInsnNode node, String name) {
        if (!(node instanceof MethodInsnNode)) return false;
        MethodInsnNode mn = (MethodInsnNode) node;
        return mn.name.equals(name) && mn.desc.equals("(Ljava/util/List;)V");
    }
}
