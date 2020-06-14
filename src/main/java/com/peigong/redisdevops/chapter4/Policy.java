package com.peigong.redisdevops.chapter4;

import java.io.Serializable;

/**
 * @author: lilei
 * @create: 2020-06-12 15:54
 **/
public class Policy implements Serializable {

    private String id;
    private String name;
    private String date;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "Policy{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
}
