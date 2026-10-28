package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.CouponCampaign;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.math.BigInteger;
import java.sql.*;
import java.time.Clock;
import java.util.*;

public final class CouponService {
    public static final class Rejected extends Exception {
        private static final long serialVersionUID=1L;
        public Rejected(String code) { super(code); }
    }
    public record AdminPage(List<CouponCampaign> items,int offset,boolean hasMore) {
        public AdminPage { items=List.copyOf(items); }
    }
    private final SqliteDatabase database;
    private final SessionAccess access;
    private final Clock clock;
    private final CouponDao coupons=new CouponDao();
    private final CartDao carts=new CartDao();
    private final PromotionPricing pricing;
    public CouponService(SqliteDatabase database,SessionStore sessions) { this(database,sessions,Clock.systemUTC()); }
    public CouponService(SqliteDatabase database,SessionStore sessions,Clock clock) {
        this.database=database; this.access=new SessionAccess(sessions); this.clock=clock; this.pricing=new PromotionPricing(clock);
    }
    public AdminPage adminPage(String token,int offset) throws SQLException,Rejected {
        if(offset<0)throw new IllegalArgumentException("分页无效。");
        return read(token,UserIdentity.Role.ADMIN,(c,user) -> {
            var list=coupons.page(c,21,offset,false);
            return new AdminPage(list.size()>20?list.subList(0,20):list,offset,list.size()>20);
        });
    }
    public CouponCampaign saveDraft(String token,CouponCampaign draft,String createKey) throws SQLException,Rejected {
        if(draft.published() || draft.claimed()!=0) throw new IllegalArgumentException("只能保存草稿。");
        return write(token,UserIdentity.Role.ADMIN,(c,user) -> {
            if(draft.campaignId()==0) {
                if(createKey==null || !createKey.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("Invalid draft key.");
                var existing=coupons.byKey(c,createKey);
                if(existing.isPresent()) {
                    var old=existing.get();
                    if(old.published() || !sameTerms(old,draft))throw new Rejected("CONFIG_CHANGED");
                    return old;
                }
                return coupons.find(c,coupons.create(c,draft,createKey)).orElseThrow();
            }
            var old=current(c,draft.campaignId(),draft.version());
            if(old.published())throw new Rejected("PUBLISHED_LOCKED");
            coupons.editDraft(c,draft); return coupons.find(c,draft.campaignId()).orElseThrow();
        });
    }
    public CouponCampaign publish(String token,long id,long version) throws SQLException,Rejected {
        return write(token,UserIdentity.Role.ADMIN,(c,user) -> {
            var old=current(c,id,version);
            if(old.published())throw new Rejected("PUBLISHED_LOCKED");
            coupons.changeState(c,id,true,false); return coupons.find(c,id).orElseThrow();
        });
    }
    public CouponCampaign pause(String token,long id,long version,boolean paused) throws SQLException,Rejected {
        return write(token,UserIdentity.Role.ADMIN,(c,user) -> {
            var old=current(c,id,version);
            if(!old.published())throw new Rejected("NOT_PUBLISHED");
            coupons.changeState(c,id,true,paused); return coupons.find(c,id).orElseThrow();
        });
    }
    public CouponPage list(String token,String kind,int limit,int offset) throws SQLException,Rejected {
        return list(token,kind,limit,offset,null);
    }
    public CouponPage list(String token,String kind,int limit,int offset,List<Long> itemIds) throws SQLException,Rejected {
        if(!Set.of("AVAILABLE","OWNED").contains(kind) || limit<1 || limit>100 || offset<0) throw new IllegalArgumentException("Invalid pagination.");
        return read(token,UserIdentity.Role.BUYER,(c,user) -> {
            long now=clock.millis();
            var selected=selection(pricing.cart(c,carts.read(c,user),java.time.Instant.ofEpochMilli(now)),itemIds);
            BigInteger subtotal=selected.estimatedTotalCents();
            var items=new ArrayList<CouponPage.Item>();
            if(kind.equals("AVAILABLE")) {
                for(var campaign:coupons.page(c,limit+1,offset,true)) {
                    var owned=coupons.owned(c,user,campaign.campaignId(),true);
                    items.add(view(campaign,owned.map(CouponDao.Owned::couponId).orElse(0L),campaign.claimStatus(now,owned.isPresent())));
                }
            } else {
                for(var owned:coupons.ownedPage(c,user,limit+1,offset)) {
                    String status=owned.campaign().useStatus(now,subtotal,owned.used());
                    if(selected.items().isEmpty() && status.equals("USABLE") && itemIds!=null)status="EMPTY_SELECTION";
                    items.add(view(owned.campaign(),owned.couponId(),status));
                }
            }
            boolean more=items.size()>limit;
            return new CouponPage(kind,more?items.subList(0,limit):items,limit,offset,more);
        });
    }
    public CouponPage.Item claim(String token,long campaign) throws SQLException,Rejected {
        if(campaign<=0) throw new IllegalArgumentException("Invalid campaign.");
        return write(token,UserIdentity.Role.BUYER,(c,user) -> {
            long now=clock.millis();
            // First find the prior result, even if the activity has since paused or ended.
            var old=coupons.owned(c,user,campaign,true);
            if(old.isEmpty()) {
                var activity=coupons.find(c,campaign).filter(CouponCampaign::published).orElseThrow(() -> new Rejected("NOT_FOUND"));
                String status=activity.claimStatus(now,false);
                if(!status.equals("CAN_CLAIM"))throw new Rejected(status);
                coupons.claim(c,user,campaign,now);
            }
            var owned=coupons.owned(c,user,campaign,true).orElseThrow();
            var subtotal=pricing.cart(c,carts.read(c,user),java.time.Instant.ofEpochMilli(now)).estimatedTotalCents();
            return view(owned.campaign(),owned.couponId(),owned.campaign().useStatus(now,subtotal,owned.used()));
        });
    }
    public QuoteSnapshot quote(String token,long version,Long coupon) throws SQLException,Rejected {
        return quote(token,version,coupon,null);
    }
    public QuoteSnapshot quote(String token,long version,Long coupon,List<Long> itemIds) throws SQLException,Rejected {
        if(version<0 || coupon!=null && coupon<=0) throw new IllegalArgumentException("Invalid quote request.");
        return read(token,UserIdentity.Role.BUYER,(c,user) -> quote(c,user,version,coupon,itemIds));
    }
    QuoteSnapshot quote(Connection c,long user,long version,Long coupon,List<Long> itemIds) throws SQLException,Rejected {
            var contents=carts.read(c,user);
            if(contents.cartVersion()!=version)throw new Rejected("CART_CHANGED");
            long now=clock.millis(),until=Long.MAX_VALUE;
            var cart=pricing.cart(c,contents,java.time.Instant.ofEpochMilli(now));
            var selected=selection(cart,itemIds);
            for(var item:selected.items()) {
                var promotion=new PromotionDao().find(c,item.productId());
                if(promotion.isPresent() && promotion.get().enabled()) {
                    long start=promotion.get().startsAt().toEpochMilli(),end=promotion.get().endsAt().toEpochMilli();
                    if(start>now)until=Math.min(until,start);
                    else if(end>now)until=Math.min(until,end);
                }
            }
            String status="NONE"; BigInteger reduction=BigInteger.ZERO;
            if(coupon!=null) {
                var owned=coupons.owned(c,user,coupon,false);
                if(owned.isEmpty()) status="NOT_FOUND";
                else {
                    var campaign=owned.get().campaign();
                    status=campaign.useStatus(now,selected.estimatedTotalCents(),owned.get().used());
                    if(campaign.startsAt()>now)until=Math.min(until,campaign.startsAt());
                    else if(campaign.endsAt()>now)until=Math.min(until,campaign.endsAt());
                    if(status.equals("USABLE")) {
                        if(selected.items().isEmpty())status=itemIds==null?"EMPTY_CART":"EMPTY_SELECTION";
                        else { status="APPLIED"; reduction=campaign.reductionFor(selected.estimatedTotalCents()); }
                    }
                }
            }
            if(itemIds!=null && itemIds.isEmpty() && status.equals("NONE"))status="EMPTY_SELECTION";
            BigInteger payable=status.equals("NONE") || status.equals("APPLIED") ? selected.estimatedTotalCents().subtract(reduction) : null;
            // A comparison digest, never a payment credential; S11 must recalculate independently.
            // Current stock is checked atomically at checkout; a harmless stock decrease does not change the agreed price.
            var binding=selected.items().stream().map(i -> List.of(i.itemId(),i.productId(),i.name(),i.priceCents(),i.quantity(),i.promotion())).toList();
            String material=user+"|"+version+"|"+new com.google.gson.Gson().toJson(binding)+"|"+coupon+"|"+status+"|"+reduction+"|"+payable+"|"+until;
            String digest;
            try { digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
            catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
            return new QuoteSnapshot(cart,CartSelection.all(selected),coupon,status,reduction,payable,now,until,digest);
    }
    private static CartSnapshot selection(CartSnapshot cart,List<Long> ids) throws Rejected {
        try{return CartSelection.select(cart,ids);}catch(IllegalArgumentException ex){throw new Rejected("CART_CHANGED");}
    }
    private CouponCampaign current(Connection c,long id,long version) throws SQLException,Rejected {
        var old=coupons.find(c,id).orElseThrow(() -> new Rejected("NOT_FOUND"));
        if(version<1 || old.version()!=version || version==Long.MAX_VALUE)throw new Rejected("CONFIG_CHANGED");
        return old;
    }
    private static boolean sameTerms(CouponCampaign a,CouponCampaign b) {
        return a.name().equals(b.name()) && a.thresholdCents()==b.thresholdCents() && a.discountCents()==b.discountCents()
                && a.startsAt()==b.startsAt() && a.endsAt()==b.endsAt() && a.total()==b.total();
    }
    private static CouponPage.Item view(CouponCampaign c,long coupon,String status) {
        return new CouponPage.Item(coupon,c.campaignId(),c.name(),c.thresholdCents(),c.discountCents(),c.startsAt(),c.endsAt(),c.total(),c.claimed(),CouponPage.Status.valueOf(status));
    }
    private <T> T read(String token,UserIdentity.Role role,Work<T> work) throws SQLException,Rejected { return transaction(token,role,work,false); }
    private <T> T write(String token,UserIdentity.Role role,Work<T> work) throws SQLException,Rejected { return transaction(token,role,work,true); }
    private <T> T transaction(String token,UserIdentity.Role role,Work<T> work,boolean write) throws SQLException,Rejected {
        try(var connection=write?database.openImmediateConnection():database.openReadOnlyConnection()) {
            connection.setAutoCommit(false);
            try {
                long user=access.require(connection,token,role).userId();
                T result=work.run(connection,user); connection.commit(); return result;
            } catch(SQLException | Rejected | RuntimeException exception) {
                try { connection.rollback(); } catch(SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        }
    }
    @FunctionalInterface private interface Work<T> { T run(Connection connection,long user) throws SQLException,Rejected; }
}
