package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import javax.swing.*;

/** Contextual notice, absent from normal screens. Full recovery details remain accessible. */
public final class NoticeBar extends JPanel {
    private final JTextArea message=Ui.text("",2);
    public NoticeBar(){super(new BorderLayout(12,0));setOpaque(false);setBorder(BorderFactory.createEmptyBorder(12,0,0,0));add(message);setVisible(false);}
    public void showMessage(String text){message.setText(text==null?"":text);setVisible(text!=null && !text.isBlank());revalidate();}
    public String getText(){return message.getText();}
    public static boolean important(String text){return text!=null && java.util.List.of("失败","不足","未确认","不可用","失效","过期","变化","核对","损坏","异常","请先","错误","取消","未能","拒绝").stream().anyMatch(text::contains);}
}
