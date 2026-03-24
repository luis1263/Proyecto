package simulator.gui;

import simulator.model.PCB;
import simulator.model.Enums.ProcessState;
import simulator.structures.CustomLinkedList;

import javax.swing.table.AbstractTableModel;
import java.awt.Color;

/**
 * JTable model for the Process Queue panel.
 * Columns: PID | Nombre | Operación | Estado | Archivo | Mensaje
 */
public class ProcessTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = { "PID", "Proceso", "Operación", "Estado", "Archivo", "Info" };

    private int[]    pids    = new int[0];
    private String[] names   = new String[0];
    private String[] ops     = new String[0];
    private String[] states  = new String[0];
    private String[] files   = new String[0];
    private String[] msgs    = new String[0];
    private Color[]  colors  = new Color[0];
    private int rowCount = 0;

    public void refresh(CustomLinkedList<PCB> processes) {
        rowCount = processes.size();
        pids   = new int[rowCount];
        names  = new String[rowCount];
        ops    = new String[rowCount];
        states = new String[rowCount];
        files  = new String[rowCount];
        msgs   = new String[rowCount];
        colors = new Color[rowCount];

        int i = 0;
        for (PCB p : processes) {
            pids[i]   = p.getPid();
            names[i]  = p.getProcessName();
            ops[i]    = p.getOperation().name();
            states[i] = stateLabel(p.getState());
            files[i]  = p.getTargetFileId() != null ? p.getTargetFileId() : "-";
            msgs[i]   = p.getStatusMessage() != null ? p.getStatusMessage() : "";
            colors[i] = stateColor(p.getState());
            i++;
        }
        fireTableDataChanged();
    }

    @Override public int getRowCount()    { return rowCount; }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int c) { return COLUMNS[c]; }

    @Override
    public Object getValueAt(int row, int col) {
        if (row >= rowCount) return null;
        return switch (col) {
            case 0 -> String.valueOf(pids[row]);
            case 1 -> names[row];
            case 2 -> ops[row];
            case 3 -> states[row];
            case 4 -> files[row];
            case 5 -> msgs[row];
            default -> null;
        };
    }

    public Color getRowColor(int row) {
        return row < rowCount ? colors[row] : Color.WHITE;
    }

    private String stateLabel(ProcessState s) {
        return switch (s) {
            case NEW        -> "🆕 Nuevo";
            case READY      -> "🟡 Listo";
            case RUNNING    -> "🟢 Ejecutando";
            case BLOCKED    -> "🔴 Bloqueado";
            case TERMINATED -> "⬛ Terminado";
        };
    }

    private Color stateColor(ProcessState s) {
        return switch (s) {
            case NEW        -> new Color(0xE0E0FF);
            case READY      -> new Color(0xFFFDE7);
            case RUNNING    -> new Color(0xE8F5E9);
            case BLOCKED    -> new Color(0xFFEBEE);
            case TERMINATED -> new Color(0xF5F5F5);
        };
    }
}
