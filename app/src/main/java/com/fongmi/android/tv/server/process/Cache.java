package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.utils.Prefers;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.response.Response;

import java.util.Map;

public class Cache implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String path) {
        return "/cache".equals(path);
    }

    private String getKey(String rule, String key) {
        return "cache_" + (TextUtils.isEmpty(rule) ? "" : rule + "_") + key;
    }

    @Override
    public Response doResponse(IHTTPSession session, String path, Map<String, String> files) {
        Map<String, String> params = session.getParms();
        String action = params.get("do");
        String rule = params.get("rule");
        String key = params.get("key");
        if ("get".equals(action)) return Nano.ok(Prefers.getString(getKey(rule, key)));
        if ("set".equals(action)) Prefers.put(getKey(rule, key), params.get("value"));
        if ("del".equals(action)) Prefers.remove(getKey(rule, key));
        return Nano.ok();
    }
}
