package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.InboxPage;
import java.awt.*;
import cn.edu.jlu.wechatmerchant.desktop.*;
import java.util.function.*;
import javax.swing.*;

final class InboxPanel extends JPanel {
    private final DefaultListModel<InboxPage.Item> model=new DefaultListModel<>();private final JList<InboxPage.Item> list=new JList<>(model);
    private final JTextArea detail=new JTextArea();private final JLabel status=new JLabel("登录后查看消息");
    private final JButton refresh=new JButton("刷新消息"),previous=new JButton("上一页"),next=new JButton("下一页"),open=new JButton("打开消息"),jump=new JButton("查看关联内容"),login=new JButton("登录查看消息");
    private final JLabel heading=Ui.title("选择消息"),timestamp=Ui.caption("");
    private final StatusPill categoryPill=new StatusPill("",StatusPill.Tone.NEUTRAL);
    private ClientController.State state;private InboxPage shown;private boolean updating;private InboxPage.Item selected;
    private BiConsumer<Integer,Long> query;private LongConsumer order,product;
    InboxPanel(){
        super(new BorderLayout(12,12));setOpaque(false);var header=new JPanel(new BorderLayout());status.putClientProperty("html.disable",true);header.add(status,BorderLayout.CENTER);var actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(login);actions.add(refresh);header.add(actions,BorderLayout.EAST);add(header,BorderLayout.NORTH);
        list.setName("message-list");list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setOpaque(false);list.setFixedCellHeight(112);list.setCellRenderer((owner,value,index,chosen,focus) -> {var card=new SurfacePanel(new BorderLayout(0,12));card.putClientProperty("theme.selected",chosen);card.add(Ui.label((value.read()?"":"● ")+value.title()),BorderLayout.NORTH);card.add(Ui.caption(category(value.kind())),BorderLayout.SOUTH);return Ui.renderer(card,owner.getWidth(),112);});
        detail.setName("message-detail");detail.setEditable(false);detail.setLineWrap(true);detail.setWrapStyleWord(true);detail.setOpaque(false);detail.setFont(new JLabel().getFont().deriveFont(16f));
        var reading=new SurfacePanel(new BorderLayout(0,24));reading.add(Ui.stack(16,heading,Ui.row(categoryPill,timestamp)),BorderLayout.NORTH);reading.add(Ui.scroll(detail),BorderLayout.CENTER);
        var split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,Ui.scroll(list),reading);split.setResizeWeight(.38);split.setDividerLocation(300);split.setBorder(BorderFactory.createEmptyBorder());split.getLeftComponent().setMinimumSize(new Dimension(240,100));split.getRightComponent().setMinimumSize(new Dimension(300,100));add(split,BorderLayout.CENTER);
        var footer=new JPanel(new FlowLayout(FlowLayout.RIGHT));previous.setName("messages-previous");next.setName("messages-next");open.setName("message-open");jump.setName("message-jump");refresh.setName("messages-refresh");footer.add(previous);footer.add(next);footer.add(open);footer.add(jump);add(footer,BorderLayout.SOUTH);
        list.addListSelectionListener(e -> {if(!e.getValueIsAdjusting() && !updating && list.getSelectedValue()!=null)open();});open.addActionListener(e -> open());jump.addActionListener(e -> {if(selected==null || state==null || state.busy())return;if(selected.orderId()!=null)order.accept(selected.orderId());else if(selected.productId()!=null)product.accept(selected.productId());});
    }
    void bind(BiConsumer<Integer,Long> query,LongConsumer order,LongConsumer product,Runnable loginAction){this.query=query;this.order=order;this.product=product;refresh.addActionListener(e -> query.accept(0,null));previous.addActionListener(e -> query.accept(Math.max(0,shown.offset()-20),null));next.addActionListener(e -> query.accept(shown.offset()+20,null));login.addActionListener(e -> loginAction.run());}
    private void open(){if(state==null || state.busy() || state.identity()==null || query==null)return;selected=list.getSelectedValue();if(selected==null)return;showDetail();controls();if(!selected.read())query.accept(shown.offset(),selected.messageId());}
    void showState(ClientController.State value){boolean ownerChanged=state==null || !java.util.Objects.equals(state.identity(),value.identity());state=value;status.setText(NoticeBar.important(value.inbox().message())?value.inbox().message():value.identity()==null?"":value.inbox().page()==null?"":value.inbox().page().unreadCount()+" 条未读");if(ownerChanged){selected=null;detail.setText("");heading.setText("选择消息");timestamp.setText("");categoryPill.set("",StatusPill.Tone.NEUTRAL);shown=null;model.clear();}
        InboxPage page=value.identity()==null?null:value.inbox().page();if(page!=shown){shown=page;Long id=selected==null?null:selected.messageId();updating=true;try{model.clear();if(page!=null)for(var item:page.items()){model.addElement(item);if(id!=null && item.messageId()==id){selected=item;list.setSelectedIndex(model.size()-1);}}}finally{updating=false;}if(page==null){selected=null;detail.setText("");}else if(selected!=null)showDetail();else detail.setText("");}controls();}
    private void showDetail(){heading.setText(selected.title());categoryPill.set(category(selected.kind()),StatusPill.Tone.ACCENT);timestamp.setText(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(java.time.ZoneId.of("Asia/Shanghai")).format(java.time.Instant.ofEpochMilli(selected.createdAt())));detail.setText(selected.text());detail.setCaretPosition(0);}
    private void controls(){boolean idle=state!=null && !state.busy(),signed=idle && state.identity()!=null;login.setVisible(state==null || state.identity()==null);login.setEnabled(idle);refresh.setEnabled(signed);list.setEnabled(signed);open.setEnabled(signed && list.getSelectedValue()!=null);jump.setEnabled(signed && selected!=null && (selected.orderId()!=null || selected.productId()!=null));previous.setVisible(shown!=null && shown.offset()>0);next.setVisible(shown!=null && shown.hasMore());previous.setEnabled(signed);next.setEnabled(signed);jump.setVisible(selected!=null && (selected.orderId()!=null || selected.productId()!=null));jump.setText(selected!=null && selected.orderId()!=null?"查看订单":"查看商品");open.setVisible(selected!=null && !selected.read());}
    private static String category(String kind){return switch(kind){case "ORDER" -> "订单";case "RETURN" -> "售后";default -> "优惠公告";};}
}
