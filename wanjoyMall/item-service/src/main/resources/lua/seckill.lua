-- KEYS[1] 库存 key   KEYS[2] 订单 Hash key（field=userId, value=orderId）
-- ARGV[1] userId     ARGV[2] 预生成的 orderId
-- 返回 0 成功 / 1 售罄 / 2 重复下单；四步在一次脚本调用里原子完成
local stock = tonumber(redis.call('get', KEYS[1]))
if stock == nil or stock <= 0 then
    return 1
end
if redis.call('hexists', KEYS[2], ARGV[1]) == 1 then
    return 2
end
redis.call('decr', KEYS[1])
redis.call('hset', KEYS[2], ARGV[1], ARGV[2])
return 0
