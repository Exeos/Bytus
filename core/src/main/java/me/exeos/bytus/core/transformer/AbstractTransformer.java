package me.exeos.bytus.core.transformer;

import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.InsnListContext;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Base class for all transformers.
 * <p>
 * A transformer can operate at different levels:
 * <ul>
 *     <li>{@link JarContext} - whole archive</li>
 *     <li>{@link ClassContext} - class level</li>
 *     <li>{@link MethodContext} - method level</li>
 *     <li>{@link InsnListContext} - instruction list level</li>
 * </ul>
 * Default implementations dispatch down the tree (jar → class → method → instructions).
 */
public abstract class AbstractTransformer implements Opcodes {

    /**
     * Configuration backing this transformer instance.
     * This is used by Transformers to determine, if they should run or not.
     * Also used to retrieve relevant values
     */
    protected final BytusConfig config;

    public AbstractTransformer(BytusConfig config) {
        this.config = config;
    }

    /**
     * Transforms a whole jar by iterating over all classes and dispatching to {@link #transform(ClassContext)}.
     *
     * @param context jar context
     */
    public void transform(JarContext context) {
        for (ClassNode classNode : context.jar().classes().values()) {
            transform(new ClassContext(context, classNode));
        }
    }

    /**
     * Transforms a class by iterating over all methods and dispatching to {@link #transform(MethodContext)}.
     *
     * @param context class context
     */
    public void transform(ClassContext context) {
        for (MethodNode methodNode : context.classNode().methods) {
            transform(new MethodContext(context, methodNode));
        }
    }

    /**
     * Transforms a method by dispatching to {@link #transform(InsnListContext)}.
     *
     * @param context method context
     */
    public void transform(MethodContext context) {
        transform(new InsnListContext(context, context.methodNode().instructions));
    }

    /**
     * Instruction-list level transform hook. Default is a no-op (leaf).
     *
     * @param context instruction list context
     */
    public void transform(InsnListContext context) {
    }

    /**
     * @return whether this transformer should be included in the pipeline based on {@link #config}.
     */
    public abstract boolean applies();

    /**
     * Determines execution order. Lower values run earlier.
     *
     * @return transformer priority
     * @see Priority
     */
    public abstract int priority();
}
