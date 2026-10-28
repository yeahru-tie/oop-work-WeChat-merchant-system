CREATE TRIGGER coupon_published_terms_immutable BEFORE UPDATE ON coupon_campaigns
WHEN OLD.published=1 AND (NEW.published<>1 OR NEW.name<>OLD.name OR NEW.threshold_cents<>OLD.threshold_cents
 OR NEW.discount_cents<>OLD.discount_cents OR NEW.starts_at<>OLD.starts_at OR NEW.ends_at<>OLD.ends_at OR NEW.total<>OLD.total)
BEGIN SELECT RAISE(ABORT,'Published coupon terms are immutable'); END
