package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicButtonUI;

public final class ThemeButtonUI extends BasicButtonUI {
    public static ComponentUI createUI(JComponent c){return new ThemeButtonUI();}
    @Override protected void installDefaults(AbstractButton b){super.installDefaults(b);b.setOpaque(false);b.setBorder(b.getName()!=null && b.getName().startsWith("Spinner.")?BorderFactory.createEmptyBorder():BorderFactory.createEmptyBorder(11,18,11,18));b.setRolloverEnabled(true);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));}
    @Override public void paint(Graphics g,JComponent c){
        var b=(AbstractButton)c;var p=(Graphics2D)g.create();p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        boolean primary=Boolean.TRUE.equals(b.getClientProperty("theme.primary")),ghost=Boolean.TRUE.equals(b.getClientProperty("theme.ghost")),selected=Boolean.TRUE.equals(b.getClientProperty("theme.selected"));
        Color fill=primary?Theme.accent():Theme.surface();if(selected){var a=Theme.accent();fill=new Color(a.getRed(),a.getGreen(),a.getBlue(),24);}else if(!b.isEnabled())fill=Theme.line();else if(b.getModel().isPressed())fill=Theme.line();else if(b.getModel().isRollover())fill=primary?fill.brighter():Theme.line();
        if(!ghost || selected || b.getModel().isRollover()){p.setColor(fill);p.fillRoundRect(1,1,c.getWidth()-2,c.getHeight()-2,12,12);}
        if(!ghost || b.hasFocus()){p.setColor(b.hasFocus()?Theme.accent():primary?Theme.accent():Theme.line());p.drawRoundRect(1,1,c.getWidth()-3,c.getHeight()-3,12,12);}
        p.dispose();super.paint(g,c);
    }
    @Override protected void paintText(Graphics g,AbstractButton b,Rectangle rect,String text){boolean primary=Boolean.TRUE.equals(b.getClientProperty("theme.primary")),selected=Boolean.TRUE.equals(b.getClientProperty("theme.selected"));g.setColor(!b.isEnabled()?Theme.muted():selected?Theme.accent():primary?(Theme.current()==Theme.Mode.LIGHT?Color.WHITE:Theme.background()):Theme.foreground());var metrics=g.getFontMetrics();javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(g,text,b.getDisplayedMnemonicIndex(),rect.x,rect.y+metrics.getAscent());}
}
