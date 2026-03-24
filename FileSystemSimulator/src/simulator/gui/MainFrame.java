package simulator.gui;

import simulator.core.*;
import simulator.model.*;
import simulator.model.Enums.*;
import simulator.structures.CustomLinkedList;
import simulator.util.JsonManager;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

/**
 * Main application window.
 * Layout:
 *   ┌──────────┬───────────────────────────────────────────────────────┐
 *   │  JTree   │  Tabs: Disco | Asignación | Procesos | Journal | Logs │
 *   │  (left)  ├───────────────────────────────────────────────────────┤
 *   │          │  Bottom toolbar: mode, policy, head, actions          │
 *   └──────────┴───────────────────────────────────────────────────────┘
 */
public class MainFrame extends JFrame {

    // ── Controller ────────────────────────────────────────────────────────────
    private final FileSystemController ctrl = new FileSystemController();

    // ── Left: JTree ───────────────────────────────────────────────────────────
    private DefaultTreeModel   treeModel;
    private DefaultMutableTreeNode treeRoot;
    private JTree              fileTree;
    private JLabel             lblNodeInfo;

    // ── Center tabs ───────────────────────────────────────────────────────────
    private DiskPanel                diskPanel;
    private FileAllocationTableModel fatModel;
    private JTable                   fatTable;
    private ProcessTableModel        procModel;
    private JTable                   procTable;
    private JTextArea                journalArea;
    private JTextArea                lockArea;
    private JTextArea                logArea;

    // ── Toolbar controls ─────────────────────────────────────────────────────
    private JComboBox<String> cbMode;
    private JComboBox<String> cbPolicy;
    private JComboBox<String> cbScanDir;
    private JTextField        tfHead;
    private JLabel            lblHead;
    private JCheckBox         cbCrash;

    // ── Colors ───────────────────────────────────────────────────────────────
    private static final Color BG_DARK   = new Color(0x1A1A2E);
    private static final Color BG_PANEL  = new Color(0x16213E);
    private static final Color BG_CARD   = new Color(0x0F3460);
    private static final Color ACCENT    = new Color(0x4D96FF);
    private static final Color FG        = new Color(0xE0E0FF);

    // ─────────────────────────────────────────────────────────────────────────

    public MainFrame() {
        super("🖥️  Simulador de Sistema de Archivos Concurrente — SO 2526-2");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 820);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        buildUI();
        wireListeners();
        refreshAll();

        setVisible(true);
    }

    // ─── UI Construction ──────────────────────────────────────────────────────

    private void buildUI() {
        setLayout(new BorderLayout(4, 4));

        // ── Top menu bar
        setJMenuBar(buildMenuBar());

        // ── Left: file tree
        JPanel leftPanel = buildTreePanel();
        leftPanel.setPreferredSize(new Dimension(240, 0));

        // ── Center: tabbed pane
        JTabbedPane tabs = buildTabs();

        // ── Bottom: toolbar
        JPanel toolbar = buildToolbar();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, tabs);
        split.setDividerLocation(240);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(BG_DARK);

        add(split, BorderLayout.CENTER);
        add(toolbar, BorderLayout.SOUTH);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        mb.setBackground(BG_PANEL);

        JMenu mFile = darkMenu("Archivo");
        JMenuItem miSave = darkItem("💾 Guardar estado...");
        JMenuItem miLoad = darkItem("📂 Cargar estado...");
        JMenuItem miLoadTC = darkItem("🧪 Cargar caso de prueba...");
        JMenuItem miExit = darkItem("❌ Salir");
        mFile.add(miSave); mFile.add(miLoad); mFile.addSeparator();
        mFile.add(miLoadTC); mFile.addSeparator(); mFile.add(miExit);

        JMenu mSim = darkMenu("Simulación");
        JMenuItem miStart  = darkItem("▶ Iniciar planificador");
        JMenuItem miReset  = darkItem("🔄 Reiniciar sistema");
        JMenuItem miRecover = darkItem("🛠️ Recuperar (Journal)");
        mSim.add(miStart); mSim.add(miReset); mSim.addSeparator(); mSim.add(miRecover);

        mb.add(mFile); mb.add(mSim);

        // Actions
        miSave.addActionListener(e -> saveState());
        miLoad.addActionListener(e -> loadState());
        miLoadTC.addActionListener(e -> loadTestCase());
        miExit.addActionListener(e -> System.exit(0));
        miStart.addActionListener(e -> ctrl.startDiskScheduler());
        miReset.addActionListener(e -> resetSystem());
        miRecover.addActionListener(e -> recover());

        return mb;
    }

    private JPanel buildTreePanel() {
        JPanel p = new JPanel(new BorderLayout(2, 2));
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 4));

        JLabel title = new JLabel("📁 Sistema de Archivos");
        title.setForeground(ACCENT); title.setFont(new Font("SansSerif", Font.BOLD, 12));
        p.add(title, BorderLayout.NORTH);

        treeRoot = new DefaultMutableTreeNode(ctrl.getFs().getRoot());
        treeModel = new DefaultTreeModel(treeRoot);
        fileTree  = new JTree(treeModel);
        fileTree.setBackground(BG_PANEL);
        fileTree.setForeground(FG);
        fileTree.setFont(new Font("SansSerif", Font.PLAIN, 12));
        fileTree.setShowsRootHandles(true);
        fileTree.setCellRenderer(buildTreeRenderer());

        JScrollPane sp = new JScrollPane(fileTree);
        sp.getViewport().setBackground(BG_PANEL);
        sp.setBorder(BorderFactory.createLineBorder(BG_CARD));
        p.add(sp, BorderLayout.CENTER);

        lblNodeInfo = new JLabel(" ");
        lblNodeInfo.setForeground(new Color(0xAABBCC));
        lblNodeInfo.setFont(new Font("Monospaced", Font.PLAIN, 10));
        lblNodeInfo.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        p.add(lblNodeInfo, BorderLayout.SOUTH);

        // Context menu
        fileTree.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e))
                    showTreePopup(e);
                else
                    updateNodeInfo();
            }
        });

        return p;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_PANEL);
        tabs.setForeground(FG);
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // ── Tab 1: Disk visualization
        diskPanel = new DiskPanel(ctrl.getDisk(), ctrl.getColorManager());
        JScrollPane diskScroll = new JScrollPane(diskPanel);
        diskScroll.getViewport().setBackground(BG_DARK);
        diskScroll.setBorder(null);
        tabs.addTab("💽 Disco", diskScroll);

        // ── Tab 2: File Allocation Table
        fatModel = new FileAllocationTableModel();
        fatTable = new JTable(fatModel);
        styleTable(fatTable, fatModel);
        fatTable.getColumnModel().getColumn(0).setMaxWidth(50);
        fatTable.getColumnModel().getColumn(0).setCellRenderer(new ColorSwatch());
        JScrollPane fatScroll = new JScrollPane(fatTable);
        fatScroll.getViewport().setBackground(BG_PANEL);
        fatScroll.setBorder(null);
        tabs.addTab("📋 Asignación", fatScroll);

        // ── Tab 3: Process queue
        procModel = new ProcessTableModel();
        procTable = new JTable(procModel);
        styleTable(procTable, null);
        procTable.getDefaultRenderer(Object.class);
        procTable.setDefaultRenderer(Object.class, new ProcRowRenderer());
        JScrollPane procScroll = new JScrollPane(procTable);
        procScroll.getViewport().setBackground(BG_PANEL);
        procScroll.setBorder(null);
        tabs.addTab("⚙️ Procesos", procScroll);

        // ── Tab 4: Journal
        journalArea = darkTextArea();
        lockArea    = darkTextArea();
        JSplitPane journalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                scrollOf(journalArea, "📒 Journal"), scrollOf(lockArea, "🔒 Locks activos"));
        journalSplit.setDividerLocation(220);
        journalSplit.setBackground(BG_PANEL);
        tabs.addTab("📒 Journal / Locks", journalSplit);

        // ── Tab 5: Event log
        logArea = darkTextArea();
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(null);
        tabs.addTab("📜 Log", logScroll);

        return tabs;
    }

    private JPanel buildToolbar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBackground(BG_CARD);
        p.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        // Mode
        p.add(darkLabel("Modo:"));
        cbMode = new JComboBox<>(new String[]{"ADMIN", "USER"});
        styleCombo(cbMode); p.add(cbMode);

        p.add(sep());

        // Policy
        p.add(darkLabel("Política:"));
        cbPolicy = new JComboBox<>(new String[]{"FIFO", "SSTF", "SCAN", "C-SCAN"});
        styleCombo(cbPolicy); p.add(cbPolicy);

        // Scan direction
        p.add(darkLabel("Dir:"));
        cbScanDir = new JComboBox<>(new String[]{"↑ UP", "↓ DOWN"});
        styleCombo(cbScanDir); p.add(cbScanDir);

        p.add(sep());

        // Head
        p.add(darkLabel("Cabezal:"));
        tfHead = new JTextField("0", 4);
        tfHead.setBackground(BG_PANEL); tfHead.setForeground(FG);
        tfHead.setBorder(BorderFactory.createLineBorder(ACCENT));
        p.add(tfHead);
        lblHead = darkLabel("pos: 0");
        lblHead.setForeground(Color.YELLOW);
        p.add(lblHead);

        JButton btnSetHead = accentButton("Fijar cabezal");
        btnSetHead.addActionListener(e -> setHead());
        p.add(btnSetHead);

        p.add(sep());

        // Crash toggle
        cbCrash = new JCheckBox("Simular fallo");
        cbCrash.setBackground(BG_CARD); cbCrash.setForeground(new Color(0xFF6B6B));
        cbCrash.setFont(new Font("SansSerif", Font.BOLD, 11));
        p.add(cbCrash);

        p.add(sep());

        // Action buttons
        JButton btnCreate   = accentButton("➕ Crear archivo");
        JButton btnDir      = accentButton("📁 Nueva carpeta");
        JButton btnRename   = accentButton("✏️ Renombrar");
        JButton btnDelete   = accentButton("🗑️ Eliminar");
        JButton btnProcess  = accentButton("⚙️ Enviar proceso");
        JButton btnStart    = accentButton("▶ Iniciar");
        JButton btnRecover  = accentButton("🛠 Recuperar");
        JButton btnRefresh  = accentButton("🔄 Refrescar");

        btnCreate.addActionListener(e -> dialogCreateFile());
        btnDir.addActionListener(e -> dialogCreateDir());
        btnRename.addActionListener(e -> dialogRename());
        btnDelete.addActionListener(e -> doDelete());
        btnProcess.addActionListener(e -> dialogSubmitProcess());
        btnStart.addActionListener(e -> ctrl.startDiskScheduler());
        btnRecover.addActionListener(e -> recover());
        btnRefresh.addActionListener(e -> refreshAll());

        for (JButton b : new JButton[]{btnCreate, btnDir, btnRename, btnDelete,
                                       btnProcess, btnStart, btnRecover, btnRefresh})
            p.add(b);

        // Mode change listener
        cbMode.addActionListener(e -> {
            ctrl.setMode(cbMode.getSelectedIndex() == 0 ? UserMode.ADMIN : UserMode.USER);
            boolean admin = ctrl.isAdmin();
            btnCreate.setEnabled(admin); btnDir.setEnabled(admin);
            btnRename.setEnabled(admin); btnDelete.setEnabled(admin);
        });

        // Policy change listener
        cbPolicy.addActionListener(e -> {
            SchedulingPolicy p2 = switch (cbPolicy.getSelectedIndex()) {
                case 1 -> SchedulingPolicy.SSTF;
                case 2 -> SchedulingPolicy.SCAN;
                case 3 -> SchedulingPolicy.CSCAN;
                default -> SchedulingPolicy.FIFO;
            };
            ctrl.setSchedulingPolicy(p2);
        });

        cbScanDir.addActionListener(e ->
            ctrl.setScanDirection(cbScanDir.getSelectedIndex() == 0 ?
                ScanDirection.UP : ScanDirection.DOWN));

        cbCrash.addActionListener(e -> ctrl.setSimulateCrash(cbCrash.isSelected()));

        return p;
    }

    // ─── Wire Listeners ───────────────────────────────────────────────────────

    private void wireListeners() {

        ctrl.setSchedulerListener(new DiskScheduler.SchedulerListener() {
            @Override public void onHeadMoved(int pos, DiskRequest r) {
                SwingUtilities.invokeLater(() -> {
                    diskPanel.setHeadPosition(pos);
                    lblHead.setText("pos: " + pos);
                });
            }
            @Override public void onRequestProcessed(DiskRequest r, CustomLinkedList<Integer> order) {
                SwingUtilities.invokeLater(() -> {
                    appendLog("💽 Req procesada: bloque=" + r.getBlockPosition()
                            + " op=" + r.getOperation() + " PID=" + r.getProcessPid());
                    refreshDisk();
                });
            }
            @Override public void onQueueChanged(CustomLinkedList<DiskRequest> pending) {
                SwingUtilities.invokeLater(() -> appendLog("Cola disco: " + pending.size() + " pendientes"));
            }
            @Override public void onSchedulerFinished() {
                SwingUtilities.invokeLater(() -> {
                    appendLog("✅ Planificador terminó.");
                    refreshAll();
                });
            }
        });

        ctrl.setProcessListener(new ProcessManager.ProcessListener() {
            @Override public void onProcessAdded(PCB p) {
                SwingUtilities.invokeLater(() -> { refreshProcesses(); appendLog("🆕 Proceso: " + p.getProcessName()); });
            }
            @Override public void onProcessStateChanged(PCB p) {
                SwingUtilities.invokeLater(() -> { refreshProcesses(); refreshTree(); refreshDisk(); refreshFAT(); });
            }
            @Override public void onAllProcessesFinished() {
                SwingUtilities.invokeLater(() -> { appendLog("✅ Todos los procesos terminaron."); refreshAll(); });
            }
            @Override public void onLogMessage(String msg) {
                SwingUtilities.invokeLater(() -> appendLog(msg));
            }
        });

        ctrl.setJournalListener(entries -> SwingUtilities.invokeLater(this::refreshJournal));

        ctrl.setLockListener(locks -> SwingUtilities.invokeLater(this::refreshLocks));
    }

    // ─── Dialogs ─────────────────────────────────────────────────────────────

    private void dialogCreateFile() {
        JTextField tfName   = new JTextField(12);
        JTextField tfBlocks = new JTextField("4", 5);
        JTextField tfOwner  = new JTextField("admin", 8);
        Object[] msg = { "Nombre del archivo:", tfName, "Bloques:", tfBlocks, "Dueño:", tfOwner };
        int r = JOptionPane.showConfirmDialog(this, msg, "Crear Archivo", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return;

        String name = tfName.getText().trim();
        String owner = tfOwner.getText().trim();
        int blocks;
        try { blocks = Integer.parseInt(tfBlocks.getText().trim()); }
        catch (NumberFormatException e) { error("Número de bloques inválido."); return; }
        if (name.isEmpty()) { error("El nombre no puede estar vacío."); return; }
        if (blocks <= 0 || blocks > DiskSimulator.TOTAL_BLOCKS) {
            error("Bloques deben ser entre 1 y " + DiskSimulator.TOTAL_BLOCKS); return; }

        String parentId = getSelectedDirId();
        FileNode created = ctrl.createFile(parentId, name, blocks, owner);
        if (created == null)
            error(ctrl.isSimulateCrash() ? "❌ CRASH simulado — entrada PENDIENTE en Journal."
                    : "❌ No se pudo crear el archivo (sin espacio o nombre duplicado).");
        else appendLog("✅ Creado: " + name);

        refreshAll();
    }

    private void dialogCreateDir() {
        String name = JOptionPane.showInputDialog(this, "Nombre del directorio:", "Nueva Carpeta",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        String parentId = getSelectedDirId();
        FileNode dir = ctrl.createDirectory(parentId, name.trim(), "admin");
        if (dir == null) error("No se pudo crear el directorio.");
        refreshAll();
    }

    private void dialogRename() {
        FileNode selected = getSelectedNode();
        if (selected == null) { error("Selecciona un nodo en el árbol."); return; }
        String newName = JOptionPane.showInputDialog(this, "Nuevo nombre:", selected.getName());
        if (newName == null || newName.trim().isEmpty()) return;
        if (!ctrl.rename(selected.getId(), newName.trim())) error("No se pudo renombrar.");
        refreshAll();
    }

    private void doDelete() {
        FileNode selected = getSelectedNode();
        if (selected == null || selected == ctrl.getFs().getRoot()) {
            error("Selecciona un archivo o directorio (no el root)."); return;
        }
        int r = JOptionPane.showConfirmDialog(this,
                "¿Eliminar «" + selected.getName() + "»?", "Confirmar", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        ctrl.deleteNode(selected.getId());
        refreshAll();
    }

    private void dialogSubmitProcess() {
        String[] ops = { "READ", "UPDATE", "DELETE" };
        JComboBox<String> cbOp = new JComboBox<>(ops);
        Object[] msg = { "Operación:", cbOp };
        FileNode selected = getSelectedNode();
        if (selected == null || selected.isDirectory()) {
            error("Selecciona un archivo primero."); return;
        }
        int r = JOptionPane.showConfirmDialog(this, msg, "Enviar Proceso I/O", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) return;

        OperationType op;
        try { op = OperationType.valueOf((String) cbOp.getSelectedItem()); }
        catch (Exception e) { op = OperationType.READ; }
        ctrl.submitIOProcess(op, selected.getId(), "admin", 0, "");
        appendLog("⚙️ Proceso enviado: " + op + " sobre " + selected.getName());
    }

    // ─── Refresh helpers ─────────────────────────────────────────────────────

    private void refreshAll() {
        refreshTree();
        refreshDisk();
        refreshFAT();
        refreshProcesses();
        refreshJournal();
        refreshLocks();
    }

    private void refreshTree() {
        treeRoot.removeAllChildren();
        buildTreeNode(treeRoot, ctrl.getFs().getRoot());
        treeModel.reload();
        expandAll(fileTree);
    }

    private void buildTreeNode(DefaultMutableTreeNode parent, FileNode fsNode) {
        for (FileNode child : fsNode.getChildren()) {
            DefaultMutableTreeNode tNode = new DefaultMutableTreeNode(child);
            parent.add(tNode);
            if (child.isDirectory()) buildTreeNode(tNode, child);
        }
    }

    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void refreshDisk() {
        diskPanel.repaint();
    }

    private void refreshFAT() {
        fatModel.refresh(ctrl.getFs().getAllFiles());
    }

    private void refreshProcesses() {
        procModel.refresh(ctrl.getProcessManager().getAllProcesses());
    }

    private void refreshJournal() {
        StringBuilder sb = new StringBuilder();
        for (JournalEntry e : ctrl.getJournal().getEntries()) {
            sb.append(String.format("[%d] %s  %s  %s%n",
                    e.getId(), e.getStatusLabel(),
                    e.getOperation(), e.getDescription()));
        }
        journalArea.setText(sb.toString());
        journalArea.setCaretPosition(journalArea.getDocument().getLength());
    }

    private void refreshLocks() {
        StringBuilder sb = new StringBuilder();
        for (LockManager.LockInfo li : ctrl.getLockManager().getActiveLocks()) {
            sb.append(li.toString()).append("\n");
        }
        lockArea.setText(sb.isEmpty() ? "(sin locks activos)" : sb.toString());
    }

    private void appendLog(String msg) {
        logArea.append("[" + java.time.LocalTime.now().withNano(0) + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // ─── Selection helpers ───────────────────────────────────────────────────

    private FileNode getSelectedNode() {
        TreePath path = fileTree.getSelectionPath();
        if (path == null) return null;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object uo = node.getUserObject();
        return uo instanceof FileNode ? (FileNode) uo : null;
    }

    private String getSelectedDirId() {
        FileNode sel = getSelectedNode();
        if (sel == null) return ctrl.getFs().getRoot().getId();
        if (sel.isDirectory()) return sel.getId();
        if (sel.getParent() != null) return sel.getParent().getId();
        return ctrl.getFs().getRoot().getId();
    }

    private void updateNodeInfo() {
        FileNode n = getSelectedNode();
        if (n == null) { lblNodeInfo.setText(" "); return; }
        lblNodeInfo.setText("<html>" + n.getName() + "<br>Dueño: " + n.getOwner()
                + "<br>Bloques: " + n.getSizeInBlocks()
                + (n.getFirstBlock() >= 0 ? "<br>1er bloque: " + n.getFirstBlock() : "") + "</html>");
    }

    // ─── Context menu ────────────────────────────────────────────────────────

    private void showTreePopup(MouseEvent e) {
        TreePath path = fileTree.getPathForLocation(e.getX(), e.getY());
        if (path != null) fileTree.setSelectionPath(path);
        JPopupMenu pop = new JPopupMenu();
        pop.setBackground(BG_CARD);

        JMenuItem miCreate = darkItem("➕ Crear archivo aquí");
        JMenuItem miDir    = darkItem("📁 Nueva carpeta aquí");
        JMenuItem miRename = darkItem("✏️ Renombrar");
        JMenuItem miDelete = darkItem("🗑️ Eliminar");
        JMenuItem miRead   = darkItem("📖 Enviar proceso READ");
        JMenuItem miUpdate = darkItem("✏️ Enviar proceso UPDATE");

        miCreate.addActionListener(ev -> dialogCreateFile());
        miDir.addActionListener(ev -> dialogCreateDir());
        miRename.addActionListener(ev -> dialogRename());
        miDelete.addActionListener(ev -> doDelete());
        miRead.addActionListener(ev -> {
            FileNode sel = getSelectedNode();
            if (sel != null && sel.isFile())
                ctrl.submitIOProcess(OperationType.READ, sel.getId(), "user", 0, "");
        });
        miUpdate.addActionListener(ev -> {
            FileNode sel = getSelectedNode();
            if (sel != null && sel.isFile())
                ctrl.submitIOProcess(OperationType.UPDATE, sel.getId(), "admin", 0, "");
        });

        boolean admin = ctrl.isAdmin();
        miCreate.setEnabled(admin); miDir.setEnabled(admin);
        miRename.setEnabled(admin); miDelete.setEnabled(admin);

        pop.add(miCreate); pop.add(miDir); pop.addSeparator();
        pop.add(miRename); pop.add(miDelete); pop.addSeparator();
        pop.add(miRead); pop.add(miUpdate);
        pop.show(fileTree, e.getX(), e.getY());
    }

    // ─── Save / Load ─────────────────────────────────────────────────────────

    private void saveState() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("filesystem_state.json"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            ctrl.saveState(fc.getSelectedFile().getAbsolutePath());
            appendLog("💾 Estado guardado: " + fc.getSelectedFile().getName());
        } catch (Exception ex) { error("Error al guardar: " + ex.getMessage()); }
    }

    private void loadState() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            ctrl.loadState(fc.getSelectedFile().getAbsolutePath());
            refreshAll();
            appendLog("📂 Estado cargado: " + fc.getSelectedFile().getName());
        } catch (Exception ex) { error("Error al cargar: " + ex.getMessage()); }
    }

    private void loadTestCase() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            // Ask for policy first
            String[] policies = {"FIFO", "SSTF", "SCAN", "C-SCAN"};
            String sel = (String) JOptionPane.showInputDialog(this,
                    "Selecciona política de planificación:", "Caso de Prueba",
                    JOptionPane.PLAIN_MESSAGE, null, policies, policies[0]);
            if (sel == null) return;
            SchedulingPolicy p = switch (sel) {
                case "SSTF"  -> SchedulingPolicy.SSTF;
                case "SCAN"  -> SchedulingPolicy.SCAN;
                case "C-SCAN"-> SchedulingPolicy.CSCAN;
                default      -> SchedulingPolicy.FIFO;
            };
            ctrl.loadTestCase(fc.getSelectedFile().getAbsolutePath());
            ctrl.setSchedulingPolicy(p);
            refreshAll();
            appendLog("🧪 Caso de prueba cargado: " + fc.getSelectedFile().getName());
        } catch (Exception ex) { error("Error: " + ex.getMessage()); }
    }

    private void setHead() {
        try {
            int pos = Integer.parseInt(tfHead.getText().trim());
            if (pos < 0 || pos >= DiskSimulator.TOTAL_BLOCKS) throw new NumberFormatException();
            ctrl.setInitialHead(pos);
            diskPanel.setHeadPosition(pos);
            lblHead.setText("pos: " + pos);
        } catch (NumberFormatException e) {
            error("Posición inválida (0–" + (DiskSimulator.TOTAL_BLOCKS - 1) + ")");
        }
    }

    private void resetSystem() {
        int r = JOptionPane.showConfirmDialog(this, "¿Reiniciar todo el sistema?",
                "Reiniciar", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        int head = 0;
        try { head = Integer.parseInt(tfHead.getText().trim()); } catch (Exception ignored) {}
        SchedulingPolicy p = switch (cbPolicy.getSelectedIndex()) {
            case 1 -> SchedulingPolicy.SSTF;
            case 2 -> SchedulingPolicy.SCAN;
            case 3 -> SchedulingPolicy.CSCAN;
            default -> SchedulingPolicy.FIFO;
        };
        ctrl.reset(head, p);
        wireListeners();
        refreshAll();
        appendLog("🔄 Sistema reiniciado.");
    }

    private void recover() {
        CustomLinkedList<JournalEntry> undone = ctrl.recover();
        if (undone.isEmpty()) {
            JOptionPane.showMessageDialog(this, "✅ No hay entradas pendientes en el Journal.",
                    "Recuperación", JOptionPane.INFORMATION_MESSAGE);
        } else {
            StringBuilder sb = new StringBuilder("Entradas deshechas:\n");
            for (JournalEntry e : undone)
                sb.append("  [").append(e.getId()).append("] ").append(e.getDescription()).append("\n");
            JOptionPane.showMessageDialog(this, sb.toString(), "Recuperación completada",
                    JOptionPane.WARNING_MESSAGE);
        }
        refreshAll();
    }

    // ─── Style helpers ────────────────────────────────────────────────────────

    private JLabel darkLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(FG); l.setFont(new Font("SansSerif", Font.PLAIN, 11)); return l;
    }

    private JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(BG_CARD); b.setForeground(ACCENT);
        b.setBorder(BorderFactory.createLineBorder(ACCENT));
        b.setFocusPainted(false); b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void styleCombo(JComboBox<?> cb) {
        cb.setBackground(BG_PANEL); cb.setForeground(FG);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 11));
    }

    private JTextArea darkTextArea() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false); ta.setBackground(BG_PANEL);
        ta.setForeground(new Color(0xAADDFF)); ta.setCaretColor(FG);
        ta.setFont(new Font("Monospaced", Font.PLAIN, 11));
        return ta;
    }

    private JScrollPane scrollOf(JTextArea ta, String title) {
        JScrollPane sp = new JScrollPane(ta);
        sp.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ACCENT), title,
                TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 11), ACCENT));
        sp.getViewport().setBackground(BG_PANEL);
        return sp;
    }

    private JMenu darkMenu(String text) {
        JMenu m = new JMenu(text);
        m.setForeground(FG); m.setFont(new Font("SansSerif", Font.PLAIN, 12)); return m;
    }

    private JMenuItem darkItem(String text) {
        JMenuItem mi = new JMenuItem(text);
        mi.setBackground(BG_PANEL); mi.setForeground(FG);
        mi.setFont(new Font("SansSerif", Font.PLAIN, 11)); return mi;
    }

    private JSeparator sep() {
        JSeparator s = new JSeparator(JSeparator.VERTICAL);
        s.setPreferredSize(new Dimension(1, 24));
        s.setForeground(new Color(0x3A3A5A)); return s;
    }

    private void styleTable(JTable table, FileAllocationTableModel model) {
        table.setBackground(BG_PANEL); table.setForeground(FG);
        table.setGridColor(new Color(0x2A2A4A));
        table.setSelectionBackground(BG_CARD);
        table.setFont(new Font("SansSerif", Font.PLAIN, 11));
        table.getTableHeader().setBackground(BG_CARD);
        table.getTableHeader().setForeground(ACCENT);
        table.setRowHeight(22);
        table.setShowHorizontalLines(true);
    }

    private TreeCellRenderer buildTreeRenderer() {
        return new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                setBackground(selected ? BG_CARD : BG_PANEL);
                setForeground(selected ? Color.WHITE : FG);
                setBorderSelectionColor(ACCENT);
                setBackgroundSelectionColor(BG_CARD);
                setBackgroundNonSelectionColor(BG_PANEL);

                if (value instanceof DefaultMutableTreeNode dtn && dtn.getUserObject() instanceof FileNode fn) {
                    if (fn.isDirectory()) setIcon(UIManager.getIcon("FileView.directoryIcon"));
                    else                  setIcon(UIManager.getIcon("FileView.fileIcon"));
                }
                return this;
            }
        };
    }

    // ─── Custom table renderers ───────────────────────────────────────────────

    private static class ColorSwatch extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean foc, int row, int col) {
            JLabel l = new JLabel();
            l.setOpaque(true);
            l.setBackground(v instanceof Color c ? c : Color.GRAY);
            return l;
        }
    }

    private class ProcRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                boolean foc, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            c.setBackground(procModel.getRowColor(row));
            c.setForeground(Color.BLACK);
            return c;
        }
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
