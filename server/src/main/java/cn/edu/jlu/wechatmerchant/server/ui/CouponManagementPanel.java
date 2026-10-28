package cn.edu.jlu.wechatmerchant.server.ui;

import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.CouponCampaign;
import java.awt.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.*;
import java.util.UUID;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.*;

public final class CouponManagementPanel extends JPanel implements AutoCloseable {
    private static final long serialVersionUID=1L;
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private final DefaultListModel<CouponCampaign> model=new DefaultListModel<>();
    private final JList<CouponCampaign> list=new JList<>(model);
    private final JTextField name=new JTextField(),threshold=new JTextField("0.00"),discount=new JTextField("1.00"),start=new JTextField(),end=new JTextField();
    private final JSpinner total=new JSpinner(new SpinnerNumberModel(100,1,Integer.MAX_VALUE,1));
    private final JButton create=new JButton("新建券"),save=new JButton("保存草稿"),publish=new JButton("发布券"),pause=new JButton("暂停领取"),reload=new JButton("重新载入"),previous=new JButton("上一页"),next=new JButton("下一页");
    private final JLabel status=new JLabel(""),details=new JLabel("请选择或新建券活动");
    private final CouponManagementController controller;
    private CouponCampaign editor;
    private String createKey;
    private boolean busy,filling,dirty,needsReload,hasMore;
    private int offset;
    public CouponManagementPanel(CouponService service,Supplier<String> token,Runnable denied) {
        super(new BorderLayout(12,12)); setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        controller=new CouponManagementController(service,token,this::showStatus,denied);
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT));
        for(var button:new JButton[]{create,previous,next,reload})toolbar.add(button);
        create.setName("coupon-new"); previous.setName("coupon-previous"); next.setName("coupon-next"); reload.setName("coupon-reload");
        add(toolbar,BorderLayout.NORTH);
        list.setName("coupon-campaigns"); list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((owner,value,index,selected,focus) -> {
            JLabel label=new JLabel(value.campaignId()+" · "+value.name()+" / "+(value.published()?(value.paused()?"暂停领取":"已发布"):"草稿"));
            label.putClientProperty("html.disable",true); label.setOpaque(true); label.setBorder(BorderFactory.createEmptyBorder(8,6,8,6));
            label.setBackground(selected?owner.getSelectionBackground():owner.getBackground()); label.setForeground(selected?owner.getSelectionForeground():owner.getForeground()); return label;
        });
        JPanel fields=new JPanel(new GridLayout(0,2,10,10));
        field(fields,"券名称（1—80字符）",name,"coupon-name");
        field(fields,"满多少元可用（0为无门槛）",threshold,"coupon-threshold");
        field(fields,"减免金额（元，须大于0）",discount,"coupon-discount");
        field(fields,"开始（北京时间 年-月-日 时:分:秒）",start,"coupon-start");
        field(fields,"结束（到达此刻不可领/用）",end,"coupon-end");
        total.setName("coupon-total"); fields.add(new JLabel("发放总量（每人每种限领1张）")); fields.add(total);
        JPanel editorPanel=new JPanel(new BorderLayout(0,16)); details.putClientProperty("html.disable",true); editorPanel.add(details,BorderLayout.NORTH);
        editorPanel.add(fields,BorderLayout.CENTER);
        JTextArea note=new JTextArea("先保存草稿，再发布。发布后条款、有效期和总量锁定；要改条款请新建券。\n暂停仅停止后续领取，已领券在有效期内仍可使用。领券/用券共用起止，开始包含、结束不包含。",4,35);
        note.setLineWrap(true); note.setWrapStyleWord(true); note.setEditable(false); note.setOpaque(false); note.setFont(new JLabel().getFont()); editorPanel.add(note,BorderLayout.SOUTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(list),editorPanel); split.setResizeWeight(.28); split.setDividerLocation(260); add(split,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(0,8)); status.putClientProperty("html.disable",true); status.setName("coupon-admin-status"); footer.add(status,BorderLayout.NORTH);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        for(var button:new JButton[]{save,publish,pause})actions.add(button);
        save.setName("coupon-save"); publish.setName("coupon-publish"); pause.setName("coupon-pause"); footer.add(actions,BorderLayout.SOUTH); add(footer,BorderLayout.SOUTH);
        list.addListSelectionListener(event -> { if(!filling && !busy && !event.getValueIsAdjusting() && list.getSelectedValue()!=null)showEditor(list.getSelectedValue()); });
        create.addActionListener(event -> newDraft()); reload.addActionListener(event -> load(offset)); previous.addActionListener(event -> load(Math.max(0,offset-20))); next.addActionListener(event -> load(offset+20));
        save.addActionListener(event -> saveDraft()); publish.addActionListener(event -> { if(editor!=null)controller.publish(editor,this::saved); });
        pause.addActionListener(event -> { if(editor!=null)controller.pause(editor,!editor.paused(),this::saved); });
        DocumentListener change=new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e){changed();} @Override public void removeUpdate(DocumentEvent e){changed();} @Override public void changedUpdate(DocumentEvent e){changed();}
        };
        for(var field:new JTextField[]{name,threshold,discount,start,end})field.getDocument().addDocumentListener(change);
        total.addChangeListener(event -> changed()); controls();
    }
    public void start(){load(0);}
    private void load(int page) {
        editor=null; needsReload=false; controls();
        controller.list(page,value -> {
            offset=value.offset(); hasMore=value.hasMore(); filling=true;
            try { model.clear(); for(var item:value.items())model.addElement(item); if(!model.isEmpty())list.setSelectedIndex(0); }
            finally { filling=false; }
            if(model.isEmpty())newDraft(); else showEditor(model.firstElement());
        });
    }
    private void newDraft() {
        filling=true; try {list.clearSelection();}finally{filling=false;}
        createKey=UUID.randomUUID().toString();
        var now=LocalDateTime.now(ZONE).withNano(0);
        showEditor(new CouponCampaign(0,0,"新优惠券",0,100,now.atZone(ZONE).toInstant().toEpochMilli(),now.plusDays(7).atZone(ZONE).toInstant().toEpochMilli(),100,0,false,false));
    }
    private void showEditor(CouponCampaign value) {
        editor=value; filling=true; needsReload=false;
        try {
            name.setText(value.name()); threshold.setText(BigDecimal.valueOf(value.thresholdCents(),2).toPlainString()); discount.setText(BigDecimal.valueOf(value.discountCents(),2).toPlainString());
            start.setText(TIME.format(Instant.ofEpochMilli(value.startsAt()).atZone(ZONE))); end.setText(TIME.format(Instant.ofEpochMilli(value.endsAt()).atZone(ZONE))); total.setValue(value.total());
            details.setText(value.campaignId()==0?"新草稿，未保存/发布":"券 "+value.campaignId()+" / 版本 "+value.version()+" / 已领 "+value.claimed()+" / "+value.total()+" 张");
        } finally { filling=false; dirty=false; }
        controls();
    }
    private void saved(CouponCampaign value) {
        filling=true;
        try {
            int found=-1; for(int i=0;i<model.size();i++)if(model.get(i).campaignId()==value.campaignId())found=i;
            if(found>=0){model.set(found,value);list.setSelectedIndex(found);}
            else if(!hasMore && model.size()<20){model.addElement(value);list.setSelectedIndex(model.size()-1);}
            else list.clearSelection();
        } finally {filling=false;}
        showEditor(value);
    }
    private void saveDraft() {
        if(editor==null)return;
        try {
            total.commitEdit();
            var draft=new CouponCampaign(editor.campaignId(),editor.version(),name.getText().strip(),CouponCampaign.amount(threshold.getText(),false),CouponCampaign.amount(discount.getText(),true),
                    LocalDateTime.parse(start.getText().strip(),TIME).atZone(ZONE).toInstant().toEpochMilli(),LocalDateTime.parse(end.getText().strip(),TIME).atZone(ZONE).toInstant().toEpochMilli(),(Integer)total.getValue(),0,false,false);
            controller.save(draft,createKey,this::saved);
        } catch(Exception ex){status.setText("输入无效，请核对名称、金额、总量及起止时间（yyyy-MM-dd HH:mm:ss）。");}
    }
    private void changed(){if(!filling){dirty=true;controls();}}
    private void showStatus(CouponManagementController.Status value){busy=value.busy(); if(value.reloadRequired())needsReload=true;status.setText(value.message());controls();}
    private void controls() {
        boolean draft=editor!=null && !editor.published(),edit=!busy && !needsReload && draft;
        for(JComponent field:new JComponent[]{name,threshold,discount,start,end,total,save})field.setEnabled(edit);
        list.setEnabled(!busy);create.setEnabled(!busy);reload.setEnabled(!busy);previous.setEnabled(!busy && offset>0);next.setEnabled(!busy && hasMore);
        publish.setEnabled(edit && editor.campaignId()>0 && !dirty);pause.setEnabled(!busy && !needsReload && editor!=null && editor.published());
        pause.setText(editor!=null && editor.paused()?"恢复领取":"暂停领取");
    }
    private static void field(JPanel panel,String text,JTextField field,String key){field.setName(key);panel.add(new JLabel(text));panel.add(field);}
    @Override public void close(){controller.close();}
}
