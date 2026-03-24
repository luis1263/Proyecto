package simulator.util;

import simulator.core.DiskSimulator;
import simulator.core.FileSystem;
import simulator.model.Enums.FileType;
import simulator.model.FileNode;
import simulator.structures.CustomLinkedList;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Minimal JSON serializer/deserializer — NO external libraries.
 * Handles the file-system state and the test-case format required by the project.
 */
public class JsonManager {

    // ─── Save ─────────────────────────────────────────────────────────────────

    public static void saveState(FileSystem fs, String filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"filesystem\": ");
        appendNode(sb, fs.getRoot(), 2);
        sb.append("\n}\n");

        try (Writer w = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
    }

    private static void appendNode(StringBuilder sb, FileNode node, int indent) {
        String pad  = "  ".repeat(indent);
        String pad1 = "  ".repeat(indent + 1);
        sb.append("{\n");
        sb.append(pad1).append("\"id\": \"").append(esc(node.getId())).append("\",\n");
        sb.append(pad1).append("\"name\": \"").append(esc(node.getName())).append("\",\n");
        sb.append(pad1).append("\"type\": \"").append(node.getType().name()).append("\",\n");
        sb.append(pad1).append("\"size\": ").append(node.getSizeInBlocks()).append(",\n");
        sb.append(pad1).append("\"owner\": \"").append(esc(node.getOwner())).append("\",\n");
        sb.append(pad1).append("\"firstBlock\": ").append(node.getFirstBlock()).append(",\n");
        if (node.getColor() != null) {
            sb.append(pad1).append("\"color\": \"")
              .append(String.format("#%06X", node.getColor().getRGB() & 0xFFFFFF))
              .append("\",\n");
        }
        sb.append(pad1).append("\"children\": [");
        CustomLinkedList<FileNode> children = node.getChildren();
        boolean first = true;
        for (FileNode child : children) {
            if (!first) sb.append(",");
            sb.append("\n").append(pad1).append("  ");
            appendNode(sb, child, indent + 2);
            first = false;
        }
        if (!children.isEmpty()) sb.append("\n").append(pad1);
        sb.append("]\n");
        sb.append(pad).append("}");
    }

    // ─── Load filesystem state ────────────────────────────────────────────────

    public static void loadState(FileSystem fs, DiskSimulator disk, String filePath)
            throws IOException {
        String json = readFile(filePath);
        // Find "filesystem" object
        int fsIdx = json.indexOf("\"filesystem\"");
        if (fsIdx < 0) throw new IOException("No 'filesystem' key found");
        int start = json.indexOf('{', fsIdx + 12);
        if (start < 0) throw new IOException("No root node object found");

        fs.reset();
        disk.reset();

        // Parse root children recursively
        parseChildren(json, start, fs.getRoot().getId(), fs, disk);
    }

    private static void parseChildren(String json, int objStart,
                                       String parentId, FileSystem fs, DiskSimulator disk) {
        // Find "children": [ ... ]
        int childIdx = json.indexOf("\"children\"", objStart);
        if (childIdx < 0) return;
        int arrStart = json.indexOf('[', childIdx);
        if (arrStart < 0) return;

        int i = arrStart + 1;
        int depth = 0;
        int nodeStart = -1;

        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) nodeStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && nodeStart >= 0) {
                    // Parse this node
                    String nodeJson = json.substring(nodeStart, i + 1);
                    processNodeJson(nodeJson, nodeStart, parentId, fs, disk);
                    nodeStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
            i++;
        }
    }

    private static void processNodeJson(String nodeJson, int absoluteStart,
                                         String parentId, FileSystem fs, DiskSimulator disk) {
        String id        = extractString(nodeJson, "id");
        String name      = extractString(nodeJson, "name");
        String type      = extractString(nodeJson, "type");
        String owner     = extractString(nodeJson, "owner");
        int    size      = extractInt(nodeJson, "size");
        int    firstBlk  = extractInt(nodeJson, "firstBlock");

        if (name == null || type == null) return;

        if ("DIRECTORY".equals(type)) {
            FileNode dir = fs.createDirectory(parentId, name, owner != null ? owner : "admin");
            if (dir != null) {
                parseChildren(nodeJson, 0, dir.getId(), fs, disk);
            }
        } else {
            if (size > 0 && firstBlk >= 0) {
                FileNode file = fs.createFileAt(parentId, name, size,
                        owner != null ? owner : "admin", firstBlk);
            } else if (size > 0) {
                fs.createFile(parentId, name, size, owner != null ? owner : "admin");
            }
        }
    }

    // ─── Load Test Case (project format) ─────────────────────────────────────

    /**
     * Loads a test-case JSON:
     * { "test_id":"P1", "initial_head":50, "requests":[...], "system_files":{...} }
     * Returns a TestCase object for the GUI.
     */
    public static TestCase loadTestCase(String filePath) throws IOException {
        String json = readFile(filePath);
        TestCase tc = new TestCase();

        tc.testId      = extractString(json, "test_id");
        tc.initialHead = extractInt(json, "initial_head");

        // Parse requests array
        int reqIdx = json.indexOf("\"requests\"");
        if (reqIdx >= 0) {
            int arrStart = json.indexOf('[', reqIdx);
            int arrEnd   = json.indexOf(']', arrStart);
            String arrJson = json.substring(arrStart + 1, arrEnd);
            parseRequestsArray(arrJson, tc);
        }

        // Parse system_files object
        int sfIdx = json.indexOf("\"system_files\"");
        if (sfIdx >= 0) {
            int objStart = json.indexOf('{', sfIdx);
            int objEnd   = matchingBrace(json, objStart);
            String sfJson = json.substring(objStart + 1, objEnd);
            parseSystemFiles(sfJson, tc);
        }

        return tc;
    }

    private static void parseRequestsArray(String arrJson, TestCase tc) {
        int i = 0;
        while (i < arrJson.length()) {
            int objStart = arrJson.indexOf('{', i);
            if (objStart < 0) break;
            int objEnd = matchingBrace(arrJson, objStart);
            String obj = arrJson.substring(objStart + 1, objEnd);
            int pos = extractInt(obj, "pos");
            String op = extractString(obj, "op");
            if (pos >= 0 && op != null) tc.requests.addLast(new TestCase.RequestEntry(pos, op));
            i = objEnd + 1;
        }
    }

    private static void parseSystemFiles(String sfJson, TestCase tc) {
        // Keys are block positions: "11": { "name":"...", "blocks": N }
        int i = 0;
        while (i < sfJson.length()) {
            int colonIdx = sfJson.indexOf(':', i);
            if (colonIdx < 0) break;
            // Find the key (block position)
            int keyEnd   = colonIdx;
            int keyStart = keyEnd - 1;
            while (keyStart >= 0 && sfJson.charAt(keyStart) != '"') keyStart--;
            if (keyStart < 0) break;
            String key = sfJson.substring(keyStart + 1, keyEnd).trim().replace("\"", "");
            int blockPos;
            try { blockPos = Integer.parseInt(key.trim()); }
            catch (NumberFormatException e) { i = colonIdx + 1; continue; }

            int objStart = sfJson.indexOf('{', colonIdx);
            if (objStart < 0) break;
            int objEnd = matchingBrace(sfJson, objStart);
            String obj = sfJson.substring(objStart + 1, objEnd);
            String name = extractString(obj, "name");
            int blocks  = extractInt(obj, "blocks");
            if (name != null && blocks > 0)
                tc.systemFiles.addLast(new TestCase.SystemFile(blockPos, name, blocks));
            i = objEnd + 1;
        }
    }

    // ─── Inner data class ─────────────────────────────────────────────────────

    public static class TestCase {
        public String testId = "";
        public int    initialHead = 0;
        public final CustomLinkedList<RequestEntry> requests    = new CustomLinkedList<>();
        public final CustomLinkedList<SystemFile>   systemFiles = new CustomLinkedList<>();

        public static class RequestEntry {
            public int pos; public String op;
            public RequestEntry(int pos, String op) { this.pos = pos; this.op = op; }
        }
        public static class SystemFile {
            public int blockPos; public String name; public int blocks;
            public SystemFile(int bp, String n, int b) { blockPos = bp; name = n; blocks = b; }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static int extractInt(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return -1;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return -1;
        int numStart = colon + 1;
        while (numStart < json.length() && (json.charAt(numStart) == ' ' || json.charAt(numStart) == '\n')) numStart++;
        int numEnd = numStart;
        while (numEnd < json.length() && (Character.isDigit(json.charAt(numEnd)) || json.charAt(numEnd) == '-')) numEnd++;
        if (numEnd == numStart) return -1;
        try { return Integer.parseInt(json.substring(numStart, numEnd)); }
        catch (NumberFormatException e) { return -1; }
    }

    private static int matchingBrace(String json, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < json.length(); i++) {
            if (json.charAt(i) == '{') depth++;
            else if (json.charAt(i) == '}') { depth--; if (depth == 0) return i; }
        }
        return json.length() - 1;
    }

    private static String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(path, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
