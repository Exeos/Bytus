package me.exeos.bytus.core.transformer;


import me.exeos.asmplus.jar.JarArchive;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.InsnListContext;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.extensions.ClassExtension;
import me.exeos.bytus.core.transformer.extensions.FieldExtension;
import me.exeos.bytus.core.transformer.extensions.JarExtension;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.Consumer;

/**
 * Executes a sequence of {@link AbstractTransformer transformers}.
 * <p>
 * In addition to running the full pipeline, the pipeline can also "re-dispatch" transformations
 * for newly emitted nodes (classes/methods) while excluding specific transformer types to avoid
 * reprocessing loops.
 */
public class Pipeline {

    private final List<AbstractTransformer> transformers;
    private final Map<JarArchive, JarExtension> jarExtensions = new HashMap<>();
    private final Map<ClassNode, ClassExtension> classExtensions = new HashMap<>();
    private final Map<FieldNode, FieldExtension> fieldExtensions = new HashMap<>();
    private final Map<MethodNode, MethodExtension> methodExtensions = new HashMap<>();

    private final Map<Class<? extends AbstractTransformer>, Set<ClassNode>> transformerExcludedClasses = new HashMap<>();
    private final Map<Class<? extends AbstractTransformer>, Set<MethodNode>> transformerExcludedMethods = new HashMap<>();

    /**
     * Creates a pipeline
     *
     * @param transformers transformers to run in order
     */
    public Pipeline(List<AbstractTransformer> transformers) {
        this.transformers = transformers;
    }

    /**
     * Runs all transformers over a jar context. (Entry point)
     *
     * @param context jar context
     */
    public void run(JarContext context) {
        long start = System.currentTimeMillis();
        transformers.forEach(transformer -> {
            String transformerName = transformer.getClass().getSimpleName();

            System.out.println("Running: " + transformerName);

            long tStart = System.currentTimeMillis();
            transformer.transform(context);

            System.out.println("Finished [" + (System.currentTimeMillis() - tStart) + "ms]: " + transformerName);
            System.out.println();
        });
        System.out.println("Finished all transformers in: " + (1000 / (System.currentTimeMillis() - start)) + "s");
    }

    /**
     * Emits a modified or newly created class into the jar, then re-runs the pipeline at class level.
     *
     * @param context    class context to emit
     * @param exclusions transformer types to skip for this re-dispatch
     */
    public void emit(ClassContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        context.jarCtx().jar().getClasses().put(context.classNode().name, context.classNode());
        context.jarCtx().getExtension().invalidateHierarchy();

        for (Class<? extends AbstractTransformer> exclusion : exclusions) {
            transformerExcludedClasses.computeIfAbsent(exclusion, _ -> new HashSet<>()).add(context.classNode());
        }

        transform(context, exclusions);
    }

    /**
     * Emits a modified or newly created method into its owner class, then re-runs the pipeline at method level.
     *
     * @param context    method context to emit
     * @param exclusions transformer types to skip for this re-dispatch
     */
    public void emit(MethodContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        context.ownerCtx().classNode().methods.add(context.methodNode());
        context.jarCtx().getExtension().invalidateHierarchy();

        for (Class<? extends AbstractTransformer> exclusion : exclusions) {
            transformerExcludedMethods.computeIfAbsent(exclusion, _ -> new HashSet<>()).add(context.methodNode());
        }

        transform(context, exclusions);
    }

    /**
     * Dispatches all transformers (excluding {@code exclusions}) at jar level.
     */
    public void transform(JarContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        dispatchFiltered(t -> t.transform(context), exclusions);
    }

    /**
     * Dispatches all transformers (excluding {@code exclusions}) at class level.
     */
    public void transform(ClassContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        dispatchFiltered(t -> t.transform(context), exclusions);
    }

    /**
     * Dispatches all transformers (excluding {@code exclusions}) at method level.
     */
    public void transform(MethodContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        dispatchFiltered(t -> t.transform(context), exclusions);
    }

    /**
     * Dispatches all transformers (excluding {@code exclusions}) at instruction-list level.
     */
    public void transform(InsnListContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        dispatchFiltered(t -> t.transform(context), exclusions);
    }

    private void dispatchFiltered(Consumer<AbstractTransformer> action, Set<Class<? extends AbstractTransformer>> exclusions) {
        for (AbstractTransformer t : transformers) {
            if (!exclusions.contains(t.getClass())) {
                action.accept(t);
            }
        }
    }

    public ClassExtension getExtension(ClassContext context) {
        return getExtension(context.classNode());
    }

    public ClassExtension getExtension(ClassNode classNode) {
        classExtensions.putIfAbsent(classNode, new ClassExtension());

        return classExtensions.get(classNode);
    }

    public Optional<FieldExtension> getExtension(FieldNode fieldNode) {
        if (fieldExtensions.containsKey(fieldNode)) {
            return Optional.of(fieldExtensions.get(fieldNode));
        }

        return Optional.empty();
    }

    public FieldExtension getExtension(ClassNode owner, FieldNode fieldNode) {
        return fieldExtensions.computeIfAbsent(fieldNode, _ -> new FieldExtension(owner, fieldNode));
    }

    public MethodExtension getExtension(MethodContext context) {
        return getExtension(context.ownerCtx().classNode(), context.methodNode());
    }

    public MethodExtension getExtension(ClassNode owner, MethodNode methodNode) {
        return methodExtensions.computeIfAbsent(methodNode, _ -> new MethodExtension(this, owner, methodNode));
    }

    public JarExtension getExtension(JarContext context) {
        return getExtension(context.jar());
    }

    public JarExtension getExtension(JarArchive jar) {
        return jarExtensions.computeIfAbsent(jar, JarExtension::new);
    }

    public boolean isExcluded(Class<? extends AbstractTransformer> transformer, ClassNode classNode) {
        return transformerExcludedClasses.containsKey(transformer) && transformerExcludedClasses.get(transformer).contains(classNode);
    }

    public boolean isExcluded(Class<? extends AbstractTransformer> transformer, MethodNode methodNode) {
        return transformerExcludedMethods.containsKey(transformer) && transformerExcludedMethods.get(transformer).contains(methodNode);
    }
}
