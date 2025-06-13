package com.github.catvod.net;

import android.net.Uri;

import com.github.catvod.utils.Util;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class OkProxySelector extends ProxySelector {

    private final List<String> hosts;
    private Proxy proxy;

    public OkProxySelector() {
        this.hosts = new ArrayList<>();
    }

    public synchronized void addAll(List<String> hosts) {
        this.hosts.addAll(hosts);
    }

    public void clear() {
        this.hosts.clear();
    }

    public void setProxy(String proxy) {
        this.proxy = getProxy(proxy);
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (proxy == null || hosts.isEmpty() || uri.getHost() == null || "127.0.0.1".equals(uri.getHost())) return List.of(Proxy.NO_PROXY);
        for (String host : hosts) if (Util.containOrMatch(uri.getHost(), host)) return List.of(proxy);
        return List.of(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
    }

    private Proxy getProxy(String proxy) {
        Uri uri = Uri.parse(proxy);
        String userInfo = uri.getUserInfo();
        if (userInfo != null && userInfo.contains(":")) setAuthenticator(userInfo);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getPort() <= 0) return Proxy.NO_PROXY;
        if (uri.getScheme().startsWith("http")) return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        if (uri.getScheme().startsWith("socks")) return new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        return Proxy.NO_PROXY;
    }

    private void setAuthenticator(String userInfo) {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userInfo.split(":")[0], userInfo.split(":")[1].toCharArray());
            }
        });
    }
}
