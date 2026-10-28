package cn.edu.jlu.wechatmerchant.server.ui;

import cn.edu.jlu.wechatmerchant.desktop.LoginPanel;
import cn.edu.jlu.wechatmerchant.server.application.AdminController;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Standalone entry point; contains no management tabs or runtime-status page. */
final class ServerLoginWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    private final LoginPanel form;

    ServerLoginWindow(BiConsumer<String, char[]> submit, Runnable exit) {
        super("微商系统 · 管理员登录");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        form = new LoginPanel(submit, exit, "退出程序");
        setContentPane(createContent(form));
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { exit.run(); }
            @Override public void windowClosed(WindowEvent event) { form.clear(); }
        });
        setSize(470, 400);
        setResizable(false);
        setLocationRelativeTo(null);
    }

    static JPanel createContent(LoginPanel form) {
        JPanel content = new JPanel(new BorderLayout(0, 4));
        JPanel heading = new JPanel(new BorderLayout(0, 8));
        heading.setBorder(BorderFactory.createEmptyBorder(20, 22, 0, 22));
        JLabel title = new JLabel("管理员登录");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        heading.add(title, BorderLayout.NORTH);
        heading.add(new JLabel("请验证账号后进入店铺管理"), BorderLayout.SOUTH);
        content.add(heading, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        return content;
    }

    void showState(AdminController.State state) {
        form.showProgress(state.busy(), state.message());
    }
}
