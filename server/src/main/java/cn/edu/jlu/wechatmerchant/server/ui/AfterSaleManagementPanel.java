package cn.edu.jlu.wechatmerchant.server.ui;

import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.application.AfterSaleService;
import java.awt.*;
import cn.edu.jlu.wechatmerchant.desktop.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.*;

/** Local administrator view/controller; SQL and transaction rules remain in the service. */
public final class AfterSaleManagementPanel extends JPanel implements AutoCloseable {
    private static final long serialVersionUID=1L;
    private final AfterSaleService service;private final Supplier<String> token;private final Runnable denied;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("after-sale-admin-",0).factory());
    private final DefaultListModel<AfterSaleService.AdminRow> model=new DefaultListModel<>();private final JList<AfterSaleService.AdminRow> list=new JList<>(model);
    private final JPanel content=new JPanel(new BorderLayout()),rejectForm=new JPanel(new BorderLayout(0,12));
    private final JTextArea note=new JTextArea(2,25),status=new JTextArea(2,30);
    private final JButton reload=new JButton("重新载入"),previous=new JButton("交易上一页"),next=new JButton("交易下一页"),open=new JButton("查看交易"),approve=new JButton("批准退货"),reject=new JButton("拒绝申请"),complete=new JButton("确认退货完成（模拟退款/补库）"),historyPrevious=new JButton("历史上一页"),historyNext=new JButton("历史下一页");
    private AfterSaleService.AdminPage page;private AfterSaleService.Detail detail;private long selectedOrder,generation;private boolean closed,busy,needsReload,filling;
    private record Loaded(AfterSaleService.AdminPage page,AfterSaleService.Detail detail,long order){}
    public AfterSaleManagementPanel(AfterSaleService service,Supplier<String> token,Runnable denied){
        super(new BorderLayout(8,8));this.service=service;this.token=token;this.denied=denied;setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        setOpaque(false);setBorder(BorderFactory.createEmptyBorder());
        reload.setText("刷新");previous.setText("上一页");next.setText("下一页");open.setText("查看");approve.setText("批准");reject.setText("拒绝");complete.setText("确认退货完成");Theme.primary(complete);
        reload.setName("returns-reload");previous.setName("returns-previous");next.setName("returns-next");open.setName("returns-open");approve.setName("returns-approve");reject.setName("returns-reject");complete.setName("returns-complete");historyPrevious.setName("returns-history-previous");historyNext.setName("returns-history-next");list.setName("returns-orders");content.setName("returns-content");note.setName("returns-note");status.setName("returns-status");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setOpaque(false);list.setFixedCellHeight(126);list.setCellRenderer((owner,row,index,chosen,focus) -> {var card=new SurfacePanel(new BorderLayout(0,12));card.putClientProperty("theme.selected",chosen);card.add(Ui.row(Ui.label("#"+row.orderId()),new StatusPill(orderStatus(row.status()),StatusPill.Tone.ACCENT)),BorderLayout.NORTH);card.add(Ui.label(money(row.paidCents())),BorderLayout.CENTER);card.add(Ui.caption(row.buyer()),BorderLayout.SOUTH);return Ui.renderer(card,owner.getWidth(),126);});
        var listing=new JPanel(new BorderLayout(0,16));listing.setOpaque(false);listing.setPreferredSize(new Dimension(286,400));listing.add(Ui.row(reload,open),BorderLayout.NORTH);listing.add(Ui.scroll(list),BorderLayout.CENTER);listing.add(Ui.row(previous,next),BorderLayout.SOUTH);
        content.setOpaque(false);var right=new JPanel(new BorderLayout(0,24));right.setOpaque(false);right.add(Ui.scroll(content),BorderLayout.CENTER);
        for(var area:new JTextArea[]{note,status}){area.setLineWrap(true);area.setWrapStyleWord(true);area.setFont(new JLabel().getFont());}status.setEditable(false);status.setOpaque(false);
        rejectForm.setOpaque(false);rejectForm.add(Ui.field("拒绝原因",Ui.scroll(note),"1—500字"),BorderLayout.CENTER);rejectForm.setVisible(false);
        var review=new SurfacePanel(new BorderLayout(0,16));review.add(Ui.row(approve,reject,complete,Ui.help("批准后不会退款。确认退货完成时，按原实付模拟退款并补库存一次；已用券不返还。")),BorderLayout.NORTH);review.add(rejectForm,BorderLayout.CENTER);review.add(Ui.row(historyPrevious,historyNext),BorderLayout.SOUTH);right.add(review,BorderLayout.SOUTH);
        add(listing,BorderLayout.WEST);add(right,BorderLayout.CENTER);add(status,BorderLayout.SOUTH);
        reload.addActionListener(e -> loadPage(page==null?0:page.offset()));previous.addActionListener(e -> loadPage(Math.max(0,page.offset()-20)));next.addActionListener(e -> loadPage(page.offset()+20));open.addActionListener(e -> {var row=list.getSelectedValue();if(row!=null)loadDetail(row.orderId(),0);});list.addListSelectionListener(e -> {if(!e.getValueIsAdjusting() && !filling && !busy){detail=null;showDetail();controls();}});
        historyPrevious.addActionListener(e -> loadDetail(selectedOrder,Math.max(0,detail.history().offset()-20)));historyNext.addActionListener(e -> loadDetail(selectedOrder,detail.history().offset()+20));approve.addActionListener(e -> manage("APPROVE"));reject.addActionListener(e -> {if(!rejectForm.isVisible()){rejectForm.setVisible(true);reject.setText("确认拒绝");status.setText("拒绝须填写原因");note.requestFocusInWindow();revalidate();}else manage("REJECT");});complete.addActionListener(e -> manage("COMPLETE"));controls();
    }
    public void start(){loadPage(0);}
    private void loadPage(int offset){run(false,session -> {var p=service.adminPage(session,offset);long id=p.items().stream().anyMatch(i -> i.orderId()==selectedOrder)?selectedOrder:p.items().isEmpty()?0:p.items().getFirst().orderId();return new Loaded(p,id==0?null:service.adminDetail(session,id,0),id);},loaded -> {page=loaded.page();selectedOrder=loaded.order();detail=loaded.detail();filling=true;try{model.clear();model.addAll(page.items());for(int i=0;i<model.size();i++)if(model.get(i).orderId()==selectedOrder)list.setSelectedIndex(i);}finally{filling=false;}needsReload=false;showDetail();status.setText("");});}
    private void loadDetail(long order,int offset){run(false,session -> service.adminDetail(session,order,offset),loaded -> {selectedOrder=order;detail=loaded;needsReload=false;showDetail();status.setText("");});}
    private void manage(String action){
        if(busy || closed || needsReload || detail==null || detail.history().items().isEmpty())return;var current=detail.history().items().getFirst();String explanation=action.equals("REJECT")?note.getText().strip():"";
        if(action.equals("REJECT") && !ApplyReturnRequest.validText(explanation)){status.setText("拒绝须填写1—500字说明");return;}
        run(true,session -> {service.manage(session,current.afterSaleId(),current.version(),action,explanation);return service.adminDetail(session,selectedOrder,0);},loaded -> {detail=loaded;needsReload=false;note.setText("");rejectForm.setVisible(false);reject.setText("拒绝");showDetail();status.setText(action.equals("COMPLETE")?"整单退货已完成":"审核已保存");for(int i=0;i<model.size();i++){var row=model.get(i);if(row.orderId()==selectedOrder)model.set(i,new AfterSaleService.AdminRow(row.orderId(),row.buyer(),detail.order().status(),row.paidCents()));}});
    }
    private <T> void run(boolean write,Work<T> work,Consumer<T> apply){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT required.");if(closed || busy)return;String session=token.get();busy=true;long attempt=++generation;status.setText(write?"正在处理，请勿重复操作…":"正在载入交易与售后…");controls();
        worker.submit(() -> {T value=null;Exception failure=null;try{value=work.run(session);}catch(Exception ex){failure=ex;}T result=value;Exception error=failure;SwingUtilities.invokeLater(() -> {if(closed || generation!=attempt)return;busy=false;if(error==null)apply.accept(result);else if(error instanceof SecurityException){denied.run();return;}else{needsReload=true;status.setText(error instanceof AfterSaleService.Rejected rejected?reason(rejected.getMessage()):write?"处理结果未确认，请先重新载入核对，不能重复点击":"载入失败，请重新载入");}controls();});});
    }
    private void showDetail(){
        content.removeAll();rejectForm.setVisible(false);reject.setText("拒绝");note.setText("");if(detail==null){content.add(Ui.empty("选择交易查看详情",null));content.revalidate();content.repaint();return;}
        var order=detail.order();var card=new SurfacePanel(new BorderLayout(0,24));card.add(Ui.row(Ui.title("订单 #"+order.orderId()),new StatusPill(orderStatus(order.status()),StatusPill.Tone.ACCENT)),BorderLayout.NORTH);
        var lines=new JPanel();lines.setOpaque(false);lines.setLayout(new BoxLayout(lines,BoxLayout.Y_AXIS));for(var item:order.items()){var row=new JPanel(new BorderLayout(20,0));row.setOpaque(false);row.add(Ui.label(item.name()+" × "+item.quantity()),BorderLayout.CENTER);row.add(Ui.label(money(item.estimatedCents())),BorderLayout.EAST);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));lines.add(row);lines.add(Box.createVerticalStrut(16));}card.add(lines,BorderLayout.CENTER);var paid=Ui.label(money(order.paidCents()));paid.setFont(paid.getFont().deriveFont(Font.BOLD,26f));var moneyDetails=Ui.help("原价："+money(order.originalCents())+"\n商品优惠："+money(order.productReductionCents())+"\n优惠券："+(order.coupon()==null?"未使用":order.coupon().name())+"\n券抵扣："+money(order.couponReductionCents())+"\n实付："+money(order.paidCents()));moneyDetails.setText("金额明细");card.add(Ui.stack(16,Ui.valueRow("订单实付",paid),moneyDetails),BorderLayout.SOUTH);
        var stack=Ui.stack(24,card);for(var sale:detail.history().items()){var step=new SurfacePanel(new BorderLayout(0,16));step.add(Ui.row(new StatusPill(saleStatus(sale.status()),StatusPill.Tone.ACCENT),Ui.caption(time(sale.appliedAt()))),BorderLayout.NORTH);var text=Ui.text("原因："+sale.reason()+(sale.reviewNote().isEmpty()?"":"\n拒绝原因："+sale.reviewNote()),3);step.add(text,BorderLayout.CENTER);var full=Ui.help("申请原因："+sale.reason()+"\n审核说明："+sale.reviewNote()+"\n退款："+money(sale.refundCents())+"\n申请："+time(sale.appliedAt()));full.setText("详情");step.add(Ui.row(Ui.caption((sale.status().equals("COMPLETED")?"模拟退款 ":"预计退款 ")+money(sale.refundCents())),full),BorderLayout.SOUTH);stack.add(step);stack.add(Box.createVerticalStrut(24));}content.add(stack,BorderLayout.NORTH);content.revalidate();content.repaint();
    }
    private void controls(){boolean idle=!busy && !closed;reload.setEnabled(idle);previous.setEnabled(idle && page!=null && page.offset()>0);next.setEnabled(idle && page!=null && page.hasMore());open.setEnabled(idle && list.getSelectedValue()!=null);list.setEnabled(idle);var sale=detail==null || detail.history().items().isEmpty()?null:detail.history().items().getFirst();boolean latest=idle && !needsReload && detail!=null && detail.history().offset()==0;approve.setEnabled(latest && sale!=null && sale.status().equals("REQUESTED"));reject.setEnabled(approve.isEnabled());complete.setEnabled(latest && sale!=null && sale.status().equals("APPROVED"));note.setEnabled(reject.isEnabled());historyPrevious.setVisible(detail!=null && detail.history().offset()>0);historyNext.setVisible(detail!=null && detail.history().hasMore());historyPrevious.setEnabled(idle);historyNext.setEnabled(idle);previous.setVisible(page!=null && page.offset()>0);next.setVisible(page!=null && page.hasMore());approve.setVisible(sale!=null && sale.status().equals("REQUESTED"));reject.setVisible(approve.isVisible());complete.setVisible(sale!=null && sale.status().equals("APPROVED"));}
    private static String reason(String code){return switch(code){case "STATE_CHANGED" -> "售后状态已变化，请重新载入后处理";case "STOCK_RESTORE_FAILED" -> "补库存失败，整次未完成；请核查商品/库存后重新载入";case "NOT_FOUND" -> "交易或售后不存在，请重新载入";default -> "处理未完成，请重新载入核对";};}
    private static String money(BigInteger cents){return "¥ "+new BigDecimal(cents,2).toPlainString();}
    private static String time(long epoch){return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(epoch));}
    private static String orderStatus(String state){return state.equals("REFUNDED")?"已退款":state.equals("RETURN_REQUESTED")?"退货处理中":"已成交";}
    private static String saleStatus(String state){return switch(state){case "REQUESTED" -> "待审核";case "APPROVED" -> "待完成";case "REJECTED" -> "已拒绝";default -> "已退款";};}
    @Override public void close(){closed=true;generation++;worker.shutdownNow();controls();}
    @FunctionalInterface private interface Work<T>{T run(String token) throws Exception;}
}
