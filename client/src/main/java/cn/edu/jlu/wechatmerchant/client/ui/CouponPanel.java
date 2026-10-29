package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.CouponPage;
import java.awt.*;
import cn.edu.jlu.wechatmerchant.desktop.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.*;
import javax.swing.*;

/** Opened from the cart, with the same controller/transport queue as every buyer action. */
final class CouponPanel extends JPanel {
    private static final long serialVersionUID=1L;
    private final DefaultListModel<CouponPage.Item> model=new DefaultListModel<>();
    private final JList<CouponPage.Item> list=new JList<>(model);
    private final JButton available=new JButton("领券活动"),owned=new JButton("我的券"),reload=new JButton("刷新"),previous=new JButton("上一页"),next=new JButton("下一页"),claim=new JButton("领取"),use=new JButton("使用"),none=new JButton("不用券"),back=new JButton("返回购物车");
    private final JTextArea details=new JTextArea(5,45),message=new JTextArea(2,45);
    private CouponPage shown;
    private ClientController.State state;
    private String kind="OWNED";
    private int offset;
    private BiConsumer<String,Integer> refresh;
    private LongConsumer take;
    private Consumer<Long> select;
    CouponPanel() {
        super(new BorderLayout(8,8));
        JPanel tools=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        for(var b:new JButton[]{back,available,owned,reload,previous,next})tools.add(b);
        back.setName("coupon-back"); available.setName("coupon-available"); owned.setName("coupon-owned"); reload.setName("coupon-refresh"); previous.setName("buyer-coupon-previous");next.setName("buyer-coupon-next"); add(tools,BorderLayout.NORTH);
        list.setName("buyer-coupons"); list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(126);list.setOpaque(false);list.setCellRenderer((owner,item,index,chosen,focus) -> {
            var card=new SurfacePanel(new BorderLayout(0,12));card.putClientProperty("theme.selected",chosen);card.add(Ui.row(Ui.label(item.name()),new StatusPill(ClientController.couponReason(item.status().name()),StatusPill.Tone.ACCENT)),BorderLayout.NORTH);
            var money=Ui.label(terms(item));money.setFont(money.getFont().deriveFont(Font.BOLD,19f));card.add(money,BorderLayout.CENTER);card.add(Ui.caption("全店适用"),BorderLayout.SOUTH);return Ui.renderer(card,owner.getWidth(),126);
        });
        add(new JScrollPane(list),BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(0,8));
        for(var area:new JTextArea[]{details,message}){area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setOpaque(false);area.setFont(new JLabel().getFont());}
        details.setName("buyer-coupon-details"); message.setName("buyer-coupon-message"); bottom.add(details,BorderLayout.NORTH);
        bottom.add(message,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        claim.setName("claim-coupon");use.setName("use-coupon");none.setName("no-coupon");for(var b:new JButton[]{claim,use,none})actions.add(b);bottom.add(actions,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
        list.addListSelectionListener(event -> { if(!event.getValueIsAdjusting())selection(); });
        available.addActionListener(event -> load("AVAILABLE",0)); owned.addActionListener(event -> load("OWNED",0)); reload.addActionListener(event -> load(kind,offset));
        previous.addActionListener(event -> load(kind,Math.max(0,offset-20))); next.addActionListener(event -> load(kind,offset+20));
        claim.addActionListener(event -> { var item=list.getSelectedValue(); if(item!=null && claim.isEnabled())take.accept(item.campaignId()); });
        use.addActionListener(event -> { var item=list.getSelectedValue(); if(item!=null && use.isEnabled())select.accept(item.couponId()); });
        none.addActionListener(event -> {if(select!=null)select.accept(null);});
        selection();
    }
    void bind(BiConsumer<String,Integer> refresh,LongConsumer claim,Consumer<Long> select,Runnable close) {
        this.refresh=refresh;this.take=claim;this.select=select;back.addActionListener(event -> close.run());
    }
    void open(){load("OWNED",0);}
    private void load(String mode,int page){kind=mode;offset=page;if(refresh!=null)refresh.accept(mode,page);}
    void showState(ClientController.State value) {
        state=value;var page=value.identity()==null?null:value.coupons().page();
        if(shown!=page){
            long selected=list.getSelectedValue()==null?0:list.getSelectedValue().couponId();shown=page;model.clear();
            if(page!=null){kind=page.kind();offset=page.offset();model.addAll(page.items());int chosen=0;for(int i=0;i<model.size();i++)if(selected>0 && model.get(i).couponId()==selected)chosen=i;if(!model.isEmpty())list.setSelectedIndex(chosen);}
        }
        message.setText(value.identity()==null?"":value.coupons().message());message.setVisible(NoticeBar.important(message.getText()));selection();
    }
    private void selection() {
        boolean idle=state!=null && !state.busy() && state.canModifyCart();var item=list.getSelectedValue();
        for(var b:new JButton[]{available,owned,reload,none})b.setEnabled(idle);
        list.setEnabled(idle);previous.setEnabled(idle && offset>0);next.setEnabled(idle && shown!=null && shown.hasMore());
        claim.setVisible(kind.equals("AVAILABLE"));use.setVisible(kind.equals("OWNED"));
        claim.setEnabled(idle && take!=null && item!=null && item.status()==CouponPage.Status.CAN_CLAIM);
        use.setEnabled(idle && select!=null && item!=null && kind.equals("OWNED") && item.status()==CouponPage.Status.USABLE);
        if(item==null){details.setText(shown!=null && model.isEmpty()?"本页暂无优惠券":"请选择券查看条款");return;}
        DateTimeFormatter time=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
        details.setText(item.name()+"\n"+terms(item)+"；全店适用，每单最多一张\n北京时间："+time.format(Instant.ofEpochMilli(item.startsAt()))+" 至 "+time.format(Instant.ofEpochMilli(item.endsAt()))+"（结束不包含）\n"+ClientController.couponReason(item.status().name())+(kind.equals("AVAILABLE")?"；已领 "+item.claimed()+" / "+item.total()+" 张":"；门槛按商品优惠后金额判断"));
        details.setCaretPosition(0);
    }
    private static String terms(CouponPage.Item item){return (item.thresholdCents()==0?"无门槛":"满¥ "+BigDecimal.valueOf(item.thresholdCents(),2).toPlainString())+" 减¥ "+BigDecimal.valueOf(item.discountCents(),2).toPlainString();}
}
