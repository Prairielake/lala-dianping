---
--- Created by lfy57557.
--- DateTime: 2025/3/21 15:36
---

if (redis.call('GET', KEYS[1]) == ARGV[1]) then
    return redis.call('DEL', KEYS[1])
end
return 0