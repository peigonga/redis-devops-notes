## 开发运维的陷阱

### Linux配置优化
#### 内存分配控制
##### vm.overcommit_memory
overcommit:Linux操作系统对大部分申请内存的请求都回复yes，以便能运行更多的程序。
申请内存后，并不会马上使用内存，这种技术叫做overcommit。
vm.overcommit_memory有三个可选值：
- 0：表示内核将检查是否有足够的可用内存供应用进程使用；如果有足够的可用内存，内存申请允许；否则，内存申请失败，并把错误返回给应用进程
- 1：表示内核允许分配所有的物理内存，而不管当前的内存状态如何。
- 2：表示内核允许分配超过所有物理内存和交换空间总和的内存

最佳实践：
- 给Redis设置合理的maxmemory，保证机器有20%到30%的闲置内存
- 集中化管理AOF重写和RDB的bgsave
- 设置vm.overcommit_memory=1，防止极端情况下会造成fork失败

##### swappiness
swap对于操作系统比较重要，当物理内存不足时，可以将一部分内存页进行swap操作，
swap空间由硬盘提供，对于需要高并发、高吞吐的应用来说，磁盘IO通常会成为系统瓶颈。
Linux中并不是要等所有物理内存都用完才会用到swap，系统参数swappiness会决定操作系统
使用swap的倾向成都，取值范围0~100，值越大使用swap的概率越高，默认值为60。
<br>
swappiness值说明

值 | 策略
:---: | :---:
0 | Linux3.5以及以上：宁愿用OOM Killer也不用swap<br>Linux3.4以及更早：宁愿用swap也不用OOM Killer
1 | Linux3.5以及以上：宁愿用OOM Killer也不用swap
60 | 默认值
100 | 操作系统会主动地使用swap

*OOM(Out Of Memoey) killer机制指Linux发现内存不足时，强制杀死一些用户进程(非内核进程)，
来保证系统有足够的可用内存进行分配
```shell script
# 临时的，重启后失效
echo 60 > /proc/sys/vm/swappiness
# 永久的
echo vm.swappiness=60 >> /etc/syslog.conf
```

#### THP
Transparent Huge Page，Linux kernel在2.6.38内核增加了THP特性，支持大内存页(2MB)分配，
默认开启。当开启时可以加快fork子进程的速度，但fork操作之后，每个内存页从原来的4KB变为2MB，会
大幅增加重写期间进程内存消耗。同时每次谢明令引起的复制内存页单位放大了512倍，会拖慢写操作的执行
时间，导致大量写操作慢查询

#### NTP
Network Time Protocol，如sentinel和cluster需要多个redis节点，可能涉及多个服务器，
加入多个服务器时间不一致，排查问题的时候会带来困扰。需要定时同步时钟

#### ulimit
open files参数是单个用户同时打开的最大文件个数<br>
Redis建议将open files至少设置成10032，因为默认maxclients默认是10000，这些是用来处理
客户端连接的，redis内部会使用最多32个文件描述符，所以10032 = 10000 + 32

### 安全的Redis

#### 密码机制
Redis提供了requirepass配置为Redis提供了密码功能
#### 伪装危险命令
```shell script
# Redis添加如下配置：
rename-command flushall slfjaoeiwjfiowf
```
使用中可能会有如下麻烦：
- 管理员要对自己的客户端进行修改：如jedis.flushall()
- rename 不支持config set操作，所以要在启动前确定那些命令要用rename
- 如果aof和rdb文件包含了rename-command之前的命令，Redis将无法启动
- Redis源码中有一些命令是写死的，rename-command可能造成Redis无法正常工作，
  如Sentinel会使用config命令，如果对config命令进行rename，会造成Sentinel无法工作
  
最佳实践
- 对一些危险的命令(如flushall)，不管是内网还是外网，一律使用rename-command
- 建议第一次配置Redis时，就应该配置rename-command
- 如果涉及主从，一定要保持各节点配置的一致性，否则存在数据不一致的可能性

#### bind
bind是指定Redis和哪个网卡进行绑定，和客户端是什么网段没有关系

#### 定期备份数据

#### 不使用默认端口

