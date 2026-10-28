package cn.edu.jlu.wechatmerchant.server.persistence;

import cn.edu.jlu.wechatmerchant.server.domain.promotion.CouponCampaign;
import java.sql.*;
import java.util.*;

/** Caller owns the transaction; published terms are additionally locked by a database trigger. */
public final class CouponDao {
    public record Owned(long couponId,long userId,CouponCampaign campaign,boolean used) { }
    public Optional<CouponCampaign> find(Connection c,long id) throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM coupon_campaigns WHERE campaign_id=?")) {
            s.setLong(1,id); try(var r=s.executeQuery()) { return r.next()?Optional.of(campaign(r)):Optional.empty(); }
        }
    }
    public Optional<CouponCampaign> byKey(Connection c,String key) throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM coupon_campaigns WHERE create_key=?")) {
            s.setString(1,key); try(var r=s.executeQuery()) { return r.next()?Optional.of(campaign(r)):Optional.empty(); }
        }
    }
    public List<CouponCampaign> page(Connection c,int limit,int offset,boolean publishedOnly) throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM coupon_campaigns "+(publishedOnly?"WHERE published=1 ":"")+"ORDER BY campaign_id LIMIT ? OFFSET ?")) {
            s.setInt(1,limit); s.setInt(2,offset);
            try(var r=s.executeQuery()) { var list=new ArrayList<CouponCampaign>(); while(r.next())list.add(campaign(r)); return List.copyOf(list); }
        }
    }
    public List<Owned> ownedPage(Connection c,long user,int limit,int offset) throws SQLException {
        try(var s=c.prepareStatement("SELECT a.*,u.coupon_id,u.user_id,u.used FROM user_coupons u JOIN coupon_campaigns a USING(campaign_id) WHERE u.user_id=? ORDER BY u.coupon_id LIMIT ? OFFSET ?")) {
            s.setLong(1,user); s.setInt(2,limit); s.setInt(3,offset);
            try(var r=s.executeQuery()) { var list=new ArrayList<Owned>(); while(r.next()) list.add(owned(r)); return List.copyOf(list); }
        }
    }
    public Optional<Owned> owned(Connection c,long user,long id,boolean byCampaign) throws SQLException {
        try(var s=c.prepareStatement("SELECT a.*,u.coupon_id,u.user_id,u.used FROM user_coupons u JOIN coupon_campaigns a USING(campaign_id) WHERE u.user_id=? AND u."+(byCampaign?"campaign_id":"coupon_id")+"=?")) {
            s.setLong(1,user); s.setLong(2,id); try(var r=s.executeQuery()) { return r.next()?Optional.of(owned(r)):Optional.empty(); }
        }
    }
    public long create(Connection c,CouponCampaign draft,String key) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO coupon_campaigns(create_key,version,name,threshold_cents,discount_cents,starts_at,ends_at,total) VALUES(?,1,?,?,?,?,?,?) RETURNING campaign_id")) {
            s.setString(1,key); terms(s,draft,2); try(var r=s.executeQuery()) { if(!r.next())throw new SQLException("Missing new coupon."); return r.getLong(1); }
        }
    }
    public void editDraft(Connection c,CouponCampaign draft) throws SQLException {
        try(var s=c.prepareStatement("UPDATE coupon_campaigns SET name=?,threshold_cents=?,discount_cents=?,starts_at=?,ends_at=?,total=?,version=version+1 WHERE campaign_id=? AND published=0")) {
            terms(s,draft,1); s.setLong(7,draft.campaignId());
            if(s.executeUpdate()!=1)throw new SQLException("Draft update failed.");
        }
    }
    public void changeState(Connection c,long id,boolean publish,boolean paused) throws SQLException {
        try(var s=c.prepareStatement("UPDATE coupon_campaigns SET published=?,paused=?,version=version+1 WHERE campaign_id=?")) {
            s.setInt(1,publish?1:0); s.setInt(2,paused?1:0); s.setLong(3,id);
            if(s.executeUpdate()!=1)throw new SQLException("State update failed.");
        }
    }
    public void claim(Connection c,long user,long campaign,long now) throws SQLException {
        try(var s=c.prepareStatement("UPDATE coupon_campaigns SET claimed=claimed+1 WHERE campaign_id=? AND published=1 AND paused=0 AND starts_at<=? AND ends_at>? AND claimed<total")) {
            s.setLong(1,campaign); s.setLong(2,now); s.setLong(3,now);
            if(s.executeUpdate()!=1)throw new SQLException("Claim reservation failed.");
        }
        try(var s=c.prepareStatement("INSERT INTO user_coupons(user_id,campaign_id,claimed_at) VALUES(?,?,?)")) {
            s.setLong(1,user); s.setLong(2,campaign); s.setLong(3,now); s.executeUpdate();
        }
    }
    private static void terms(PreparedStatement s,CouponCampaign d,int offset) throws SQLException {
        s.setString(offset,d.name()); s.setLong(offset+1,d.thresholdCents()); s.setLong(offset+2,d.discountCents());
        s.setLong(offset+3,d.startsAt()); s.setLong(offset+4,d.endsAt()); s.setInt(offset+5,d.total());
    }
    private static Owned owned(ResultSet r) throws SQLException { return new Owned(r.getLong("coupon_id"),r.getLong("user_id"),campaign(r),r.getInt("used")==1); }
    private static CouponCampaign campaign(ResultSet r) throws SQLException {
        try { return new CouponCampaign(r.getLong("campaign_id"),r.getLong("version"),r.getString("name"),r.getLong("threshold_cents"),
                r.getLong("discount_cents"),r.getLong("starts_at"),r.getLong("ends_at"),r.getInt("total"),r.getInt("claimed"),r.getInt("published")==1,r.getInt("paused")==1); }
        catch(RuntimeException ex) { throw new SQLException("Invalid coupon data.",ex); }
    }
}
