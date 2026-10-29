package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicScrollBarUI;

public final class ThemeScrollUI extends BasicScrollBarUI {
    public static ComponentUI createUI(JComponent c){return new ThemeScrollUI();}
    @Override protected JButton createDecreaseButton(int direction){return zero();}
    @Override protected JButton createIncreaseButton(int direction){return zero();}
    private JButton zero(){var b=new JButton();b.setPreferredSize(new Dimension(0,0));return b;}
    @Override protected void paintTrack(Graphics g,JComponent c,Rectangle r){g.setColor(Theme.background());g.fillRect(r.x,r.y,r.width,r.height);}
    @Override protected void paintThumb(Graphics g,JComponent c,Rectangle r){var g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.setColor(Theme.line());g2.fillRoundRect(r.x+3,r.y+3,Math.max(3,r.width-6),Math.max(3,r.height-6),8,8);g2.dispose();}
}
