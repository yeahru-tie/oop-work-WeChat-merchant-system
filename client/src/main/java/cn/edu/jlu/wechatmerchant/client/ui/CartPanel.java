package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import cn.edu.jlu.wechatmerchant.protocol.CartSelection;
import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.desktop.*;
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
    private final JLabel total=Ui.label("—"),original=Ui.label("—"),reduction=Ui.label("—"),couponAmount=Ui.label("—"),selectedCount=Ui.label("");
    private final JPanel reductionRow=Ui.valueRow("商品优惠",reduction),couponRow=Ui.valueRow("优惠券",couponAmount);
    private final SurfacePanel summary=new SurfacePanel(new BorderLayout(0,24));
    private final NoticeBar quoteNotice=new NoticeBar(),cartNotice=new NoticeBar();
    private final JButton login = new JButton("登录查看购物车");
    private final JButton refresh = new JButton("刷新购物车");
    private final JButton coupons = new JButton("领券/选券");
    private final JButton quoteRefresh = new JButton("刷新报价");
    private final CouponPanel couponPanel=new CouponPanel();
    private final javax.swing.Timer quoteClock=new javax.swing.Timer(1000,event -> showTotals());
    private boolean couponOpen;
    private final JButton checkout=new JButton("确认模拟成交"),queryPending=new JButton("核对本次结果"),retryPending=new JButton("原键重试本次");
    private final NoticeBar checkoutStatus=new NoticeBar();
    private java.util.function.BiConsumer<Long,Boolean> selectItem;
    private ClientController.State state;
    private UserIdentity owner;
    private CartSnapshot displayed;
    private BiConsumer<Long, Integer> edit;

    CartPanel(ProductImages images) {
        super(new BorderLayout(0, 12));
        this.images = images;
        JPanel heading=new JPanel(new BorderLayout(16,0));status.setFont(status.getFont().deriveFont(Font.BOLD,16f));heading.add(status,BorderLayout.WEST);
        refresh.setText("刷新");refresh.setName("refresh-cart");login.setName("cart-login");
        heading.add(Ui.row(refresh,Ui.help("加入购物车不占用库存。数量修改自动保存；输入后按回车。结算前会再次确认最终金额。")),BorderLayout.EAST);add(heading,BorderLayout.NORTH);
        grid.setName("cart-cards");views.add(Ui.scroll(grid),"cards");
        var emptyPage=new JPanel(new java.awt.GridBagLayout());emptyPage.setOpaque(false);empty.setFont(empty.getFont().deriveFont(Font.BOLD,18f));emptyPage.add(Ui.stack(24,empty,login));views.add(emptyPage,"empty");views.add(couponPanel,"coupons");
        total.setName("cart-total");total.setFont(total.getFont().deriveFont(Font.BOLD,30f));total.putClientProperty("theme.accent",true);total.setForeground(Theme.accent());
        quoteNotice.setName("quote-notice");var details=Ui.stack(20,Ui.title("结算明细"),selectedCount,Ui.valueRow("商品金额",original),reductionRow,couponRow,Ui.caption("预估应付"),total);summary.add(details,BorderLayout.NORTH);
        coupons.setText("选择优惠券");coupons.setName("open-coupons");quoteRefresh.setText("更新报价");quoteRefresh.setName("refresh-quote");quoteRefresh.putClientProperty("theme.ghost",true);
        checkout.setText("去结算");checkout.setName("prepare-checkout");Theme.primary(checkout);queryPending.setName("query-pending");retryPending.setName("retry-pending");checkoutStatus.setName("checkout-status");
        var footer=Ui.stack(12,coupons,quoteRefresh,quoteNotice,checkout,queryPending,retryPending,checkoutStatus);summary.add(footer,BorderLayout.SOUTH);summary.setPreferredSize(null);
        var side=new JPanel(new BorderLayout());side.setOpaque(false);side.add(summary,BorderLayout.NORTH);side.setPreferredSize(new Dimension(292,0));
        var workspace=new JPanel(new BorderLayout(24,24)){
            private boolean narrow;
            @Override public void doLayout(){boolean next=getWidth()<720;if(next!=narrow){narrow=next;remove(side);add(side,narrow?BorderLayout.SOUTH:BorderLayout.EAST);summary.remove(details);summary.remove(footer);summary.add(details,narrow?BorderLayout.WEST:BorderLayout.NORTH);summary.add(footer,narrow?BorderLayout.EAST:BorderLayout.SOUTH);summary.setPreferredSize(null);side.setPreferredSize(new Dimension(292,narrow?Math.max(details.getPreferredSize().height,footer.getPreferredSize().height)+48:0));}super.doLayout();}
        };workspace.setOpaque(false);workspace.add(views,BorderLayout.CENTER);workspace.add(side,BorderLayout.EAST);add(workspace,BorderLayout.CENTER);cartNotice.setName("cart-notice");add(cartNotice,BorderLayout.SOUTH);
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
        status.setText(incoming==null?next.identity()==null?"":"正在同步购物车":incoming.items().isEmpty()?"":"共 "+incoming.items().size()+" 种商品");
        cartNotice.showMessage(NoticeBar.important(next.cart().message())?next.cart().message():"");
        couponPanel.showState(next);
        showTotals();
        if(couponOpen && next.identity()!=null) viewLayout.show(views,"coupons");
        else if (cards.isEmpty()) {
            empty.setText(next.identity()==null?"登录后查看购物车":incoming!=null?"购物车还是空的":next.busy()?"正在加载购物车…":"请刷新购物车");
            viewLayout.show(views, "empty");
        } else {
            viewLayout.show(views, "cards");
        }
        updateActions();
    }

    private void showTotals() {
        if(state==null || displayed==null){total.setText("");original.setText("");reduction.setText("");couponAmount.setText("");selectedCount.setText("");quoteNotice.showMessage("");return;}
        var selected=CartSelection.select(displayed,state.selectedIds());var savings=selected.totalCents().subtract(selected.estimatedTotalCents());
        selectedCount.setText("已选 "+selected.items().size()+" 种商品");original.setText(money(selected.totalCents()));reduction.setText("− "+money(savings));reductionRow.setVisible(savings.signum()>0);
        var coupon=state.coupons();couponRow.setVisible(false);String warning="";
        if(!coupon.active())total.setText(money(selected.estimatedTotalCents()));
        else if(state.busy() || coupon.quote()==null || !coupon.fresh()){total.setText("—");warning=state.busy()?"正在更新金额…":"报价已失效，请更新";}
        else if(!coupon.quote().ready()){total.setText("—");warning=coupon.quote().itemIds().isEmpty()?"请选择商品":"优惠券不可用，请更新报价";}
        else{total.setText(money(coupon.quote().payableCents()));couponAmount.setText("− "+money(coupon.quote().couponReductionCents()));couponRow.setVisible(coupon.quote().couponReductionCents().signum()>0);}
        if(state.busy() && state.action()==ClientController.Action.CART_WRITE)total.setText("更新中…");if(NoticeBar.important(coupon.message()))warning=coupon.message();quoteNotice.showMessage(warning);
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
        login.setVisible(guest);summary.setVisible(!guest && (displayed!=null && !displayed.items().isEmpty() || state.orders().pending()!=null || state.orders().storageBlocked()));
        login.setEnabled(!busy);
        refresh.setEnabled(!guest && !busy);
        coupons.setEnabled(!busy && (guest || state.canModifyCart()));
        quoteRefresh.setEnabled(!guest && !busy && state.canModifyCart());
        boolean pending=!guest && state.orders().pending()!=null;
        checkout.setEnabled(selectItem!=null && state!=null && state.canModifyCart() && !state.selectedIds().isEmpty());queryPending.setVisible(pending);retryPending.setVisible(pending);queryPending.setEnabled(pending && !busy);retryPending.setEnabled(pending && !busy);
        String trade=guest?"":state.orders().message();
        checkoutStatus.showMessage(!guest && (pending || state.orders().storageBlocked() || NoticeBar.important(trade)) && !trade.equals("尚无订单数据")?trade:"");
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

    private final class ItemCard extends cn.edu.jlu.wechatmerchant.desktop.SurfacePanel {
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
            selected.setText("");selected.setOpaque(false);selected.setName("cart-selected-"+item.itemId());selected.setToolTipText("参与本次结算");selected.getAccessibleContext().setAccessibleName("勾选商品");
            remove.setName("remove-cart-item-"+item.itemId());remove.putClientProperty("theme.ghost",true);
            var header=new JPanel(new BorderLayout());header.setOpaque(false);header.add(Ui.row(selected,imageNote),BorderLayout.WEST);header.add(remove,BorderLayout.EAST);add(header,BorderLayout.NORTH);
            var top=new JPanel(new BorderLayout(20,0));top.setOpaque(false);picture.setPreferredSize(new Dimension(88,88));top.add(picture,BorderLayout.WEST);
            configureText(name);name.setName("cart-name-"+item.itemId());name.setFont(name.getFont().deriveFont(Font.BOLD,17f));Theme.muted(stock);Theme.muted(imageNote);imageNote.setFont(imageNote.getFont().deriveFont(12f));
            price.setFont(price.getFont().deriveFont(Font.BOLD,16f));top.add(Ui.stack(8,name,price,stock),BorderLayout.CENTER);add(top,BorderLayout.CENTER);
            var bottom=new JPanel(new BorderLayout(12,12));bottom.setOpaque(false);quantity.setName("cart-quantity-"+item.itemId());quantity.setPreferredSize(new Dimension(132,42));quantity.setToolTipText("数量自动保存；输入后按回车");bottom.add(quantity,BorderLayout.WEST);
            configureText(subtotal);subtotal.setRows(1);subtotal.setColumns(7);subtotal.setName("cart-subtotal-"+item.itemId());subtotal.setFont(subtotal.getFont().deriveFont(Font.BOLD,18f));
            configureText(promotion);promotion.setRows(1);promotion.setName("cart-promotion-"+item.itemId());Theme.muted(promotion);bottom.add(subtotal,BorderLayout.EAST);bottom.add(promotion,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);

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
                imageNote.setText(pictures.demo() ? "AI演示图" : "暂无图片");
                price.setText(money(next.estimatedCents().divide(BigInteger.valueOf(next.quantity())))+" / 件");
                stock.setText(next.quantity()>next.stock()?"库存不足":"剩余 "+next.stock()+" 件");
                stock.setForeground(next.quantity() > next.stock() ? new Color(168, 62, 30) : getForeground());
                var savings=next.subtotalCents().subtract(next.estimatedCents());promotion.setText(savings.signum()>0?"已优惠 "+money(savings):"");promotion.setVisible(savings.signum()>0);promotion.setToolTipText(PromotionText.details(next.promotion(),next.quantity()));
                subtotal.setText(money(next.estimatedCents()));
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
        private static final int GAP = 24;
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
