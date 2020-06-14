package com.peigong.redisdevops.chapter4;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Properties;

/**
 * @author: lilei
 * @create: 2020-06-12 16:05
 **/
public class JedisPoolTest {

    public static final Logger logger = LoggerFactory.getLogger(JedisPoolTest.class);

    public static void main(String[] args) {
        Jedis jedis = null;
        Properties prop = new Properties();
        try {
            prop.load(JedisTest.class.getResourceAsStream("/conf/conf.properties"));
            String host = prop.getProperty("redis.host");
            String port = prop.getProperty("redis.port");
            GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
            poolConfig.setMaxTotal(10);//最大连接数
            poolConfig.setMaxIdle(5);//最大空闲数
            //GenericObjectPoolConfig 与常规连接池具有的配置相似
            JedisPool pool = new JedisPool(poolConfig, host, Integer.parseInt(port));
            jedis = pool.getResource();
            String ret = jedis.get("hello");
            System.out.println(ret);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

}
