package simulator.gui;

import simulator.model.FileNode;
import simulator.structures.CustomLinkedList;

import javax.swing.table.AbstractTableModel;
import java.awt.*;

/**
 * JTable model for the File Allocation Table panel.
 * Columns: Color | Name | Owner | Blocks | First Block
 */
public class FileAllocationTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = { "Color", "Nombre", "Dueño", "Bloques", "1er Bloque" };

    // Snapshot arrays — rebuilt on refresh
    private Color[]  rowColors  = new Color[0];
    private String[] rowNames   = new String[0];
    private String[] rowOwners  = new String[0];
    private int[]    rowBlocks  = new int[0];
    private int[]    rowFirst   = new int[0];
    private int rowCount = 0;

    public void refresh(CustomLinkedList<FileNode> files) {
        rowCount  = files.size();
        rowColors = new Color[rowCount];
        rowNames  = new String[rowCount];
        rowOwners = new String[rowCount];
        rowBlocks = new int[rowCount];
        rowFirst  = new int[rowCount];

        int i = 0;
        for (FileNode f : files) {
            rowColors[i] = f.getColor() != null ? f.getColor() : Color.LIGHT_GRAY;
            rowNames[i]  = f.getName();
            rowOwners[i] = f.getOwner();
            rowBlocks[i] = f.getSizeInBlocks();
            rowFirst[i]  = f.getFirstBlock();
            i++;
        }
        fireTableDataChanged();
    }

    @Override public int getRowCount()    { return rowCount; }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }

    @Override
    public Class<?> getColumnClass(int col) {
        return col == 0 ? Color.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (row >= rowCount) return null;
        return switch (col) {
            case 0 -> rowColors[row];
            case 1 -> rowNames[row];
            case 2 -> rowOwners[row];
            case 3 -> String.valueOf(rowBlocks[row]);
            case 4 -> rowFirst[row] >= 0 ? String.valueOf(rowFirst[row]) : "-";
            default -> null;
        };
    }

    /** Returns the color for a given row (used by custom renderer). */
    public Color getRowColor(int row) {
        return row < rowCount ? rowColors[row] : Color.LIGHT_GRAY;
    }
}
