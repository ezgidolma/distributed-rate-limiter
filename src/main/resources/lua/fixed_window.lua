local current = redis.call("INCR", KEYS[1])

if current == 1 then
    redis.call("PEXPIRE", KEYS[1], ARGV[2])
end

if current > tonumber(ARGV[1]) then
    local ttl = redis.call("PTTL", KEYS[1])
    return { 0, 0, ttl }
end

return { 1, tonumber(ARGV[1]) - current, 0 }