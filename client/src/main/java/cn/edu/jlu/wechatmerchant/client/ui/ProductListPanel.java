package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.ProductPage;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.math.BigDecimal;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.text.ParseException;
import java.util.function.BiConsumer;

/** View only: consumes immutable catalog state; contains no network or database calls. */
final class ProductListPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final ProductImages images;
    private final DefaultListModel<ProductPage.Item> model = new DefaultListModel<>();
    private final JList<ProductPage.Item> list = new JList<>(model);
    private final JScrollPane listScroll = new JScrollPane(list);
    private final JButton refresh = new JButton("刷新");
    private final JButton previous = new JButton("上一页");
    private final JButton next = new JButton("下一页");
    private final JLabel pageNumber = label("第 1 页");
    private final JLabel status = label("尚未加载商品");
    private final JLabel message = label("尚未加载商品");
    private final CardLayout resultsLayout = new CardLayout();
    private final JPanel results = new JPanel(resultsLayout);
    private final JLabel preview = new JLabel();
    private final JLabel title = label("选中商品查看图片和说明");
    private final JLabel facts = label("");
    private final JLabel promotion = label("");
    private final JLabel imageNote = label("");
    private final JTextArea description = new JTextArea();
    private ClientController.CatalogState shownCatalog;
    private ClientController.State shownState;
    private final JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,Integer.MAX_VALUE,1));
    private final JButton addToCart=new JButton("加入购物车");
    private final JLabel cartNotice=label("登录后可加入购物车");

    ProductListPanel(ProductImages images) {
        super(new BorderLayout(0, 10));
        this.images = images;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));

        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.add(label("商品目录"), BorderLayout.WEST);
        refresh.setName("refresh-products");
        refresh.setEnabled(false);
        toolbar.add(refresh, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

        list.setName("product-list");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(108);
        list.setCellRenderer(new ProductRenderer());
        list.getAccessibleContext().setAccessibleName("商品图文列表");
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) showDetails(list.getSelectedValue());
        });
        listScroll.setMinimumSize(new Dimension(180, 120));
        results.add(listScroll, "list");
        message.setHorizontalAlignment(SwingConstants.CENTER);
        message.setFont(message.getFont().deriveFont(15f));
        JPanel statePanel = new JPanel(new BorderLayout());
        statePanel.add(message, BorderLayout.CENTER);
        results.add(statePanel, "message");
        resultsLayout.show(results, "message");

        JPanel detail = new JPanel(new BorderLayout(16, 0));
        detail.setBorder(BorderFactory.createEmptyBorder(10, 4, 0, 4));
        preview.setPreferredSize(new Dimension(176, 176));
        preview.setHorizontalAlignment(SwingConstants.CENTER);
        preview.setVerticalAlignment(SwingConstants.TOP);
        detail.add(preview, BorderLayout.WEST);
        JPanel detailText = new JPanel(new BorderLayout(0, 6));
        JPanel summary = new JPanel(new GridLayout(0, 1, 0, 5));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        summary.add(title);
        summary.add(facts);
        promotion.setName("product-promotion");
        summary.add(promotion);
        detailText.add(summary, BorderLayout.NORTH);
        description.setName("product-description");
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setFont(title.getFont().deriveFont(Font.PLAIN, 14f));
        description.setRows(3);
        JScrollPane descriptionScroll = new JScrollPane(description);
        descriptionScroll.setBorder(BorderFactory.createEmptyBorder());
        descriptionScroll.setOpaque(false);
        descriptionScroll.getViewport().setOpaque(false);
        detailText.add(descriptionScroll, BorderLayout.CENTER);
        imageNote.setForeground(new Color(95, 104, 116));
        JPanel purchase=new JPanel(new GridLayout(0,1,0,4));
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        quantity.setName("product-quantity");
        addToCart.setName("add-to-cart");
        addToCart.setEnabled(false);
        controls.add(label("数量")); controls.add(quantity); controls.add(addToCart);
        purchase.add(imageNote); purchase.add(controls); purchase.add(cartNotice);
        detailText.add(purchase, BorderLayout.SOUTH);
        detail.add(detailText, BorderLayout.CENTER);
        detail.setMinimumSize(new Dimension(180, 245));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, results, detail);
        split.setResizeWeight(1.0);
        split.setDividerLocation(285);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.add(status, BorderLayout.CENTER);
        JPanel pages = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        previous.setName("previous-products");
        next.setName("next-products");
        previous.setEnabled(false);
        next.setEnabled(false);
        pages.add(previous);
        pages.add(pageNumber);
        pages.add(next);
        footer.add(pages, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        showDetails(null);
    }

    void bindActions(Runnable reload, Runnable goPrevious, Runnable goNext) {
        refresh.addActionListener(event -> reload.run());
        previous.addActionListener(event -> goPrevious.run());
        next.addActionListener(event -> goNext.run());
    }

    void showState(ClientController.State state) {
        shownState=state;
        updatePurchaseActions();
        refresh.setEnabled(!state.busy());
        previous.setEnabled(state.canGoPrevious());
        next.setEnabled(state.canGoNext());
        ClientController.CatalogState catalog = state.catalog();
        // PING changes busy/connection status only; retain selection and scroll position.
        if (shownCatalog == catalog) return;
        shownCatalog = catalog;
        status.setText(catalog.message());
        pageNumber.setText("第 " + ((long) catalog.targetOffset() / ClientController.PAGE_SIZE + 1) + " 页");
        model.clear();
        showDetails(null);
        if (catalog.status() == ClientController.CatalogStatus.READY && catalog.page() != null) {
            model.addAll(catalog.page().items());
            resultsLayout.show(results, "list");
            list.setSelectedIndex(0);
            // The list card may have had zero viewport height while hidden. Reset after layout,
            // otherwise ensureIndexIsVisible can scroll past the first item's name on first load.
            SwingUtilities.invokeLater(() -> {
                if (shownCatalog == catalog) listScroll.getViewport().setViewPosition(new Point(0, 0));
            });
        } else {
            message.setText(catalog.message());
            resultsLayout.show(results, "message");
        }
    }

    private void showDetails(ProductPage.Item product) {
        quantity.setValue(1);
        updatePurchaseActions();
        if (product == null) {
            preview.setIcon(null);
            title.setText("选中商品查看图片和说明");
            facts.setText("");
            promotion.setText("");
            description.setText("");
            imageNote.setText("");
            return;
        }
        ProductImages.Pictures pictures = images.forProduct(product);
        preview.setIcon(pictures.preview());
        title.setText(product.name());
        facts.setText(priceText(product.priceCents()) + "    库存 " + product.stock() + " 件");
        promotion.setText(PromotionText.activity(product.promotion())
                +(product.promotion()==null ? "" : "    预估单价 "+priceText(product.promotion().estimatedUnitCents())));
        description.setText(product.description().isBlank() ? "暂无商品说明" : product.description());
        description.setCaretPosition(0);
        imageNote.setText(pictures.demo() ? "AI 生成演示图，仅供课程展示" : "暂无商品图片");
    }

    void bindCartAction(BiConsumer<Long,Integer> add,Runnable login) {
        addToCart.addActionListener(event -> {
            if (shownState==null || shownState.busy()) return;
            if (shownState.identity()==null) { login.run(); return; }
            ProductPage.Item product=list.getSelectedValue();
            if (product==null || !shownState.canModifyCart()) return;
            try {
                quantity.commitEdit();
                add.accept(product.productId(),(Integer)quantity.getValue());
            } catch (ParseException | IllegalArgumentException exception) {
                cartNotice.setText("请输入 1 到 2147483647 之间的整数数量");
            }
        });
    }

    private void updatePurchaseActions() {
        ProductPage.Item product=list.getSelectedValue();
        boolean available=shownState!=null && !shownState.busy() && product!=null && product.stock()>0;
        quantity.setEnabled(available);
        addToCart.setEnabled(available && (shownState.identity()==null || shownState.canModifyCart()));
        if (shownState!=null) cartNotice.setText(shownState.identity()==null ? "登录后可保存本人购物车" : shownState.cart().message());
    }

    static String priceText(long cents) {
        if (cents < 0) throw new IllegalArgumentException("Price cannot be negative.");
        return "¥ " + BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(text);
        return label;
    }

    private final class ProductRenderer extends JPanel implements ListCellRenderer<ProductPage.Item> {
        private static final long serialVersionUID = 1L;
        private final JLabel picture = new JLabel();
        private final JLabel name = label("");
        private final JLabel amount = label("");
        private final JLabel stock = label("");

        ProductRenderer() {
            super(new BorderLayout(16, 0));
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                    new Color(228, 232, 237)), BorderFactory.createEmptyBorder(9, 12, 9, 12)));
            picture.setPreferredSize(new Dimension(88, 88));
            add(picture, BorderLayout.WEST);
            JPanel text = new JPanel(new GridLayout(0, 1, 0, 3));
            text.setOpaque(false);
            name.setFont(name.getFont().deriveFont(Font.BOLD, 16f));
            amount.setFont(amount.getFont().deriveFont(Font.BOLD, 17f));
            text.add(name);
            text.add(amount);
            text.add(stock);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ProductPage.Item> owner, ProductPage.Item product,
                int index, boolean selected, boolean focused) {
            picture.setIcon(images.forProduct(product).thumbnail());
            name.setText(product.name());
            amount.setText(product.promotion()!=null && product.promotion().estimatedUnitCents()!=product.priceCents()
                    ? "预估 "+priceText(product.promotion().estimatedUnitCents())+"    原价 "+priceText(product.priceCents())
                    : priceText(product.priceCents()));
            stock.setText("库存 " + product.stock() + " 件");
            Color foreground = selected ? owner.getSelectionForeground() : owner.getForeground();
            name.setForeground(foreground);
            stock.setForeground(foreground);
            amount.setForeground(selected ? foreground : new Color(20, 93, 101));
            setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
            return this;
        }
    }
}
