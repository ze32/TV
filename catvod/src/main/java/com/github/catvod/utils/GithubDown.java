package com.github.catvod.utils;

import com.github.catvod.net.OkHttp;

import java.io.File;

public class GithubDown {

    //public static final String URL = "https://tvv.tw/https://raw.githubusercontent.com/ze32/files/master/FongMi_TV/so";
    public static final String URL = "https://gcore.jsdelivr.net/gh/ze32/files@refs/heads/master/FongMi_TV/so";
    public static String ABI;

    private static String getUrl(String name) {
        // https://tvv.tw/https://raw.githubusercontent.com/ze32/files/master/FongMi_TV/so/armeabi_v7a/libjpa.so
        // https://gcore.jsdelivr.net/gh/ze32/files@refs/heads/master/FongMi_TV/so/armeabi_v7a/libjpa.so
        return URL + "/" + ABI + "/" + name;
    }

    public static String getSo(String name) {
        try {
            File file = Path.so(name);
            moveExist(Path.download(), file);
            String url = name.startsWith("http") ? name : getUrl(file.getName());
            if (file.length() < 300) Path.write(file, OkHttp.newCall(url).execute().body().bytes());
            return file.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }

    private static void moveExist(File path, File file) {
        File temp = new File(path, file.getName());
        if (temp.exists()) Path.move(temp, file);
    }
}
