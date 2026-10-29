package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Shared entry form only; no credentials are stored after submission. */
public final class LoginPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JTextField username = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    private final JButton submit = new JButton("登录");
    private final JLabel status = new JLabel("请输入账号和密码");
    private boolean busy;

    public LoginPanel(BiConsumer<String, char[]> onSubmit, Runnable onCancel) {
        this(onSubmit, onCancel, "取消");
    }

    public LoginPanel(BiConsumer<String, char[]> onSubmit, Runnable onCancel, String cancelLabel) {
        super(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        username.setName("login-username");
        password.setName("login-password");
        submit.setName("login-submit");
        Theme.primary(submit);
        status.putClientProperty("html.disable", Boolean.TRUE);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 7));
        JLabel usernameLabel = new JLabel("账号");
        usernameLabel.setLabelFor(username);
        JLabel passwordLabel = new JLabel("密码");
        passwordLabel.setLabelFor(password);
        fields.add(usernameLabel);
        fields.add(username);
        fields.add(passwordLabel);
        fields.add(password);
        add(fields, BorderLayout.NORTH);
        add(status, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton(cancelLabel);
        cancel.setName("login-cancel");
        cancel.addActionListener(event -> { clear(); onCancel.run(); });
        buttons.add(cancel);
        buttons.add(submit);
        add(buttons, BorderLayout.SOUTH);
        Runnable send = () -> {
            if (busy) return;
            char[] supplied = password.getPassword();
            password.setText("");
            if (username.getText().isBlank() || supplied.length < 1 || supplied.length > 1024
                    || !username.getText().matches("[a-z0-9_]{3,32}")) {
                Arrays.fill(supplied, '\0');
                status.setText("请填写有效账号和密码");
                return;
            }
            // Ownership transfers to the controller; it clears the array in finally.
            onSubmit.accept(username.getText(), supplied);
        };
        submit.addActionListener(event -> send.run());
        password.addActionListener(event -> send.run());
        username.addActionListener(event -> password.requestFocusInWindow());
    }

    public void showProgress(boolean busy, String message) {
        this.busy = busy;
        username.setEnabled(!busy);
        password.setEnabled(!busy);
        submit.setEnabled(!busy);
        status.setText(message);
    }

    public void clear() {
        password.setText("");
        username.setText("");
    }
}
