package cn.edu.jlu.wechatmerchant.server.ui;

import cn.edu.jlu.wechatmerchant.server.application.PromotionManagementController;
import cn.edu.jlu.wechatmerchant.server.application.PromotionManagementService;
import cn.edu.jlu.wechatmerchant.server.application.PromotionPreviewService;
import cn.edu.jlu.wechatmerchant.server.domain.Product;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import java.awt.BorderLayout;
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
        setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        controller=new PromotionManagementController(service,token,this::showStatus,unauthorized);
        JPanel top=new JPanel(new BorderLayout(8,8));
        products.setName("promotion-products");
        products.setPreferredSize(new java.awt.Dimension(360,28));
        products.setRenderer((list,value,index,selected,focus) -> {
            JLabel label=new JLabel(value==null ? "请选择商品" : value.productId()+" · "+value.name());
            label.putClientProperty("html.disable",Boolean.TRUE);
            label.setOpaque(true);
            label.setBackground(selected?list.getSelectionBackground():list.getBackground());
            label.setForeground(selected?list.getSelectionForeground():list.getForeground());
            return label;
        });
        top.add(products,BorderLayout.CENTER);
        JPanel pages=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));
        pages.add(previous); pages.add(next); pages.add(reload); top.add(pages,BorderLayout.EAST);
        add(top,BorderLayout.NORTH);
        JPanel body=new JPanel(new BorderLayout(0,8));
        JPanel fields=new JPanel(new GridLayout(0,2,12,6));
        fields.add(new JLabel("当前商品价格 / 库存")); fields.add(price);
        field(fields,"每件直降（元，0 表示不直降）",reduction,"promotion-reduction");
        field(fields,"统一折扣（>0 至10折）",discount,"promotion-discount");
        field(fields,"开始（北京时间，年-月-日 时:分:秒）",start,"promotion-start");
        field(fields,"结束（到达此刻即失效）",end,"promotion-end");
        fields.add(new JLabel("启停状态")); fields.add(enabled); enabled.setName("promotion-enabled");
        fields.add(new JLabel("试算数量（不占库存）")); fields.add(quantity); quantity.setName("promotion-quantity");
        fields.add(new JLabel("已保存状态")); fields.add(saved);
        body.add(fields,BorderLayout.NORTH);
        result.setEditable(false); result.setLineWrap(true); result.setWrapStyleWord(true);
        result.setName("promotion-preview-result");
        body.add(new JScrollPane(result),BorderLayout.CENTER);
        add(body,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(0,8));
        status.putClientProperty("html.disable",Boolean.TRUE); bottom.add(status,BorderLayout.NORTH);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        preview.setName("promotion-preview"); save.setName("promotion-save"); reload.setName("promotion-reload");
        actions.add(preview); actions.add(save); bottom.add(actions,BorderLayout.SOUTH); add(bottom,BorderLayout.SOUTH);
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
        saved.setText(configuration==null ? "尚无配置（默认停用）" : "版本 "+configuration.version()+" / "+(configuration.enabled()?"已启用（须在有效期内）":"已停用"));
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
        StringBuilder text=new StringBuilder("未保存参数试算；忽略启停/时间，不代表活动已发布。\n");
        price.setText("¥ "+BigDecimal.valueOf(value.product().priceCents(),2)+" / "+value.product().stock()+" 件");
        var breakdown=value.breakdown();
        text.append("试算数量：").append(breakdown.quantity()).append(" 件；当前单价：¥ ")
                .append(BigDecimal.valueOf(value.product().priceCents(),2)).append('\n');
        text.append("原价合计：¥ ").append(new BigDecimal(breakdown.originalTotalCents(),2)).append('\n');
        for (var step:breakdown.steps()) text.append(step.stage()==PriceBreakdown.Stage.DIRECT_REDUCTION?"直降优惠":"折扣优惠")
                .append("：¥ ").append(new BigDecimal(step.savingsCents(),2)).append('\n');
        text.append("优惠合计：¥ ").append(new BigDecimal(breakdown.savingsTotalCents(),2)).append('\n')
                .append("预估金额：¥ ").append(new BigDecimal(breakdown.estimatedTotalCents(),2))
                .append("\n每件先减后折，逐件四舍五入到分；不扣库存、不生成订单。");
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
        status.setText(value.message()); updateControls();
    }
    private void updateControls() {
        products.setEnabled(!busy); reload.setEnabled(!busy);
        previous.setEnabled(!busy && offset>0); next.setEnabled(!busy && hasMore);
        boolean editable=!busy && editor!=null && !needsReload;
        for (JComponent field: new JComponent[]{reduction,discount,start,end,enabled,quantity,preview,save}) field.setEnabled(editable);
    }
    private static void field(JPanel panel,String label,JTextField field,String name) {
        field.setName(name); panel.add(new JLabel(label)); panel.add(field);
    }
    @Override public void close() { controller.close(); }
}
