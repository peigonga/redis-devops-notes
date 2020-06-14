## 小功能大用处
除了基础的数据结构外，Redis还提供了其他附加功能：
- 慢查询分析：通过慢查询分析，找到有问题的命令进行优化
- Redis Shell：功能强大的Redis Shell会有意想不到的实用功能
- Pipeline：通过Pipeline(管道或流水线)机制有效提高客户端性能
- 事务与Lua：制作自己的专属原子命令
- Bitmaps：通过在字符串数据结构上使用位操作，有效节省内存，为开放提供新的思路
- HyperLogLog：一种基于概率的新算法，难以想象的节省内存空间
- 发布订阅：基于发布订阅模式的消息通信机制
- GEO：Redis 3.2提供了基于地理位置信息的功能

### 慢查询分析
关于慢查询功能，需要明确两件事
- 预设阈值怎么设置
- 慢查询记录存放在哪

Redis提供了slowlog-log-slower-than和slowlog-max-len配置来解决这两个问题。
slowlog-log-slower-than单位是微秒，默认是10000，即10毫秒，设为0会记录所有命令，
小于0对任何命令都不记录。

Redis使用了一个列表来存储慢查询日志，slowlog-max-len定义列表的最大长度。

两种方式修改配置，一种是修改配置文件，一种是使用config-set
```
config set slowlog-log-slower-than 20000
config set slowlog-max-len 1000
config rewrite//将配置持久化到配置文件
```
慢查询日志由4部分组成
- 标识id
- 发生时间戳
- 命令耗时，单位微秒
- 执行命令和参数

```
获取当前慢查询日志长度
slowlog len
重置
slowlog reset
```
最佳实践
- slowlog-max-len建议：线上建议调大，记录慢查询时Redis会对长命令做截断操作，
    并不会占用大量内存。增大慢查询列表可以减缓慢查询被剔除的可能，如线上设置为1000以上
- slowlog-log-slower-than建议：需要根据Redis并发量调整该值。由于Redis采用
  单线程响应命令，对于高流量的场景，如果命令执行时间在1毫秒以上，那么Redis最多
  可支撑OPS(Operation Per Second)不到1000，因此对于高OPS场景的Redis建议
  设置为1毫秒
- 慢查询只记录命令实际执行时间，不包括命令排队和网络传输时间。因此客户端执行命令
  的时间会大于命令实际执行时间。因为命令执行排队机制，慢查询会导致其他命令级联阻塞，
  因此当客户端出现请求超时，需要检查该时间点是否有对应的慢查询，从而分析出是否是
  为慢查询导致的命令级联阻塞
- 由于慢查询日志是一个先进先出的队列，也就是说如果慢查询比较多的情况下，可能会丢失
  部分慢查询命令，为了防止这种情况，可以定期执行slow get命令将慢查询日志持久化
  到其他存储中
  
---

### Redis Shell
#### redis-cli
- -r：命令执行多次
    ```
     ./redis-cli -r 3 ping
            PONG
            PONG
            PONG  
    ```
        
- -i：每几秒执行一次
   ```
    ./redis-cli -r 3 -i 1 ping
               PONG
               PONG
               PONG
   ```
        
- -x：代表从标准输入读取数据作为redis-cli的最后一个参数
  ```
  echo "world" | redis-cli -x set hello
          OK
  ```
    
- -a：如果Redis配置了密码，可以用-a
- --scan和--pattern：用于扫描指定模式的键，相当于scan命令
- --slave：把当前客户端模拟成Redis节点的从节点
- --rdb：会请求Redis实例生成并发送RDB持久化文件，保存在本地，
  可以用作持久化文件的定期备份
- --pipe：用于将命令封装成Redis通信协议定义的数据格式，批量发送给Redis执行
- --bigkeys：使用scan命令对Redis的键进行采样，从中找到内存占用比较大的键值，
  这些键可能是系统的瓶颈
- --eval：用于执行指定的lua脚本
- --latency：有三个选项--latency、--latency-history、--latency-dist，都可以
  检测网络延迟，对于Redis开发与运维非常有帮助。
    - --latency：可以测试客户端到目标Redis的网络延迟
        ```
        redis-cli -h {remote} --latency
        例：
        redis-cli -h 127.0.0.1 --latency
        min: 0, max: 1, avg: 0.09 (705 samples)
        ```     
    - --latency-history：--latency只有一条结果，--latency-history可以
      分段式展示延迟信息，可使用-i控制间隔时间
    - --latency-dist：使用统计图表形式输出延迟统计信息
- --stat：可以实时获取Redis的重要统计信息，虽然info命令中的统计信息更全，但是
  能实时看到一些增量的数据(例如requests)对于Redis的运维有一定的帮助
- --raw和--no-raw：no-raw返回必须是原始格式，--raw返回格式化后的结果
    ```
    redis-cli set hello 你好
    OK
    redis-cli get hello
    "\xe4\xbd\xa0\xe5\xa5\xbd"
    redis-cli --raw get hello
    你好
    ```
  
---

#### redis-server
redis-server除了启动Redis外，还有一个--test-memory选项。可用来检测当前操作系统
能否稳定地分配指定容量的内存给Redis
```
redis-server --test-memory 1024
```
当输出passed this test说明内存检测完毕

---

#### redis-benchmark
redis-benchmark可以为Redis做基准测试，他提供了很多选项帮助开发和运维人员测试Redis
的相关性能。
- -c：代表客户端的并发数量，默认是50
- -n：代表客户端请求总量，默认是100000
    ```
    redis-benchmark -c 100 -n 20000
    //代表100个客户端同时请求Redis，工执行20000次
    ====== SET ======
      5000 requests completed in 0.05 seconds
      100 parallel clients
      3 bytes payload
      keep alive: 1
      host configuration "save": 900 1 300 10 60 10000
      host configuration "appendonly": no
      multi-thread: no
    
    88.78% <= 1 milliseconds
    96.02% <= 2 milliseconds
    96.30% <= 3 milliseconds
    98.02% <= 6 milliseconds
    100.00% <= 6 milliseconds
    92592.59 requests per second
    //对于以上结果只是上述命令返回结果的一部分截取，实际上会包含所有数据结构的操作
    ```
- -q：仅显示requests per second信息
    ```
    redis-benchmark -c 100 -n 5000 -q
    PING_INLINE: 113636.37 requests per second
    PING_BULK: 121951.22 requests per second
    SET: 119047.62 requests per second
    GET: 125000.00 requests per second
    INCR: 125000.00 requests per second
    LPUSH: 121951.22 requests per second
    RPUSH: 128205.12 requests per second
    LPOP: 121951.22 requests per second
    RPOP: 119047.62 requests per second
    SADD: 125000.00 requests per second
    HSET: 119047.62 requests per second
    SPOP: 119047.62 requests per second
    LPUSH (needed to benchmark LRANGE): 119047.62 requests per second
    LRANGE_100 (first 100 elements): 45045.04 requests per second
    LRANGE_300 (first 300 elements): 16949.15 requests per second
    LRANGE_500 (first 450 elements): 12406.95 requests per second
    LRANGE_600 (first 600 elements): 9363.30 requests per second
    MSET (10 keys): 92592.59 requests per second
    ```
- -r：向Redis插入随机键。-r选项会在key、counter键上加一个12位后缀，-r1000表示
  只对后四位做随机处理(-r不是随机数的个数)
    ```
    redis-benchmark -c 100 -n 5000 -r 10000
    ```
- -p：代表每个请求pipeline的数据量
- -k <boolean>：代表客户端是否使用keepalive，1为使用，0为不使用，默认值为1
- -t：可以对指定命令进行基准测试
    ```
    redis-benchmark -c 100 -n 5000 -r 10000 -t set,get
    ```
- --cvs：将结果按照cvs格式输出，便于后续处理，如倒出到excel

---

### Pipeline
Redis客户端执行一条命令分为四个过程
1. 发送命令
2. 命令排队
3. 命令执行
4. 返回结果
其中1+4称为Round Trip Time(RTT，往返时间)<br>
Redis提供了批量操作命令(如mget、mset等)，有效节约RTT。但大部分命令是不支持
批量操作的。例如执行n次hgetall操作需要消耗n次RTT，在服务器与客户端在不同的机器上的场景，
这n次RTT的损耗比较大，与Redis的高并发高吞吐特性背道而驰。<br>
Pipeline机制能改善上面这类问题，它能将一组Redis命令进行组装，通过一次RTT传输给
Redis，再将这组命令的执行结果按顺序返回给客户端

#### 性能测试
- Pipeline执行速度一般比逐条执行要快
- 客户端和服务器的网络延迟越大，Pipeline的效果越明显

*在不同网络下，10000条set非Pipeline和Pipeline的执行时间对比

网络 | 延迟 | 非Pipeline | Pipeline
:---: | :---: | :---: | :---:
本机 | 0.17ms | 573ms | 134ms
内网服务器 | 0.41ms | 1610ms | 240ms
异地机房 | 7ms | 78499ms | 1104ms

#### 原生批量命令与Pipeline对比
可以使用Pipeline模拟出批量操作的效果，但是要注意与原生批量命令的区别：
- 原生批量命令是原子的，Pipeline是非原子的
- 原生批量命令是一个命令对应多个key，Pipeline支持多个命令
- 原生批量命令是Redis服务端支持实现的，而Pipeline需要服务器和客户端共同实现

#### 最佳实践
Pipeline组装的命令个数不能没有节制，否则一次组装的Pipeline数据量过大，一方面
会增加客户端的等待时间，另一方面会造成一定的网络阻塞，可以将一次包含大量命令的
Pipeline拆分成多次较小的Pipeline来完成。

---

### 事务与Lua
为了保证多条命令组合的原子性，Redis提供了简单的事务功能以及集成Lua脚本来解决这个
问题。

#### 事务
    127.0.0.1:6379> multi
    OK
    127.0.0.1:6379> sadd user:a:follow user:b
    QUEUED  //开启了事务之后，执行命令返回结果为QUEUED，并没有直接执行，而是暂时保存在Redis中
    127.0.0.1:6379> sadd user:b:fans user:a
    QUEUED
    127.0.0.1:6379> exec
    1) (integer) 1
    2) (integer) 1
##### 停止事务执行
    127.0.0.1:6379> multi
    OK
    127.0.0.1:6379> set happening yes
    QUEUED
    127.0.0.1:6379> discard
    OK
    127.0.0.1:6379> get happening
    (nil)
##### 事务中出错
- 命令错误：整个事务无法执行，如set写为sett
- 运行时错误：如事务中执行两条应该是sadd的操作，但有一条写成zadd，导致报错，sadd会执行成功，
  此时需要开发人员自己修复这类问题
 
有些应用场景需要在事务之前，确保事务中的key没有被其他客户端修改过才执行，
否则不执行(类似乐观锁)。Redis提供了watch命令来解决这类问题

时间点 | 客户端-1 | 客户端-2
:---: | :---: | :---: 
T1 | set key "java" | 
T2 | watch key |
T3 | multi |
T4 |  | append key python
T5 | append key jedis | 
T6 | exec | 
T7 | get key |

这种情况下，事务不会被正常执行，会返回nil

#### Lua
在Redis中执行Lua脚本有两种方法
##### eval
    eval 脚本内容 key 个数 key列表 参数列表
    例：
    127.0.0.1:6379> eval 'return "hello " .. KEYS[1] .. ARGV[1]' 1 redis world
    "hello redisworld"
如果Lua脚本较长，可以使用redis-cli --eval 直接执行文件

##### evalsha
首先要将Lua脚本加载到Redis服务端，得到该脚本的SHA1校验，evalsha使用SHA1作为参数
可以直接执行对应的Lua脚本，避免每次发送Lua脚本的开销。这样客户端就不需要每次执行脚本
内容，而脚本也会常驻服务端，脚本功能得到了复用
```shell script
[root@VM_0_8_centos bin]# cat hello.lua
return "hello" .. KEYS[1] .. ARGV[1]
[root@VM_0_8_centos bin]# redis-cli script load "$(cat hello.lua)"
"13aa9692c74cca906d2cc4824e4a61e363540645"
[root@VM_0_8_centos bin]# redis-cli
127.0.0.1:6379> evalsha 13aa9692c74cca906d2cc4824e4a61e363540645 1 redis world
"helloredisworld"
```

##### Lua的Redis API
Lua可以使用redis.call和redis.pcall函数实现对Redis的访问。
两者区别在于redis.call执行失败，脚本执行结束会直接返回失败。
redis.pcall会忽略错误继续执行脚本
```shell script
127.0.0.1:6379> eval 'return redis.call("get", KEYS[1])' 1 nickname
"tom"
```
##### 案例
- Lua脚本在Redis中是原子执行的，执行过程中间不会插入其他命令
- Lua脚本可以帮助开发和运维人员创草除自己定制的命令，并可以将这些命令
  常驻在Redis内存中，实现复用效果
- Lua脚本可以将多条命令一次性打包，有效的减少网络开销

##### 场景用例：
有列表记录热门用户id
```shell script
127.0.0.1:6379> lrange hot:user:list 0 -1
1) "user:1:ratio"
2) "user:8:ratio"
3) "user:3:ratio"
4) "user:99:ratio"
5) "user:72:ratio"
```
user:{id}:ratio代表用户热度，本身也是键
```shell script
127.0.0.1:6379> mget user:1:ratio user:8:ratio user:3:ratio user:99:ratio user:72:ratio
1) "986"
2) "762"
3) "556"
4) "400"
5) "101"
```
现要求将列表内所有键的对应热度甲乙，并且保证是原子执行，此时可以利用lua实现
```lua
-- 将列表中所有元素取出
local mylist = redis.call("lrange", KEYS[1], 0 , -1)
-- 定义局部变量
local count = 0
-- 遍历mylist，每次操作完count加一，最后返回count，即执行次数
for index,key in ipairs(mylist)
do
    redis.call("incr",key)
    count = count + 1
end
return count
```
```shell script
[root@VM_0_8_centos bin]# redis-cli --eval lrange_and_mincr.lua hot:user:list
(integer) 5
[root@VM_0_8_centos bin]# redis-cli
127.0.0.1:6379> mget user:1:ratio user:8:ratio user:3:ratio user:99:ratio user:72:ratio
1) "987"
2) "763"
3) "557"
4) "401"
5) "102"
```

##### Redis如何管理Lua脚本
1. script load script：用于将脚本加载到Redis内存中
2. script exists：
    ```shell script
    script exists sha1 [sha1 ...]
    用于判断sha1是否已经加入到Redis内存
    ```
3. script flush：清除Redis内存已经加载的所有Lua脚本
4. script kill：用于杀掉正在执行的lua脚本，如果lua脚本很耗时或出现死循环等。

### Bitmaps
跳过，后补

### HyperLogLog
并不是一中心的数据结构(实际类型为字符串类型)，而是一种基数算法，通过
HyperLogLog可以利用极小的内存空间完成独立总数的统计，数据集可以是IP、Email、
ID等。特点是内存占用少，但是存在误差，官方给出的误差率是0.81%
##### 添加
    pfadd key element [element ...]
    例：
       127.0.0.1:6379> pfadd unique:ids uuid-1 uuid-2 uuid-3 uuid-4
       (integer) 1
##### 计算独立用户数
    pfcount key [key ...]
    例：
        127.0.0.1:6379> pfcount unique:ids
        (integer) 4
        127.0.0.1:6379> pfadd unique:ids uuid-1 uuid-2 uuid-3 uuid-50
        (integer) 1
        127.0.0.1:6379> pfcount unique:ids
        (integer) 5
##### 合并
    pfmerge destkey sourcekey [sourcekey ...]

数据类型 | 1天 | 1个月 | 1年
:---: | :---: | :---: | :---:
集合类型 | 80M | 2.4G | 28G
HyperLogLog | 15k | 450k | 5M

使用HyperLogLog时需要确认
- 只为了计算独立总数，不需要获取单条数据
- 可以容忍一定误差

---

### 发布订阅
#### 命令
##### 发布消息
    publish channel message
    例：
        127.0.0.1:6379> publish channel:msg hello
        (integer) 0  -- 因为此时没有订阅者，所以返回0
##### 订阅消息
    subscribe channel
    例：
    127.0.0.1:6379> subscribe channel:msg
    Reading messages... (press Ctrl-C to quit)
    1) "subscribe"
    2) "channel:msg"
    3) (integer) 1

注意：
- 客户端在执行订阅命令之后进入订阅状态，只能接收subscribe、psubscribe、
  unsubscribe和punsubscribe四个命令
- 新开启的订阅客户端，无法收到该频道之前的消息，因为Redis不会对发布的消息进行
  持久化

##### 取消订阅
    unsubscribe [channel [channel ...]]

##### 按照模式订阅和取消订阅
    psubscribe pattern [pattern ...]
    unpsubscribe pattern [pattern ...]

##### 查询订阅
    查看活跃的频道
    pubsub channels [pattern]
    查看频道订阅数
    pubsub numsub [channel ...]

#### 使用场景
聊天室、公告牌、服务之间利用消息解耦都可以使用发布订阅模式

### GEO
跳过
