package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import cn.edu.jlu.wechatmerchant.protocol.CartSelection;
import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;

/** Private card view; each event targets its card's item ID, never a global selection. */
final class CartPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final ProductImages images;
    private final Map<Long, ItemCard> cards = new LinkedHashMap<>();
    private final CardGrid grid = new CardGrid();
    private final CardLayout viewLayout = new CardLayout();
    private final JPanel views = new JPanel(viewLayout);
    private final JLabel empty = label("登录后查看和保存自己的购物车");
    private final JLabel status = label("登录后查看和保存自己的购物车");
    private final JTextArea total = new JTextArea(5, 24);
    private final JButton login = new JButton("登录查看购物车");
    private final JButton refresh = new JButton("刷新购物车");
    private final JButton coupons = new JButton("领券/选券");
    private final JButton quoteRefresh = new JButton("刷新报价");
    private final CouponPanel couponPanel=new CouponPanel();
    private final javax.swing.Timer quoteClock=new javax.swing.Timer(1000,event -> showTotals());
    private boolean couponOpen;
    private final JButton checkout=new JButton("确认模拟成交"),queryPending=new JButton("核对本次结果"),retryPending=new JButton("原键重试本次");
    private final JTextArea checkoutStatus=new JTextArea(2,24);
    private java.util.function.BiConsumer<Long,Boolean> selectItem;
    private ClientController.State state;
    private UserIdentity owner;
    private CartSnapshot displayed;
    private BiConsumer<Long, Integer> edit;

    CartPanel(ProductImages images) {
        super(new BorderLayout(0, 12));
        this.images = images;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel heading = new JPanel(new BorderLayout());
        heading.add(status, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        login.setName("cart-login");
        refresh.setName("refresh-cart");
        actions.add(login);
        actions.add(refresh);
        coupons.setName("open-coupons");quoteRefresh.setName("refresh-quote");actions.add(coupons);actions.add(quoteRefresh);
        heading.add(actions, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        grid.setName("cart-cards");
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        views.add(scroll, "cards");
        JPanel emptyPage = new JPanel(new BorderLayout());
        empty.setHorizontalAlignment(JLabel.CENTER);
        emptyPage.add(empty, BorderLayout.CENTER);
        views.add(emptyPage, "empty");
        views.add(couponPanel,"coupons");
        viewLayout.show(views, "empty");
        add(views, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 6));
        total.setName("cart-total");
        configureText(total);
        footer.add(total,BorderLayout.NORTH);
        JTextArea hint=new JTextArea("加入购物车不占库存；确认模拟成交后才扣库存和核销所选券。预估不锁价。",2,24);
        configureText(hint);
        footer.add(hint,BorderLayout.CENTER);
        JPanel settlement=new JPanel(new BorderLayout(0,5));configureText(checkoutStatus);checkoutStatus.setName("checkout-status");settlement.add(checkoutStatus,BorderLayout.CENTER);
        JPanel settlementActions=new JPanel(new FlowLayout(FlowLayout.RIGHT));checkout.setName("prepare-checkout");queryPending.setName("query-pending");retryPending.setName("retry-pending");settlementActions.add(checkout);settlementActions.add(queryPending);settlementActions.add(retryPending);settlement.add(settlementActions,BorderLayout.SOUTH);footer.add(settlement,BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        updateActions();
    }
    void bindCheckoutActions(java.util.function.BiConsumer<Long,Boolean> select,Runnable prepare,Runnable query,Runnable retry){this.selectItem=select;checkout.addActionListener(e -> prepare.run());queryPending.addActionListener(e -> query.run());retryPending.addActionListener(e -> retry.run());updateActions();}

    void bindCouponActions(java.util.function.BiConsumer<String,Integer> refreshCoupons,java.util.function.LongConsumer claim,
            java.util.function.Consumer<Long> select,Runnable refreshQuote,Runnable signIn) {
        couponPanel.bind(refreshCoupons,claim,select,() -> {couponOpen=false;showState(state);});
        coupons.addActionListener(event -> {
            if(state==null || state.busy())return;
            if(state.identity()==null){signIn.run();return;}
            couponOpen=true;viewLayout.show(views,"coupons");couponPanel.open();
        });
        quoteRefresh.addActionListener(event -> refreshQuote.run());
    }

    void bindActions(Runnable signIn, Runnable reload, BiConsumer<Long, Integer> edit) {
        this.edit = edit;
        login.addActionListener(event -> signIn.run());
        refresh.addActionListener(event -> reload.run());
        updateActions();
    }

    void showState(ClientController.State next) {
        state = next;
        boolean sameOwner = next.identity() != null && next.identity().equals(owner);
        CartSnapshot incoming = next.identity() == null ? null : next.cart().contents();
        // Keep disabled cards visible only while this owner's known write is in flight.
        boolean saving = sameOwner && next.busy() && next.action() == ClientController.Action.CART_WRITE
                && next.cart().status() == ClientController.CartStatus.LOADING && displayed != null;
        if (!sameOwner) { clearCards(); couponOpen=false; }
        owner = next.identity();
        if (incoming != null) display(incoming);
        else if (!saving) clearCards();
        status.setText(next.cart().message());
        couponPanel.showState(next);
        showTotals();
        if(couponOpen && next.identity()!=null) viewLayout.show(views,"coupons");
        else if (cards.isEmpty()) {
            empty.setText(incoming != null ? "购物车为空，去商品页挑选商品吧" : next.cart().message());
            viewLayout.show(views, "empty");
        } else {
            viewLayout.show(views, "cards");
        }
        updateActions();
    }

    private void showTotals() {
        if(state==null || displayed==null){total.setText("");return;}
        boolean saving=state.busy() && state.action()==ClientController.Action.CART_WRITE;
        var selected=CartSelection.select(displayed,state.selectedIds());
        String text="已选原价合计："+money(selected.totalCents())+"\n商品优惠："+money(selected.totalCents().subtract(selected.estimatedTotalCents()));
        var coupon=state.coupons();
        if(!coupon.active())text+="\n预估合计："+money(selected.estimatedTotalCents())+(saving?"（正在更新）":"");
        else if(state.busy() || coupon.quote()==null || !coupon.fresh())text+="\n券后报价：待刷新报价或重新选券";
        else if(!coupon.quote().ready())text+="\n券后报价：所选券不可用，请重选或明确不用";
        else text+="\n优惠券抵扣："+money(coupon.quote().couponReductionCents())+"\n预估应付："+money(coupon.quote().payableCents());
        if(coupon.active())text+="\n"+(coupon.quote()!=null && !coupon.fresh()?"活动时间边界已到，请刷新报价核对":coupon.message());
        total.setText(text);total.setCaretPosition(0);
    }
    @Override public void addNotify(){super.addNotify();quoteClock.start();}
    @Override public void removeNotify(){quoteClock.stop();super.removeNotify();}

    private void display(CartSnapshot contents) {
        var ids = contents.items().stream().map(CartSnapshot.Item::itemId).toList();
        boolean membershipChanged = !ids.equals(cards.keySet().stream().toList());
        cards.keySet().removeIf(id -> !ids.contains(id));
        for (CartSnapshot.Item item : contents.items()) {
            ItemCard card = cards.computeIfAbsent(item.itemId(), ignored -> new ItemCard(item));
            card.update(item);
        }
        if (membershipChanged) {
            grid.removeAll();
            for (long id : ids) grid.add(cards.get(id));
            grid.revalidate();
            grid.repaint();
        }
        displayed = contents;
    }

    private void clearCards() {
        cards.clear();
        grid.removeAll();
        grid.revalidate();
        grid.repaint();
        displayed = null;
    }

    private void updateActions() {
        boolean guest = state == null || state.identity() == null;
        boolean busy = state != null && state.busy();
        login.setVisible(guest);
        login.setEnabled(!busy);
        refresh.setEnabled(!guest && !busy);
        coupons.setEnabled(!busy && (guest || state.canModifyCart()));
        quoteRefresh.setEnabled(!guest && !busy && state.canModifyCart());
        boolean pending=!guest && state.orders().pending()!=null;
        checkout.setEnabled(selectItem!=null && state!=null && state.canModifyCart() && !state.selectedIds().isEmpty());queryPending.setVisible(pending);retryPending.setVisible(pending);queryPending.setEnabled(pending && !busy);retryPending.setEnabled(pending && !busy);
        checkoutStatus.setText(guest?"":state.orders().message().equals("尚无订单数据")?"勾选商品后，可核对金额并二次确认模拟成交":state.orders().message());
        for (ItemCard card : cards.values()) card.setEditable(canEdit(card));
    }

    private boolean canEdit(ItemCard card) {
        return edit != null && state != null && state.canModifyCart() && owner != null
                && owner.equals(state.identity()) && cards.get(card.item.itemId()) == card;
    }

    static String money(BigInteger cents) { return "¥ " + new BigDecimal(cents, 2).toPlainString(); }

    private static JLabel label(String text) {
        JLabel result = new JLabel();
        result.putClientProperty("html.disable", Boolean.TRUE);
        result.setText(text);
        return result;
    }

    private final class ItemCard extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JLabel picture = new JLabel();
        private final JTextArea name = new JTextArea(2, 14);
        private final JLabel imageNote = label("");
        private final JLabel price = label("");
        private final JLabel stock = label("");
        private final JTextArea subtotal = new JTextArea(2, 18);
        private final JTextArea promotion = new JTextArea(3,18);
        private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
        private final JButton remove = new JButton("移除");
        private final javax.swing.JCheckBox selected=new javax.swing.JCheckBox("本次结算",true);
        private CartSnapshot.Item item;
        private boolean updating;

        ItemCard(CartSnapshot.Item item) {
            super(new BorderLayout(0, 10));
            setName("cart-item-" + item.itemId());
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(221, 227, 233), 1, true),
                    BorderFactory.createEmptyBorder(14, 14, 14, 14)));
            JPanel top = new JPanel(new BorderLayout(12, 0));
            top.setOpaque(false);
            picture.setPreferredSize(new Dimension(88, 88));
            top.add(picture, BorderLayout.WEST);
            JPanel title = new JPanel(new BorderLayout(0, 4));
            title.setOpaque(false);
            configureText(name);
            name.setName("cart-name-" + item.itemId());
            name.setFont(name.getFont().deriveFont(Font.BOLD, 16f));
            title.add(name, BorderLayout.CENTER);
            imageNote.setFont(imageNote.getFont().deriveFont(12f));
            imageNote.setForeground(new Color(95, 104, 116));
            title.add(imageNote, BorderLayout.SOUTH);
            top.add(title, BorderLayout.CENTER);
            selected.setOpaque(false);selected.setName("cart-selected-"+item.itemId());top.add(selected,BorderLayout.SOUTH);
            add(top, BorderLayout.NORTH);
            JPanel facts = new JPanel();
            facts.setLayout(new javax.swing.BoxLayout(facts,javax.swing.BoxLayout.Y_AXIS));
            facts.setOpaque(false);
            facts.add(price);
            facts.add(javax.swing.Box.createVerticalStrut(4));
            facts.add(stock);
            facts.add(javax.swing.Box.createVerticalStrut(4));
            configureText(promotion);
            promotion.setName("cart-promotion-"+item.itemId());
            facts.add(promotion);
            facts.add(javax.swing.Box.createVerticalStrut(4));
            configureText(subtotal);
            subtotal.setName("cart-subtotal-" + item.itemId());
            subtotal.setFont(subtotal.getFont().deriveFont(Font.BOLD, 15f));
            facts.add(subtotal);
            add(facts, BorderLayout.CENTER);
            JPanel bottom = new JPanel(new BorderLayout(0, 5));
            bottom.setOpaque(false);
            JPanel controls = new JPanel(new BorderLayout(8, 0));
            controls.setOpaque(false);
            JPanel amount = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            amount.setOpaque(false);
            amount.add(label("数量"));
            quantity.setName("cart-quantity-" + item.itemId());
            quantity.setPreferredSize(new Dimension(104, 28));
            amount.add(quantity);
            controls.add(amount, BorderLayout.WEST);
            remove.setName("remove-cart-item-" + item.itemId());
            controls.add(remove, BorderLayout.EAST);
            bottom.add(controls, BorderLayout.NORTH);
            JLabel hint = label("数量改后自动保存，输入后按回车");
            hint.setFont(hint.getFont().deriveFont(12f));
            hint.setForeground(new Color(95, 104, 116));
            bottom.add(hint, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);

            quantity.addChangeListener(event -> {
                if (!updating && canEdit(this) && !quantity.getValue().equals(this.item.quantity())) {
                    edit.accept(this.item.itemId(), (Integer) quantity.getValue());
                }
            });
            var field = ((JSpinner.NumberEditor) quantity.getEditor()).getTextField();
            field.setFocusLostBehavior(JFormattedTextField.PERSIST);
            field.addActionListener(event -> commitQuantity());
            field.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent event) {
                    if (event.getOppositeComponent() != remove) commitQuantity();
                }
            });
            remove.addActionListener(event -> { if (canEdit(this)) edit.accept(this.item.itemId(), null); });
            selected.addActionListener(event -> {if(canEdit(this) && selectItem!=null)selectItem.accept(this.item.itemId(),selected.isSelected());});
            update(item);
        }

        void update(CartSnapshot.Item next) {
            if (next.equals(item)) return;
            updating = true;
            try {
                item = next;
                var pictures = images.forProduct(next.productId(), next.name());
                picture.setIcon(pictures.thumbnail());
                name.setText(next.name());
                name.setCaretPosition(0);
                imageNote.setText(pictures.demo() ? "AI 生成演示图" : "暂无商品图片");
                price.setText("当前单价：" + money(BigInteger.valueOf(next.priceCents())));
                stock.setText("当前库存：" + next.stock() + (next.quantity() > next.stock() ? " 件（库存不足）" : " 件"));
                stock.setForeground(next.quantity() > next.stock() ? new Color(168, 62, 30) : getForeground());
                promotion.setText(PromotionText.details(next.promotion(),next.quantity()));
                subtotal.setText("原价小计：" + money(next.subtotalCents())+"\n预估小计："+money(next.estimatedCents()));
                subtotal.setCaretPosition(0);
                quantity.setValue(next.quantity());
            } finally {
                updating = false;
            }
        }

        void setEditable(boolean editable) { quantity.setEnabled(editable); remove.setEnabled(editable);selected.setEnabled(editable && selectItem!=null);selected.setSelected(state!=null && state.selectedIds().contains(item.itemId())); }

        private void commitQuantity() {
            if (updating || !canEdit(this)) return;
            try {
                quantity.commitEdit();
            } catch (ParseException exception) {
                status.setText("数量必须为有效正整数，原数量未修改");
                updating = true;
                try {
                    quantity.setValue(item.quantity());
                    ((JSpinner.NumberEditor) quantity.getEditor()).getTextField().setValue(item.quantity());
                } finally { updating = false; }
            }
        }
    }

    private static void configureText(JTextArea area) {
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(new JLabel().getFont());
    }

    /** Tracks viewport width and lays out complete cards in one to three columns. */
    private static final class CardGrid extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        private static final int GAP = 14;
        CardGrid() { setLayout(null); }
        private int columns() { return Math.max(1, Math.min(3, (getWidth() + GAP) / (300 + GAP))); }
        private int rowHeight() {
            int height = 280;
            for (Component card : getComponents()) height = Math.max(height, card.getPreferredSize().height);
            return height;
        }
        @Override public Dimension getPreferredSize() {
            int rows = (getComponentCount() + columns() - 1) / columns();
            return new Dimension(Math.max(300, getWidth()), rows == 0 ? 0 : rows * (rowHeight() + GAP) - GAP);
        }
        @Override public void doLayout() {
            int columns = columns();
            int width = Math.max(0, (getWidth() - (columns - 1) * GAP) / columns);
            int height = rowHeight();
            for (int i = 0; i < getComponentCount(); i++) {
                getComponent(i).setBounds((i % columns) * (width + GAP), (i / columns) * (height + GAP), width, height);
            }
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(850, 440); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
        @Override public int getScrollableUnitIncrement(Rectangle bounds, int orientation, int direction) { return 28; }
        @Override public int getScrollableBlockIncrement(Rectangle bounds, int orientation, int direction) { return Math.max(28, bounds.height - 28); }
    }
}
