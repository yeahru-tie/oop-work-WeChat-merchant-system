package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSpinnerUI;

/** Retains the real JSpinner model/editor and its keyboard/accessibility behavior. */
public final class QuantitySpinnerUI extends BasicSpinnerUI {
    public static ComponentUI createUI(JComponent c){return new QuantitySpinnerUI();}
    @Override protected Component createNextButton(){var button=Ui.ghost("+");button.setBorder(BorderFactory.createEmptyBorder());button.setName("Spinner.nextButton");button.getAccessibleContext().setAccessibleName("增加数量");installNextButtonListeners(button);return button;}
    @Override protected Component createPreviousButton(){var button=Ui.ghost("−");button.setBorder(BorderFactory.createEmptyBorder());button.setName("Spinner.previousButton");button.getAccessibleContext().setAccessibleName("减少数量");installPreviousButtonListeners(button);return button;}
    @Override protected LayoutManager createLayout(){return new LayoutManager(){
        public void addLayoutComponent(String name,Component c){}public void removeLayoutComponent(Component c){}
        public Dimension preferredLayoutSize(Container c){return new Dimension(132,42);}public Dimension minimumLayoutSize(Container c){return new Dimension(108,40);}
        public void layoutContainer(Container c){var in=c.getInsets();int w=c.getWidth()-in.left-in.right,h=c.getHeight()-in.top-in.bottom;for(var child:c.getComponents()){if(child==spinner.getEditor())child.setBounds(in.left+32,in.top,Math.max(1,w-64),h);else if("Spinner.nextButton".equals(child.getName()))child.setBounds(in.left+w-32,in.top,32,h);else child.setBounds(in.left,in.top,32,h);}if(spinner.getEditor() instanceof JSpinner.DefaultEditor editor)editor.getTextField().setHorizontalAlignment(JTextField.CENTER);}
    };}
}
