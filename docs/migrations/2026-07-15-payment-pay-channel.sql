-- docs/migrations/2026-07-15-payment-pay-channel.sql
ALTER TABLE payment
  ADD COLUMN pay_channel VARCHAR(16) DEFAULT NULL COMMENT '支付渠道: MINIAPP/MP_H5' AFTER pay_method;
