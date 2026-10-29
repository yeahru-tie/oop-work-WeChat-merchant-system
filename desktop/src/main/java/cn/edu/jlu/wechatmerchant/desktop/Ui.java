package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import javax.swing.*;

/** Shared presentation primitives. No domain or transport dependencies. */
public final class Ui {
    public static final int GAP=24, SMALL=12, PAGE=28;
    private Ui(){}
    public static JPanel row(Component... children){var panel=new JPanel(new FlowLayout(FlowLayout.LEFT,12,0));panel.setOpaque(false);for(var child:children)panel.add(child);return panel;}
    public static JPanel stack(int gap,Component... children){var panel=new JPanel();panel.setOpaque(false);panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));for(int i=0;i<children.length;i++){var child=children[i];if(child instanceof JComponent c)c.setAlignmentX(Component.LEFT_ALIGNMENT);if(child instanceof JButton b)b.setMaximumSize(new Dimension(Integer.MAX_VALUE,Math.max(42,b.getPreferredSize().height)));panel.add(child);if(i+1<children.length)panel.add(Box.createVerticalStrut(gap));}return panel;}
    public static JLabel label(String text){var value=new JLabel();value.putClientProperty("html.disable",true);value.setText(text);return value;}
    public static JLabel title(String text){var value=label(text);value.setFont(value.getFont().deriveFont(Font.BOLD,22f));return value;}
    public static JLabel caption(String text){var value=label(text);value.setFont(value.getFont().deriveFont(12f));Theme.muted(value);return value;}
    public static JButton primary(String text){var b=new JButton(text);Theme.primary(b);return b;}
    public static JButton ghost(String text){var b=new JButton(text);b.putClientProperty("theme.ghost",true);return b;}
    public static JButton help(String text){var b=ghost("?");b.setToolTipText(text);b.getAccessibleContext().setAccessibleName("操作说明");b.addActionListener(e -> {var detail=text(text,10);detail.setFocusable(true);var scroll=scroll(detail);scroll.setPreferredSize(new Dimension(500,300));JOptionPane.showMessageDialog(b,scroll,"详细说明",JOptionPane.PLAIN_MESSAGE);});return b;}
    public static JPanel field(String title,JComponent input,String hint){
        var p=new JPanel(new BorderLayout(0,10));p.setOpaque(false);var label=label(title);label.setLabelFor(input);p.add(label,BorderLayout.NORTH);var inputBox=new JPanel(new BorderLayout());inputBox.setOpaque(false);inputBox.add(input,input instanceof JTextField || input instanceof JComboBox || input instanceof JSpinner?BorderLayout.NORTH:BorderLayout.CENTER);p.add(inputBox,BorderLayout.CENTER);if(hint!=null)input.setToolTipText(hint);
        if(input instanceof JTextField || input instanceof JComboBox || input instanceof JSpinner){input.setPreferredSize(new Dimension(Math.max(132,input.getPreferredSize().width),42));input.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));p.setMaximumSize(new Dimension(Integer.MAX_VALUE,p.getPreferredSize().height));}
        else if(input instanceof JScrollPane scroll && scroll.getViewport().getView() instanceof JTextArea area && area.isEditable()){scroll.setBorder(Theme.inputBorder());area.setBackground(Theme.background());area.setOpaque(true);}
        return p;
    }
    public static <T extends JComponent> T renderer(T card,int width,int height){card.setSize(Math.max(1,width),height);layout(card);return card;}
    private static void layout(Container c){c.doLayout();for(var child:c.getComponents())if(child instanceof Container nested)layout(nested);}
    public static JPanel empty(String title,JButton action){var box=new JPanel(new GridBagLayout());box.setOpaque(false);var heading=title(title);Theme.muted(heading);var stack=stack(20,heading);if(action!=null)stack.add(action);box.add(stack);return box;}
    public static JScrollPane scroll(Component content){var scroll=new JScrollPane(content);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);scroll.getVerticalScrollBar().setUnitIncrement(24);return scroll;}
    public static JTextArea text(String text,int rows){var area=new JTextArea(text,rows,20);area.setEditable(false);area.setFocusable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setOpaque(false);area.setFont(new JLabel().getFont());return area;}
    public static JPanel valueRow(String name,JLabel value){var row=new JPanel(new BorderLayout(16,0));row.setOpaque(false);row.add(caption(name),BorderLayout.WEST);row.add(value,BorderLayout.EAST);return row;}
    public static void quiet(JComponent component,String text){component.setToolTipText(text);}
}
