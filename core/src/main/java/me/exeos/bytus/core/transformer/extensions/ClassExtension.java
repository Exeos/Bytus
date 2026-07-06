package me.exeos.bytus.core.transformer.extensions;

import java.util.Set;

public record ClassExtension(ClassSaltInfo saltInfo) {

    public ClassExtension() {
        this(new ClassSaltInfo());
    }

    public static class ClassSaltInfo {
        private int salt;
        private boolean hasSalt;
        private Set<ClassSaltInfo> preInitingSalts;
        private String owner;
        private String name;
        private String desc;

        public ClassSaltInfo() {
            hasSalt = false;
            preInitingSalts = Set.of();
        }

        public int getSalt() {
            if (!hasSalt) {
                throw new IllegalStateException("Can't read salt if not set. Would likely cause logical issues");
            }

            return salt;
        }

        public int getSaltOrDefault() {
            return getSaltOrDefault(0);
        }

        public int getSaltOrDefault(int defaultSalt) {
            return hasSalt ? salt : defaultSalt;
        }

        public boolean hasSalt() {
            return hasSalt;
        }

        public boolean hasPreInitializingSalts() {
            return !preInitingSalts.isEmpty();
        }

        public Set<ClassSaltInfo> getPreInitingSalts() {
            return preInitingSalts;
        }

        public String getOwner() {
            if (!hasSalt) {
                throw new IllegalStateException("Can't read if salt not set. Would likely cause logical issues");
            }

            return owner;
        }

        public String getName() {
            if (!hasSalt) {
                throw new IllegalStateException("Can't read if salt not set. Would likely cause logical issues");
            }

            return name;
        }

        public String getDesc() {
            if (!hasSalt) {
                throw new IllegalStateException("Can't read if salt not set. Would likely cause logical issues");
            }

            return desc;
        }

        public void setPreInitingSalts(Set<ClassSaltInfo> preInitingSalts) {
            this.preInitingSalts = preInitingSalts;
        }

        public void setSalt(int salt, String owner, String name, String desc) {
            this.salt = salt;
            this.hasSalt = true;
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }
    }
}
