package simulator.gui;

import simulator.core.DiskSimulator;
import simulator.model.DiskBlock;
import simulator.util.ColorManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Paints the disk as a grid of colored blocks.
 * Free blocks = light gray | Occupied = file's assigned color.
 * Shows the disk-head position with an arrow indicator.
 */
public class DiskPanel extends JPanel {

    private static final int COLS       = 20;
    private static final int BLOCK_SIZE = 26;
    private static final int GAP        = 2;

    private final DiskSimulator disk;
    private final ColorManager  colors;
    private int headPosition = 0;
    private String hoveredBlock = null; // tooltip info

    public DiskPanel(DiskSimulator disk, ColorManager colors) {
        this.disk   = disk;
        this.colors = colors;
        int rows = (int) Math.ceil((double) DiskSimulator.TOTAL_BLOCKS / COLS);
        int w = COLS * (BLOCK_SIZE + GAP) + GAP + 40;
        int h = rows * (BLOCK_SIZE + GAP) + GAP + 40;
        setPreferredSize(new Dimension(w, h));
        setBackground(new Color(0x1A1A2E));
        setToolTipText("");

        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateTooltip(e.getX(), e.getY()); }
        });
    }

    public void setHeadPosition(int pos) {
        this.headPosition = pos;
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent e) { return hoveredBlock; }

    private void updateTooltip(int mx, int my) {
        int col = (mx - GAP) / (BLOCK_SIZE + GAP);
        int row = (my - GAP) / (BLOCK_SIZE + GAP);
        int id  = row * COLS + col;
        if (id >= 0 && id < DiskSimulator.TOTAL_BLOCKS) {
            DiskBlock b = disk.getBlock(id);
            hoveredBlock = b.isOccupied()
                ? "Bloque " + id + " | Archivo: " + b.getFileId() + " → " + b.getNextBlock()
                : "Bloque " + id + " | Libre";
        } else {
            hoveredBlock = null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        DiskBlock[] blocks = disk.getBlocks();

        for (int i = 0; i < DiskSimulator.TOTAL_BLOCKS; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x   = GAP + col * (BLOCK_SIZE + GAP);
            int y   = GAP + row * (BLOCK_SIZE + GAP);

            DiskBlock b = blocks[i];
            Color fill;
            if (b.isOccupied()) {
                fill = colors.getColor(b.getFileId());
                if (fill == null) fill = new Color(0x4D96FF);
            } else {
                fill = new Color(0x2D2D44);
            }

            // Draw block
            g2.setColor(fill);
            g2.fillRoundRect(x, y, BLOCK_SIZE, BLOCK_SIZE, 4, 4);

            // Border
            if (i == headPosition) {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
            } else {
                g2.setColor(new Color(0x44444A));
                g2.setStroke(new BasicStroke(0.5f));
            }
            g2.drawRoundRect(x, y, BLOCK_SIZE, BLOCK_SIZE, 4, 4);

            // Block number (small)
            g2.setColor(b.isOccupied() ? Color.WHITE : new Color(0x666688));
            g2.setFont(new Font("Monospaced", Font.PLAIN, 7));
            String num = String.valueOf(i);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(num, x + (BLOCK_SIZE - fm.stringWidth(num)) / 2,
                          y + BLOCK_SIZE / 2 + fm.getAscent() / 2 - 1);
        }

        // ── Head indicator ──────────────────────────────────────────────
        if (headPosition >= 0 && headPosition < DiskSimulator.TOTAL_BLOCKS) {
            int col = headPosition % COLS;
            int row = headPosition / COLS;
            int x   = GAP + col * (BLOCK_SIZE + GAP);
            int y   = GAP + row * (BLOCK_SIZE + GAP);

            g2.setColor(Color.YELLOW);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(x - 1, y - 1, BLOCK_SIZE + 2, BLOCK_SIZE + 2, 6, 6);

            // Small triangle on top
            int[] tx = { x + BLOCK_SIZE / 2 - 4, x + BLOCK_SIZE / 2 + 4, x + BLOCK_SIZE / 2 };
            int[] ty = { y - 8, y - 8, y - 2 };
            g2.setColor(Color.YELLOW);
            g2.fillPolygon(tx, ty, 3);
        }

        // ── Stats bar ────────────────────────────────────────────────────
        int rows  = (int) Math.ceil((double) DiskSimulator.TOTAL_BLOCKS / COLS);
        int statsY = GAP + rows * (BLOCK_SIZE + GAP) + 6;
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(new Color(0xAABBCC));
        g2.drawString(
            "Bloques: " + disk.getUsedBlockCount() + " usados / " +
            disk.getFreeBlockCount() + " libres  |  Cabezal: " + headPosition,
            GAP, statsY + 12);
    }
}
