package cn.edu.jlu.wechatmerchant.server.ui;

import cn.edu.jlu.wechatmerchant.server.application.PromotionManagementController;
import cn.edu.jlu.wechatmerchant.server.application.PromotionManagementService;
import cn.edu.jlu.wechatmerchant.server.application.PromotionPreviewService;
import cn.edu.jlu.wechatmerchant.server.domain.Product;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import cn.edu.jlu.wechatmerchant.desktop.*;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.function.Supplier;
import javax.swing.*;

/** Draft editor; persistence and authentication stay in the application layer. */
public final class PromotionManagementPanel extends JPanel implements AutoCloseable {
    private static final long serialVersionUID=1L;
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private final JComboBox<Product> products=new JComboBox<>();
    private final JButton previous=new JButton("上一页"), next=new JButton("下一页"), reload=new JButton("重新载入");
    private final JTextField reduction=new JTextField("0.00"), discount=new JTextField("10"), start=new JTextField(), end=new JTextField();
    private final JCheckBox enabled=new JCheckBox("启用此活动（保存后生效）");
    private final JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,Integer.MAX_VALUE,1));
    private final JButton preview=new JButton("预览未保存参数"), save=new JButton("保存配置");
    private final JLabel status=new JLabel("请选择商品"), price=new JLabel(""), saved=new JLabel("");
    private final JTextArea result=new JTextArea(8,48);
    private final PromotionManagementController controller;
    private PromotionManagementService.Editor editor;
    private int offset;
    private boolean hasMore, busy, filling, needsReload;

    public PromotionManagementPanel(PromotionManagementService service, Supplier<String> token, Runnable unauthorized) {
        super(new BorderLayout(10,10));
        setOpaque(false);controller=new PromotionManagementController(service,token,this::showStatus,unauthorized);
        products.setName("promotion-products");products.setPreferredSize(new Dimension(360,42));products.setRenderer((list,value,index,chosen,focus) -> {var label=Ui.label(value==null?"选择商品":value.name());label.setOpaque(true);label.setBackground(chosen?list.getSelectionBackground():list.getBackground());label.setForeground(chosen?list.getSelectionForeground():list.getForeground());label.setBorder(BorderFactory.createEmptyBorder(index<0?0:8,10,index<0?0:8,10));return label;});
        reload.setText("刷新");var top=new JPanel(new BorderLayout(24,0));top.setOpaque(false);top.add(products,BorderLayout.CENTER);top.add(Ui.row(previous,next,reload),BorderLayout.EAST);add(top,BorderLayout.NORTH);
        var form=new SurfacePanel(new BorderLayout(0,24));form.add(Ui.row(Ui.title("活动设置"),Ui.help("先直降再折扣，逐件四舍五入到分。时间按北京时间，开始包含、结束不包含。点击保存后生效。")),BorderLayout.NORTH);
        var fields=new JPanel(new GridLayout(0,2,24,24));fields.setOpaque(false);field(fields,"直降 / 元",reduction,"promotion-reduction");field(fields,"折扣 / 折",discount,"promotion-discount");field(fields,"开始时间",start,"promotion-start");field(fields,"结束时间",end,"promotion-end");
        enabled.setText("启用活动");enabled.setName("promotion-enabled");form.add(Ui.stack(24,price,fields,Ui.row(enabled,saved)),BorderLayout.CENTER);
        preview.setText("预览");save.setText("保存");preview.setName("promotion-preview");save.setName("promotion-save");reload.setName("promotion-reload");Theme.primary(save);form.add(Ui.row(save),BorderLayout.SOUTH);
        var previewCard=new SurfacePanel(new BorderLayout(0,24));previewCard.setPreferredSize(new Dimension(300,400));previewCard.add(Ui.row(Ui.title("效果预览"),new StatusPill("仅试算",StatusPill.Tone.NEUTRAL)),BorderLayout.NORTH);
        quantity.setName("promotion-quantity");quantity.setPreferredSize(new Dimension(132,42));result.setEditable(false);result.setLineWrap(true);result.setWrapStyleWord(true);result.setOpaque(false);result.setFont(new JLabel().getFont().deriveFont(16f));result.setName("promotion-preview-result");previewCard.add(Ui.stack(24,Ui.field("数量",quantity,null),preview,Ui.scroll(result)),BorderLayout.CENTER);
        var body=new JPanel(new BorderLayout(24,24));body.setOpaque(false);body.add(form,BorderLayout.CENTER);body.add(previewCard,BorderLayout.EAST);add(body,BorderLayout.CENTER);
        status.putClientProperty("html.disable",true);add(status,BorderLayout.SOUTH);
        products.addActionListener(event -> { if (!filling && !busy) loadSelected(); });
        previous.addActionListener(event -> loadPage(Math.max(0,offset-20)));
        next.addActionListener(event -> loadPage(offset+20));
        reload.addActionListener(event -> { if (products.getSelectedItem()==null) loadPage(offset); else loadSelected(); });
        preview.addActionListener(event -> previewDraft());
        save.addActionListener(event -> saveDraft());
        var draftChanges=new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { result.setText(""); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { result.setText(""); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { result.setText(""); }
        };
        for (JTextField input:new JTextField[]{reduction,discount,start,end}) input.getDocument().addDocumentListener(draftChanges);
        quantity.addChangeListener(event -> result.setText(""));
        enabled.addItemListener(event -> result.setText(""));
        updateControls();
    }

    public void start() { loadPage(0); }
    private void loadPage(int target) {
        editor=null; needsReload=false; result.setText("");
        controller.list(target,page -> {
            offset=page.offset(); hasMore=page.hasMore(); filling=true;
            try { products.removeAllItems(); for (Product p:page.products()) products.addItem(p); }
            finally { filling=false; }
            if (page.products().isEmpty()) { price.setText(""); saved.setText(""); }
            else loadSelected();
            updateControls();
        });
    }
    private void loadSelected() {
        Product selected=(Product)products.getSelectedItem();
        editor=null; needsReload=false; result.setText(""); updateControls();
        if (selected!=null) controller.load(selected.productId(),this::showEditor);
    }
    private void showEditor(PromotionManagementService.Editor value) {
        editor=value; needsReload=false;
        var configuration=value.configuration();
        reduction.setText(configuration==null ? "0.00" : BigDecimal.valueOf(configuration.terms().reductionCents(),2).toPlainString());
        discount.setText(configuration==null ? "10" : configuration.terms().discountText());
        var now=LocalDateTime.now(ZONE).withNano(0);
        start.setText(TIME.format(configuration==null ? now : LocalDateTime.ofInstant(configuration.startsAt(),ZONE)));
        end.setText(TIME.format(configuration==null ? now.plusDays(7) : LocalDateTime.ofInstant(configuration.endsAt(),ZONE)));
        enabled.setSelected(configuration!=null && configuration.enabled());
        price.setText("¥ "+BigDecimal.valueOf(value.product().priceCents(),2)+" / "+value.product().stock()+" 件");
        saved.setText(configuration==null ? "未配置" : "版本 "+configuration.version()+" / "+(configuration.enabled()?"已启用":"已停用"));
        result.setText(""); updateControls();
    }
    private void previewDraft() {
        if (editor==null) return;
        try {
            quantity.commitEdit();
            controller.preview(editor.product().productId(),(Integer)quantity.getValue(),terms(),this::showPreview);
        } catch (java.text.ParseException | RuntimeException exception) { status.setText("输入无效："+exception.getMessage()); }
    }
    private void showPreview(PromotionPreviewService.Preview value) {
        StringBuilder text=new StringBuilder();
        price.setText("¥ "+BigDecimal.valueOf(value.product().priceCents(),2)+" / "+value.product().stock()+" 件");
        var breakdown=value.breakdown();
        text.append("试算数量：").append(breakdown.quantity()).append(" 件；当前单价：¥ ")
                .append(BigDecimal.valueOf(value.product().priceCents(),2)).append('\n');
        text.append("原价合计：¥ ").append(new BigDecimal(breakdown.originalTotalCents(),2)).append('\n');
        for (var step:breakdown.steps()) text.append(step.stage()==PriceBreakdown.Stage.DIRECT_REDUCTION?"直降优惠":"折扣优惠")
                .append("：¥ ").append(new BigDecimal(step.savingsCents(),2)).append('\n');
        text.append("优惠合计：¥ ").append(new BigDecimal(breakdown.savingsTotalCents(),2)).append('\n')
                .append("预估金额：¥ ").append(new BigDecimal(breakdown.estimatedTotalCents(),2))
                ;
        result.setText(text.toString()); result.setCaretPosition(0);
    }
    private PromotionTerms terms() { return PromotionTerms.parse(reduction.getText(),discount.getText()); }
    private void saveDraft() {
        if (editor==null) return;
        try {
            var draft=new PromotionConfiguration(editor.product().productId(), editor.configuration()==null?0:editor.configuration().version(),
                    terms(),LocalDateTime.parse(start.getText().strip(),TIME).atZone(ZONE).toInstant(),
                    LocalDateTime.parse(end.getText().strip(),TIME).atZone(ZONE).toInstant(),enabled.isSelected());
            controller.save(draft,this::showEditor);
        } catch (RuntimeException exception) { status.setText("配置无效：请检查金额及起止时间（yyyy-MM-dd HH:mm:ss），结束须晚于开始。"); }
    }
    private void showStatus(PromotionManagementController.Status value) {
        busy=value.busy(); if (value.reloadRequired()) needsReload=true;
        status.setText(value.message());status.setVisible(value.busy() || NoticeBar.important(value.message()));updateControls();
    }
    private void updateControls() {
        products.setEnabled(!busy); reload.setEnabled(!busy);
        previous.setVisible(offset>0);next.setVisible(hasMore);previous.setEnabled(!busy && offset>0); next.setEnabled(!busy && hasMore);
        boolean editable=!busy && editor!=null && !needsReload;
        for (JComponent field: new JComponent[]{reduction,discount,start,end,enabled,quantity,preview,save}) field.setEnabled(editable);
    }
    private static void field(JPanel panel,String label,JTextField field,String name) {
        field.setName(name); panel.add(Ui.field(label,field,label.contains("时间")?"yyyy-MM-dd HH:mm:ss（北京时间）":null));
    }
    @Override public void close() { controller.close(); }
}
