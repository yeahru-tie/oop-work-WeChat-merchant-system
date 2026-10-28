package cn.edu.jlu.wechatmerchant.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.*;
import java.awt.*;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class CouponQuoteExpiryTest {
    @Test void crossedServerBoundaryHidesPayableAndExplicitInvalidCouponNeverFallsBackToOriginalEstimate() throws Exception {
        var images=ProductImages.load();
        SwingUtilities.invokeAndWait(() -> {
            var view=new CartPanel(images);
            var cart=new CartSnapshot(1,List.of(new CartSnapshot.Item(1,1001,"商品",10000,20,2,new PromotionPrice(1,10000,1000,900,9000,8100))));
            var quote=new QuoteSnapshot(cart,1L,"APPLIED",BigInteger.valueOf(2000),BigInteger.valueOf(14200),100,101,"a".repeat(64));
            var identity=new UserIdentity(1,"buyer_a","买家 A",UserIdentity.Role.BUYER);
            var catalog=new ClientController.CatalogState(ClientController.CatalogStatus.NOT_LOADED,null,0,"");
            var state=new ClientController.State(false,"",catalog,identity,"",ClientController.Action.NONE,new ClientController.CartState(ClientController.CartStatus.READY,cart,""),
                    new ClientController.CouponState(null,quote,1L,true,"旧报价",System.nanoTime()-TimeUnit.MILLISECONDS.toNanos(10)));
            view.showState(state);var total=(JTextArea)named(view,"cart-total");assertFalse(total.getText().contains("142.00"));assertTrue(total.getText().contains("时间边界已到"));
            var invalid=new QuoteSnapshot(cart,1L,"EXPIRED",BigInteger.ZERO,null,100,Long.MAX_VALUE,"b".repeat(64));
            view.showState(new ClientController.State(false,"",catalog,identity,"",ClientController.Action.NONE,state.cart(),new ClientController.CouponState(null,invalid,1L,true,"请重新选择",System.nanoTime())));
            assertTrue(total.getText().contains("所选券不可用"));assertFalse(total.getText().contains("预估应付"));assertFalse(total.getText().contains("预估合计"));
        });
    }
    private static Component named(Container parent,String key){for(var child:parent.getComponents()){if(key.equals(child.getName()))return child;if(child instanceof Container c){var value=named(c,key);if(value!=null)return value;}}return null;}
}
