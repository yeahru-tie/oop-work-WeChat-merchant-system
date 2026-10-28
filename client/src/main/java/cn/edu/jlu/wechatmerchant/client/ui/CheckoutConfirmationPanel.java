package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.protocol.QuoteSnapshot;
import java.awt.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;

final class CheckoutConfirmationPanel extends JPanel {
    private static final long serialVersionUID=1L;
    private final JButton confirm=new JButton("确认模拟成交");
    private final JLabel status=new JLabel("请再次确认商品和最终金额");
    private final BooleanSupplier fresh;
    private final Timer timer=new Timer(500,event -> update());
    CheckoutConfirmationPanel(QuoteSnapshot quote,BooleanSupplier fresh,Runnable submit,Runnable cancel){
        super(new BorderLayout(0,12));this.fresh=fresh;setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        JTextArea text=new JTextArea(OrderText.confirmation(quote),17,45);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new JLabel().getFont());text.setCaretPosition(0);text.setName("checkout-confirmation-summary");add(new JScrollPane(text),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(0,8));status.setName("checkout-confirmation-status");footer.add(status,BorderLayout.NORTH);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));JButton back=new JButton("返回，不成交");confirm.setName("checkout-confirm");back.setName("checkout-cancel");buttons.add(back);buttons.add(confirm);footer.add(buttons,BorderLayout.SOUTH);add(footer,BorderLayout.SOUTH);
        confirm.addActionListener(event -> {if(fresh.getAsBoolean())submit.run();else update();});back.addActionListener(event -> cancel.run());update();
    }
    private void update(){boolean ready=fresh.getAsBoolean();confirm.setEnabled(ready);status.setText(ready?"请再次确认商品和最终金额":"报价时间边界已到，请返回并刷新报价后再次确认");}
    @Override public void addNotify(){super.addNotify();timer.start();}
    @Override public void removeNotify(){timer.stop();super.removeNotify();}
}
