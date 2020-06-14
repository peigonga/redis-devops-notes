package com.peigong.redisdevops.chapter4;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * @author: lilei
 * @create: 2020-06-12 16:05
 **/
public class JedisPipelineTest {

    public static final Logger logger = LoggerFactory.getLogger(JedisPipelineTest.class);

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

            Pipeline pipeline = jedis.pipelined();
            pipeline.multi();
            pipeline.sadd("user:1:follow", "user:2");
            pipeline.sadd("user:2:fans", "user:1");
            pipeline.exec();
            pipeline.smembers("user:1:follow");
            pipeline.smembers("user:2:fans");
            //pipeline.sync();
            List<Object> objects = pipeline.syncAndReturnAll();
            for (Object object : objects) {
                System.out.println(object);
            }


            System.out.println("---");

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

}
