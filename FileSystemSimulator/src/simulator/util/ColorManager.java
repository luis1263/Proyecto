package simulator.util;

import java.awt.Color;

/**
 * Assigns and tracks distinct colors for files in the disk visualization.
 * Uses a fixed palette; no java.util.Map — simple parallel arrays.
 */
public class ColorManager {

    private static final Color[] PALETTE = {
        new Color(0xFF6B6B), new Color(0xFFD93D), new Color(0x6BCB77),
        new Color(0x4D96FF), new Color(0xF4A261), new Color(0xA8DADC),
        new Color(0xE76F51), new Color(0x2A9D8F), new Color(0x9B5DE5),
        new Color(0xF15BB5), new Color(0x00BBF9), new Color(0xFEE440),
        new Color(0x80B918), new Color(0xFF6361), new Color(0x58508D),
        new Color(0xBC5090), new Color(0x003F88), new Color(0xC77DFF),
        new Color(0x06D6A0), new Color(0xEF233C)
    };

    private static final int MAX = 256;
    private final String[] fileIds = new String[MAX];
    private final Color[]  colors  = new Color[MAX];
    private int count = 0;
    private int paletteIndex = 0;

    public Color assignColor(String fileId) {
        // Check existing
        for (int i = 0; i < count; i++)
            if (fileIds[i].equals(fileId)) return colors[i];
        // Assign new
        Color c = PALETTE[paletteIndex % PALETTE.length];
        paletteIndex++;
        if (count < MAX) {
            fileIds[count] = fileId;
            colors[count]  = c;
            count++;
        }
        return c;
    }

    public Color getColor(String fileId) {
        if (fileId == null) return Color.LIGHT_GRAY;
        for (int i = 0; i < count; i++)
            if (fileIds[i].equals(fileId)) return colors[i];
        return Color.LIGHT_GRAY;
    }

    public void releaseColor(String fileId) {
        for (int i = 0; i < count; i++) {
            if (fileIds[i].equals(fileId)) {
                System.arraycopy(fileIds, i + 1, fileIds, i, count - i - 1);
                System.arraycopy(colors,  i + 1, colors,  i, count - i - 1);
                count--;
                fileIds[count] = null;
                colors[count]  = null;
                return;
            }
        }
    }

    public void reset() {
        for (int i = 0; i < count; i++) { fileIds[i] = null; colors[i] = null; }
        count = 0;
        paletteIndex = 0;
    }
}
