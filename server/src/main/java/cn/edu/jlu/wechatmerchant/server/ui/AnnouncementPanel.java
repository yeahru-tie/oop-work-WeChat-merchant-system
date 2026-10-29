package cn.edu.jlu.wechatmerchant.server.ui;

import cn.edu.jlu.wechatmerchant.desktop.*;
import cn.edu.jlu.wechatmerchant.server.application.InboxService;
import cn.edu.jlu.wechatmerchant.server.application.PromotionManagementService;
import cn.edu.jlu.wechatmerchant.server.domain.Product;
import java.awt.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javax.swing.*;

public final class AnnouncementPanel extends JPanel implements AutoCloseable {
    private final InboxService service;private final PromotionManagementService catalog;private final Supplier<String> token;private final Runnable denied;
    private final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    private final JTextField title=new JTextField(),product=new JTextField();private final JTextArea text=new JTextArea(8,24);
    private final JComboBox<Object> picker=new JComboBox<>();private final JPanel history=new JPanel();
    private final JLabel status=Ui.caption("");
    private final JButton publish=Ui.primary("发布公告"),reload=Ui.ghost("刷新"),previous=Ui.ghost("上一页"),next=Ui.ghost("下一页");
    private String key=UUID.randomUUID().toString();private boolean busy,closed,uncertain;private int offset,shownCount;private long generation;
    private record Choice(long id,String name){@Override public String toString(){return name;}}
    private record Loaded(java.util.List<InboxService.Announcement> items,java.util.List<Product> products){}
    public AnnouncementPanel(InboxService service,Supplier<String> token,Runnable denied){this(service,token,denied,null);}
    public AnnouncementPanel(InboxService service,Supplier<String> token,Runnable denied,PromotionManagementService catalog){
        super(new BorderLayout(24,24));this.service=service;this.token=token;this.denied=denied;this.catalog=catalog;setOpaque(false);
        title.setName("announcement-title");product.setName("announcement-product");text.setName("announcement-text");history.setName("announcement-history");publish.setName("announcement-publish");reload.setName("announcement-refresh");
        text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new JLabel().getFont());text.setMargin(new Insets(12,12,12,12));
        picker.setEditable(true);picker.setEditor(new javax.swing.plaf.basic.BasicComboBoxEditor(){@Override protected JTextField createEditorComponent(){return product;}});picker.addItem("不关联商品");
        var compose=new SurfacePanel(new BorderLayout(0,24));compose.setPreferredSize(new Dimension(360,450));
        compose.add(Ui.row(Ui.title("新公告"),Ui.help("面向全部买家。标题最多80字，正文最多2000字。发布后保留原文；公告不改变实际优惠。")),BorderLayout.NORTH);
        var form=Ui.stack(24,Ui.field("标题",title,"最多80字"),Ui.field("关联商品",picker,"可选；选择商品或填写编号"),Ui.field("正文",Ui.scroll(text),"最多2000字"));compose.add(form,BorderLayout.CENTER);
        compose.add(Ui.stack(12,status,Ui.row(publish)),BorderLayout.SOUTH);add(compose,BorderLayout.WEST);
        var records=new JPanel(new BorderLayout(0,20));records.setOpaque(false);var heading=new JPanel(new BorderLayout());heading.setOpaque(false);heading.add(Ui.label("已发布"),BorderLayout.WEST);heading.add(reload,BorderLayout.EAST);records.add(heading,BorderLayout.NORTH);history.setOpaque(false);history.setLayout(new BoxLayout(history,BoxLayout.Y_AXIS));records.add(Ui.scroll(history),BorderLayout.CENTER);records.add(Ui.row(previous,next),BorderLayout.SOUTH);add(records,BorderLayout.CENTER);
        publish.addActionListener(e -> publish());reload.addActionListener(e -> load(offset));previous.addActionListener(e -> load(Math.max(0,offset-20)));next.addActionListener(e -> load(offset+20));controls(0);
    }
    public void start(){load(0);}
    private Long linkedProduct(){
        String value=product.getText().strip();Object selected=picker.getSelectedItem();
        if(value.isEmpty() || value.equals("不关联商品"))return null;
        if(selected instanceof Choice choice && value.equals(choice.name()))return choice.id();
        return Long.valueOf(value);
    }
    private void publish(){
        if(busy || closed)return;final Long link;
        try{link=linkedProduct();if(!cn.edu.jlu.wechatmerchant.protocol.InboxPage.validText(title.getText(),80) || !cn.edu.jlu.wechatmerchant.protocol.InboxPage.validText(text.getText(),2000) || link!=null && link<=0)throw new IllegalArgumentException();}
        catch(IllegalArgumentException ex){status.setText("请检查标题、正文和关联商品");return;}
        String heading=title.getText(),body=text.getText(),publishKey=key;
        run(session -> service.publish(session,publishKey,heading,body,link),id -> {uncertain=false;key=UUID.randomUUID().toString();title.setText("");text.setText("");picker.setSelectedIndex(0);status.setText("已发布");load(0);},true);
    }
    private void load(int target){
        run(session -> {
            var items=service.announcements(session,target);var choices=new ArrayList<Product>();
            if(catalog!=null && pickerNeedsData()){int page=0;while(page<200){var batch=catalog.list(session,page);choices.addAll(batch.products());if(!batch.hasMore())break;page+=20;}}
            return new Loaded(items,choices);
        },loaded -> {
            offset=target;shownCount=loaded.items().size();
            if(!loaded.products().isEmpty()){String old=product.getText();picker.removeAllItems();picker.addItem("不关联商品");for(var p:loaded.products())picker.addItem(new Choice(p.productId(),p.name()));product.setText(old);}
            history.removeAll();
            for(var item:loaded.items()){
                var card=new SurfacePanel(new BorderLayout(0,16));var title=Ui.label(item.title());title.setFont(title.getFont().deriveFont(Font.BOLD,17f));title.setToolTipText("标题："+item.title());card.add(title,BorderLayout.NORTH);
                String excerpt=item.text().codePointCount(0,item.text().length())>60?item.text().substring(0,item.text().offsetByCodePoints(0,60))+"…":item.text();card.add(Ui.text(excerpt,3),BorderLayout.CENTER);
                var more=Ui.help(item.title()+"\n\n"+item.text());more.setText("阅读全文");card.add(Ui.row(Ui.caption(java.time.Instant.ofEpochMilli(item.createdAt()).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString()),more),BorderLayout.SOUTH);
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE,250));history.add(card);history.add(Box.createVerticalStrut(16));
            }
            if(loaded.items().isEmpty())history.add(Ui.empty("暂无公告",null));history.revalidate();history.repaint();
        },false);
    }
    private volatile boolean optionsLoaded;
    private boolean pickerNeedsData(){if(optionsLoaded)return false;optionsLoaded=true;return true;}
    private <T> void run(Work<T> work,java.util.function.Consumer<T> apply,boolean write){
        if(busy || closed)return;String session=token.get();busy=true;long attempt=++generation;controls(0);
        worker.submit(() -> {T value=null;Exception error=null;try{value=work.run(session);}catch(Exception ex){error=ex;}T result=value;Exception failure=error;
            SwingUtilities.invokeLater(() -> {if(closed || generation!=attempt)return;busy=false;if(failure==null)apply.accept(result);else if(failure instanceof SecurityException){denied.run();return;}else if(failure instanceof IllegalArgumentException)status.setText(failure.getMessage());else{if(write)uncertain=true;else optionsLoaded=false;status.setText(write?"结果待核对，可重试原公告":"加载失败，请刷新");}controls(shownCount);});
        });
    }
    private void controls(int count){boolean idle=!busy && !closed;publish.setEnabled(idle);publish.setText(uncertain?"重试原公告":"发布公告");reload.setEnabled(idle);title.setEnabled(idle && !uncertain);text.setEnabled(idle && !uncertain);product.setEnabled(idle && !uncertain);picker.setEnabled(idle && !uncertain);previous.setVisible(offset>0);next.setVisible(count==20);previous.setEnabled(idle);next.setEnabled(idle);}
    @Override public void close(){closed=true;generation++;worker.shutdownNow();controls(0);}
    @FunctionalInterface private interface Work<T>{T run(String session) throws Exception;}
}
