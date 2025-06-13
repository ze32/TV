package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Entity(indices = @Index(value = {"uuid", "name"}, unique = true))
public class Device {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    private Integer id;
    @SerializedName("uuid")
    private String uuid;
    @SerializedName("name")
    private String name;
    @SerializedName("ip")
    private String ip;
    @SerializedName("type")
    private int type;

    @Ignore
    @SerializedName("serial")
    private String serial;
    @Ignore
    @SerializedName("eth")
    private String eth;
    @Ignore
    @SerializedName("wlan")
    private String wlan;
    @Ignore
    @SerializedName("time")
    private long time;

    public static Device get() {
        Device device = new Device();
        device.setTime(App.time());
        device.setSerial(Util.getSerial());
        device.setEth(Util.getMac("eth0"));
        device.setWlan(Util.getMac("wlan0"));
        device.setUuid(Util.getAndroidId());
        device.setName(Util.getDeviceName());
        device.setIp(Server.get().getAddress());
        device.setType(Product.getDeviceType());
        return device;
    }

    public static Device get(org.fourthline.cling.model.meta.Device<?, ?, ?> item) {
        Device device = new Device();
        device.setUuid(item.getIdentity().getUdn().getIdentifierString());
        device.setName(item.getDetails().getFriendlyName());
        device.setType(2);
        return device;
    }

    public static Device objectFrom(String str) {
        return App.gson().fromJson(str, Device.class);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUuid() {
        return TextUtils.isEmpty(uuid) ? "" : uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return TextUtils.isEmpty(ip) ? "" : ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public void setEth(String eth) {
        this.eth = eth;
    }

    public void setWlan(String wlan) {
        this.wlan = wlan;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public boolean isLeanback() {
        return getType() == 0;
    }

    public boolean isMobile() {
        return getType() == 1;
    }

    public boolean isDLNA() {
        return getType() == 2;
    }

    public boolean isApp() {
        return isLeanback() || isMobile();
    }

    public String getHost() {
        return isDLNA() ? getUuid() : UrlUtil.host(getIp());
    }

    public Device save() {
        AppDatabase.get().getDeviceDao().insertOrUpdate(this);
        return this;
    }

    public static List<Device> getAll() {
        return AppDatabase.get().getDeviceDao().findAll();
    }

    public static void delete() {
        AppDatabase.get().getDeviceDao().delete();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Device)) return false;
        Device it = (Device) obj;
        return getUuid().equals(it.getUuid()) && getName().equals(it.getName());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    public static class Sorter implements Comparator<Device> {

        public static void sort(List<Device> items) {
            if (items.size() > 1) Collections.sort(items, new Sorter());
        }

        @Override
        public int compare(Device o1, Device o2) {
            int comp = Integer.compare(o1.getType(), o2.getType());
            return comp != 0 ? comp : o1.getName().compareTo(o2.getName());
        }
    }
}
