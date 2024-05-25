package me.exeos.bytus.api.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.exeos.bytus.Bytus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class ConfigLoader {

    public Config loadFile(String path) throws IOException {
        return loadString(new String(Files.readAllBytes(Paths.get(path))));
    }

    public Config loadString(String jsonStr) throws JsonProcessingException {
        HashMap<String, Object> keySet = new HashMap<>();
        flattenJson("", new ObjectMapper().readTree(jsonStr), keySet);

        return new Config(keySet);
    }

    private void flattenJson(String prefix, JsonNode node, HashMap<String, Object> flattenedMap) {
        if (node.isObject()) {
            Iterator<Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Entry<String, JsonNode> field = fields.next();
                String newPrefix = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                flattenJson(newPrefix, field.getValue(), flattenedMap);
            }
        } else {
            switch (node.getNodeType()) {
                case BOOLEAN:
                case NUMBER:
                case STRING:
                    flattenedMap.put(prefix, nodeValue(node));
                    break;
                case ARRAY:
                    parseArrayNode(flattenedMap, node, prefix);
                    break;
                default:
                    Bytus.instance.logger.error("Unsupported config node Type: " + node.getNodeType());
            }
        }
    }
    
    private void parseArrayNode(HashMap<String, Object> flattenedMap, JsonNode arrayNode, String prefix) {
        ArrayList<Object> items = new ArrayList<>();
        arrayNode.elements().forEachRemaining(jsonNode -> {
            Object value = nodeValue(jsonNode);
            /* !array */
            if (value != null) {
                items.add(nodeValue(jsonNode));
            }
        });

        flattenedMap.put(prefix, items);
    }
    
    private Object nodeValue(JsonNode node) {
        switch (node.getNodeType()) {
            case BOOLEAN:
                return node.asBoolean();
            case NUMBER:
                return node.asInt();
            case STRING:
                return node.asText();
            case ARRAY:
                return null;
            default:
                throw new IllegalStateException("Unsupported config node Type: " + node.getNodeType());
        }
    }
}
