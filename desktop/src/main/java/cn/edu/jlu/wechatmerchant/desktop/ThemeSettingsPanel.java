package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import javax.swing.*;

public final class ThemeSettingsPanel extends JPanel {
    private final JLabel status=Ui.caption("");
    private final StatusPill current=new StatusPill("",StatusPill.Tone.ACCENT);
    public ThemeSettingsPanel(){
        super(new BorderLayout());setOpaque(false);
        var card=new SurfacePanel(new BorderLayout(0,24));card.add(Ui.row(Ui.title("外观"),current),BorderLayout.NORTH);
        JButton light=new JButton("浅色"),dark=new JButton("深色");light.setName("theme-light");dark.setName("theme-dark");
        light.setPreferredSize(new Dimension(156,88));dark.setPreferredSize(new Dimension(156,88));
        var choices=new JPanel(new GridLayout(1,2,24,0));choices.setOpaque(false);choices.add(light);choices.add(dark);card.add(choices,BorderLayout.CENTER);
        card.add(status,BorderLayout.SOUTH);add(card,BorderLayout.NORTH);mark(light,dark);
        light.addActionListener(e -> change(Theme.Mode.LIGHT,light,dark));dark.addActionListener(e -> change(Theme.Mode.DARK,light,dark));
    }
    private void mark(JButton light,JButton dark){light.putClientProperty("theme.primary",Theme.current()==Theme.Mode.LIGHT);dark.putClientProperty("theme.primary",Theme.current()==Theme.Mode.DARK);current.set(Theme.current()==Theme.Mode.LIGHT?"浅色":"深色",StatusPill.Tone.ACCENT);}
    private void change(Theme.Mode mode,JButton light,JButton dark){
        light.setEnabled(false);dark.setEnabled(false);status.setText("保存中…");
        new SwingWorker<Void,Void>(){
            @Override protected Void doInBackground() throws Exception {Theme.store(mode);return null;}
            @Override protected void done(){try{get();Theme.install(mode);mark(light,dark);status.setText("已保存");}catch(Exception ex){status.setText("保存失败，请检查本机目录权限");}finally{light.setEnabled(true);dark.setEnabled(true);}}
        }.execute();
    }
}
