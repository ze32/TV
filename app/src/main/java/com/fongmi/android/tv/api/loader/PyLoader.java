package com.fongmi.android.tv.api.loader;

import android.content.Context;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private Object loader;
    private String recent;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
        init();
    }

    public void clear() {
        for (Spider spider : spiders.values()) App.execute(spider::destroy);
        spiders.clear();
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    private void init() {
        try {
            loader = Class.forName("com.fongmi.chaquo.Loader").newInstance();
        } catch (Throwable ignored) {
        }
    }

    public Spider getSpider(String key, String api, String ext) {
        try {
            if (spiders.containsKey(key)) return spiders.get(key);
            Method method = loader.getClass().getMethod("spider", Context.class, String.class);
            Spider spider = (Spider) method.invoke(loader, App.get(), api);
            spider.init(App.get(), ext);
            spiders.put(key, spider);
            return spider;
        } catch (Throwable e) {
            e.printStackTrace();
            return new SpiderNull();
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            if (!params.containsKey("siteKey")) return spiders.get(recent).proxyLocal(params);
            return BaseLoader.get().getSpider(params).proxyLocal(params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
