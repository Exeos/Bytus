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
    public static final int FLOW_PARAM_GENERIFY = 1;
    public static final int FLOW_CTRL_FLATTENING = 2;
    public static final int FLOW_JUMP_FLATTENING = 3;
    public static final int STR_ENCRYPT_STRINGS = 4;
    public static final int STR_SPLIT_STRINGS = 5;
    public static final int REF_PROXY = 6;
    public static final int REF_ENC = 7;
    public static final int MBA = 8;
    public static final int CONST_ARRAY = 9;
    public static final int NUM_UNDER_OVER_FLOW = 10;
}
