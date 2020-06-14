package com.peigong.redisdevops.chapter9;

import com.peigong.redisdevops.chapter4.JedisTest;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * @author: lilei
 * @create: 2020-06-14 15:56
 **/
public class SentinelTest {

    public static final Logger logger = LoggerFactory.getLogger(SentinelTest.class);

    public static void main(String[] args) {
        Jedis jedis = null;
        Properties prop = new Properties();
        try {
            prop.load(JedisTest.class.getResourceAsStream("/conf/conf.properties"));
            String masterName = prop.getProperty("redis.master.name");
            String sentinelsString = prop.getProperty("redis.sentinels");
            Set<String> sentinels = new HashSet<String>(Arrays.asList(sentinelsString.split(",")));
            GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
            JedisSentinelPool pool = new JedisSentinelPool(masterName,sentinels, poolConfig);
            jedis = pool.getResource();


            String ret = jedis.info("replication");
            System.out.println(ret);
            System.out.println();
            System.out.println(jedis.info("server"));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

}
