package cn.edu.jlu.wechatmerchant.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.*;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class PromotionDisplayTest {
    @Test void cartCardsDisplayDiscountStepsAndExactTotalsAndLogoutClearsPrices() throws Exception {
        var images=ProductImages.load();
        SwingUtilities.invokeAndWait(() -> {
            var view=new CartPanel(images);
            var price=new PromotionPrice(1,1290,100,900,1190,1071);
            var cart=new CartSnapshot(1,List.of(new CartSnapshot.Item(1,1001,"商品",1290,20,2,price)));
            var state=new ClientController.State(false,"",new ClientController.CatalogState(ClientController.CatalogStatus.NOT_LOADED,null,0,""),
                    new UserIdentity(1,"buyer_a","买家 A",UserIdentity.Role.BUYER),"",ClientController.Action.NONE,
                    new ClientController.CartState(ClientController.CartStatus.READY,cart,"已加载"));
            view.showState(state);
            String details=((JTextArea)named(view,"cart-promotion-1")).getToolTipText();
            assertTrue(details.contains("直降优惠：¥ 2.00")); assertTrue(details.contains("折扣优惠：¥ 2.38"));
            assertTrue(((JLabel)named(view,"cart-total")).getText().contains("21.42"));
            view.showState(new ClientController.State(false,"",state.catalog()));
            assertEquals("",((JLabel)named(view,"cart-total")).getText());
            assertNull(find(view,"cart-promotion-1"));
        });
    }
    @Test void publicProductShowsActivityAndRefreshRemovesExpiredDiscount() throws Exception {
        var images=ProductImages.load();
        SwingUtilities.invokeAndWait(() -> {
            var view=new ProductListPanel(images);
            for (boolean active:new boolean[]{true,false}) {
                var promotion=active ? new PromotionPrice(1,1290,100,900,1190,1071) : new PromotionPrice(1,1290,0,1000,1290,1290);
                var page=new ProductPage(List.of(new ProductPage.Item(1001,"商品",1290,20,"说明",promotion)),20,0,false);
                view.showState(new ClientController.State(false,"",new ClientController.CatalogState(ClientController.CatalogStatus.READY,page,0,"")));
                String text=((javax.swing.JTextArea)named(view,"product-promotion")).getText();
                assertTrue(text.contains(active ? "每件减 ¥ 1.00，再9折" : "暂无生效优惠"));
                assertTrue(((JLabel)named(view,"product-price")).getText().contains(active ? "10.71" : "12.90"));
            }
        });
    }
    private static Component named(Container parent,String name) {
        Component found=find(parent,name); assertNotNull(found,name); return found;
    }
    private static Component find(Container parent,String name) {
        for (Component child:parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) { var found=find(nested,name); if (found!=null) return found; }
        }
        return null;
    }
}
