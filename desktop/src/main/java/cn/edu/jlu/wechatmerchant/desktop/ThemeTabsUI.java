package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

public final class ThemeTabsUI extends BasicTabbedPaneUI {
    public static ComponentUI createUI(JComponent c){return new ThemeTabsUI();}
    @Override protected void installDefaults(){super.installDefaults();tabInsets=new Insets(11,18,11,18);contentBorderInsets=new Insets(12,0,0,0);tabAreaInsets=new Insets(0,0,0,0);selectedTabPadInsets=new Insets(0,0,0,0);}
    @Override protected void paintTabBackground(Graphics g,int placement,int index,int x,int y,int w,int h,boolean selected){var g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.setColor(selected?Theme.accent():Theme.background());g2.fillRoundRect(x,y,w-2,h-2,12,12);g2.dispose();}
    @Override protected void paintTabBorder(Graphics g,int p,int i,int x,int y,int w,int h,boolean selected){}
    @Override protected void paintContentBorder(Graphics g,int placement,int selected){}
    @Override protected void paintText(Graphics g,int p,Font font,FontMetrics metrics,int i,String title,Rectangle rect,boolean selected){g.setFont(font);g.setColor(selected?(Theme.current()==Theme.Mode.LIGHT?Color.WHITE:Theme.background()):Theme.muted());g.drawString(title,rect.x,rect.y+metrics.getAscent());}
}
