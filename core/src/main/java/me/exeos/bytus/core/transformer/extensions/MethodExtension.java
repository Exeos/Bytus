package me.exeos.bytus.core.transformer.extensions;

public class MethodExtension {

    private int salt;
    private int saltSlot;
    private boolean hasSalt;

    public MethodExtension() {
        hasSalt = false;
    }

    public MethodExtension(int salt, int saltSlot) {
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

    public void setSalt(int salt) {
        setSalt(salt, getSaltSlot());
    }

    public void setSalt(int salt, int saltSlot) {
        hasSalt = true;
        this.saltSlot = salt;
        this.salt = salt;
    }
}
