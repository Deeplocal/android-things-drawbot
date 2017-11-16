package com.deeplocal.drawbot;

import android.graphics.Bitmap;
import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Server {

    public static final String TAG = "abc";

//    public static void sendReset(final RequestQueue requestQueue) {
//
//        final String url = "http://" + MainActivity.SERVER_IP + "/reset";
//
//        StringRequest request = new StringRequest(Request.Method.GET, url,
//                new Response.Listener<String>() {
//                    @Override
//                    public void onResponse(String response) {
//                        if (!response.equals("ok"))
//                            Log.d(TAG, "GET /reset OK");
//                        else
//                            Log.d(TAG, "GET /reset ERROR");
//                    }
//                }, new Response.ErrorListener() {
//            @Override
//            public void onErrorResponse(VolleyError error) {
//                error.printStackTrace();
//                Log.d(TAG, "GET /reset ERROR");
//            }
//        });
//
//        requestQueue.add(request);
//    }

    public static void sendLines(ArrayList<Line> lines, RequestQueue requestQueue) {

        final String linesString = Utilities.toJsonString(lines);

        final String url = "http://" + MainActivity.SERVER_IPS[MainActivity.mKioskNum] + "/lines";

        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "Sent lines (" + response + ")");
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Error sending lines");
                error.printStackTrace();
            }
        })
        {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("lines", linesString);
                return params;
            }
        };

        requestQueue.add(postRequest);
    }

    public static void get(final String endpoint, RequestQueue requestQueue) {

        final String url = "http://" + MainActivity.SERVER_IPS[MainActivity.mKioskNum] + endpoint;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, String.format("GET %s OK", endpoint));
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, String.format("GET %s Error: %s", endpoint, error.toString()));
                error.printStackTrace();
            }
        });
        requestQueue.add(request);
    }

    public static void uploadFile(final Bitmap bitmap, RequestQueue requestQueue) {

        final String url = "http://" + MainActivity.SERVER_IPS[MainActivity.mKioskNum] + "/upload";

        VolleyMultipartRequest multipartRequest = new VolleyMultipartRequest(Request.Method.POST, url,
                new Response.Listener<NetworkResponse>() {
                    @Override
                    public void onResponse(NetworkResponse response) {
                        String resultResponse = new String(response.data);
                        Log.d(TAG, "Volley response = " + resultResponse);
                        // parse success output
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        })
        {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("name", "Oscar");
                return params;
            }

            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> params = new HashMap<>();
                params.put("file", new DataPart("preview.png", getFileDataFromDrawable(bitmap)));

                return params;
            }
        };

        requestQueue.add(multipartRequest);
    }

    private static byte[] getFileDataFromDrawable(Bitmap bitmap) {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    public static void networkLog(final String s, RequestQueue requestQueue) {

        final String url = "http://" + MainActivity.SERVER_IPS[MainActivity.mKioskNum] + "/log";

        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "Volley response = " + response);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        })
        {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("message", s);
                return params;
            }
        };

        requestQueue.add(postRequest);
    }
}
