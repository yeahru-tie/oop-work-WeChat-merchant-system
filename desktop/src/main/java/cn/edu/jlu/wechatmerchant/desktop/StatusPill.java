package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import javax.swing.*;

public final class StatusPill extends JLabel {
    public enum Tone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }
    private Tone tone;
    public StatusPill(String text,Tone tone){putClientProperty("html.disable",true);setBorder(BorderFactory.createEmptyBorder(5,10,5,10));setFont(getFont().deriveFont(Font.PLAIN,12f));set(text,tone);}
    public void set(String text,Tone tone){setText(text);this.tone=tone;repaint();}
    @Override public Color getForeground(){if(tone==null)return Theme.foreground();return switch(tone){case ACCENT,SUCCESS -> Theme.accent();case WARNING -> Theme.current()==Theme.Mode.LIGHT?new Color(0x98610d):new Color(0xf4c266);case DANGER -> Theme.current()==Theme.Mode.LIGHT?new Color(0xb34444):new Color(0xf5a3a3);default -> Theme.muted();};}
    @Override protected void paintComponent(Graphics g){var copy=(Graphics2D)g.create();copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);var foreground=getForeground();copy.setColor(new Color(foreground.getRed(),foreground.getGreen(),foreground.getBlue(),22));copy.fillRoundRect(0,0,getWidth(),getHeight(),getHeight(),getHeight());copy.dispose();super.paintComponent(g);}
}
