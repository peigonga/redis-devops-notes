package com.peigong.redisdevops.chapter4;

import com.dyuproject.protostuff.LinkedBuffer;
import com.dyuproject.protostuff.ProtostuffIOUtil;
import com.dyuproject.protostuff.Schema;
import com.dyuproject.protostuff.runtime.RuntimeSchema;

/**
 * @author: lilei
 * @create: 2020-06-12 15:43
 **/
public class ProtostuffSerializer {

    public static byte[] serialize(Policy policy) {
        Schema<Policy> schema = RuntimeSchema.createFrom(Policy.class);
        LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
        try {
            return ProtostuffIOUtil.toByteArray(policy, schema, buffer);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }finally {
            buffer.clear();
        }
    }

    public static Policy deserialize(byte[] bytes) {
        Schema<Policy> schema = RuntimeSchema.createFrom(Policy.class);

        try {
            Policy t = schema.newMessage();
            ProtostuffIOUtil.mergeFrom(bytes, t, schema);
            return t;
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        Policy policy = new Policy();
        policy.setName("xxx");
        policy.setId("id-xxxx");
        policy.setDate("2020-06-06");
        byte[] serialize = serialize(policy);
        Policy deserialize = deserialize(serialize);
        System.out.println(deserialize.getId());
    }

}
