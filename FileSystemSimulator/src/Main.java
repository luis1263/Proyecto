import simulator.gui.MainFrame;
import javax.swing.*;

/**
 * Entry point — sets the Look & Feel and launches the main frame on the EDT.
 */
public class Main {
    public static void main(String[] args) {
        // Try a nice dark-compatible L&F; fall back to system
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception e) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
        }

        SwingUtilities.invokeLater(() -> {
            try {
                new MainFrame();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "Error al iniciar la aplicación:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
