package me.exeos.bytus.core.transformer.extensions;

import me.exeos.bytus.core.asm.ObfCodenGen;
import me.exeos.bytus.core.transformer.Pipeline;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.HashMap;
import java.util.Map;

public class MethodExtension {

    private final Pipeline pipeline;
    private final ClassNode owner;

    public final MethodSaltInfo saltInfo;
    public final ParamObfInfo paramObfInfo;

    public MethodExtension() {
        this(null, null);
    }

    public MethodExtension(Pipeline pipeline, ClassNode owner) {
        this.pipeline = pipeline;
        this.owner = owner;
        this.saltInfo = new MethodSaltInfo();
        this.paramObfInfo = new ParamObfInfo();
    }

    public InsnList getObfuscatedIntPush(int value) {
        return ObfCodenGen.getObfuscatedIntPush(value, pipeline == null ? new ClassExtension.ClassSaltInfo() : pipeline.getExtension(owner).saltInfo(), saltInfo, paramObfInfo);
    }

    public InsnList getObfuscatedJump(LabelNode to) {
        return ObfCodenGen.getRandomJump(to, pipeline == null ? new ClassExtension.ClassSaltInfo() : pipeline.getExtension(owner).saltInfo(), saltInfo, paramObfInfo);
    }

    public static class MethodSaltInfo {
        private int salt;
        private int saltSlot;
        private boolean hasSalt;

        public MethodSaltInfo() {
            hasSalt = false;
        }

        public MethodSaltInfo(int salt, int saltSlot) {
            this.salt = salt;
            this.saltSlot = saltSlot;
            hasSalt = true;
        }

        public boolean hasSalt() {
            return hasSalt;
        }

        public int getSaltSlot() {
            if (!hasSalt) {
                throw new IllegalStateException("Can't read salt if not set. Would likely cause logical issues");
            }
            return saltSlot;
        }

        public int getSalt() {
            if (!hasSalt) {
                throw new IllegalStateException("Can't read salt if not set. Would likely cause logical issues");
            }
            return salt;
        }

        public int getSaltSlotOrDefault() {
            return getSaltSlotOrDefault(0);
        }
        
        public int getSaltSlotOrDefault(int defaultSlot) {
            return hasSalt ? saltSlot : defaultSlot;
        }

        public int getSaltOrDefault() {
            return getSaltOrDefault(0);
        }
        
        public int getSaltOrDefault(int defaultSalt) {
            return hasSalt ? salt : defaultSalt;
        }

        public void setSalt(int salt, int saltSlot) {
            hasSalt = true;
            this.saltSlot = saltSlot;
            this.salt = salt;
        }
    }

    public static class ParamObfInfo {
        private boolean hasParamObf;
        private int objArrSlot;
        private Map<Integer, Integer> paramArrayIndexBySlot;

        public ParamObfInfo() {
            hasParamObf = false;
            paramArrayIndexBySlot = new HashMap<>();
        }

        public ParamObfInfo(int objArrSlot, Map<Integer, Integer> paramArrayIndexBySlot) {
            hasParamObf = true;
            this.objArrSlot = objArrSlot;
            this.paramArrayIndexBySlot = paramArrayIndexBySlot;
        }

        public int getObjArrSlot() {
            if (!hasParamObf) {
                throw new IllegalStateException("Can't get slot when Method insn ParamObfed");
            }

            return objArrSlot;
        }

        public int getObjArrSlotOrDefault() {
            return hasParamObf ? objArrSlot : 0;
        }

        public void setParamObf(int objArrSlot, Map<Integer, Integer> paramArrayIndexBySlot) {
            hasParamObf = true;
            this.objArrSlot = objArrSlot;
            this.paramArrayIndexBySlot = paramArrayIndexBySlot;
        }

        public boolean hasParamObf() {
            return hasParamObf;
        }

        public int getArrayIndexBySlotOrSlot(int slot) {
            if (!hasParamObf) {
                return slot;
            }

            return paramArrayIndexBySlot.getOrDefault(slot, slot);
        }
    }
}
