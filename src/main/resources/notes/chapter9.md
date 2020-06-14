## 哨兵
### 基本概念
#### 主从复制的问题
- 一旦主节点出现故障，需要手动将一个节点晋升为主节点，同时需要修改应用方的
  主节点地址，还需要命令其他从节点去复制新的主节点，整个过程需要人工干预。
- 主节点的写能力受到单机的限制
- 主节点的存储能力受到单机限制

#### 高可用
Redis Sentinel具有以下几个功能
- 监控：Sentinel节点会定期监测Redis数据节点、其余Sentinel节点是否可达
- 通知：Sentinel节点会将故障转移的结果通知给应用方
- 主节点故障转移：实现从节点晋升为主节点并维护后续正确的主从关系
- 配置提供者：在Redis Sentinel结构中，客户端在初始化的时候连接的是Sentinel节点
  集合，从中获取主节点信息
  
多个Sentinel节点有两个好处
- 对于节点的故障判断是由多个Sentinel节点共同完成的，这样可以有效防止误判
- Sentinel节点集合是由若干个Sentinel节点组成的，这样即使个别Sentinel节点
  不可用，整个Sentinel节点集合依然是健壮的

### 安装和部署
Redis主节点<redis.conf>
```shell script
port 6379
daemonize yes
logfie "6379.log"
dbfilename "dump-6379.rdb"
dir "/opt/soft/redis/data/"
```
Redis从节点<redis.conf>
```shell script
port 6380
daemonize yes
logfie "6380.log"
dbfilename "dump-6380.rdb"
dir "/opt/soft/redis/data/"
```
Sentinel节点<sentinel.conf>
```shell script
port 26379
daemonize yes
logfie "26379.log"
dir "/opt/soft/redis/data/"
#需要监控127.0.0.1:6379这个节点,2代表至少需要两个节点同意
sentinel monitor mymaster 127.0.0.1 6379 2
sentinel down-after-milliseconds mymaster 30000
sentinel parallel-syncs mymaster 1
sentinel failover-timeout mymaster 180000
```

#### Sentinel配置优化
##### sentinel monitor
    sentinel monitor <mastername> <ip> <port> <quorum>
quonum参数用于故障发现和判定，如将quorum设为2，代表至少有两个Sentinel节点
认为主节点不可达，那么这个不大达的判定就是客观的。同时还与Sentinel节点的领导者
选举有关，至少要有max(quorum,num(sentinels)/2 + 1)个Sentinel节点参与选举，
才能选举出领导者Sentinel，从而完成故障转移。如有5个Sentinel节点，quorum为4，
那么至少要有max(quorum,num(sentinels)/2 + 1)=4个在线Sentinel节点才可以进行
领导者选举

##### sentinel down-after-milliseconds
    sentinel down-after-milliseconds <mastername> <times>
每个sentinel节点都要通过定期发送ping命令来判断Redis数据节点和其余Sentinel
节点是否可达，如果超过了down-after-milliseconds配置的时间且没有有效的回复，
则判定节点不可达，<times>单位毫秒

##### sentinel parallel-syncs
    sentinel parallel-syncs <mastername> <nums>
故障转移后，从节点会像新的主节点发起复制操作，parallel-syncs是用来限制
故障转移后，每次向新的主节点发起复制的节点个数。如果过大会对主节点的机器
造成一定的网络和磁盘I/O开销。

##### sentinel failover-timeout
    sentinel failover-timeout <mastername> <times>
通常被解释成故障转移超时时间，但实际上它作用于故障转移的各个阶段
1. 选出合适从节点
2. 晋升选出的从节点为主节点
3. 命令其余从节点复制新的主节点
4. 等待原主节点回复后命令它区复制新的主节点

failover-timeout的作用体现在四个方面
- 如果redis sentinel对一个主节点故障转移失败，那么下次再对该主节点做故障转移
  的起始时间是failover-timeout的两倍
- 在2阶段，如果sentinel向选出来的从节点执行salveof no one一直失败，当此过程
  超过failover-timeout时，则故障转移失败
- 在2阶段执行成功，sentinel还会执行info命令来确认选出的节点确实晋升为主节点，
  如果此过程时间超过failover-timeout，则故障转移失败
- 如果3阶段执行时间超过了failover-timeout(不包含复制时间)，则故障转移失败。
  即使超过了这个时间，sentinel也会最终配置从节点区同步最新的主节点
  
##### sentinel auth-pass
    sentinel auth-pass <mastername> <password>
如果监控的主节点配置了密码，需要使用该配置

##### sentinel notification-script
    sentinel notification-script <mastername> <script-path>
作用是在故障转移期间，当一些经高级别的sentinel事件发生(如-sdown：客观下线、-odown：主观下线)时，
会触发对应路径的脚本，并向脚本发送相应的事件参数

##### sentinel client-reconfig-script
    sentinel client-reconfig-script <mastername> <script-path>
作用是故障转移后，会触发对应路径的脚本，并向脚本发送故障转移的相关结果

#### 监控多个主节点
指定多个masterName来区分不同的主节点即可

#### 部署技巧
- Sentinel节点不应该部署在同一台物理机器上，因为一个物理机可能有多个虚拟机
- 至少部署3个且奇数个Sentinel节点

### API
- sentinel masters：展示所有被监控的主节点状态及相关统计信息
- sentinel master <mastername>：展示指定master信息
- sentinel salves <mastername>：展示指定master的从节点的信息
- sentinel sentinels <mastername>：展示指定master的sentinel节点集合，不包括当前节点
- sentinel get-master-addr-by-name <mastername>：返回指定master的ip和端口
- sentinel reset <pattern>：当前sentinel对符合pattern的主节点进行
  重置，包含清除主节点相关状态，重新发现从节点和Sentinel
- sentinel failover <mastername>：对指定master强制进行故障转移
- sentinel ckquorum：监测当前可达的sentinel是否达到quorum的个数
- sentinel flushconfig：将sentinel节点的配置强制刷到磁盘上
- sentinel remove <mastername>：取消监控master
- sentinel monitor <mastername> <ip> <port> <quorum> ： 效果同配置文件
- sentinel set <mastername>
- sentinel is-master-down-by-addr：sentinel节点之间用来交换主节点是否下线的判断

### 客户端连接
实现Redis Sentinel客户端的基本步骤如下
1. 遍历Sentinel节点集合获取一个可用的Sentinel节点
2. 通过sentinel get-master-addr-by-name获取对应主节点的信息
3. 验证当前获取的主节点是真正的主节点
4. 保持和Sentinel节点集合的联系，时可获取关于主节点的相关信息