package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Okio;

public class ResponseInterceptor implements Interceptor {

    private final ConcurrentHashMap<String, String> redirectMap;
    private final ConcurrentHashMap<String, JsonObject> headerMap;

    public ResponseInterceptor() {
        headerMap = new ConcurrentHashMap<>();
        redirectMap = new ConcurrentHashMap<>();
    }

    public synchronized void setHeaders(List<JsonElement> items) {
        for (JsonElement item : items) {
            JsonObject object = Json.safeObject(item);
            headerMap.put(object.get("host").getAsString(), object.get("header").getAsJsonObject());
        }
    }

    public void clear() {
        headerMap.clear();
        redirectMap.clear();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = check(chain.request());
        Response response = chain.proceed(request);
        if ("deflate".equals(response.header(HttpHeaders.CONTENT_ENCODING))) return deflate(response);
        if (response.code() == 406 && redirectMap.containsKey(request.url().toString())) return redirect(request, response);
        if (response.code() == 302 && response.header(HttpHeaders.LOCATION) != null) redirectMap.put(response.header(HttpHeaders.LOCATION), request.url().toString());
        return response;
    }

    private Request check(Request request) {
        String host = request.url().host();
        Request.Builder builder = request.newBuilder();
        if (!headerMap.containsKey(host)) return request;
        for (Map.Entry<String, JsonElement> entry : headerMap.get(host).entrySet()) builder.header(entry.getKey(), entry.getValue().getAsString());
        return builder.build();
    }

    private Response redirect(Request request, Response response) {
        return new Response.Builder().request(request).protocol(response.protocol()).code(302).message("Found").header(HttpHeaders.LOCATION, redirectMap.get(request.url().toString())).build();
    }

    private Response deflate(Response response) {
        InflaterInputStream is = new InflaterInputStream(response.body().byteStream(), new Inflater(true));
        return response.newBuilder().headers(response.headers()).body(new ResponseBody() {
            @Nullable
            @Override
            public MediaType contentType() {
                return response.body().contentType();
            }

            @Override
            public long contentLength() {
                return response.body().contentLength();
            }

            @NonNull
            @Override
            public BufferedSource source() {
                return Okio.buffer(Okio.source(is));
            }
        }).build();
    }
}
