package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.*;
import java.awt.*;
import java.util.function.*;
import javax.swing.*;

final class OrdersPanel extends JPanel {
    private static final long serialVersionUID=1L;
    private final DefaultListModel<OrderPage.Summary> model=new DefaultListModel<>();private final JList<OrderPage.Summary> list=new JList<>(model);
    private final JButton login=new JButton("登录查看订单"),refresh=new JButton("刷新订单"),previous=new JButton("上一页"),next=new JButton("下一页"),detail=new JButton("查看详情"),query=new JButton("核对本次结果"),retry=new JButton("原键重试本次");
    private final JTextArea content=new JTextArea(),message=new JTextArea(3,40);private ClientController.State state;private OrderPage shownPage;private OrderSnapshot shownDetail;
    OrdersPanel(){
        super(new BorderLayout(10,10));setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel tools=new JPanel(new FlowLayout(FlowLayout.LEFT));for(var b:new JButton[]{login,refresh,previous,next,detail,query,retry})tools.add(b);add(tools,BorderLayout.NORTH);
        login.setName("orders-login");refresh.setName("orders-refresh");previous.setName("orders-previous");next.setName("orders-next");detail.setName("orders-detail");query.setName("orders-query-pending");retry.setName("orders-retry-pending");list.setName("order-list");content.setName("order-content");message.setName("orders-message");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setCellRenderer((owner,item,index,selected,focus) -> {
            JLabel label=new JLabel("订单 #"+item.orderId()+" · "+OrderText.money(item.paidCents())+" · "+item.itemCount()+"种商品");label.putClientProperty("html.disable",true);label.setBorder(BorderFactory.createEmptyBorder(10,8,10,8));label.setOpaque(true);label.setBackground(selected?owner.getSelectionBackground():owner.getBackground());label.setForeground(selected?owner.getSelectionForeground():owner.getForeground());return label;});
        for(var area:new JTextArea[]{content,message}){area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setFont(new JLabel().getFont());}message.setOpaque(false);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(list),new JScrollPane(content));split.setDividerLocation(280);split.setResizeWeight(.35);add(split,BorderLayout.CENTER);add(message,BorderLayout.SOUTH);
        list.addListSelectionListener(event -> controls());controls();
    }
    void bind(Runnable signIn,IntConsumer load,LongConsumer open,Runnable reconcile,Runnable repeat){
        login.addActionListener(e -> signIn.run());refresh.addActionListener(e -> load.accept(state!=null && state.orders().page()!=null?state.orders().page().offset():0));previous.addActionListener(e -> load.accept(Math.max(0,state.orders().page().offset()-20)));next.addActionListener(e -> load.accept(state.orders().page().offset()+20));
        detail.addActionListener(e -> {var selected=list.getSelectedValue();if(selected!=null)open.accept(selected.orderId());});query.addActionListener(e -> reconcile.run());retry.addActionListener(e -> repeat.run());
    }
    void showState(ClientController.State next){state=next;var page=next.identity()==null?null:next.orders().page();
        if(shownPage!=page){shownPage=page;model.clear();if(page!=null){model.addAll(page.items());if(!model.isEmpty())list.setSelectedIndex(0);}}
        var order=next.identity()==null?null:next.orders().detail();if(order!=shownDetail){shownDetail=order;content.setText(order==null?"选择订单后点击“查看详情”":OrderText.detail(order));content.setCaretPosition(0);}
        if(next.identity()==null){content.setText("登录后查看自己的历史订单");model.clear();}
        message.setText(next.identity()==null?"登录后查询本人订单":next.orders().message());controls();
    }
    private void controls(){boolean guest=state==null || state.identity()==null,idle=state!=null && !state.busy() && !guest;login.setVisible(guest);login.setEnabled(state==null || !state.busy());refresh.setEnabled(idle);list.setEnabled(idle);detail.setEnabled(idle && list.getSelectedValue()!=null);previous.setEnabled(idle && shownPage!=null && shownPage.offset()>0);next.setEnabled(idle && shownPage!=null && shownPage.hasMore());boolean pending=state!=null && state.orders().pending()!=null && !guest;query.setVisible(pending);retry.setVisible(pending);query.setEnabled(idle && pending);retry.setEnabled(idle && pending);}
}
