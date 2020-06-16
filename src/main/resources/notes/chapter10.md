## 集群

### 数据分布
理论：分布式数据库首先要解决把整个数据集按照分区规则映射到多个节点的问题，即
把数据集划分到多个节点上，每个节点负责整体数据的一个子集。需要重点关注的是
数据分区规则。常见的有哈希分区和顺序分区，Redis Cluster采用哈希分区规则。

常见的哈希分区规则有几种：
1. 节点取余分区：使用特定的数据，如Redis的键或用户ID，再根据节点数量N使用公式:
    hash(key)%N计算出哈希值，用来决定数据映射到哪一个节点上。这种方案存在一个
    问题：当结点数量变化时，如扩容或收缩节点，数据节点映射关系需要重新计算，会
    导致数据的重新迁移。优点是简单
2. 一致性哈希分区：实现思路是位系统中的每个节点分配一个token，范围一般在0-2<sup>32，
   这些token构成一个哈希环。数据读写执行节点查找操作时，现根据key计算哈希值，
   然后顺时针找到第一个大于该哈希值的token节点。这种方式比节点取余最大的好处在于
   加入和删除节点只影响哈希环中相邻的节点，对其他节点无影响。但存在几个问题
    - 加减节点或造成哈希环中部分数据无法命中，需要手动处理或忽略这部分数据，因此
      常用与缓存场景
    - 当使用少量节点时，节点变化将大范围影响哈希环中的数据映射，所以这种方式不适合
      少量节点的分布式方案
    - 普通的一致性哈希分区在增减节点时需要增加一倍或减去一半节点才能保证数据和负载的均衡
3. 虚拟槽分区：巧妙的使用了哈希空间，使用分散度良好的哈希函数把所有数据映射到一个
   固定范围的整数集合中，整数定义为槽(slot)。这个范围一般远远大于节点数，比如Redis
   Cluster槽的范围是0~16383.槽是集群内数据管理和迁移的基本单位。采用大范围槽的
   主要目的是为了方便数据拆分和集群扩展。每个节点会负责一定数量的槽
 
#### Redis数据分区
采用虚拟槽分区，所有键根据哈希函数映射到0~16383整数槽内，
计算公式：slot=CRC16(key)&16383.每一个节点负责维护一部分槽及槽所映射的键值数据。

虚拟槽分区的特点
- 解耦数据和节点之间的关系，简化了节点扩容和收缩难度
- 节点自身维护槽的映射关系，不需要客户端或者代理服务维护槽分区元数据
- 支持节点、槽、键之间的映射查询，用于数据路由、在线伸缩等场景

#### 集群功能的限制
- key批量操作支持有限。目前只支持具有相同slot值的key执行批量操作
- key事务操作支持有限。只支持多key在同一节点上的事务操作
- key作为数据分区的最小力度，因此不能讲一个大的键值对象如hash、list等映射到不同的节点
- 不支持多数据库空间，即只能使用db 0
- 复制结构只支持一层，从节点只能复制主节点，不支持嵌套树状复制结构

### 搭建集群
需要三步
- 准备节点
- 节点握手
- 分配槽

#### 准备节点
节点数量至少为6个才能保证组成完整高可用的集群。建议为集群内所有节点统一目录，一般
划分为三个目录：conf、data、log，分别存放配置、数据和日志
```shell script
port 6379
cluster-enabled tes
cluster-node-timeout 15000
cluster-config-file nodes-6379.conf
```

#### 节点握手
指一批运行在集群模式下的节点通过Gossip协议彼此通信，达到感知对方的过程。
第一步由客户端发起命令：
```shell script
127.0.0.1:6379> cluster meet 127.0.0.1 6380
```

#### 分配槽
```shell script
#!/bin/bash
port=$1
start=$2
end=$3
for slot in `seq $start $end`
do
	echo "addalot:"$slot
	redis-cli -p $port cluster addslots $slot
done
```
```
sh addslot.sh 6379 0 5461
sh addslot.sh 6380 5462 10922
sh addslot.sh 6381 10923 16383
```
当所有槽都分配给节点，查看cluster状态
```shell script
127.0.0.1:6379> cluster info
cluster_state:ok
cluster_slots_assigned:16384
cluster_slots_ok:16384
cluster_slots_pfail:0
cluster_slots_fail:0
cluster_known_nodes:6
cluster_size:3
cluster_current_epoch:5
cluster_my_epoch:1
cluster_stats_messages_ping_sent:1742
cluster_stats_messages_pong_sent:2006
cluster_stats_messages_meet_sent:14
cluster_stats_messages_sent:3762
cluster_stats_messages_ping_received:2006
cluster_stats_messages_pong_received:1756
cluster_stats_messages_received:3762
```
每个负责处理槽的节点应该具有从节点，保证当他出现故障时可以自动进行故障转移
```
cluster replicate <主节点id>
127.0.0.1:6382> cluster replicate 06396006bcc1c07de10df8d8dc1ad3b415a5f51a
127.0.0.1:6383> cluster replicate 21d903cbf420b5f6898228b5aa59fd7ceda4b8f6
127.0.0.1:6384> cluster replicate 5c635fb7321a60390c1ebfbf2eb25c7482748f44
```

#### 使用redis-trib.rb搭建集群
Redis6.0之前可用，redis6.0后可以用redis-cli初始化
```
redis-cli --cluster create \
        127.0.0.1:6379 127.0.0.1:6382 \
        127.0.0.1:6380 127.0.0.1:6383 \
        127.0.0.1:6381 127.0.0.1:6384 \
        --cluster-replicas 
```

#### 扩容集群
步骤如下：
##### 准备新节点
新的节点启动后为孤儿节点
##### 加入集群
```
127.0.0.1:6379>cluster meet 127.0.0.1:6385
127.0.0.1:6379>cluster meet 127.0.0.1:6386
```
新节点一般有两种选择
- 为它迁移槽和数据实现扩容
- 作为其他主节点的从节点福则故障转移
##### 迁移槽和数据
首先计划元首节点的哪些槽需要迁移到新节点，然后进行迁移，迁移的过程是逐个槽进行的。
<br>
流程：
1. 对目标节点发送cluster setslot {slot} importing {sourceNodeId}命令，让
   目标节点准备导入槽数据
2. 对源节点发送cluster setslot {slot} migrating {targetNodeId}明星让源节点
   准备迁出槽数据
3. 源节点循环执行cluster getkeysinslot {slot} {count}命令，获取count个属于
   槽{slot}的数据
4. 在源节点上执行migrate {targetIp} {tragetPort} "" 0 {timeout} keys {keys...}
   命令，把获取的键通过pipeline机制批量迁移到目标节点
5. 重复第3和第4步，直到槽下所有键值数据都迁移到目标节点
6. 向集群内所有朱节点发送cluster setslot {slot} node {targetNodeId}命令，
   通知槽分配给目标节点

伪代码如下
```
def move_slot(source,traget,slot):
    target.cluster("setslot",slot,"importing",source.nodeId);
    source.cluster("setslot",slot,"migrating",target.nodeId);
    while true:
        keys = source.cluster("getkeysinslot",slot,pipepine_size);
        if keys.length == 0:
            break;
        source.call("migrate",target.host,target.port,"",0,timeout,"keys" keys);
    
    for node in nodes:
        if node.flag == 'slave'
            continue;
        node.cluster("setslot",slot,"node",target.nodeId);
```

#### 收缩集群与扩容操作方向相反

低版本的Redis可以使用redis-trib.rb来创建集群、扩容、缩容等功能<br>
高版本(5.0以后)的Redis使用--cluster取代了redis-trib.rb。
详见[官方文档](https://redis.io/topics/cluster-tutorial)


### 路由请求
Redis集群对客户端通信协议做了比较大的修改，为了追求性能最大化，并没有采用代理
的方式而是采用客户端直连的方式(6.0开始提供了Redis-Cluster-Proxy，但是还
不完善。[参考](https://github.com/RedisLabs/redis-cluster-proxy))

#### 请求重定向
在集群模式下，Redis接受任何键相关命令时，首先计算键对应的槽，再根据曹找出所对应的节点，
如果是自身，则处理键命令，否则回复MOVED重定向错误，通知客户端请求正确的节点。
这个过程为MOVED重定向。
```
127.0.0.1:6379> set key:test:2 value-2
(error) MOVED 9252 127.0.0.1:6380
```
使用redis-cli时，可以加入-c参数支持自动重定向
```
[root@VM_0_8_centos cluster-data]# redis-cli -c
127.0.0.1:6379> set key:test:2 value-2
-> Redirected to slot [9252] located at 127.0.0.1:6380
OK
```

### 搭建过程中遇到的问题
- 端口问题：如集群内某一节点端口为6379，防火墙和安全组不知道开放6379端口，
  也要开放16379端口，即集群总线端口，偏移量固定为10000
  
- IP问题：meet的IP需要用客户端访问实际IP(公网或内网)，尽管多节点可能在一个单机
  (生产环境不太可能)，也要使用客户端实际访问的IP，主从+sentinel的场景也一样
  
### 故障转移
#### 故障发现
Redis集群内通过ping/pong消息实现节点通信，消息不但可以传播节点槽信息，还可以
传播其他状态，如主从状态、节点故障等。
##### 主观下线
指某个节点认为另一节点不可用，即下线状态，并不是最终故障判定，只代表意见。
集群中每个节点会定期向其他节点发送ping消息，接收节点回复pong消息作为响应。
如果在cluster-node-timeout时间内通信一直失败，则发送节点会认为接收节点
存在故障，标记为主观下线状态
##### 客观下线
指标记一个节点真正的下线，集群内多个节点都认为该节点不可用，从而
 达成共识的结果。如果是持有槽的主节点发生故障，需要为该节点记性故障转移。
当某个节点判断另一个节点主观下线后，相应的节点状态会跟随消息在集群内传播。
ping/pong消息的消息体会携带集群1/10的其他节点状态数据，当接受节点发现
消息体中含有主观下线的节点状态时，会在本地找到故障节点的ClusterNode结构，
保存到下线报告链表中。通过Gossip消息传播，集群内节点不断收集到故障节点
的下线报告。当半数以上持有槽的主节点都标记某个节点是主观下线时，触发客观
下线流程。
- 为什么必须是负责槽的主节点参与故障发现决策？因为集群模式下只有处理槽
  的主节点才负责读写请求和集群槽等关键信息的维护，而从节点只进行主节点
  数据和状态信息的复制
- 为什么要半数以上处理槽的主节点？必须半数以上是为了应对网络分区等原因
  曹成的集群分割情况，被分割的小集群因为无法完成从主观下线到客观下线
  这一关键过程，从而防止小集群完成故障转移之后继续对外提供服务。
  
#### 故障恢复
故障节点变为客观下线后，如果下线节点是持有槽的主节点，则需要在它的从节点中选出
一个替换它，下线主节点的所有从节点承担故障恢复的义务，当从节点通过内部定时任务
发现资深复制的主节点进入客观下线时，将会触发故障恢复流程

### 集群读写分离
#### 只读连接
集群模式下从节点不接受任何读写请求，发送过来的键命令会重定向到负责槽的主节点上。
当需要使用从节点分担主节点读压力时，可以使用readonly命令打开客户端连接只读状态。
主从中的配置slave-read-only在集群模式下无效。readonly是连接级别生效，每次新建连接时
都需要执行readonly开启只读状态
