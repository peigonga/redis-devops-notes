## API的理解和使用

#### 全局命令
- keys *：查看所有键
- dbsize : 键总数
- exists ： 键是否存在
- del key [key...] : 删除键
- expire key seconds : 键过期
- ttl key : 查看键剩余过期时间,返回-1为不过期，-2为键不存在
- type key : 键的数据结构类型，键不存在返回none

#### 数据结构和内部编码
type命令实际返回的就是当前建的数据结构类型，分别是string、hash、list、set、
zset，但是这些知识Redis对外的数据结构。<br>
实际上每种数据结构都有自己底层的内部编码实现，这样Redis会在合适的场景选择合适的
内部编码，可以通过object encoding命令查询内部编码
- string
    - raw
    - int
    - embstr
- hash
    - hashtable
    - ziptable
- list
    - linkedlist
    - ziplist
- set
    - hashtable
    - inset
- zset
    - skiplist
    - ziplist
    
---

#### 单线程架构
Redis使用了单线程架构和I/O多路复用模型来实现高性能的内存数据库服务
- 为什么单线程还能这么快
    1. 纯内存访问：Redis将所有数据放在内存中，内存的响应时长大约为100ns
    2. 非阻塞I/O：Redis使用epoll作为I/O多路复用的技术实现，再加上Redis
       的时间处理模型将epoll中的连接、读写、关闭都转换为时间，不在网络I/O
       上浪费时间
    3. 单线程避免了线程谢欢和竞态产生的消耗
    
单线程会有一个问题：对于每个命令的执行时间是有要求的。如果某个命令执行过长，
会造成其他命令的阻塞，对于Redis这种高性能的服务来说是致命的，所以Redis是
面向快速执行场景的数据库

---

### 字符串
字符串类型是Redis最基础的数据结构。<br>
字符串的实际类型可以是字符串(简单的字符串、复杂的字符串(JSON，XML))、
数字(整数、浮点数)，甚至是二进制(图片、音频、视频)，但是值最大不超过512MB
<br>
#### 常用命令
##### 设置值set常用命令
    ex seconds:为键设置过期时间，也可以使用setex key seconds value
    px milliseconds：为键设置毫秒级过期时间
    nx：键必须不存在才可以设置成功，也可以使用setnx，用于添加，另外setnx
        可以作为分布式锁的一种实现
    xx：与nx相反，键必须存在，用于更新
##### 获取值
    get key
##### 批量设置
    mset key value [key value ...] 
##### 批量获取 
    mget key [key ...] 
##### 计数
    incr key:自增，值不是整数返回错误；值是整数，返回自增后的结果；
            值不存在，认为0自增，返回1
    decr key：自减
    incrby key increment：自增指定数字
    decrby key increment：自减指定数字
    incrbyfloat key increment：自增浮点数

#### 不常用命令
##### 追加值
    append key value
##### 字符串长度
    strlen key
##### 设置并返回原值
    getset key value
    同样会设置值，但是会返回原来的值
    getset hello world
    (nil)
    getset hello redis
    world
##### 设定指定位置的字符串
    setrange key offset value
    例：set redis pest
       OK
       setrange redis 0 b
       4
       get redis
       "best"
##### 获取部分字符串
    getrange key start end

#### 内部编码
字符串类型的内部编码有三种
- int：8个字节长整形
- embstr：小于等于39个字节的字符串
- raw： 大于39个字节的字符串

Redis会根据当前值的类型和长度决定使用哪种内部编码实现

#### 典型使用场景
##### 缓存功能
以典型的缓存场景为例，Redis作为缓存层，MySQL作为存储层，绝大部分请求的数据
都是从Redis中获取。由于Redis具有支撑高并发的特性，所以缓存通常能起到加速读
写和降低后端压力的作用
```java
UserInfo getUserInfo(long id){
    String key = "user:info:" + id;
    String val = redis.get(key);
    UserInfo userInfo;
    if(value != null){
        userInfo = deserialize(val);
    }else{
        userInfo = mysql.get(id);
        if(userInfo != null){
            redis.setex(key,3600,serialize(userInfo));
        }       
    }
    return userInfo;
}
```
##### 计数
许多应用会使用Redis作为计数的基础工具，他可以实现快速计数、查询缓存的功能
```java
long incrVideoCounter(long id){
    String key = "vedio:playCount:" + id;
    return redis.incr(key);
}
```
##### 共享session
一个分布式的Web服务器将用户的session保存在各自服务器中，出于负载均衡的考虑，
分布式服务会将用户的访问均衡到不同的服务器，会导致用户刷新一次访问可能会需要
重新登陆，所以可以使用Redis将Session进行集中管理

##### 限速
如短信验证码
```java
String phone = "138xxxxxxxx";
String key = "shortMsg:limit:" + phone;
boolean exists = redis.set(key,1,"EX 60","NX");
if(!exists || redis.incr(key) < =5){
    //OK
}else{
    //Not OK
}
```

---
    
### 哈希
#### 命令
##### 设置值
    hset key field value
    例：hset user:1 name tom
    另外提供了hsetnx命令，效果同setnx
##### 获取值
    hget key field
    例：hget user:1 name
    如果键或field不存在，返回nil
##### 删除field
    hdel key field [field ...]
    例：hdel user:1 name
##### 计算field个数
    hlen key
##### 批量设置或获取field-value
    hmget key field [field ...]
    hmset key field value [field valie ...]
##### 判断field是否存在
    hexists key field
##### 获取所有field
    hkeys key
##### 获取所有value
    hvals key
##### 获取所有的field-value
    hgetall key
    例：hgetall user:1
       1) "name"
       2) "tom"
       3) "age"
       4) "10"
##### hincrby和hincrbyfloat
    hincrby key field
    hincrbyfloay key field
##### 计算value的字符串的长度
    hstrlen key field

#### 内部编码
hash的内部编码有两种
- ziplist(压缩列表)：当哈希类型元素个数小于hash-max-ziplist-entries配置
    (默认512个)、同时所有制都小于hash-max-ziplist-value配置(默认64字节)时，
    Redis会使用ziplist，ziplist使用更加紧凑的结构实现多个元素的连续存储，
    所以在节省内存方面比hashtable更加优秀
- hashtable：当无法满足ziplist的条件是，Redis使用hashtable作为内部实现，
    因为此时ziplist的读写效率会下降

#### 使用场景
相比于使用字符串序列化缓存用户信息，哈希类型变得更加直观，并且在更新操作上更加
便捷。
```java
UserInfo getUserInfo(long id){
    String key = "user:info:" + id;
    Map userInfoMap = redis.hgetAll(key);
    UserInfo userInfo;
    if(userInfoMap != null){
        userInfo = transferMapToUserInfo(userInfoMap);
    }else{
        userInfo = mysql.get(id);
        if(userInfo != null){
            redis.hmset(key,transferUserInfoToMap(userInfo));
            redis.expire(key,3600);
        }
    }
}
```
##### 哈希类型与关系型数据库有两点不同之处
- 哈希类型是稀疏的，而关系型数据库是完全结构化的，如哈希类型每个键可以有不同
    的field，而关系型数据库一单添加新的列，所有行都要为其设置值(即使为null)
- 关系型数据库可以做复杂的关系查询，而Redis去模拟关系型复杂查询开发困难，维护成本高

---

### 列表
列表类型是用来存储多个有序的字符串，一个列表最多存储2<sup>32</sup>-1个元素。
在Redis中可以对列表两端插入(push)和弹出(pop)，还可以获取指定范围的元素列表、
获取指定索引下标的元素等。是一种比较灵活的数据结构，他可以充当栈和队列的角色

#### 命令
##### 添加操作
##### 右边插入
    rpush key value [value ...]
##### 左边插入
    lpush key value [value ...]
##### 像某个元素前或后插入元素
    linsert key before|after pivot value
    例：linsert mylist before b java
    如果有重复的，则插入从左数第一个匹配到的
    
#### 查找    
##### 从左到右获取列表元素
    lrange key start end
    lrange mylist 0 -1  代表从左到右获取所有元素
索引下标有两个特点
- 索引下标从左到右分别是0到N-1，但是从右到左是-1到-N
- lrange中的end选项包含了自身，这和很多编程语言不包含end不太相同
  如lrange mylist 1 3 获取mylist的第2、3、4个元素

##### 获取指定索引下标的元素
    lindex key index
##### 获取列表长度
    llen key
#### 删除
##### 从左侧弹出
    lpop key
##### 从右侧弹出
    rpop key
##### 删除指定元素
    lrem key count value 
根据count不同分为三种情况
- count>0，从左到右，删除最多count个元素
- count<0，从右到左，删除最多count个元素
- count=0，删除所有

##### 按照索引范围修剪
    ltrim key start end

#### 修改
    lset key index newValue

#### 阻塞操作
    blpop key [key ...] timeout
    brpop key [key ...] timeout
- 列表为空：如果timeout=3，那么客户端要等到3秒后返回，如果timeout=0，
    那么客户端一直阻塞等下去，如果在此期间添加了元素，则客户端立即返回
- 列表不为空，客户端会立即返回

在使用brpop时需要注意
- 如果是多个键，那么brpop会从左至右遍历键，一旦有一个键能淡出元素，客户端立即返回，
    brpop list:1 list:2 list:3
- 如果多个客户端对同一个键执行brpop，那么最先执行brpop命令的客户端可以获取到弹出的值

#### 内部编码
- ziplist：当列表的元素个数小于list-max-ziplist-entries配置(默认512个)，
    同时列表中每个元素的值都小于list_max-ziplist-value配置(默认64字节)，Redis
    会选用ziplist来作为列表的内部实现来减少内存的使用
- linkedlist：当列表无法满足ziplist的条件时，会使用linkedlist作为内部结构

#### 使用场景
1. 消息队列<br>
    使用lpush+brpop命令组合可以实现阻塞队列
2. 文章列表

实际上列表的使用场景很多
lpush+lpop=栈
lpush+rpop=队列
lpush+ltrim=有限集合
lpush+brpop=消息队列 

---

### 集合
集合也是用来保存多个字符串的元素，单机和中不允许有元素重复，且集合中的元素是无序的，
不能通过下标获取元素
    
#### 命令-集合内操作
##### 添加元素
    sadd key element [element ...]
    返回结果为成功添加元素的个数
    例：
        127.0.0.1:6379> sadd myset java
        (integer) 1
        127.0.0.1:6379> sadd myset a java
        (integer) 1
        127.0.0.1:6379> sadd myset a java
        (integer) 0
    
#####  删除操作
    srem key element [element ...]
    返回结果为成功删除元素的个数
    例：
        127.0.0.1:6379> srem myset java b
        (integer) 1

##### 计算元素个数
    scard key
    例：
        127.0.0.1:6379> scard myset
        (integer) 1
他不会遍历集合所有元素，而是直接用Redis内部的变量

##### 判断元素是否在集合中
    sismember key element
    如果在返回1，否则返回0
    例：
        127.0.0.1:6379> sismember myset a
        (integer) 1

##### 随即从集合返回指定个数元素
    srandmember key [count]
    如果不指定count，默认为1
    例：
        127.0.0.1:6379> srandmember myset
        "python"
        127.0.0.1:6379> srandmember myset 2
        1) "a"
        2) "python"

##### 从集合随机弹出元素
    spop key
    
    Redis 3.2之后spop也支持count参数，与srandmember不同，spop的元素
    会从集合中删除
    例：
        127.0.0.1:6379> spop myset 2
        1) "python"
        2) "java"

##### 获取所有元素
    smsmbers key
    例：
        127.0.0.1:6379> smembers myset
        1) "a"
        2) "go"
        3) "rust"
    
    smembes、lrange和hgetall都属于比较重的命令，如果元素过多存在
    阻塞Redis的可能性，这时候可以使用sscan完成

#### 命令-集合间操作
现有两个集合
```
127.0.0.1:6379> sadd user:1:follow it music his sports
(integer) 4
127.0.0.1:6379> sadd user:2:follow it news ent sports
(integer) 4
```
##### 求多个集合的交集
    sinter key [key ...]
    例：
        127.0.0.1:6379> sinter user:1:follow user:2:follow
        1) "sports"
        2) "it"
##### 求多个集合的并集
    sunion key [key ...]
    例：
        127.0.0.1:6379> sunion user:1:follow user:2:follow
        1) "music"
        2) "ent"
        3) "sports"
        4) "it"
        5) "news"
        6) "his"
        
##### 求多个集合的差集
    sdiff key [key ...]
    例： 
        127.0.0.1:6379> sdiff user:1:follow user:2:follow
        1) "music"
        2) "his"

##### 将交集、并集、差集的结果保存
    sinterstore destination key [key ...]
    sunionstore destination key [key ...]
    sdiffstore destination key [key ...]
    例：
        127.0.0.1:6379> sunionstore user:1_2:union user:1:follow user:2:follow
        (integer) 6
        127.0.0.1:6379> smembers user:1_2:union
        1) "music"
        2) "ent"
        3) "sports"
        4) "it"
        5) "news"
        6) "his"

#### 内部编码
- intset：当集合中的元素都是整数且元素个数小于set-max-intset-entries配置(默认512个)时，
  Redis会选用intset来作为集合的内部实现，从而减少内存使用
- hashtable：当集合类型无法满足intset的条件时，Redis或使用hashtable作为集合内部实现

#### 使用场景
集合类型比较典型的使用场景是标签(tag)，例如一个用户可能对娱乐、体育比较感兴趣，
这些兴趣点就是标签。有了这些数据就可以得到喜欢同一个标签的人，以及用户的共同
喜好标签，这些数据对于用户体验以及增强用户粘度比较重要
```
1）给用户添加标签
sadd user:1:tags tag1 tag2 tagx
sadd user:2:tags tag2 tagx
...
2）给标签添加用户
sadd tag1:users user:1
sadd tag2:users user1:user2
1、2应该放在一个事务内执行，防止部分命令失败造成数据不一致
3）用户删除标签
srem user:1:tags tag1
4）标签删除用户
srem tag1:users user:1
3和4也要放到一个事务里
5）计算用户共同感兴趣的标签
sinter user:1:tags user:2:tags

*以上只是实现标签的基本思路
```

---

### 有序集合
它保留了集合不能有重复成员的特性，但是集合中的元素可以排序

| 数据结构 | 元素可重复 | 有序 | 有序实现方式 | 场景 |
|:---:|:---:|:---:|:---:|:---:|
| 列表 | 是 | 是 | 索引下标 | 时间轴、消息队列等
| 集合 | 否 | 否| 无 | 标签、社交
| 有序集合 | 否 | 是 | 分值 | 排行榜系统、社交

#### 集合内命令
##### 添加成员
    zadd key score member [score member ...]
    返回结果代表成功添加的个数
    例：
        127.0.0.1:6379> zadd user:ranking 1 tom 2 jack 3 frank
        (integer) 3

Redis 3.2 添加了四个命令
- nx：member必须不存在，用于添加
- xx：member必须存在，用于更新
- ch：返回此次操作后，有序几何元素和分数发生变化的个数
- incr：对score做增加，相当于zincrby

##### 计算成员个数
    zcard key
    例：
        127.0.0.1:6379> zcard user:ranking
        (integer) 3

##### 某个成员的分数
    zscore key member
    例：
        127.0.0.1:6379> zscore user:ranking jack
        "2"

##### 计算成员的排名
    zrank key member 分数从低到高
    zrevrank key member 分时从高到低
    返回nil代表member不存在
    例：
        127.0.0.1:6379> zadd user:ranking 50 rose
        (integer) 1
        127.0.0.1:6379> zrank user:ranking rose
        (integer) 3
        127.0.0.1:6379> zrevrank user:ranking rose
        (integer) 0
        
##### 删除成员
    zrem key member [member ...]
    例：
        127.0.0.1:6379> zrem user:ranking tom
        (integer) 1
        
##### 增加成员分数
    zincrby key increment member
    返回结果为增量后的值
    例：
        127.0.0.1:6379> zincrby user:ranking 1 jack
        "3"
        
##### 返回指定排名范围的成员
    zrange key start end [withscores] 低到高
    zrevrange key start end [withscores] 高到低
    加上withscore选项同时会返回成员的分数
    例：
        127.0.0.1:6379> zrange user:ranking 0 3 withscores
        1) "frank"
        2) "3"
        3) "jack"
        4) "3"
        5) "rose"
        6) "51"

##### 返回指定分数范围的成员
    zrangebyscore key min max [withscores] [limit offset count]
    zrevrangebyscore key min max [withscores] [limit offset count]
    min和max还支持开区间(小括号)和闭区间(中括号)，-inf和+inf分别代表无限小和无限大
    例：
        127.0.0.1:6379> zrangebyscore user:ranking 50 +inf withscores
        1) "rose"
        2) "51"

##### 返回指定分数范围成员个数
    zcount key min max
    例：
        127.0.0.1:6379> zcount user:ranking 51 100
        (integer) 1
        
##### 删除指定排名内的升序元素
    zremrangebyrank key start end
    注意是从start到end
    例：
        127.0.0.1:6379> zremrangebyrank user:ranking 0 1
        (integer) 2
        
##### 删除指定分数范围的成员
    zremrangebyscore key min max
    例：
        127.0.0.1:6379> zremrangebyscore user:ranking 50 60
        (integer) 1
        
#### 集合间操作
```
现有如下两个有序集合
127.0.0.1:6379> zadd user:ranking:1 1 kris 91 mike 200 frank 220 tim 250 martin 251 tom
(integer) 6
127.0.0.1:6379> zadd user:ranking:2 8 james 77 mike 625 martin 888 tom
(integer) 4
```

##### 交集
    zinterstore destination numkeys key [key ...] [weights weight [weight ...]] [aggregate sum|min|max]
命令参数介绍
- destination：交集的计算结果保存到这个键
- num可以：需要做交集计算键的个数
- key [key ...]：需要做交集计算的键
- weights weight [weight ...]：每个键的权重，在做交集时，每个键中的每个
  member会将自己的分数乘以这个权重，默认为1
- aggregate sum|min|max：计算成员交集后，分值可按照sum、min、max做汇总，
  默认是sum

##### 例：
    127.0.0.1:6379> zinterstore user:ranking:1_inter_2 2 user:ranking:1 user:ranking:2
    (integer) 3
    127.0.0.1:6379> zrange user:ranking:1_inter_2 0 -1 withscores
    1) "mike"
    2) "168"
    3) "martin"
    4) "875"
    5) "tom"
    6) "1139"
##### 如果想让user:ranking:2的权重变为0.5，并且聚合效果使用max：
    127.0.0.1:6379> zinterstore user:ranking:1_inter_2 2 user:ranking:1 user:ranking:2 weights 1 0.5 aggregate max
    (integer) 3
    127.0.0.1:6379> zrange user:ranking:1_inter_2 0 -1 withscores
    1) "mike"
    2) "91"
    3) "martin"
    4) "312.5"
    5) "tom"
    6) "444"
    
---

##### 并集
    zunionstore destination numkeys key [key ...] [weights weight [weight ...]] [aggregate sum|min|max]
    在使用上与交集类似
    例：
        127.0.0.1:6379> zunionstore user:ranking:1_union_2 2 user:ranking:1 user:ranking:2
        (integer) 7
        127.0.0.1:6379> zrange user:ranking:1_union_2 0 -1 withscores
         1) "kris"
         2) "1"
         3) "james"
         4) "8"
         5) "mike"
         6) "168"
         7) "frank"
         8) "200"
         9) "tim"
        10) "220"
        11) "martin"
        12) "875"
        13) "tom"
        14) "1139"
        
---
        
#### 内部实现
- ziplist：当有序集合内部元素个数小于zset-max-ziplist-entries配置(默认128),
  同时每个元素的值都小于zset-max-ziplist-value配置(默认64字节)时，Redis会
  采用ziplist作为有序集合内部实现，可有效减少内存使用
- skiplist(跳跃表)：当ziplist的条件不满足时，有序集合会使用skiplist作为内部实现，
  因为此时ziplist的读写效率会下降
 
---

#### 使用场景
有序集合比较典型的使用场景就是排行榜系统。如视频网站的多维度榜单：按照时间、按照播放数量、
按照获得的赞数。以赞榜单为例
```
1）添加用户赞
上传了视频并获得了3个赞
zadd user:ranking:2016_03_15 3 mike
再获得赞可以使用zincrby
zincrby user:ranking:2016_03_15 1 mike
2）取消用户赞
zrem user:ranking:2016_03_15 mike
3）获取赞数最多的10个用户
zrevrangebyrank user:ranking:2016_03_15 0 9
4）展示用户信息及分数
hgetall user:info:tom
zscore user:ranking:2016_03_15 mike
zrank user:ranking:2016_03_15 mike
```

---

### 键管理

#### 单个键管理
##### 键重命名
    rename key newkey
    需要注意，如果newkey已经存在，则会被覆盖
    127.0.0.1:6379> set a 1
    OK
    127.0.0.1:6379> set b 2
    OK
    127.0.0.1:6379> rename a b
    OK
    127.0.0.1:6379> get b
    "1"
    如果不想被强行rename，可以使用renamenx
有两点需要注意
- 由于重命名键期间会执行del命令删除旧的键，如果键对应的值比较大，会存在阻塞Redis
  的可能性
- 如果rename和renamenx中的key和newkey相同，在Redis 3.2之前和之后返回结果不同，
  3.2之后返回OK，之前返回error

##### 随即返回一个键
    randomkey
##### 键过期
- expire key seconds：键在seconds秒后过期
- expireat key timestamp：键在秒级时间戳timestamp后过期
- ttl和pttl都可以查询键的生育过期时间，pttl精度为毫秒级，返回结果大于0为过期剩余秒数
  或毫秒数，-1为没有过期时间，-2为key不存在
- pexpire key milliseconds：键在milliseconds毫秒后过期
- pexpireat key milliseconds-timestamp：键在毫秒级时间戳后过期
- persist：可以清除键的过期时间，成功返回1

另外需要注意
- 如果expire key不存在，返回结果为0
- 如果过期时间为负，键会被立即删除
- 对于字符串类型键，使用set后，过期时间会被清除
- Redis不支持二级数据结构内部元素的过期功能
- setex不但是原子操作，同时减少了一次网络通信时间

---

##### 迁移键
##### move key db
    把key从源数据库迁移到目标数据库，不常用
##### dump + restore
    可以实现再不用Redis实例之间进行数据迁移的功能，整个迁移非原子性，
    在源Redis使用dump key，在目标Redis使用restore
    dump key
    restore key ttl value，其中ttl为过期时间，0代表无过期，value为dump key返回的值
##### migrate
    migrate host port key|"" destination-db timeout [copy] [replace] [keys key [key ...]]
    是dump+restore+del的结合，具有原子性
    
命令 | 作用域 | 原子性 | 支持多个键
:---: | :---: | :---: | :---:
move | Redis实例内部 | 是 | 否
dump + restore | Redis实例之间 | 否 | 否
migrate | Redis势力之间 | 是 | 是

#### 遍历键
##### 全量遍历键
    keys pattern
pattern使用glob风格通配符
- * 代表匹配任意字符
- ？ 匹配一个字符
- [] 匹配部分字符，如[1,3]匹配1,3，[1-10]匹配1到10
- \x 用来做转义，如星号、问号

不建议在生产环境使用keys，如果确实有需求可以：
- 在一个不对外的节点上执行，不会阻塞客户端请求，但是会影响主从复制
- 如果键比较少，可以使用
- 使用scan命令

##### 渐进式遍历
    scan cursor [match pattern] [count number]
- cursor是必须参数，实际上cursor是一个游标，第一次遍历从0开始，每次scan都会返回
  当前游标值，直到游标为0，表示遍历完成
- match pattern和keys的pattern类似
- count number遍历的键的个数

对应hgetall、smembers、zrange，有分别对应的hscan、sscan、zscan

伪代码示例
```java
String key = "myset";
String pattern = "old:user:*";
String cursor = "0";
while(true){
    ScanResult sr = redis.sscan(key,cursor,pattern);
    List element = sr.getResult();
    if(element != null && element.size() > 0){
        //批量删除
        redis.srem(key,element);
    }
    //获取新的游标
    cursor = sr.getStringCursor();
    if("0".equals(cursor)){
        break;
    }
}
```

##### 清除数据库
    flushdb/flushall
    flushdb清除当前库，flushall清除所有库