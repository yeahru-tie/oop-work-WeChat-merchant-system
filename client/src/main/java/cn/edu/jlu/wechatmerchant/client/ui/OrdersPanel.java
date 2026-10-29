package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.desktop.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import java.awt.*;
import java.util.function.*;
import javax.swing.*;

final class OrdersPanel extends JPanel {
    private final DefaultListModel<OrderPage.Summary> model=new DefaultListModel<>();
    private final JList<OrderPage.Summary> list=new JList<>(model);
    private final JButton login=Ui.primary("登录"),refresh=Ui.ghost("刷新"),previous=Ui.ghost("上一页"),next=Ui.ghost("下一页");
    private final JButton query=new JButton("核对成交"),retry=new JButton("重试原请求"),apply=new JButton("申请售后");
    private final JButton returnRefresh=Ui.ghost("刷新"),returnPrevious=Ui.ghost("上一页"),returnNext=Ui.ghost("下一页"),returnQuery=new JButton("核对申请"),returnRetry=new JButton("重试原申请");
    private final JPanel content=new JPanel(new BorderLayout()),history=new JPanel();
    private final JPanel pages=new JPanel(new CardLayout()),workspace=new JPanel(new BorderLayout(24,0));
    private final NoticeBar message=new NoticeBar(),returnMessage=new NoticeBar(),requestNotice=new NoticeBar();
    private ClientController.State state;private OrderPage shownPage;private OrderSnapshot shownDetail;private AfterSalePage shownHistory;
    private boolean filling;private LongConsumer openOrder;private BiConsumer<Long,String> submit;
    private final JTextArea reason=new JTextArea(5,24);private final JButton send=Ui.primary("提交申请");private JDialog requestDialog;private JPanel requestForm;
    OrdersPanel(){
        super(new BorderLayout(0,24));setOpaque(false);
        refresh.setName("orders-refresh");previous.setName("orders-previous");next.setName("orders-next");login.setName("orders-login");
        query.setName("orders-query-pending");retry.setName("orders-retry-pending");list.setName("order-list");content.setName("order-content");message.setName("orders-message");
        apply.setName("apply-return");returnRefresh.setName("return-refresh");returnPrevious.setName("return-previous");returnNext.setName("return-next");returnQuery.setName("return-query");returnRetry.setName("return-retry");returnMessage.setName("return-message");history.setName("return-history");reason.setName("return-reason");send.setName("submit-return");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setOpaque(false);list.setFixedCellHeight(130);list.setCellRenderer((owner,item,index,selected,focus) -> {
            var card=new SurfacePanel(new BorderLayout(0,12));card.putClientProperty("theme.selected",selected);
            card.add(Ui.row(Ui.label("#"+item.orderId()),new StatusPill(shortStatus(item.status()),tone(item.status()))),BorderLayout.NORTH);
            var amount=Ui.label(OrderText.money(item.paidCents()));amount.setFont(amount.getFont().deriveFont(Font.BOLD,18f));card.add(amount,BorderLayout.CENTER);
            card.add(Ui.caption(OrderText.time(item.createdAt())),BorderLayout.SOUTH);return Ui.renderer(card,owner.getWidth(),130);
        });
        var listing=new JPanel(new BorderLayout(0,16));listing.setOpaque(false);listing.setPreferredSize(new Dimension(284,400));listing.add(Ui.row(Ui.caption("全部订单"),refresh),BorderLayout.NORTH);listing.add(Ui.scroll(list),BorderLayout.CENTER);listing.add(Ui.row(previous,next),BorderLayout.SOUTH);
        content.setOpaque(false);workspace.setOpaque(false);workspace.add(listing,BorderLayout.WEST);workspace.add(Ui.scroll(content),BorderLayout.CENTER);
        pages.add(workspace,"orders");pages.add(Ui.empty("登录后查看订单",login),"guest");pages.add(Ui.empty("还没有订单",null),"empty");
        add(pages,BorderLayout.CENTER);add(Ui.stack(8,Ui.row(query,retry,returnQuery,returnRetry),message,returnMessage),BorderLayout.SOUTH);
        history.setLayout(new BoxLayout(history,BoxLayout.Y_AXIS));history.setOpaque(false);
        list.addListSelectionListener(e -> {if(!e.getValueIsAdjusting() && !filling && state!=null && !state.busy() && list.getSelectedValue()!=null && openOrder!=null)openOrder.accept(list.getSelectedValue().orderId());});
        reason.setLineWrap(true);reason.setWrapStyleWord(true);reason.setFont(new JLabel().getFont());
        apply.addActionListener(e -> openRequest());
        send.addActionListener(e -> {if(state!=null && state.canApplyReturn() && submit!=null){String text=reason.getText();if(!ApplyReturnRequest.validText(text)){requestNotice.showMessage("请填写退货原因（1—500字）");return;}submit.accept(state.orders().detail().orderId(),text);if(requestDialog!=null)requestDialog.dispose();}});
        controls();
    }
    void bind(Runnable signIn,IntConsumer load,LongConsumer open,Runnable reconcile,Runnable repeat){
        openOrder=open;login.addActionListener(e -> signIn.run());refresh.addActionListener(e -> load.accept(shownPage==null?0:shownPage.offset()));previous.addActionListener(e -> load.accept(Math.max(0,shownPage.offset()-20)));next.addActionListener(e -> load.accept(shownPage.offset()+20));query.addActionListener(e -> reconcile.run());retry.addActionListener(e -> repeat.run());
    }
    void bindReturns(BiConsumer<Long,String> submit,IntConsumer load,Runnable reconcile,Runnable retry){
        this.submit=submit;returnRefresh.addActionListener(e -> load.accept(0));returnPrevious.addActionListener(e -> load.accept(Math.max(0,state.returns().history().offset()-20)));returnNext.addActionListener(e -> load.accept(state.returns().history().offset()+20));returnQuery.addActionListener(e -> reconcile.run());returnRetry.addActionListener(e -> retry.run());
    }
    private void openRequest(){
        if(state==null || !state.canApplyReturn())return;requestForm=new JPanel(new BorderLayout(0,24));requestForm.setBorder(BorderFactory.createEmptyBorder(28,28,28,28));
        var top=Ui.stack(16,Ui.title("申请整单退货"),Ui.label("预计退款 "+OrderText.money(state.orders().detail().paidCents())),Ui.caption("退原实付 · 已用券不返还"));requestForm.add(top,BorderLayout.NORTH);
        requestNotice.showMessage("");requestForm.add(Ui.stack(16,Ui.field("退货原因",Ui.scroll(reason),"1—500字"),requestNotice),BorderLayout.CENTER);var cancel=Ui.ghost("取消");cancel.addActionListener(e -> {if(requestDialog!=null)requestDialog.dispose();});requestForm.add(Ui.row(cancel,send),BorderLayout.SOUTH);
        if(!GraphicsEnvironment.isHeadless()){if(requestDialog!=null)requestDialog.dispose();requestDialog=new JDialog(SwingUtilities.getWindowAncestor(this),"申请售后",Dialog.ModalityType.DOCUMENT_MODAL);requestDialog.setContentPane(requestForm);requestDialog.setSize(520,480);requestDialog.setLocationRelativeTo(this);requestDialog.setVisible(true);}
    }
    JPanel requestForm(){return requestForm;}
    void showState(ClientController.State value){
        boolean ownerChanged=state==null || !java.util.Objects.equals(state.identity(),value.identity());state=value;
        if(ownerChanged){reason.setText("");requestForm=null;if(requestDialog!=null)requestDialog.dispose();}
        var page=value.identity()==null?null:value.orders().page();
        if(page!=shownPage){long chosen=list.getSelectedValue()==null?0:list.getSelectedValue().orderId();shownPage=page;filling=true;try{model.clear();if(page!=null){model.addAll(page.items());for(int i=0;i<model.size();i++)if(model.get(i).orderId()==chosen)list.setSelectedIndex(i);}}finally{filling=false;}}
        var order=value.identity()==null?null:value.orders().detail();var records=value.identity()==null?null:value.returns().history();
        if(order!=shownDetail || records!=shownHistory){if(shownDetail==null || order==null || order.orderId()!=shownDetail.orderId())reason.setText("");shownDetail=order;shownHistory=records;renderDetail();}
        ((CardLayout)pages.getLayout()).show(pages,value.identity()==null?"guest":page!=null && page.items().isEmpty() && order==null?"empty":"orders");
        message.showMessage(value.identity()!=null && (value.orders().pending()!=null || value.orders().storageBlocked() || NoticeBar.important(value.orders().message()))?value.orders().message():"");
        returnMessage.showMessage(value.identity()!=null && (value.returns().pending()!=null || value.returns().storageBlocked() || NoticeBar.important(value.returns().message()))?value.returns().message():"");
        controls();
    }
    private void renderDetail(){
        content.removeAll();history.removeAll();if(shownDetail==null){content.add(Ui.empty("选择订单查看详情",null));content.revalidate();content.repaint();return;}
        var order=shownDetail;var card=new SurfacePanel(new BorderLayout(0,24));var header=new JPanel(new BorderLayout());header.setOpaque(false);header.add(Ui.title("订单 #"+order.orderId()),BorderLayout.WEST);header.add(new StatusPill(shortStatus(order.status()),tone(order.status())),BorderLayout.EAST);card.add(header,BorderLayout.NORTH);
        var items=new JPanel();items.setOpaque(false);items.setLayout(new BoxLayout(items,BoxLayout.Y_AXIS));var placed=Ui.caption(OrderText.time(order.createdAt())+" · 模拟成交");placed.setAlignmentX(Component.LEFT_ALIGNMENT);items.add(placed);items.add(Box.createVerticalStrut(24));
        for(var item:order.items()){var row=new JPanel(new BorderLayout(20,0));row.setOpaque(false);row.add(Ui.stack(8,Ui.label(item.name()),Ui.caption("× "+item.quantity())),BorderLayout.CENTER);row.add(Ui.label(OrderText.money(item.estimatedCents())),BorderLayout.EAST);row.setAlignmentX(Component.LEFT_ALIGNMENT);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,64));items.add(row);items.add(Box.createVerticalStrut(20));}
        card.add(items,BorderLayout.CENTER);
        var paid=Ui.label(OrderText.money(order.paidCents()));paid.setFont(paid.getFont().deriveFont(Font.BOLD,27f));var breakdown=Ui.help(OrderText.detail(order));breakdown.setText("金额明细");card.add(Ui.stack(16,Ui.valueRow("实付",paid),Ui.row(breakdown)),BorderLayout.SOUTH);
        var returns=new SurfacePanel(new BorderLayout(0,20));returns.add(Ui.row(Ui.title("售后"),apply,returnRefresh),BorderLayout.NORTH);
        if(shownHistory==null || shownHistory.items().isEmpty())history.add(Ui.caption("暂无售后记录"));
        else for(var sale:shownHistory.items()){var step=new JPanel(new BorderLayout(0,10));step.setOpaque(false);step.add(Ui.row(new StatusPill(returnStatus(sale.status()),tone(sale.status())),Ui.caption(OrderText.time(sale.appliedAt()))),BorderLayout.NORTH);var explanation=Ui.text(sale.status().equals("REJECTED")?"拒绝原因："+sale.reviewNote():sale.reason(),2);step.add(explanation,BorderLayout.CENTER);var detail=Ui.help(OrderText.history(new AfterSalePage(order.orderId(),java.util.List.of(sale),20,0,false)));detail.setText("详情");step.add(Ui.row(Ui.caption((sale.status().equals("COMPLETED")?"已退款 ":"预计退款 ")+OrderText.money(sale.refundCents())),detail),BorderLayout.SOUTH);step.setMaximumSize(new Dimension(Integer.MAX_VALUE,158));history.add(step);history.add(Box.createVerticalStrut(24));}
        returns.add(history,BorderLayout.CENTER);returns.add(Ui.row(returnPrevious,returnNext),BorderLayout.SOUTH);var body=Ui.stack(24,card,returns);content.add(body,BorderLayout.NORTH);content.revalidate();content.repaint();
    }
    private void controls(){
        boolean guest=state==null || state.identity()==null,idle=state!=null && !state.busy() && !guest;login.setEnabled(state==null || !state.busy());refresh.setEnabled(idle);list.setEnabled(idle);previous.setVisible(shownPage!=null && shownPage.offset()>0);next.setVisible(shownPage!=null && shownPage.hasMore());previous.setEnabled(idle);next.setEnabled(idle);
        boolean pending=!guest && state.orders().pending()!=null;query.setVisible(pending);retry.setVisible(pending);query.setEnabled(idle);retry.setEnabled(idle);
        apply.setVisible(state!=null && state.orders().detail()!=null && state.orders().detail().status().equals("COMPLETED"));apply.setEnabled(state!=null && state.canApplyReturn());send.setEnabled(apply.isEnabled());reason.setEnabled(apply.isEnabled());returnRefresh.setEnabled(idle && shownDetail!=null);
        var h=state==null?null:state.returns().history();returnPrevious.setVisible(h!=null && h.offset()>0);returnNext.setVisible(h!=null && h.hasMore());returnPrevious.setEnabled(idle);returnNext.setEnabled(idle);
        boolean waiting=!guest && state.returns().pending()!=null;returnQuery.setVisible(waiting);returnRetry.setVisible(waiting);returnQuery.setEnabled(idle);returnRetry.setEnabled(idle);
    }
    private static String shortStatus(String status){return switch(status){case "REFUNDED" -> "已退款";case "RETURN_REQUESTED" -> "售后中";default -> "已成交";};}
    private static String returnStatus(String status){return switch(status){case "COMPLETED" -> "已退款";case "APPROVED" -> "待完成";case "REJECTED" -> "已拒绝";default -> "待审核";};}
    private static StatusPill.Tone tone(String status){return switch(status){case "REJECTED" -> StatusPill.Tone.DANGER;case "REQUESTED","RETURN_REQUESTED","APPROVED" -> StatusPill.Tone.WARNING;case "COMPLETED","REFUNDED" -> StatusPill.Tone.SUCCESS;default -> StatusPill.Tone.NEUTRAL;};}
}
