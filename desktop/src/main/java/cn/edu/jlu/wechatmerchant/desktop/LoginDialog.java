package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.BiConsumer;
import javax.swing.JDialog;

public final class LoginDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private final LoginPanel form;

    public LoginDialog(Window owner, String title, BiConsumer<String, char[]> onSubmit, Runnable onCancel) {
        super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        form = new LoginPanel(onSubmit, () -> { onCancel.run(); dispose(); });
        setContentPane(form);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { onCancel.run(); }
            @Override public void windowClosed(WindowEvent event) { form.clear(); }
        });
        setSize(470, 325);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    public void showProgress(boolean busy, String message) {
        form.showProgress(busy, message);
    }
}
