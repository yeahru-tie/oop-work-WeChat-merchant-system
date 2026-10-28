package cn.edu.jlu.wechatmerchant.server.ui;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.desktop.LoginPanel;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ServerLoginWindowContentTest {
    @Test
    void actualStartupContentContainsOnlyLoginAndAnExplicitProgramExit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var form = new LoginPanel((username, password) -> java.util.Arrays.fill(password, '\0'), () -> { }, "退出程序");
            JPanel content = ServerLoginWindow.createContent(form);
            List<Component> components = descendants(content);
            assertTrue(components.stream().noneMatch(component -> component instanceof JTabbedPane));
            assertTrue(components.stream().filter(component -> component instanceof JLabel).map(component -> ((JLabel) component).getText())
                    .noneMatch(text -> text != null && (text.contains("运行状态") || text.contains("交易与售后") || text.contains("优惠公告"))));
            assertTrue(components.stream().anyMatch(component -> "login-password".equals(component.getName())));
            assertTrue(components.stream().anyMatch(component -> component instanceof JButton button && "退出程序".equals(button.getText())));
        });
    }

    private static List<Component> descendants(Container container) {
        List<Component> result = new ArrayList<>();
        for (Component component : container.getComponents()) {
            result.add(component);
            if (component instanceof Container nested) result.addAll(descendants(nested));
        }
        return result;
    }
}
