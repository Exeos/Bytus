package me.exeos.bytus.core.transformer.extensions;

public class MethodExtension {

    public final SaltInfo saltInfo;

    public MethodExtension() {
        this.saltInfo = new SaltInfo();
    }

    public MethodExtension(int salt, int saltSlot) {
        this.saltInfo = new SaltInfo(salt, saltSlot);
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

        public int getSaltSlotOrDefault(int defaultSlot) {
            return hasSalt ? saltSlot : defaultSlot;
        }

        public int getSaltOrDefault(int defaultSalt) {
            return hasSalt ? salt : defaultSalt;
        }

        public void setSalt(int salt) {
            setSalt(salt, getSaltSlot());
        }

        public void setSalt(int salt, int saltSlot) {
            hasSalt = true;
            this.saltSlot = salt;
            this.salt = salt;
        }
    }
}
