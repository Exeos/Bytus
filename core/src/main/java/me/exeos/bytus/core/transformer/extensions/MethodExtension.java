package me.exeos.bytus.core.transformer.extensions;

import me.exeos.bytus.asmplus.utils.InsnUtil;
import org.objectweb.asm.tree.InsnList;

import java.util.HashMap;
import java.util.Map;

public class MethodExtension {

    public final SaltInfo saltInfo;
    public final ParamObfInfo paramObfInfo;

    public MethodExtension() {
        this.saltInfo = new SaltInfo();
        this.paramObfInfo = new ParamObfInfo();
    }

    public MethodExtension(int salt, int saltSlot) {
        this.saltInfo = new SaltInfo(salt, saltSlot);
        paramObfInfo = new ParamObfInfo();
    }

    public MethodExtension(int paramObfParamSlot, Map<Integer, Integer> paramArrayIndexBySlot) {
        paramObfInfo = new ParamObfInfo(paramObfParamSlot, paramArrayIndexBySlot);
        saltInfo = new SaltInfo();
    }

    public InsnList getObfuscatedIntPush(int value) {
        return InsnUtil.getIntPushSalted(
                value,
                saltInfo.hasSalt(),
                saltInfo.getSaltOrDefault(),
                paramObfInfo.getArrayIndexBySlotOrSlot(saltInfo.getSaltSlotOrDefault()),
                paramObfInfo.hasParamObf(),
                paramObfInfo.getObjArrSlotOrDefault()
        );
    }

    public static class SaltInfo {
        private int salt;
        private int saltSlot;
        private boolean hasSalt;

        public SaltInfo() {
            hasSalt = false;
        }

        public SaltInfo(int salt, int saltSlot) {
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

            return paramArrayIndexBySlot.get(slot);
        }
    }
}
