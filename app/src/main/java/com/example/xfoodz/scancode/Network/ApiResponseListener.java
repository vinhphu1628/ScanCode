package com.example.xfoodz.scancode.Network;

/**
 * Created by Le Trong Nhan on 08/06/2018.
 */

public interface ApiResponseListener<T> {

    void onSuccess(T response);

    void onError(String error);
}
