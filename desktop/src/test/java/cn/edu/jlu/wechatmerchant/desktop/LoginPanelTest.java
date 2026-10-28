package cn.edu.jlu.wechatmerchant.desktop;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LoginPanelTest {
    @Test
    void standaloneEntryCanExitWhileBusyAndClearsCredentialsBeforeItsCallback() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger exits = new AtomicInteger();
            final LoginPanel[] owner = new LoginPanel[1];
            LoginPanel panel = new LoginPanel((username, password) -> fail("Exit must not submit credentials"), () -> {
                assertEquals("", ((JTextField) find(owner[0], "login-username")).getText());
                assertEquals(0, ((JPasswordField) find(owner[0], "login-password")).getPassword().length);
                exits.incrementAndGet();
            }, "退出程序");
            owner[0] = panel;
            ((JTextField) find(panel, "login-username")).setText("shop_admin");
            ((JPasswordField) find(panel, "login-password")).setText("test-password");
            panel.showProgress(true, "正在验证");
            JButton exit = (JButton) find(panel, "login-cancel");
            assertEquals("退出程序", exit.getText());
            assertTrue(exit.isEnabled());
            exit.doClick();
            assertEquals(1, exits.get());
        });
    }

    @Test
    void submissionClearsTheVisiblePasswordAndBusyStatePreventsDuplicates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger attempts = new AtomicInteger();
            LoginPanel panel = new LoginPanel((username, supplied) -> {
                assertEquals("buyer_a", username);
                assertArrayEquals("test-password".toCharArray(), supplied);
                java.util.Arrays.fill(supplied, '\0');
                attempts.incrementAndGet();
            }, () -> { });
            JTextField username = (JTextField) find(panel, "login-username");
            JPasswordField password = (JPasswordField) find(panel, "login-password");
            JButton submit = (JButton) find(panel, "login-submit");
            username.setText("buyer_a");
            password.setText("test-password");
            submit.doClick();
            assertEquals(1, attempts.get());
            assertEquals(0, password.getPassword().length);
            panel.showProgress(true, "正在登录");
            password.setText("test-password");
            password.postActionEvent();
            submit.doClick();
            assertEquals(1, attempts.get());
            panel.clear();
            assertEquals(0, password.getPassword().length);
            assertEquals("", username.getText());
        });
    }

    @Test
    void invalidFieldsDoNotSendCredentials() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LoginPanel panel = new LoginPanel((username, password) -> fail("Invalid fields must not submit"), () -> { });
            ((JTextField) find(panel, "login-username")).setText("' OR 1=1 --");
            JPasswordField password = (JPasswordField) find(panel, "login-password");
            password.setText("test-password");
            ((JButton) find(panel, "login-submit")).doClick();
            assertEquals(0, password.getPassword().length);
        });
    }

    private static Component find(Container owner, String name) {
        for (Component component : owner.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container nested) {
                Component found = find(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
