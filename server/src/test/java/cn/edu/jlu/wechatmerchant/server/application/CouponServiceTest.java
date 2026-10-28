package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.support.MutableClock;
import java.math.BigInteger;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class CouponServiceTest {
    @TempDir Path folder;
    private SqliteDatabase db;
    private final MutableClock clock=new MutableClock();
    private final SessionStore sessions=new SessionStore(clock,Duration.ofMinutes(30));
    private CouponService service;
    private String admin,a,b;
    @BeforeEach void setup() throws Exception {
        db=new SqliteDatabase(folder.resolve("shop.db"));db.initialize();service=new CouponService(db,sessions,clock);
        var users=new UserDao(db);admin=sessions.create(users.findByUsername("shop_admin").orElseThrow().identity());
        a=sessions.create(users.findByUsername("buyer_a").orElseThrow().identity());b=sessions.create(users.findByUsername("buyer_b").orElseThrow().identity());
    }
    private CouponCampaign draft(long threshold,long reduction,int total){return new CouponCampaign(0,0,"满减活动",threshold,reduction,clock.millis(),clock.millis()+60_000,total,0,false,false);}
    private CouponCampaign published(long threshold,long reduction,int total) throws Exception {
        var saved=service.saveDraft(admin,draft(threshold,reduction,total),UUID.randomUUID().toString());return service.publish(admin,saved.campaignId(),saved.version());
    }
    private void price(long value) throws Exception {try(var c=db.openWriteConnection();var s=c.prepareStatement("UPDATE products SET price_cents=? WHERE product_id=1001")){s.setLong(1,value);s.executeUpdate();}}
    @Test void draftPublicationPauseAndImmutableTermsPersistWithoutChangingIssuedRights() throws Exception {
        var draft=draft(0,2000,2);String key=UUID.randomUUID().toString();
        var saved=service.saveDraft(admin,draft,key);
        assertEquals(saved,service.saveDraft(admin,draft,key));
        assertTrue(service.list(a,"AVAILABLE",20,0).items().isEmpty());
        assertEquals("NOT_FOUND",assertThrows(CouponService.Rejected.class,() -> service.claim(a,saved.campaignId())).getMessage());
        var pub=service.publish(admin,saved.campaignId(),saved.version());
        var owned=service.claim(a,pub.campaignId());
        var paused=service.pause(admin,pub.campaignId(),pub.version(),true);
        assertEquals("PAUSED",assertThrows(CouponService.Rejected.class,() -> service.claim(b,pub.campaignId())).getMessage());
        assertEquals(owned.couponId(),service.claim(a,pub.campaignId()).couponId());
        assertEquals(CouponPage.Status.USABLE,service.list(a,"OWNED",20,0).items().getFirst().status());
        assertThrows(CouponService.Rejected.class,() -> service.saveDraft(admin,new CouponCampaign(pub.campaignId(),paused.version(),"改条款",0,1,pub.startsAt(),pub.endsAt(),2,0,false,false),key));
        try(var c=db.openWriteConnection();var s=c.createStatement()) {assertThrows(java.sql.SQLException.class,() -> s.executeUpdate("UPDATE coupon_campaigns SET discount_cents=1"));}
        service.pause(admin,pub.campaignId(),paused.version(),false);
        assertEquals(2,service.claim(b,pub.campaignId()).claimed());
        db.initialize();var restarted=new CouponService(new SqliteDatabase(db.file()),sessions,clock);
        assertEquals(owned.couponId(),restarted.list(a,"OWNED",20,0).items().getFirst().couponId());
    }
    @Test void mixedQuoteMatches142AndThresholdBoundariesAndNeverWritesOrConsumes() throws Exception {
        price(10_000);var cart=new CartService(db).add(1,1001,2,0);
        new PromotionManagementService(db,sessions).save(admin,new PromotionConfiguration(1001,0,new PromotionTerms(1000,900),clock.instant(),clock.instant().plusSeconds(60),true));
        var activity=published(15_000,2000,2);long coupon=service.claim(a,activity.campaignId()).couponId();
        byte[] before=Files.readAllBytes(db.file());
        var quote=service.quote(a,cart.cartVersion(),coupon);
        assertEquals(new BigInteger("16200"),quote.cart().estimatedTotalCents());assertEquals(new BigInteger("14200"),quote.payableCents());
        assertEquals(new BigInteger("2000"),quote.couponReductionCents());assertEquals(quote.digest(),service.quote(a,1,coupon).digest());
        service.list(a,"OWNED",20,0);service.list(b,"AVAILABLE",20,0);assertArrayEquals(before,Files.readAllBytes(db.file()));
        assertEquals(20,quote.cart().items().getFirst().stock());
        assertEquals("NOT_FOUND",service.quote(b,0,coupon).couponStatus());assertNull(service.quote(b,0,coupon).payableCents());
        // Remove the separate product activity to test exact threshold edges.
        var manager=new PromotionManagementService(db,sessions);var previous=manager.load(admin,1001).configuration();
        manager.save(admin,new PromotionConfiguration(1001,previous.version(),previous.terms(),previous.startsAt(),previous.endsAt(),false));
        new CartService(db).edit(1,cart.items().getFirst().itemId(),1,1);
        price(15_000);assertEquals(new BigInteger("13000"),service.quote(a,2,coupon).payableCents());
        price(14_900);var invalid=service.quote(a,2,coupon);assertEquals("THRESHOLD_NOT_MET",invalid.couponStatus());assertNull(invalid.payableCents());
        assertEquals(new BigInteger("14900"),service.quote(a,2,null).payableCents());
        assertEquals("CART_CHANGED",assertThrows(CouponService.Rejected.class,() -> service.quote(a,1,coupon)).getMessage());
    }
    @Test void couponReductionClampsAtZeroAndSupportsLongPricesAndLargeCartTotals() throws Exception {
        price(1000);new CartService(db).add(1,1001,1,0);var p=published(0,2000,2);long coupon=service.claim(a,p.campaignId()).couponId();
        var zero=service.quote(a,1,coupon);assertEquals(BigInteger.ZERO,zero.payableCents());assertEquals(BigInteger.valueOf(1000),zero.couponReductionCents());
        price(Long.MAX_VALUE);var cart=new CartService(db).edit(1,zero.cart().items().getFirst().itemId(),2,1);
        assertEquals(new BigInteger("18446744073709549614"),service.quote(a,cart.cartVersion(),coupon).payableCents());
    }
    @Test void inclusiveStartExclusiveEndAndUsedCouponsCannotSilentlyFallBack() throws Exception {
        long start=clock.millis()+1000,end=start+1000;
        var saved=service.saveDraft(admin,new CouponCampaign(0,0,"边界",0,100,start,end,2,0,false,false),UUID.randomUUID().toString());
        service.publish(admin,saved.campaignId(),saved.version());
        assertEquals("NOT_STARTED",assertThrows(CouponService.Rejected.class,() -> service.claim(a,saved.campaignId())).getMessage());
        clock.advance(Duration.ofSeconds(1));long coupon=service.claim(a,saved.campaignId()).couponId();new CartService(db).add(1,1001,1,0);
        assertTrue(service.quote(a,1,coupon).ready());clock.advance(Duration.ofMillis(999));assertTrue(service.quote(a,1,coupon).ready());
        clock.advance(Duration.ofMillis(1));assertEquals("EXPIRED",service.quote(a,1,coupon).couponStatus());assertNull(service.quote(a,1,coupon).payableCents());
        assertEquals("EXPIRED",assertThrows(CouponService.Rejected.class,() -> service.claim(b,saved.campaignId())).getMessage());
        assertEquals(coupon,service.claim(a,saved.campaignId()).couponId());
        try(var c=db.openWriteConnection();var s=c.createStatement()){s.executeUpdate("UPDATE user_coupons SET used=1");}
        assertEquals("USED",service.quote(a,1,coupon).couponStatus());assertNull(service.quote(a,1,coupon).payableCents());
    }
    @Test void concurrentClaimingCannotExceedTotalOrDuplicateOwnerEvenAfterReopen() throws Exception {
        var p=published(0,100,1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var go=new CountDownLatch(1);
            Callable<String> first=() -> {go.await();try{return "OK:"+service.claim(a,p.campaignId()).couponId();}catch(CouponService.Rejected e){return e.getMessage();}};
            Callable<String> second=() -> {go.await();try{return "OK:"+service.claim(b,p.campaignId()).couponId();}catch(CouponService.Rejected e){return e.getMessage();}};
            var one=pool.submit(first);var two=pool.submit(second);go.countDown();var results=List.of(one.get(5,TimeUnit.SECONDS),two.get(5,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(v -> v.startsWith("OK:")).count());assertEquals(1,results.stream().filter(v -> v.equals("SOLD_OUT")).count());
        }
        String winner=service.list(a,"OWNED",20,0).items().isEmpty()?b:a;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var x=pool.submit(() -> service.claim(winner,p.campaignId()));var y=pool.submit(() -> service.claim(winner,p.campaignId()));
            assertEquals(x.get().couponId(),y.get().couponId());
        }
        try(var c=db.openReadOnlyConnection();var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT claimed FROM coupon_campaigns")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
            try(var r=s.executeQuery("SELECT count(*) FROM user_coupons")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
        }
    }
    @Test void failedClaimInsertionRollsBackIssuedCountAndStaleAdminCannotOverwrite() throws Exception {
        var p=published(0,100,2);
        try(var c=db.openWriteConnection();var s=c.createStatement()){s.execute("CREATE TRIGGER fail_claim BEFORE INSERT ON user_coupons BEGIN SELECT RAISE(ABORT,'test'); END");}
        assertThrows(java.sql.SQLException.class,() -> service.claim(a,p.campaignId()));
        assertEquals(0,service.adminPage(admin,0).items().getFirst().claimed());
        assertEquals("CONFIG_CHANGED",assertThrows(CouponService.Rejected.class,() -> service.pause(admin,p.campaignId(),1,true)).getMessage());
    }
    @Test void rolesRevocationAndDisabledUsersAreCheckedByEveryServiceEntry() throws Exception {
        var p=published(0,100,2);
        assertThrows(SecurityException.class,() -> service.adminPage(a,0));
        assertThrows(SecurityException.class,() -> service.saveDraft(a,draft(0,1,1),UUID.randomUUID().toString()));
        assertThrows(SecurityException.class,() -> service.publish(a,p.campaignId(),p.version()));
        assertThrows(SecurityException.class,() -> service.pause(a,p.campaignId(),p.version(),true));
        assertThrows(SecurityException.class,() -> service.list(admin,"OWNED",20,0));
        assertThrows(SecurityException.class,() -> service.claim(admin,p.campaignId()));
        assertThrows(SecurityException.class,() -> service.quote(admin,0,null));
        sessions.revoke(b);assertThrows(SecurityException.class,() -> service.list(b,"AVAILABLE",20,0));
        try(var c=db.openWriteConnection();var s=c.createStatement()){s.executeUpdate("UPDATE users SET enabled=0 WHERE user_id=1");}
        assertThrows(SecurityException.class,() -> service.claim(a,p.campaignId()));assertThrows(SecurityException.class,() -> service.quote(a,0,null));
        clock.advance(Duration.ofMinutes(30));assertThrows(SecurityException.class,() -> service.adminPage(admin,0));
    }
    @Test void paginationIncludesReasonsAndPersonalResultsAreOrderedAndIsolated() throws Exception {
        for(int i=0;i<23;i++) {var p=published(0,100,5);service.claim(a,p.campaignId());}
        assertTrue(service.adminPage(admin,0).hasMore());assertEquals(3,service.adminPage(admin,20).items().size());
        var page=service.list(a,"OWNED",20,0);assertTrue(page.hasMore());assertEquals(20,page.items().size());
        assertEquals(3,service.list(a,"OWNED",20,20).items().size());assertTrue(service.list(b,"OWNED",20,0).items().isEmpty());
        assertTrue(service.list(a,"AVAILABLE",20,0).items().stream().allMatch(i -> i.status()==CouponPage.Status.ALREADY_CLAIMED));
        assertTrue(service.list(b,"AVAILABLE",20,0).items().stream().allMatch(i -> i.status()==CouponPage.Status.CAN_CLAIM));
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.001","1e2","NaN","92233720368547758.08","","+1"})
    void malformedMoneyIsRejected(String value){assertThrows(IllegalArgumentException.class,() -> CouponCampaign.amount(value,false));}
}
