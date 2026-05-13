package me.exeos.bytus.core.transformer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class TransformerPipeline {

    private final Deque<Transformer> queue = new ArrayDeque<>();

    public TransformerPipeline(List<Transformer> transformers) {
        queue.addAll(transformers);
    }

    public void insertNext(Transformer transformer) {
        queue.addFirst(transformer);
    }

    public void append(Transformer transformer) {
        queue.addLast(transformer);
    }

    public void executeTransformers() {
        while (!queue.isEmpty()) {
            queue.pollFirst().transform(this);
        }
    }
}
