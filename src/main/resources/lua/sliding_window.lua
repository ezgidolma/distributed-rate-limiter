local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local key = KEYS[1]

-- pencere dışındaki eski istekleri sil
redis.call("ZREMRANGEBYSCORE", key, 0, now - window)

-- şimdiki isteği ekle (skor = timestamp, değer = timestamp)
redis.call("ZADD", key, now, now)

-- penceredeki toplam istek sayısını al
local count = redis.call("ZCARD", key)

-- key'in süresini pencere kadar uzat
redis.call("PEXPIRE", key, window)

if count > limit then
    return { 0, 0, window }
end

return { 1, limit - count, 0 }