package me.exeos.bytus.core.transformer;

/**
 * Shared numeric priority values for transformers.
 * <p>
 * Lower values run earlier.
 *
 * @see AbstractTransformer#priority()
 */
public class Priority {

    public static final int PRE_PROCESSOR = 0;
    public static final int RENAME = 1;
    public static final int SALT_CLASS = 2;
    public static final int SALT_METHOD = 3;
    public static final int FLOATING_TO_INT = 4;
    public static final int NUM_ENC = 5;
    public static final int FLOW_BLOCK_SPLIT = 6;
    public static final int FLOW_CTRL_FLATTENING = 7;
    public static final int FLOW_ENTRY_DISPATCH = 8;
    public static final int FLOW_BLOCK_REARRANGE = 9;
    public static final int FLOW_JUMP_FLATTENING = 10;
    public static final int CONST_ARRAY = 11;
    public static final int FLOW_PARAM_GENERIFY = 12;
    public static final int STR_ENCRYPT_STRINGS = 13;
    public static final int STR_SPLIT_STRINGS = 14;
    public static final int REF_PROXY = 15;
    public static final int REF_ENC = 16;
    public static final int FLOW_GOTO_REPLACE = 17;
    public static final int MBA = 18;
}
