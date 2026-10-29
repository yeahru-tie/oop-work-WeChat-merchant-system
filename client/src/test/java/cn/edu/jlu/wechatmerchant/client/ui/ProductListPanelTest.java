package cn.edu.jlu.wechatmerchant.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.client.application.ClientController.CatalogState;
import cn.edu.jlu.wechatmerchant.client.application.ClientController.CatalogStatus;
import cn.edu.jlu.wechatmerchant.client.application.ClientController.State;
import cn.edu.jlu.wechatmerchant.protocol.ProductPage;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductListPanelTest {
    @Test
    void guestJoinOnlyRequestsLoginAndSignedInJoinPreservesSelectionAndQuantity() throws Exception {
        ProductImages images=ProductImages.load();
        SwingUtilities.invokeAndWait(() -> {
            var panel=new ProductListPanel(images); var actions=new ArrayList<String>();
            panel.bindCartAction((id,q) -> actions.add(id+":"+q),() -> actions.add("login"));
            var catalog=new CatalogState(CatalogStatus.READY,new ProductPage(List.of(new ProductPage.Item(1001,"商品",100,5,"")),20,0,false),0,"已加载");
            panel.showState(new State(false,"",catalog));
            var quantity=(javax.swing.JSpinner)named(panel,"product-quantity"); quantity.setValue(3);
            ((JButton)named(panel,"add-to-cart")).doClick(); assertEquals(List.of("login"),actions);
            var buyer=new cn.edu.jlu.wechatmerchant.protocol.UserIdentity(1,"buyer_a","买家 A",cn.edu.jlu.wechatmerchant.protocol.UserIdentity.Role.BUYER);
            var cart=new ClientController.CartState(ClientController.CartStatus.READY,new cn.edu.jlu.wechatmerchant.protocol.CartSnapshot(0,List.of()),"已加载");
            panel.showState(new State(false,"",catalog,buyer,"已登录",ClientController.Action.NONE,cart));
            assertEquals(3,quantity.getValue()); assertEquals(1,actions.size());
            ((JButton)named(panel,"add-to-cart")).doClick(); assertEquals(List.of("login","1001:3"),actions);
            panel.showState(new State(false,"",catalog,buyer,"已登录",ClientController.Action.NONE,
                    new ClientController.CartState(ClientController.CartStatus.UNCERTAIN,null,"先刷新核对")));
            assertFalse(named(panel,"add-to-cart").isEnabled());
        });
    }
    @ParameterizedTest
    @CsvSource({"0,¥ 0.00", "1,¥ 0.01", "1290,¥ 12.90", "9007199254740993,¥ 90071992547409.93",
            "9223372036854775807,¥ 92233720368547758.07"})
    void formatsIntegerCentsWithoutBinaryRounding(long cents, String expected) {
        assertEquals(expected, ProductListPanel.priceText(cents));
    }

    @Test
    void refusesNegativeDisplayAmounts() {
        assertThrows(IllegalArgumentException.class, () -> ProductListPanel.priceText(-1));
    }

    @Test
    void cardInputsSurviveProbeAndFailedOrEmptyQueriesDoNotLeaveStaleProducts() throws Exception {
        ProductImages images = ProductImages.load();
        SwingUtilities.invokeAndWait(() -> {
            ProductListPanel panel = new ProductListPanel(images);
            var first = new ProductPage.Item(1001, "演示笔记本", 1290, 20, "第一条说明");
            var second = new ProductPage.Item(1002, "演示水杯", 2590, 15, "<html>这段是普通文本</html>");
            var catalog = new CatalogState(CatalogStatus.READY,
                    new ProductPage(List.of(first, second), 20, 20, false), 20, "已加载本页 2 件商品");
            panel.showState(new State(false, "尚未测试连接", catalog));
            Container grid = (Container) named(panel, "product-cards");
            Container secondCard = (Container) named(panel, "product-card-1002");
            JTextArea description = (JTextArea) named(secondCard, "product-description");
            var quantity=(javax.swing.JSpinner)named(secondCard,"product-quantity");quantity.setValue(4);
            assertEquals(2, grid.getComponentCount());
            assertEquals(second.description(), description.getText());
            panel.showState(new State(true, "正在测试连接…", catalog));
            assertSame(secondCard,named(panel,"product-card-1002"));assertEquals(4,quantity.getValue());
            assertFalse(named(panel, "refresh-products").isEnabled());
            assertFalse(named(panel, "previous-products").isEnabled());
            panel.showState(new State(false, "通信测试通过",
                    new CatalogState(CatalogStatus.ERROR, null, 20, "商品查询失败")));
            assertNull(find(panel,"product-card-1002"));
            assertTrue(named(panel, "refresh-products").isEnabled());
            assertTrue(named(panel, "previous-products").isEnabled());
            assertFalse(named(panel, "next-products").isEnabled());
            assertTrue(labels(panel).stream().anyMatch(label -> "商品查询失败".equals(label.getText())));
            panel.showState(new State(false, "通信测试通过", new CatalogState(CatalogStatus.EMPTY,
                    new ProductPage(List.of(), 20, 0, false), 0, "暂无商品")));
            assertNull(find(panel,"product-card-1001"));
            assertFalse(named(panel, "previous-products").isEnabled());
            assertTrue(labels(panel).stream().anyMatch(label -> "暂无商品".equals(label.getText())));
        });
    }

    @Test
    void remoteProductNamesAreDisplayedAsPlainText() throws Exception {
        ProductImages images = ProductImages.load();
        SwingUtilities.invokeAndWait(() -> {
            ProductListPanel panel = new ProductListPanel(images);
            var item = new ProductPage.Item(9999, "<html><b>商品名称</b></html>", 1, 0, "");
            panel.showState(new State(false, "", new CatalogState(CatalogStatus.READY,
                    new ProductPage(List.of(item), 20, 0, false), 0, "已加载")));
            JLabel title = labels(panel).stream().filter(label -> item.name().equals(label.getText())).findFirst().orElseThrow();
            assertEquals(Boolean.TRUE, title.getClientProperty("html.disable"));
            assertNull(title.getClientProperty("html"));
        });
    }

    private static Component named(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container nested) {
                Component found = find(nested, name);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Missing component: " + name);
    }

    private static Component find(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container nested) {
                Component found = find(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<JLabel> labels(Container container) {
        List<JLabel> result = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label) result.add(label);
            if (component instanceof Container nested) result.addAll(labels(nested));
        }
        return result;
    }
}
