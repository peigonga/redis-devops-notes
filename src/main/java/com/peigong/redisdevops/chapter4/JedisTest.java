package com.peigong.redisdevops.chapter4;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Tuple;

import java.net.URL;
import java.util.*;

/**
 * @author: lilei
 * @create: 2020-06-12 13:53
 **/
public class JedisTest {

    public static Logger logger = LoggerFactory.getLogger(JedisTest.class);

    public static void main(String[] args) {
        Jedis jedis = null;
        Properties prop = new Properties();
        try {
            prop.load(JedisTest.class.getResourceAsStream("/conf/conf.properties"));
            String host = prop.getProperty("redis.host");
            String port = prop.getProperty("redis.port");
            jedis = new Jedis(host, Integer.valueOf(port));

            //string
            Long ret = jedis.setnx("hello", "你好");
            System.out.println(ret);
            String str = jedis.get("hello");
            System.out.println(str);
            jedis.incr("counter");
            System.out.println("------");

            //hash
            jedis.hset("myhash", "f1", "v1");
            jedis.hset("myhash", "f2", "v2");
            Map<String, String> myhash = jedis.hgetAll("myhash");

            //list
            jedis.rpush("mylist", "1");
            jedis.rpush("mylist", "2");
            jedis.rpush("mylist", "3");
            List<String> mylist = jedis.lrange("mylist", 0, -1);

            //set
            jedis.sadd("myset", "a","b");
            jedis.sadd("myset", "b");
            Set<String> myset = jedis.smembers("myset");

            //zset
            jedis.zadd("myzset", 70, "tom");
            jedis.zadd("myzset", 65, "jerry");
            jedis.zadd("myzset", 81, "rose");
            Set<Tuple> myset1 = jedis.zrangeWithScores("myzset", 0, -1);

            String id = "policy:id:xxx";
            Policy policy = new Policy();
            policy.setName("xxx");
            policy.setId(id);
            policy.setDate("2020-06-06");
            byte[] serialize = ProtostuffSerializer.serialize(policy);
            jedis.setnx(id.getBytes(), serialize);
            Policy deserialize = ProtostuffSerializer.deserialize(jedis.get(id.getBytes()));
            System.out.println(deserialize.toString());
        } catch (Exception e) {
            logger.error("failed",e);
        }finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

}
