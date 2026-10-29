package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.ProductPage;
import cn.edu.jlu.wechatmerchant.desktop.Theme;
import cn.edu.jlu.wechatmerchant.desktop.SurfacePanel;
import java.awt.*;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;
import javax.swing.*;

/** Catalog presentation only. Entry point retained; cards reuse the existing controller/cache. */
final class ProductListPanel extends JPanel {
    private final ProductImages images;private final CardGrid grid=new CardGrid();private final JScrollPane scroll=new JScrollPane(grid);
    private final LinkedHashMap<Long,ProductCard> cards=new LinkedHashMap<>();
    private final JButton refresh=new JButton("刷新商品"),previous=new JButton("上一页"),next=new JButton("下一页");
    private final JLabel status=label("尚未加载商品"),pageNumber=label("第 1 页");
    private final JTextArea notice=text("欢迎浏览，登录后可保存本人购物车。",2);
    private ClientController.State state;private ClientController.CatalogState shownCatalog;
    private BiConsumer<Long,Integer> add;private Runnable login;private Long focus;
    ProductListPanel(ProductImages images){
        super(new BorderLayout(0,14));this.images=images;setBorder(BorderFactory.createEmptyBorder());
        var heading=new JPanel(new BorderLayout());var title=label("发现好物");title.setFont(title.getFont().deriveFont(Font.BOLD,22f));heading.add(status,BorderLayout.WEST);refresh.setName("refresh-products");heading.add(refresh,BorderLayout.EAST);add(heading,BorderLayout.NORTH);
        grid.setName("product-cards");scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);scroll.getVerticalScrollBar().setUnitIncrement(24);add(scroll,BorderLayout.CENTER);
        var footer=new JPanel(new BorderLayout(8,8));var pages=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));previous.setName("previous-products");next.setName("next-products");pages.add(previous);pages.add(pageNumber);pages.add(next);footer.add(pages,BorderLayout.EAST);footer.add(notice,BorderLayout.SOUTH);Theme.muted(notice);add(footer,BorderLayout.SOUTH);refresh.setEnabled(false);previous.setEnabled(false);next.setEnabled(false);
    }
    void bindActions(Runnable reload,Runnable goPrevious,Runnable goNext){refresh.addActionListener(e -> reload.run());previous.addActionListener(e -> goPrevious.run());next.addActionListener(e -> goNext.run());}
    void bindCartAction(BiConsumer<Long,Integer> add,Runnable login){this.add=add;this.login=login;}
    void focusProduct(long id){focus=id;focusCard();}
    private void focusCard(){if(focus==null || !cards.containsKey(focus))return;var card=cards.get(focus);SwingUtilities.invokeLater(() -> {if(cards.get(card.item.productId())==card){grid.scrollRectToVisible(card.getBounds());card.addButton.requestFocusInWindow();}});}
    void showState(ClientController.State value){
        state=value;refresh.setEnabled(!value.busy());previous.setEnabled(value.canGoPrevious());next.setEnabled(value.canGoNext());previous.setVisible(value.catalog().targetOffset()>0);next.setVisible(value.catalog().page()!=null && value.catalog().page().hasMore());pageNumber.setVisible(previous.isVisible() || next.isVisible());String cartMessage=value.cart().message();notice.setText(cn.edu.jlu.wechatmerchant.desktop.NoticeBar.important(cartMessage)?cartMessage:"");notice.setVisible(!notice.getText().isEmpty());
        var catalog=value.catalog();if(shownCatalog!=catalog){shownCatalog=catalog;cards.clear();grid.removeAll();status.setText(catalog.status()==ClientController.CatalogStatus.READY?catalog.page().items().size()+" 种商品":catalog.message());pageNumber.setText("第 "+((long)catalog.targetOffset()/ClientController.PAGE_SIZE+1)+" 页");if(catalog.status()==ClientController.CatalogStatus.READY && catalog.page()!=null){for(var item:catalog.page().items()){var card=new ProductCard(item);cards.put(item.productId(),card);grid.add(card);}}else{var message=label(catalog.message());message.setHorizontalAlignment(SwingConstants.CENTER);grid.add(message);}
            grid.revalidate();grid.repaint();SwingUtilities.invokeLater(() -> {if(shownCatalog==catalog){scroll.getViewport().setViewPosition(new Point(0,0));focusCard();}});
        }
        for(var card:cards.values())card.controls();
    }
    private final class ProductCard extends SurfacePanel {
        private final ProductPage.Item item;private final JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,Integer.MAX_VALUE,1));private final JButton addButton=new JButton("加入购物车");
        ProductCard(ProductPage.Item item){
            super(new BorderLayout(0,10));this.item=item;setName("product-card-"+item.productId());
            var visual=new JPanel(new BorderLayout(0,10));visual.setOpaque(false);var pictures=images.forProduct(item);var picture=new JLabel(pictures.preview(),SwingConstants.CENTER);picture.setPreferredSize(new Dimension(176,176));visual.add(picture,BorderLayout.CENTER);
            var summary=new JPanel(new GridLayout(0,1,0,5));summary.setOpaque(false);var name=label(item.name());name.setFont(name.getFont().deriveFont(Font.BOLD,18f));summary.add(name);var price=label(priceText(item.promotion()==null?item.priceCents():item.promotion().estimatedUnitCents()));price.setName("product-price");price.setFont(price.getFont().deriveFont(Font.BOLD,22f));price.setForeground(Theme.accent());price.putClientProperty("theme.accent",true);summary.add(price);var stock=label("库存 "+item.stock()+" 件  ·  "+(pictures.demo()?"AI 演示图":"暂无图片"));stock.setFont(stock.getFont().deriveFont(12f));Theme.muted(stock);summary.add(stock);visual.add(summary,BorderLayout.SOUTH);add(visual,BorderLayout.NORTH);
            var details=new JPanel(new BorderLayout(0,6));details.setOpaque(false);var description=text(item.description().isBlank()?"暂无商品说明":item.description(),3);description.setName("product-description");var toggle=new JButton("详情");toggle.putClientProperty("theme.ghost",true);toggle.setName("product-details");description.setVisible(false);toggle.addActionListener(e -> {description.setVisible(!description.isVisible());toggle.setText(description.isVisible()?"收起":"详情");grid.revalidate();});
            details.add(description,BorderLayout.CENTER);details.add(cn.edu.jlu.wechatmerchant.desktop.Ui.row(toggle),BorderLayout.NORTH);var activity=text(PromotionText.activity(item.promotion()),2);activity.setName("product-promotion");Theme.muted(activity);activity.setRows(1);activity.setVisible(item.promotion()!=null && (item.promotion().reductionCents()>0 || item.promotion().discountHundredths()<1000));details.add(activity,BorderLayout.SOUTH);add(details,BorderLayout.CENTER);
            var actions=new JPanel(new BorderLayout(12,0));actions.setOpaque(false);quantity.setName("product-quantity");quantity.setPreferredSize(new Dimension(132,42));addButton.setName("add-to-cart");Theme.primary(addButton);actions.add(quantity,BorderLayout.WEST);actions.add(addButton,BorderLayout.CENTER);add(actions,BorderLayout.SOUTH);
            addButton.addActionListener(e -> {if(state==null || state.busy())return;if(state.identity()==null){if(login!=null)login.run();return;}if(!state.canModifyCart() || add==null)return;try{quantity.commitEdit();add.accept(item.productId(),(Integer)quantity.getValue());}catch(ParseException | IllegalArgumentException ex){notice.setText("请输入1到2147483647之间的整数数量。");}});
        }
        void controls(){boolean available=state!=null && !state.busy() && item.stock()>0;quantity.setEnabled(available);addButton.setEnabled(available && (state.identity()==null || state.canModifyCart()));addButton.setText(item.stock()==0?"暂时售罄":"加入购物车");}
    }
    static String priceText(long cents){if(cents<0)throw new IllegalArgumentException("Price cannot be negative.");return "¥ "+BigDecimal.valueOf(cents,2).toPlainString();}
    private static JLabel label(String value){var label=new JLabel();label.putClientProperty("html.disable",true);label.setText(value);return label;}
    private static JTextArea text(String value,int rows){var area=new JTextArea(value,rows,16);area.setEditable(false);area.setFocusable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setOpaque(false);area.setFont(new JLabel().getFont());return area;}
    private static final class CardGrid extends JPanel implements Scrollable {
        CardGrid(){setLayout(null);}
        private int columns(){return Math.max(1,Math.min(3,(getWidth()+24)/344));}
        private int height(){int h=380;for(Component card:getComponents())h=Math.max(h,card.getPreferredSize().height);return h;}
        @Override public Dimension getPreferredSize(){int rows=(getComponentCount()+columns()-1)/columns();return new Dimension(Math.max(280,getWidth()),rows==0?0:rows*(height()+24)-24);}
        @Override public void doLayout(){int columns=columns(),width=Math.max(1,(getWidth()-(columns-1)*24)/columns),h=height();for(int i=0;i<getComponentCount();i++)getComponent(i).setBounds(i%columns*(width+24),i/columns*(h+24),width,h);}
        @Override public Dimension getPreferredScrollableViewportSize(){return new Dimension(850,460);}
        @Override public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 24;}
        @Override public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(24,r.height-24);}
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public boolean getScrollableTracksViewportHeight(){return false;}
    }
}
