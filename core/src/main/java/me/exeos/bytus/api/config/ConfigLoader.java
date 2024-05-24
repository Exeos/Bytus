package me.exeos.bytus.api.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    private static void flattenJson(String prefix, JsonNode node, HashMap<String, Object> flattenedMap) {
        if (node.isObject()) {
            Iterator<Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Entry<String, JsonNode> field = fields.next();
                String newPrefix = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                flattenJson(newPrefix, field.getValue(), flattenedMap);
            }
        } else if (node.isArray()) {
            int index = 0;
            for (JsonNode arrayElement : node) {
                String newPrefix = prefix + "[" + index + "]";
                flattenJson(newPrefix, arrayElement, flattenedMap);
                index++;
            }
        } else {
            switch (node.getNodeType()) {
                case BOOLEAN:
                    flattenedMap.put(prefix, node.asBoolean());
                    break;
                case NUMBER:
                    flattenedMap.put(prefix, node.asInt());
                    break;
                case STRING:
                    flattenedMap.put(prefix, node.asText());
                    break;
            }
        }
    }
}
