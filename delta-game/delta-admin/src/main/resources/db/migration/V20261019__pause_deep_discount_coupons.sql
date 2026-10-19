-- 暂停发放八折、7.5 折。已发给用户的券仍可使用。
UPDATE coupon SET status = 0 WHERE type IN ('DISCOUNT_8', 'DISCOUNT_75');
