package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.event.*;

/** Navigation and page containers only, independent of either application's controller. */
public final class NavigationPane extends JPanel {
    private final JPanel menu=new JPanel(),pages=new JPanel(new CardLayout()),right=new JPanel(new BorderLayout(0,24));
    private final ArrayList<Entry> entries=new ArrayList<>();private final EventListenerList listeners=new EventListenerList();private int selected=-1;
    private final JLabel pageTitle=Ui.title("");private final JPanel account=Ui.row();
    private static final class Entry {String title;final String key=java.util.UUID.randomUUID().toString();Component page;final JButton button=Ui.ghost("");Entry(String title,Component page){this.title=title;this.page=page;}}
    public NavigationPane(String brand){
        super(new BorderLayout());setName("app-navigation");var sidebar=new JPanel(new BorderLayout(0,32));sidebar.putClientProperty("theme.surface",true);sidebar.setBackground(Theme.surface());sidebar.setPreferredSize(new Dimension(190,400));sidebar.setBorder(BorderFactory.createEmptyBorder(32,18,24,14));
        var logo=Ui.title(brand);logo.setFont(logo.getFont().deriveFont(20f));sidebar.add(logo,BorderLayout.NORTH);menu.setOpaque(false);menu.setLayout(new BoxLayout(menu,BoxLayout.Y_AXIS));sidebar.add(menu,BorderLayout.CENTER);add(sidebar,BorderLayout.WEST);
        right.setBorder(BorderFactory.createEmptyBorder(28,24,20,24));var header=new JPanel(new BorderLayout());header.setOpaque(false);header.add(pageTitle,BorderLayout.WEST);header.add(account,BorderLayout.EAST);right.add(header,BorderLayout.NORTH);right.add(pages,BorderLayout.CENTER);add(right,BorderLayout.CENTER);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("alt LEFT"),"previous");getActionMap().put("previous",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){if(selected>0)setSelectedIndex(selected-1);}});
    }
    public JPanel accountArea(){return account;}
    public void addTab(String title,Component page){insertTab(title,null,page,null,entries.size());}
    public void insertTab(String title,Icon ignored,Component page,String tooltip,int index){
        var entry=new Entry(title,page);entries.add(index,entry);pages.add(page,entry.key);entry.button.setHorizontalAlignment(SwingConstants.LEFT);entry.button.setIcon(new NavIcon(title));entry.button.setIconTextGap(12);entry.button.setToolTipText(tooltip);entry.button.addActionListener(e -> setSelectedIndex(entries.indexOf(entry)));if(selected<0)selected=0;else if(index<=selected)selected++;rebuild();
    }
    private void rebuild(){menu.removeAll();for(int i=0;i<entries.size();i++){var entry=entries.get(i);entry.button.setText(entry.title);entry.button.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));entry.button.setPreferredSize(new Dimension(166,48));entry.button.putClientProperty("theme.selected",i==selected);menu.add(entry.button);menu.add(Box.createVerticalStrut(8));}showPage();menu.revalidate();menu.repaint();}
    private void showPage(){if(selected<0)return;var entry=entries.get(selected);((CardLayout)pages.getLayout()).show(pages,entry.key);pageTitle.setText(entry.title.replaceAll(" · .*",""));for(int i=0;i<entries.size();i++)entries.get(i).button.putClientProperty("theme.selected",i==selected);menu.repaint();}
    public int getSelectedIndex(){return selected;}public int getTabCount(){return entries.size();}public String getTitleAt(int i){return entries.get(i).title;}
    public void setSelectedIndex(int value){if(value<0 || value>=entries.size())throw new IllegalArgumentException("Invalid navigation.");if(value==selected)return;selected=value;showPage();var e=new ChangeEvent(this);for(var listener:listeners.getListeners(ChangeListener.class))listener.stateChanged(e);}
    public void addChangeListener(ChangeListener listener){listeners.add(ChangeListener.class,listener);}
    public void setTitleAt(int i,String title){var entry=entries.get(i);entry.title=title;entry.button.setText(title);if(i==selected)pageTitle.setText(title.replaceAll(" · .*",""));}
    public void setComponentAt(int i,Component component){var entry=entries.get(i);pages.remove(entry.page);entry.page=component;pages.add(component,entry.key);showPage();}
    private static final class NavIcon implements Icon {
        private final String title;NavIcon(String title){this.title=title;}public int getIconWidth(){return 20;}public int getIconHeight(){return 20;}
        public void paintIcon(Component c,Graphics g,int x,int y){var p=(Graphics2D)g.create();p.translate(x,y);p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);p.setColor(Boolean.TRUE.equals(((JComponent)c).getClientProperty("theme.selected"))?Theme.accent():Theme.muted());p.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            if(title.contains("商品")){for(int i=0;i<4;i++)p.drawRoundRect(2+i%2*9,2+i/2*9,6,6,2,2);}
            else if(title.contains("购物")){p.drawPolyline(new int[]{1,4,6,16,18},new int[]{3,3,13,13,6},5);p.drawLine(5,6,18,6);p.drawOval(7,16,2,2);p.drawOval(14,16,2,2);}
            else if(title.contains("订单") || title.contains("交易")){p.drawRoundRect(4,2,12,16,2,2);p.drawLine(7,7,13,7);p.drawLine(7,11,13,11);p.drawLine(7,15,11,15);}
            else if(title.contains("消息") || title.contains("公告")){p.drawRoundRect(2,3,16,12,3,3);p.drawPolyline(new int[]{6,6,10},new int[]{15,18,15},3);p.drawLine(6,7,14,7);p.drawLine(6,11,11,11);}
            else if(title.contains("优惠") || title.contains("促销")){p.drawRoundRect(2,4,16,12,3,3);p.drawLine(7,5,7,15);p.drawOval(11,7,3,3);}
            else if(title.contains("状态")){p.drawOval(2,2,16,16);p.drawPolyline(new int[]{4,8,10,13,16},new int[]{11,11,6,14,10},5);}
            else{p.drawOval(4,4,12,12);p.drawOval(8,8,4,4);for(int i=0;i<4;i++){p.rotate(Math.PI/2,10,10);p.drawLine(10,1,10,4);}}p.dispose();}
    }
}
