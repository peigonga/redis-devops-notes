package com.peigong.redisdevops.chapter10;

import com.peigong.redisdevops.chapter4.JedisTest;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * @author: lilei
 * @create: 2020-06-15 16:13
 **/
public class JedisClusterTest {

    public static void main(String[] args) throws Exception {
        Properties prop = new Properties();
        prop.load(JedisTest.class.getResourceAsStream("/conf/conf.properties"));
        String host = prop.getProperty("redis.host");
        Set<HostAndPort> nodes = new HashSet<HostAndPort>();
        nodes.add(new HostAndPort(host, 6379));
        nodes.add(new HostAndPort(host, 6380));
        nodes.add(new HostAndPort(host, 6381));
        nodes.add(new HostAndPort(host, 6382));
        nodes.add(new HostAndPort(host, 6383));
        nodes.add(new HostAndPort(host, 6384));
        GenericObjectPoolConfig pool = new GenericObjectPoolConfig();
        JedisCluster cluster = new JedisCluster(nodes, pool);
        String ret = cluster.get("key:test:2");
        System.out.println(ret);
    }

}
