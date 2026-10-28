package cn.edu.jlu.wechatmerchant.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.ArrayList;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class CartPanelTest {
    private final ProductImages images = ProductImages.load();
    private UserIdentity buyer() { return new UserIdentity(1,"buyer_a","买家 A",UserIdentity.Role.BUYER); }
    private CartSnapshot contents() {
        return new CartSnapshot(1,List.of(new CartSnapshot.Item(5,1001,"<html>普通商品文本",Long.MAX_VALUE,1,2)));
    }
    private ClientController.State state(UserIdentity user,ClientController.CartStatus status,CartSnapshot contents,
            boolean busy,ClientController.Action action) {
        return new ClientController.State(busy,"",new ClientController.CatalogState(ClientController.CatalogStatus.NOT_LOADED,null,0,""),
                user,"",action,new ClientController.CartState(status,contents,"状态提示"));
    }
    private ClientController.State ready(CartSnapshot contents) {
        return state(buyer(),ClientController.CartStatus.READY,contents,false,ClientController.Action.NONE);
    }
    @Test void cardsDisplayExactAmountsAndAutoSaveWithoutGlobalSelectionOrSaveButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel=new CartPanel(images); var edits=new ArrayList<String>();
            panel.bindActions(() -> {},() -> {},(id,q) -> edits.add(id+":"+q));
            panel.showState(ready(contents()));
            assertEquals(1,((Container)named(panel,"cart-cards")).getComponentCount());
            assertEquals("<html>普通商品文本",((JTextArea)named(panel,"cart-name-5")).getText());
            assertEquals("原价小计：¥ 184467440737095516.14\n预估小计：¥ 184467440737095516.14",((JTextArea)named(panel,"cart-subtotal-5")).getText());
            assertTrue(labels(panel).stream().anyMatch(l -> l.getText().contains("库存不足")));
            assertNull(find(panel,"save-cart-quantity"));
            assertNull(find(panel,"remove-cart-item"));
            assertNull(find(panel,"cart-table"));
            ((JSpinner)named(panel,"cart-quantity-5")).setValue(1);
            ((JButton)named(panel,"remove-cart-item-5")).doClick();
            assertEquals(List.of("5:1","5:null"),edits);
        });
    }
    @Test void keyboardCommitAndFocusLossSaveOnceAndInvalidInputDoesNotWrite() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel=new CartPanel(images); var edits=new ArrayList<Integer>();
            panel.bindActions(() -> {},() -> {},(id,q) -> edits.add(q));
            panel.showState(ready(contents()));
            var spinner=(JSpinner)named(panel,"cart-quantity-5");
            var field=((JSpinner.NumberEditor)spinner.getEditor()).getTextField();
            for(String invalid:List.of("0","-1","2147483648","abc")) {
                field.setText(invalid); field.postActionEvent();
                assertTrue(edits.isEmpty());
                assertEquals(2,spinner.getValue());
                assertFalse(field.getText().equals(invalid));
            }
            field.setText("1"); field.postActionEvent(); field.postActionEvent();
            assertEquals(List.of(1),edits);
            panel.showState(ready(new CartSnapshot(2,List.of(new CartSnapshot.Item(5,1001,"商品",100,5,1)))));
            field.setText("3");
            for(var listener:field.getFocusListeners()) listener.focusLost(new FocusEvent(field,FocusEvent.FOCUS_LOST));
            assertEquals(List.of(1,3),edits);
            var remove=(JButton)named(panel,"remove-cart-item-5");
            field.setText("4");
            for(var listener:field.getFocusListeners()) listener.focusLost(new FocusEvent(field,FocusEvent.FOCUS_LOST,false,remove));
            assertEquals(List.of(1,3),edits);
            remove.doClick(); assertEquals(3,edits.size()); assertNull(edits.getLast());
        });
    }
    @Test void inFlightWriteKeepsDisabledCardsThenFailureClearsAllOldContent() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel=new CartPanel(images); var edits=new ArrayList<String>();
            panel.bindActions(() -> {},() -> {},(id,q) -> edits.add(id+":"+q));
            panel.showState(ready(contents()));
            panel.showState(state(buyer(),ClientController.CartStatus.LOADING,null,true,ClientController.Action.CART_WRITE));
            assertEquals(1,((Container)named(panel,"cart-cards")).getComponentCount());
            assertFalse(named(panel,"cart-quantity-5").isEnabled());
            assertFalse(named(panel,"remove-cart-item-5").isEnabled());
            ((JButton)named(panel,"remove-cart-item-5")).doClick();
            assertTrue(edits.isEmpty());
            assertTrue(((JTextArea)named(panel,"cart-total")).getText().contains("正在更新"));
            panel.showState(state(buyer(),ClientController.CartStatus.UNCERTAIN,null,false,ClientController.Action.NONE));
            assertEquals(0,((Container)named(panel,"cart-cards")).getComponentCount());
            assertEquals("",((JTextArea)named(panel,"cart-total")).getText());
            assertTrue(named(panel,"refresh-cart").isEnabled());
        });
    }
    @Test void logoutAndAccountSwitchRemovePersonalCardsAndIgnoreDetachedCallbacks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel=new CartPanel(images); var edits=new ArrayList<String>();
            panel.bindActions(() -> {},() -> {},(id,q) -> edits.add(id+":"+q));
            panel.showState(ready(contents()));
            var oldQuantity=(JSpinner)named(panel,"cart-quantity-5");
            var oldRemove=(JButton)named(panel,"remove-cart-item-5");
            panel.showState(state(null,ClientController.CartStatus.GUEST,null,true,ClientController.Action.LOGOUT));
            assertEquals(0,((Container)named(panel,"cart-cards")).getComponentCount());
            assertEquals("",((JTextArea)named(panel,"cart-total")).getText());
            var b=new UserIdentity(2,"buyer_b","买家 B",UserIdentity.Role.BUYER);
            panel.showState(state(b,ClientController.CartStatus.READY,contents(),false,ClientController.Action.NONE));
            assertNotSame(oldQuantity,named(panel,"cart-quantity-5"));
            oldQuantity.setValue(1); oldRemove.setEnabled(true); oldRemove.doClick();
            assertTrue(edits.isEmpty());
        });
    }
    @Test void guestCanRequestLoginAndEmptyOrUncertainStatesNeverLeavePrivateCards() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel=new CartPanel(images); var actions=new ArrayList<String>();
            panel.bindActions(() -> actions.add("login"),() -> actions.add("refresh"),(id,q) -> actions.add("edit"));
            panel.showState(state(null,ClientController.CartStatus.GUEST,null,false,ClientController.Action.NONE));
            ((JButton)named(panel,"cart-login")).doClick();
            assertEquals(List.of("login"),actions);
            assertFalse(named(panel,"refresh-cart").isEnabled());
            panel.showState(ready(contents()));
            panel.showState(ready(new CartSnapshot(2,List.of())));
            assertEquals(0,((Container)named(panel,"cart-cards")).getComponentCount());
            assertTrue(((JTextArea)named(panel,"cart-total")).getText().contains("0.00"));
        });
    }
    @Test void authoritativeUpdatesNeverTriggerAnotherWriteAndProbeKeepsTheSameCard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel=new CartPanel(images); var edits=new ArrayList<Integer>();
            panel.bindActions(() -> {},() -> {},(id,q) -> edits.add(q));
            panel.showState(ready(contents()));
            var spinner=named(panel,"cart-quantity-5");
            panel.showState(state(buyer(),ClientController.CartStatus.READY,contents(),true,ClientController.Action.PROBE));
            assertSame(spinner,named(panel,"cart-quantity-5")); assertFalse(spinner.isEnabled());
            panel.showState(ready(new CartSnapshot(2,List.of(new CartSnapshot.Item(5,1001,"商品",100,10,4)))));
            assertSame(spinner,named(panel,"cart-quantity-5"));
            assertEquals(4,((JSpinner)spinner).getValue()); assertTrue(edits.isEmpty());
        });
    }
    @Test void responsiveGridUsesMultipleColumnsAndReusesProductImageCache() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel=new CartPanel(images);
            panel.showState(ready(new CartSnapshot(1,List.of(
                    new CartSnapshot.Item(1,1001,"演示笔记本",1290,20,1),
                    new CartSnapshot.Item(2,1002,"演示水杯",2590,15,1),
                    new CartSnapshot.Item(3,1003,"演示帆布袋",1990,30,1)))));
            var grid=(JPanel)named(panel,"cart-cards");
            grid.setSize(900,800); grid.doLayout();
            assertEquals(grid.getComponent(0).getY(),grid.getComponent(1).getY());
            assertTrue(grid.getComponent(1).getX()>grid.getComponent(0).getX());
            assertTrue(grid.getComponent(2).getY()>grid.getComponent(0).getY());
            grid.setSize(360,800); grid.doLayout();
            assertEquals(0,grid.getComponent(1).getX());
            assertTrue(grid.getComponent(1).getY()>grid.getComponent(0).getY());
            assertTrue(grid.getComponent(0).getWidth()<=360);
            assertTrue(labels(panel).stream().anyMatch(l -> l.getIcon()==images.forProduct(1001,"演示笔记本").thumbnail()));
        });
    }
    @Test void eachCardEditsItsOwnIdWithoutSelectingAnotherProductFirst() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel=new CartPanel(images); var edits=new ArrayList<String>();
            panel.bindActions(() -> {},() -> {},(id,q) -> edits.add(id+":"+q));
            panel.showState(ready(new CartSnapshot(1,List.of(
                    new CartSnapshot.Item(5,1001,"商品 A",100,20,1),new CartSnapshot.Item(9,1002,"商品 B",200,20,2)))));
            ((JSpinner)named(panel,"cart-quantity-9")).setValue(3);
            ((JButton)named(panel,"remove-cart-item-9")).doClick();
            assertEquals(List.of("9:3","9:null"),edits);
            assertEquals(1,((JSpinner)named(panel,"cart-quantity-5")).getValue());
        });
    }
    private static Component named(Container parent,String name) {
        var result=find(parent,name);
        if(result==null) throw new AssertionError("Missing "+name);
        return result;
    }
    private static Component find(Container parent,String name) {
        for(Component c:parent.getComponents()) {
            if(name.equals(c.getName())) return c;
            if(c instanceof Container nested) { var found=find(nested,name); if(found!=null) return found; }
        }
        return null;
    }
    private static List<JLabel> labels(Container parent) {
        var result=new ArrayList<JLabel>();
        for(Component c:parent.getComponents()) {
            if(c instanceof JLabel l) result.add(l);
            if(c instanceof Container nested) result.addAll(labels(nested));
        }
        return result;
    }
}
