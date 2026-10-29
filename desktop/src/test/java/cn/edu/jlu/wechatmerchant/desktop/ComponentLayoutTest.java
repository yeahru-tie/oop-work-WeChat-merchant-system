package cn.edu.jlu.wechatmerchant.desktop;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.util.ArrayList;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class ComponentLayoutTest {
    @Test void navigationKeepsSelectionWhenManagementPagesAreInsertedAndReplaced() throws Exception {
        SwingUtilities.invokeAndWait(() -> {var nav=new NavigationPane("店铺");var changes=new ArrayList<Integer>();nav.addTab("首页",new JPanel());nav.addTab("订单",new JPanel());nav.addChangeListener(e -> changes.add(nav.getSelectedIndex()));nav.setSelectedIndex(1);nav.insertTab("活动",null,new JPanel(),null,1);assertEquals(2,nav.getSelectedIndex());assertEquals("订单",nav.getTitleAt(2));var replacement=new JPanel();nav.setComponentAt(2,replacement);assertTrue(replacement.isVisible());nav.setTitleAt(2,"订单 · 2");assertEquals(2,nav.getSelectedIndex());assertEquals(java.util.List.of(1),changes);nav.setSelectedIndex(0);assertFalse(replacement.isVisible());});
    }
    @Test void quantityButtonsFitAndUseTheExistingSpinnerModelInBothThemes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {try{for(var mode:Theme.Mode.values()){Theme.install(mode);var spinner=new JSpinner(new SpinnerNumberModel(2,1,10,1));spinner.setSize(132,42);layout(spinner);JButton plus=null,minus=null;for(var child:spinner.getComponents())if(child instanceof JButton button){if(button.getText().equals("+"))plus=button;else minus=button;}assertNotNull(plus);assertNotNull(minus);assertTrue(plus.getWidth()-plus.getInsets().left-plus.getInsets().right>=plus.getFontMetrics(plus.getFont()).stringWidth("+"));plus.doClick();assertEquals(3,spinner.getValue());minus.doClick();assertEquals(2,spinner.getValue());}}finally{Theme.install(Theme.Mode.LIGHT);}});
    }
    @Test void formsKeepSingleLineInputsAtControlHeightAndCompositeListCardsHaveVisibleChildren() throws Exception {
        SwingUtilities.invokeAndWait(() -> {var field=new JTextField();var panel=Ui.stack(24,Ui.field("标题",field,null),Ui.field("正文",new JTextArea(5,20),null));panel.setSize(400,600);layout(panel);assertTrue(field.getHeight()<=44 && field.getHeight()>=36);var card=new SurfacePanel(new BorderLayout());var value=Ui.label("订单 #1");card.add(Ui.row(value),BorderLayout.NORTH);Ui.renderer(card,280,130);assertTrue(value.getWidth()>0);assertTrue(value.getHeight()>0);});
    }
    private static void layout(Container c){c.doLayout();for(var child:c.getComponents())if(child instanceof Container nested)layout(nested);}
}
