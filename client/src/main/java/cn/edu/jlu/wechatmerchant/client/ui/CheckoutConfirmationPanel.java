package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.desktop.*;
import cn.edu.jlu.wechatmerchant.protocol.QuoteSnapshot;
import java.awt.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;

final class CheckoutConfirmationPanel extends JPanel {
    private final JButton confirm=Ui.primary("确认模拟成交");
    private final JLabel status=Ui.caption("");
    private final BooleanSupplier fresh;
    private final Timer timer=new Timer(500,event -> update());
    CheckoutConfirmationPanel(QuoteSnapshot quote,BooleanSupplier fresh,Runnable submit,Runnable cancel){
        super(new BorderLayout(0,24));this.fresh=fresh;setBorder(BorderFactory.createEmptyBorder(28,28,28,28));
        add(Ui.row(Ui.title("确认订单"),new StatusPill("模拟成交",StatusPill.Tone.ACCENT)),BorderLayout.NORTH);
        var lines=new JPanel();lines.setOpaque(false);lines.setLayout(new BoxLayout(lines,BoxLayout.Y_AXIS));lines.setName("checkout-confirmation-summary");
        for(var item:quote.selectedCart().items()){var row=new SurfacePanel(new BorderLayout(20,0));row.add(Ui.stack(8,Ui.label(item.name()),Ui.caption("× "+item.quantity())),BorderLayout.CENTER);row.add(Ui.label(OrderText.money(item.estimatedCents())),BorderLayout.EAST);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,110));lines.add(row);lines.add(Box.createVerticalStrut(12));}
        add(Ui.scroll(lines),BorderLayout.CENTER);
        var money=Ui.label(OrderText.money(quote.payableCents()));money.setFont(money.getFont().deriveFont(Font.BOLD,30f));money.setForeground(Theme.accent());money.putClientProperty("theme.accent",true);
        var back=Ui.ghost("返回");confirm.setName("checkout-confirm");back.setName("checkout-cancel");status.setName("checkout-confirmation-status");
        var help=Ui.help(OrderText.confirmation(quote));help.setText("金额明细");
        add(Ui.stack(20,Ui.valueRow("应付金额",money),status,Ui.row(help,back,confirm)),BorderLayout.SOUTH);
        confirm.addActionListener(e -> {if(fresh.getAsBoolean())submit.run();else update();});back.addActionListener(e -> cancel.run());update();
    }
    private void update(){boolean ready=fresh.getAsBoolean();confirm.setEnabled(ready);status.setText(ready?"无真实支付":"报价已失效，请返回更新");}
    @Override public void addNotify(){super.addNotify();timer.start();}
    @Override public void removeNotify(){timer.stop();super.removeNotify();}
}
