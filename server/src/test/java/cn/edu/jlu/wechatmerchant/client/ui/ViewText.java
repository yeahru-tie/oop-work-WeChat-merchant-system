package cn.edu.jlu.wechatmerchant.client.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** Asserts the actual text rendered by structured components rather than an old text dump. */
final class ViewText {
    private ViewText(){}
    static String of(Component c){if(c==null)return "";var out=new StringBuilder();if(c instanceof JLabel l)out.append(l.getText()).append('\n');else if(c instanceof JTextComponent t)out.append(t.getText()).append('\n');if(c instanceof Container parent)for(var child:parent.getComponents())out.append(of(child));return out.toString();}
}
