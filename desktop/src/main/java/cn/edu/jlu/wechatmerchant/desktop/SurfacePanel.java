package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import javax.swing.JPanel;

public class SurfacePanel extends JPanel {
    public SurfacePanel(LayoutManager layout){super(layout);setOpaque(false);putClientProperty("theme.surface",true);setBackground(Theme.surface());setBorder(Theme.cardBorder());}
    @Override protected void paintComponent(Graphics g){
        var p=(Graphics2D)g.create();p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(int i=7;i>=1;i--){p.setColor(new Color(0,0,0,Theme.current()==Theme.Mode.LIGHT?2:4));p.fillRoundRect(4-i/2,3+i/2,getWidth()-8+i,getHeight()-10+i/2,22,22);}
        p.setColor(Theme.surface());p.fillRoundRect(4,3,getWidth()-8,getHeight()-10,20,20);p.dispose();super.paintComponent(g);
    }
}
